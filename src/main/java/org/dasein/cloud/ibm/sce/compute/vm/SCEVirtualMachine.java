/**
 * Copyright (C) 2012 enStratus Networks Inc
 *
 * ====================================================================
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ====================================================================
 */

package org.dasein.cloud.ibm.sce.compute.vm;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.apache.log4j.Logger;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.ProviderContext;
import org.dasein.cloud.Requirement;
import org.dasein.cloud.ResourceStatus;
import org.dasein.cloud.Tag;
import org.dasein.cloud.compute.AbstractVMSupport;
import org.dasein.cloud.compute.Architecture;
import org.dasein.cloud.compute.ImageClass;
import org.dasein.cloud.compute.ImageCreateOptions;
import org.dasein.cloud.compute.MachineImage;
import org.dasein.cloud.compute.MachineImageState;
import org.dasein.cloud.compute.Platform;
import org.dasein.cloud.compute.VMLaunchOptions;
import org.dasein.cloud.compute.VirtualMachine;
import org.dasein.cloud.compute.VirtualMachineProduct;
import org.dasein.cloud.compute.VmState;
import org.dasein.cloud.ibm.sce.ExtendedRegion;
import org.dasein.cloud.ibm.sce.SCE;
import org.dasein.cloud.ibm.sce.SCEConfigException;
import org.dasein.cloud.ibm.sce.SCEMethod;
import org.dasein.cloud.ibm.sce.identity.keys.SSHKeys;
import org.dasein.cloud.identity.SSHKeypair;
import org.dasein.cloud.identity.ServiceAction;
import org.dasein.util.CalendarWrapper;
import org.dasein.util.uom.storage.Gigabyte;
import org.dasein.util.uom.storage.Megabyte;
import org.dasein.util.uom.storage.Storage;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Random;

/**
 * Implementation of the Dasein Cloud virtual machine support for IBM SmartCloud.
 * <p>Created by George Reese: 7/16/12 7:37 PM</p>
 * @author George Reese
 * @version 2012.04 initial version
 * @version 2012.09 updated with support for new 2012.09 object model (George Reese)
 * @since 2012.04
 */
public class SCEVirtualMachine extends AbstractVMSupport {
    static private final Logger logger = SCE.getLogger(SCEVirtualMachine.class, "std");

    private SCE provider;

    public SCEVirtualMachine(SCE provider) {
        super(provider);
        this.provider = provider;
    }

    @Override
    public @Nonnull VirtualMachine clone(@Nonnull String vmId, @Nonnull String intoDcId, @Nonnull String name, @Nonnull String description, boolean powerOn, @Nullable String... firewallIds) throws InternalException, CloudException {
        VirtualMachine vm = getVirtualMachine(vmId);

        if( vm == null ) {
            throw new CloudException("No such virtual machine: " + vmId);
        }
        ImageCreateOptions options = ImageCreateOptions.getInstance(vm,  name, description);


        MachineImage img = provider.getComputeServices().getImageSupport().captureImage(options);
        VirtualMachineProduct prd = getProduct(vm.getProductId());

        if( prd == null ) {
            throw new CloudException("Unknown product associated with VM");
        }
        long timeout = System.currentTimeMillis() + (CalendarWrapper.MINUTE * 10L);
        String machineImageId = img.getProviderMachineImageId();

        while( img == null || !MachineImageState.ACTIVE.equals(img.getCurrentState()) ) {
            if( System.currentTimeMillis() > timeout ) {
                throw new CloudException("Cloud timed out while waiting for cloning image");
            }
            try { Thread.sleep(15000L); }
            catch( InterruptedException ignore ) { }
            img = provider.getComputeServices().getImageSupport().getImage(machineImageId);
        }
        return launch(machineImageId, prd, intoDcId, name, description, null, null, false, false, firewallIds, new Tag[0]);
    }

    @Override
    public @Nonnull String getConsoleOutput(@Nonnull String vmId) throws InternalException, CloudException {
        ProviderContext ctx = provider.getContext();

        if( ctx == null ) {
            throw new SCEConfigException("No context was configured for this request");
        }
        SCEMethod method = new SCEMethod(provider);

        Document xml = method.getAsXML("instances/" + vmId + "/logs");

        if( xml == null ) {
            return "";
        }
        NodeList locations = xml.getElementsByTagName("Logs");
        StringBuilder console = new StringBuilder();

        for( int i=0; i<locations.getLength(); i++ ) {
            Node item = locations.item(i);

            if( item.hasChildNodes() ) {
                console.append(item.getFirstChild().getNodeValue());
                console.append("\n");
            }
        }
        return console.toString();
    }

    @Override
    public VirtualMachineProduct getProduct(@Nonnull String productId) throws InternalException, CloudException {
        for( Architecture architecture : Architecture.values() ) {
            for( VirtualMachineProduct prd : listProducts(architecture) ) {
                if( prd.getProviderProductId().equals(productId) ) {
                    return prd;
                }
            }
        }
        return null;
    }

    @Override
    public @Nonnull String getProviderTermForServer(@Nonnull Locale locale) {
        return "instance";
    }

    @Override
    public VirtualMachine getVirtualMachine(@Nonnull String vmId) throws InternalException, CloudException {
        ProviderContext ctx = provider.getContext();

        if( ctx == null ) {
            throw new SCEConfigException("No context was configured for this request");
        }
        SCEMethod method = new SCEMethod(provider);

        Document xml = method.getAsXML("instances/" + vmId);

        if( xml == null ) {
            return null;
        }
        NodeList locations = xml.getElementsByTagName("Instance");

        for( int i=0; i<locations.getLength(); i++ ) {
            Node item = locations.item(i);
            VirtualMachine vm = toVirtualMachine(ctx, item);

            if( vm != null ) {
                return vm;
            }
        }
        return null;
    }

    @Override
    public @Nonnull Requirement identifyImageRequirement(@Nonnull ImageClass cls) throws CloudException, InternalException {
        return (cls.equals(ImageClass.MACHINE) ? Requirement.REQUIRED : Requirement.NONE);
    }

    @Override
    public @Nonnull Requirement identifyPasswordRequirement() throws CloudException, InternalException {
        return Requirement.REQUIRED;
    }

    @Override
    public @Nonnull Requirement identifyPasswordRequirement(Platform platform) throws CloudException, InternalException {
    	Requirement passRequirement;
    	if (platform.isWindows()) {
    		passRequirement = Requirement.REQUIRED;
    	}
    	else {
    		passRequirement = Requirement.NONE;
    	}
        return passRequirement;
    }

    @Override
    public @Nonnull Requirement identifyRootVolumeRequirement() throws CloudException, InternalException {
        return Requirement.NONE;
    }

    @Override
    public @Nonnull Requirement identifyShellKeyRequirement(Platform platform) throws CloudException, InternalException {
        return Requirement.REQUIRED;
    }

    @Override
    public @Nonnull Requirement identifyStaticIPRequirement() throws CloudException, InternalException {
        return Requirement.OPTIONAL;
    }

    @Override
    public @Nonnull Requirement identifyVlanRequirement() throws CloudException, InternalException {
        return Requirement.OPTIONAL;
    }

    @Override
    public boolean isAPITerminationPreventable() throws CloudException, InternalException {
        return false;
    }

    @Override
    public boolean isBasicAnalyticsSupported() throws CloudException, InternalException {
        return false;
    }

    @Override
    public boolean isExtendedAnalyticsSupported() throws CloudException, InternalException {
        return false;
    }

    @Override
    public boolean isSubscribed() throws CloudException, InternalException {
        ProviderContext ctx = provider.getContext();

        if( ctx == null ) {
            throw new SCEConfigException("No context was specified for this request");
        }
        try {
            ExtendedRegion region = provider.getDataCenterServices().getRegion(ctx.getRegionId());

            return (region != null && region.isCompute());
        }
        catch( CloudException e ) {
            if( e.getHttpCode() == HttpServletResponse.SC_FORBIDDEN || e.getHttpCode() == HttpServletResponse.SC_UNAUTHORIZED ) {
                return false;
            }
            throw e;
        }
    }

    @Override
    public boolean isUserDataSupported() throws CloudException, InternalException {
        return true;
    }

    @Override
    public @Nonnull VirtualMachine launch(@Nonnull VMLaunchOptions withLaunchOptions) throws CloudException, InternalException {
    	Logger logger = SCE.getLogger(SCEVirtualMachine.class, "launch");
        ProviderContext ctx = provider.getContext();

        if( ctx == null ) {
            throw new SCEConfigException("No context was configured for this request");
        }
        ArrayList<NameValuePair> parameters = new ArrayList<NameValuePair>();
        String keypair = withLaunchOptions.getBootstrapKey();
        String password = null;

        parameters.add(new BasicNameValuePair("name", withLaunchOptions.getHostName()));
        parameters.add(new BasicNameValuePair("instanceType", withLaunchOptions.getStandardProductId()));
        parameters.add(new BasicNameValuePair("imageID", withLaunchOptions.getMachineImageId()));
        parameters.add(new BasicNameValuePair("location", ctx.getRegionId()));
        MachineImage launchImage = provider.getComputeServices().getImageSupport().getImage(withLaunchOptions.getMachineImageId());
        if (launchImage.getPlatform().isUnix()) {
            if( keypair == null ) {
                keypair = identifyKeypair();
            }
            parameters.add(new BasicNameValuePair("publicKey", keypair));
        }
        else if (launchImage.getPlatform().isWindows()) {
            if (withLaunchOptions.getBootstrapUser() != null) {
            	logger.debug("Adding UserName parameter: " + withLaunchOptions.getBootstrapUser());
                parameters.add(new BasicNameValuePair("UserName", withLaunchOptions.getBootstrapUser()));
            }
            password = getRandomPassword(withLaunchOptions.getBootstrapUser());
            parameters.add(new BasicNameValuePair("Password", password));
        }
        
        if( withLaunchOptions.getVlanId() != null ) {
        	logger.debug("Adding vlanID parameter: " + withLaunchOptions.getVlanId());
            parameters.add(new BasicNameValuePair("vlanID", withLaunchOptions.getVlanId()));
        }
        
        String[] ips = withLaunchOptions.getStaticIpIds();

        if( ips.length > 0 ) {
            parameters.add(new BasicNameValuePair("ip", ips[0]));
            if( ips.length > 1 ) {
                for( int i=1; i<ips.length; i++ ) {
                    if( i > 3 ) {
                        logger.warn("Attempt to assign more than 3 secondary IPs to a VM in IBM SmartCloud for account " + ctx.getAccountNumber());
                        break;
                    }
                    parameters.add(new BasicNameValuePair("SecondaryIP", ips[i]));
                }
            }
        }

        String userData = withLaunchOptions.getUserData();

        if( userData != null ) {
             Properties p = new Properties();

             try {
                 p.load(new ByteArrayInputStream(userData.getBytes("utf-8")));
             }
             catch( UnsupportedEncodingException e ) {
                 throw new InternalException(e);
             }
             catch( IOException e ) {
                 throw new InternalException(e);
             }
             for( String key : p.stringPropertyNames() ) {
                 String value = p.getProperty(key);

                 if( value != null ) {
                     parameters.add(new BasicNameValuePair(key, value));
                 }
             }
         }
        SCEMethod method = new SCEMethod(provider);
        String response = method.post("instances", parameters);

        if( response == null ) {
            throw new CloudException("Cloud accepted the post, but no body was in the response");
        }

        Document doc = method.parseResponse(response, true);

        NodeList locations = doc.getElementsByTagName("Instance");

        for( int i=0; i<locations.getLength(); i++ ) {
            Node item = locations.item(i);
            VirtualMachine vm = toVirtualMachine(ctx, item);

            if( vm != null ) {
                vm.setRootPassword(password);
            	vm.setRootUser(withLaunchOptions.getBootstrapUser());
                return vm;
            }
        }
        throw new CloudException("No instance was in the XML response");
    }

    private @Nonnull String identifyKeypair() throws CloudException, InternalException {
        SSHKeys keys = provider.getIdentityServices().getShellKeySupport();

        for( SSHKeypair kp : keys.list() ) {
            String id = kp.getProviderKeypairId();

            if( id != null ) {
                return id;
            }
        }
        SSHKeypair kp = keys.createKeypair("dsn" + System.currentTimeMillis());
        String id = kp.getProviderKeypairId();

        if( id != null ) {
            return id;
        }
        throw new CloudException("Unable to identify keys in the cloud");
    }

    @Override
    public @Nonnull Iterable<String> listFirewalls(@Nonnull String vmId) throws InternalException, CloudException {
        return Collections.emptyList();
    }

    static private Map<Architecture,Collection<VirtualMachineProduct>> products;

    @Override
    public Iterable<VirtualMachineProduct> listProducts(Architecture architecture) throws InternalException, CloudException {
        if( products == null ) {
            ProviderContext ctx = provider.getContext();

            if( ctx == null ) {
                throw new SCEConfigException("No context was configured for this request");
            }
            SCEMethod method = new SCEMethod(provider);

            Document xml = method.getAsXML("offerings/image");

            if( xml == null ) {
                return Collections.emptyList();
            }
            HashMap<String,VirtualMachineProduct> t = new HashMap<String, VirtualMachineProduct>();
            HashMap<String,VirtualMachineProduct> s = new HashMap<String, VirtualMachineProduct>();
            NodeList items = xml.getElementsByTagName("Image");

            for( int i=0; i<items.getLength(); i++ ) {
                HashMap<String,VirtualMachineProduct> prdMap = new HashMap<String, VirtualMachineProduct>();
                NodeList attrs = items.item(i).getChildNodes();
                Architecture a = null;

                for( int j=0; j<attrs.getLength(); j++ ) {
                    Node attr = attrs.item(j);

                    if( attr.getNodeName().equalsIgnoreCase("Architecture") && attr.hasChildNodes() ) {
                        String val = attr.getFirstChild().getNodeValue().trim();

                        if( val.equals("i386") ) {
                            a = Architecture.I32;
                        }
                        else if( val.startsWith("x86") ) {
                            a = Architecture.I64;
                        }
                        else {
                            System.out.println("DEBUG: Unknown architecture: " + val);
                            a = Architecture.I32;
                        }
                    }
                    else if( attr.getNodeName().equalsIgnoreCase("SupportedInstanceTypes") && attr.hasChildNodes() ) {
                        NodeList types = attr.getChildNodes();

                        for( int k=0; k<types.getLength(); k++ ) {
                            Node type = types.item(k);

                            if( type.getNodeName().equalsIgnoreCase("InstanceType") && type.hasChildNodes() ) {
                                VirtualMachineProduct prd = new VirtualMachineProduct();
                                NodeList nodes = type.getChildNodes();

                                for( int l=0; l<nodes.getLength(); l++ ) {
                                    Node node = nodes.item(l);

                                    if( node.getNodeName().equals("ID") && node.hasChildNodes() ) {
                                        prd.setProviderProductId(node.getFirstChild().getNodeValue().trim());
                                    }
                                    else if( node.getNodeName().equals("Label") && node.hasChildNodes() ) {
                                        prd.setName(node.getFirstChild().getNodeValue().trim());
                                    }
                                    else if( node.getNodeName().equals("Detail") && node.hasChildNodes() ) {
                                        prd.setDescription(node.getFirstChild().getNodeValue().trim());
                                    }
                                }
                                if( prd.getProviderProductId() != null ) {
                                    String[] parts = prd.getProviderProductId().split("/");

                                    if( parts.length == 3 ) {
                                        String[] sub = parts[0].split("\\.");

                                        if( sub.length > 0 ) {
                                            parts[0] = sub[sub.length-1];
                                        }
                                        try {
                                            prd.setCpuCount(Integer.parseInt(parts[0]));
                                        }
                                        catch( NumberFormatException ignore ) {
                                            // ignore
                                        }
                                        try {
                                            prd.setRamSize(new Storage<Megabyte>(Integer.parseInt(parts[1]), Storage.MEGABYTE));
                                        }
                                        catch( NumberFormatException ignore ) {
                                            // ignore
                                        }
                                        try {
                                            int idx = parts[2].indexOf("*");

                                            if( idx < 1 ) {
                                                prd.setRootVolumeSize(new Storage<Gigabyte>(Integer.parseInt(parts[2]), Storage.GIGABYTE));
                                            }
                                            else {
                                                prd.setRootVolumeSize(new Storage<Gigabyte>(Integer.parseInt(parts[2].substring(0,idx)), Storage.GIGABYTE));
                                            }
                                        }
                                        catch( NumberFormatException ignore ) {
                                            // ignore
                                        }
                                    }
                                    prdMap.put(prd.getProviderProductId(), prd);
                                }
                            }
                        }
                    }
                }
                if( a != null ) {
                    if( a.equals(Architecture.I32) ) {
                        t.putAll(prdMap);
                    }
                    else if( a.equals(Architecture.I64) ) {
                        s.putAll(prdMap);
                    }
                }
            }
            HashMap<Architecture,Collection<VirtualMachineProduct>> tmp = new HashMap<Architecture, Collection<VirtualMachineProduct>>();

            tmp.put(Architecture.I32, Collections.unmodifiableCollection(t.values()));
            tmp.put(Architecture.I64, Collections.unmodifiableCollection(s.values()));
            products = tmp;
        }
        return products.get(architecture);
    }

    @Override
    public @Nonnull Iterable<ResourceStatus> listVirtualMachineStatus() throws InternalException, CloudException {
        ProviderContext ctx = provider.getContext();

        if( ctx == null ) {
            throw new SCEConfigException("No context was configured for this request");
        }
        SCEMethod method = new SCEMethod(provider);

        Document xml = method.getAsXML("instances");

        if( xml == null ) {
            return Collections.emptyList();
        }
        NodeList locations = xml.getElementsByTagName("Instance");
        ArrayList<ResourceStatus> vms = new ArrayList<ResourceStatus>();

        for( int i=0; i<locations.getLength(); i++ ) {
            Node item = locations.item(i);
            ResourceStatus vm = toStatus(item);

            if( vm != null ) {
                vms.add(vm);
            }
        }
        return vms;
    }

    @Override
    public @Nonnull Iterable<VirtualMachine> listVirtualMachines() throws InternalException, CloudException {
        ProviderContext ctx = provider.getContext();

        if( ctx == null ) {
            throw new SCEConfigException("No context was configured for this request");
        }
        SCEMethod method = new SCEMethod(provider);

        Document xml = method.getAsXML("instances");

        if( xml == null ) {
            return Collections.emptyList();
        }
        NodeList locations = xml.getElementsByTagName("Instance");
        ArrayList<VirtualMachine> vms = new ArrayList<VirtualMachine>();

        for( int i=0; i<locations.getLength(); i++ ) {
            Node item = locations.item(i);
            VirtualMachine vm = toVirtualMachine(ctx, item);

            if( vm != null ) {
                vms.add(vm);
            }
        }
        return vms;
    }

    @Override
    public void reboot(@Nonnull String vmId) throws CloudException, InternalException {
        ProviderContext ctx = provider.getContext();

        if( ctx == null ) {
            throw new SCEConfigException("No context was configured for this request");
        }

        ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
        SCEMethod method = new SCEMethod(provider);

        params.add(new BasicNameValuePair("state", "restart"));
        method.put("instances/" + vmId, params);
    }

    @Override
    public boolean supportsPauseUnpause(@Nonnull VirtualMachine vm) {
        return false;
    }

    @Override
    public boolean supportsStartStop(@Nonnull VirtualMachine vm) {
        return false;
    }

    @Override
    public boolean supportsSuspendResume(@Nonnull VirtualMachine vm) {
        return false;
    }

    @Override
    public void terminate(final @Nonnull String vmId) throws InternalException, CloudException {
        ProviderContext ctx = provider.getContext();

        if( ctx == null ) {
            throw new SCEConfigException("No context was configured for this request");
        }
        long timeout = System.currentTimeMillis() + (CalendarWrapper.MINUTE * 20);
        VirtualMachine vm = getVirtualMachine(vmId);

        while( vm != null && vm.getCurrentState().equals(VmState.PENDING) ) {
            if( System.currentTimeMillis() >= timeout ) {
                throw new CloudException("Termination timed out");
            }
            try { Thread.sleep(15000L); }
            catch( InterruptedException ignore ) { }
            vm = getVirtualMachine(vmId);
        }
        if( vm == null ) {
            throw new CloudException("The VM " + vmId + " went away");
        }
        SCEMethod method = new SCEMethod(provider);

        method.delete("instances/" + vmId);
    }

    @Override
    public @Nonnull String[] mapServiceAction(@Nonnull ServiceAction action) {
        return new String[0];
    }

    private @Nonnull String parseAddress(@Nonnull Node ipNode) {
        NodeList attrs = ipNode.getChildNodes();
        String address = null;

		for( int j=0; j<attrs.getLength(); j++ ) {
			Node attr= attrs.item(j);
			if( attr.getNodeName().equalsIgnoreCase("IP") && attr.hasChildNodes() ) {
				address = attr.getFirstChild().getNodeValue().trim();
			}

		}
        return address;
    }

    private @Nonnull String[] addAddress(@Nullable String[] current, @Nonnull String ipAddress) {
        String address = ipAddress;
        for (String currentIp : current) {
            if (currentIp.equals(address)) {
                address = null;
            }
        }

        if( address == null ) {
            return (current == null ? new String[0] : current);
        }

        String[] addresses = new String[current == null ? 1 : current.length+1];
        int i = 0;

        if( current != null ) {
            for( String addr : current ) {
                addresses[i++] = addr;
            }
        }
        addresses[i] = address;
        return addresses;
    }

    public @Nullable VirtualMachine toVirtualMachine(@Nonnull ProviderContext ctx, @Nullable Node node) throws CloudException, InternalException {
        if( node == null ) {
            return null;
        }
        VirtualMachine vm = new VirtualMachine();

        vm.setRebootable(true);
        vm.setArchitecture(Architecture.I64);
        vm.setClonable(false);
        vm.setCurrentState(VmState.PENDING);
        vm.setImagable(true);
        vm.setPausable(false);
        vm.setPersistent(true);
        vm.setPlatform(Platform.UNKNOWN);
        NodeList nodes = node.getChildNodes();

        for( int i=0; i<nodes.getLength(); i++ ) {
            Node attr = nodes.item(i);
            String nodeName = attr.getNodeName();

            if( nodeName.equalsIgnoreCase("ID") && attr.hasChildNodes() ) {
                vm.setProviderVirtualMachineId(attr.getFirstChild().getNodeValue().trim());
            }
            else if( nodeName.equalsIgnoreCase("Name") && attr.hasChildNodes() ) {
                vm.setName(attr.getFirstChild().getNodeValue().trim());
            }
            else if( nodeName.equalsIgnoreCase("Location") && attr.hasChildNodes() ) {
                vm.setProviderRegionId(attr.getFirstChild().getNodeValue().trim());
            }
            else if( nodeName.equalsIgnoreCase("Owner") && attr.hasChildNodes() ) {
                vm.setProviderOwnerId(attr.getFirstChild().getNodeValue().trim());
            }
            else if( nodeName.equalsIgnoreCase("Hostname") && attr.hasChildNodes() ) {
                vm.setPublicDnsAddress(attr.getFirstChild().getNodeValue().trim());
            }
            else if( nodeName.equalsIgnoreCase("IP") && attr.hasChildNodes() ) {
                String ip = attr.getFirstChild().getNodeValue().trim();
                boolean isPublic = isPublicIpAddress(ip);
                String[] addrs;
                if (isPublic) {
                    addrs = vm.getPublicIpAddresses();
                }
                else {
                    addrs = vm.getPrivateIpAddresses();
                }

                if( addrs == null || addrs.length == 0 ) {
                    if (isPublic) {
                        vm.setPublicIpAddresses(new String[]{ ip });
                    }
                    else {
                        vm.setPrivateIpAddresses(new String[]{ ip });
                    }
                }
                else {
                    String[] tmp = new String[addrs.length + 1];

                    //noinspection ManualArrayCopy
                    for(int idx=0; idx<addrs.length; idx++ ) {
                        tmp[idx] = addrs[idx];
                    }
                    tmp[tmp.length-1] = ip;
                    addrs = tmp;
                    if (isPublic) {
                        vm.setPublicIpAddresses(addrs);
                    }
                    else {
                        vm.setPrivateIpAddresses(addrs);
                    }
                }
            }
            else if( nodeName.equalsIgnoreCase("ImageID") && attr.hasChildNodes() ) {
                vm.setProviderMachineImageId(attr.getFirstChild().getNodeValue().trim());
            }
            else if( nodeName.equalsIgnoreCase("InstanceType") && attr.hasChildNodes() ) {
                vm.setProductId(attr.getFirstChild().getNodeValue().trim());
            }
            else if( nodeName.equalsIgnoreCase("Status") && attr.hasChildNodes() ) {
                String status = attr.getFirstChild().getNodeValue().trim();

                vm.setCurrentState(toVmState(status));
            }
            else if( nodeName.equalsIgnoreCase("LaunchTime") && attr.hasChildNodes() ) {
                vm.setCreationTimestamp(provider.parseTimestamp(attr.getFirstChild().getNodeValue().trim()));
            }
             else if( nodeName.equalsIgnoreCase("ExpirationTime") && attr.hasChildNodes() ) {
                // what exactly is expiration time?
            }
            else if( nodeName.equalsIgnoreCase("PrimaryIP") && attr.hasChildNodes() ) {
                String ipAddress = parseAddress(attr);
                boolean isPublic = isPublicIpAddress(ipAddress);
                if (isPublic) {
                    vm.setPublicIpAddresses(addAddress(vm.getPublicIpAddresses(), ipAddress));
                }
                else {
                    vm.setPrivateIpAddresses(addAddress(vm.getPrivateIpAddresses(), ipAddress));
                }
            }
            else if( nodeName.equalsIgnoreCase("SecondaryIP") && attr.hasChildNodes() ) {
                String ipAddress = parseAddress(attr);
                boolean isPublic = isPublicIpAddress(ipAddress);
                if (isPublic) {
                    vm.setPublicIpAddresses(addAddress(vm.getPublicIpAddresses(), ipAddress));
                }
                else {
                    vm.setPrivateIpAddresses(addAddress(vm.getPrivateIpAddresses(), ipAddress));
                }
            }
            else if( nodeName.equalsIgnoreCase("Volume") && attr.hasChildNodes() ) {
                // volume
            }
            else if( nodeName.equalsIgnoreCase("Vlan") && attr.hasChildNodes() ) {
                NodeList items = attr.getChildNodes();

                for( int j=0; j< items.getLength(); j++ ) {
                    Node item = items.item(j);

                    if( item.getNodeName().equalsIgnoreCase("ID") && item.hasChildNodes() ) {
                        vm.setProviderVlanId(item.getFirstChild().getNodeValue().trim());
                    }
                }
            }
            else if( nodeName.equalsIgnoreCase("Software") && attr.hasChildNodes() ) {
                NodeList software = attr.getChildNodes();

                for( int j=0; j<software.getLength(); j++ ) {
                    Node snode = software.item(j);
                    String type = null;
                    String name = null;

                    if( snode.hasChildNodes() ) {
                        NodeList sattrs = snode.getChildNodes();

                        for( int k=0; k<sattrs.getLength(); k++ ) {
                            Node sattr = sattrs.item(k);

                            if( sattr.getNodeName().equalsIgnoreCase("Name") && sattr.hasChildNodes() ) {
                                name = sattr.getFirstChild().getNodeValue().trim();
                            }
                            else if( sattr.getNodeName().equalsIgnoreCase("Type") && sattr.hasChildNodes() ) {
                                type = sattr.getFirstChild().getNodeValue().trim();
                            }
                        }
                    }
                    if( name != null && type != null && type.equalsIgnoreCase("OS") ) {
                        vm.setPlatform(Platform.guess(name));
                    }
                }
            }
        }
        if( vm.getProviderVirtualMachineId() == null ) {
            return null;
        }
        if( vm.getProviderRegionId() == null || !vm.getProviderRegionId().equals(ctx.getRegionId()) ) {
            return null;
        }
        if( vm.getName() == null ) {
            vm.setName(vm.getProviderVirtualMachineId());
        }
        if( vm.getDescription() == null ) {
            vm.setDescription(vm.getName());
        }
        vm.setProviderDataCenterId(vm.getProviderRegionId());
        vm.setLastBootTimestamp(vm.getCreationTimestamp());
        return vm;
    }

    private boolean isPublicIpAddress(String ipv4Address) {
        if (ipv4Address == null || ipv4Address.isEmpty())  {
            return false;
        }
        if( ipv4Address.startsWith("10.") || ipv4Address.startsWith("192.168") || ipv4Address.startsWith("169.254") ) {
            return false;
        }
        else if( ipv4Address.startsWith("172.") ) {
            String[] parts = ipv4Address.split("\\.");

            if( parts.length != 4 ) {
                return true;
            }
            int x = Integer.parseInt(parts[1]);

            if( x >= 16 && x <= 31 ) {
                return false;
            }
        }
        return true;
    }

    private @Nonnull VmState toVmState(@Nonnull String vmState) throws InternalException {
        if( vmState.equals("0") || vmState.equals("1") || vmState.equals("4") || vmState.equals("6") || vmState.equals("9") || vmState.equals("14") || vmState.equals("15") ) {
            return VmState.PENDING;
        }
        else if( vmState.equals("2") || vmState.equals("3") ) {
            return VmState.TERMINATED;
        }
        else if( vmState.equals("5") ) {
            return VmState.RUNNING;
        }
        else if( vmState.equals("7") || vmState.equals("10") || vmState.equals("12") ) {
            return VmState.STOPPING;
        }
        else if( vmState.equals("8") || vmState.equals("13") ) {
            return VmState.REBOOTING;
        }
        else if( vmState.equals("11") ) {
            return VmState.PAUSED;
        }
        else {
            System.out.println("DEBUG: Unknown VM state: " + vmState);
            return VmState.PENDING;
        }
    }

    public @Nullable ResourceStatus toStatus(@Nullable Node node) throws CloudException, InternalException {
        if( node == null ) {
            return null;
        }
        NodeList nodes = node.getChildNodes();
        VmState state = null;
        String vmId = null;

        for( int i=0; i<nodes.getLength(); i++ ) {
            Node attr = nodes.item(i);
            String nodeName = attr.getNodeName();

            if( nodeName.equalsIgnoreCase("ID") && attr.hasChildNodes() ) {
                vmId = attr.getFirstChild().getNodeValue().trim();
            }
            else if( nodeName.equalsIgnoreCase("Status") && attr.hasChildNodes() ) {
                String status = attr.getFirstChild().getNodeValue().trim();

                state = toVmState(status);
            }
            if( state != null && vmId != null ) {
                break;
            }
        }
        if( vmId == null ) {
            return null;
        }
        return new ResourceStatus(vmId, state == null ? VmState.PENDING : state);
    }

    static public String uppercaseAlphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    static public String lowercaseAlphabet = "abcdefghijklmnopqrstuvwxyz";
    static public String numbers = "0123456789";
    static public String symbols = "!$#@%^&*()-_=+[]{},.<>?/;:";
    static public String allChars = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!$#@%^&*()-_=+[]{},.<>?/;:";
    static private final Random random = new Random();

    public String getRandomPassword(String username) {
        StringBuilder password = new StringBuilder();
        ArrayList<String> invalidSubstrings = new ArrayList<String>();
        final String[] invalidStrings = {username, "admin", "Administrator", "idcadmin"};
        for (String invalidString : invalidStrings) {
            invalidSubstrings.addAll(generateThreeCharSubstrings(invalidString));
        }
        int rnd = random.nextInt();
        int length = 16;

        if( rnd < 0 ) {
            rnd = -rnd;
        }
        length = length + (rnd%8);
        while( password.length() < 1 ) {
            char c;

            rnd = random.nextInt();
            if( rnd < 0 ) {
                rnd = -rnd;
            }
            c = (char)(rnd%255);
            if( uppercaseAlphabet.contains(String.valueOf(c)) ) {
                password.append(c);
            }
        }
        while( password.length() < 2 ) {
            char c;

            rnd = random.nextInt();
            if( rnd < 0 ) {
                rnd = -rnd;
            }
            c = (char)(rnd%255);
            if( lowercaseAlphabet.contains(String.valueOf(c)) ) {
                password.append(c);
            }
        }
        while( password.length() < 3 ) {
            char c;

            rnd = random.nextInt();
            if( rnd < 0 ) {
                rnd = -rnd;
            }
            c = (char)(rnd%255);
            if( numbers.contains(String.valueOf(c)) ) {
                password.append(c);
            }
        }
        while( password.length() < 4 ) {
            char c;

            rnd = random.nextInt();
            if( rnd < 0 ) {
                rnd = -rnd;
            }
            c = (char)(rnd%255);
            if( symbols.contains(String.valueOf(c)) ) {
                password.append(c);
            }
        }
        while( password.length() < length ) {
            char c;

            rnd = random.nextInt();
            if( rnd < 0 ) {
                rnd = -rnd;
            }
            c = (char)(rnd%255);
            if( allChars.contains(String.valueOf(c)) ) {
                password.append(c);
            }
            for (String invalidSubstring : invalidSubstrings) {
                if (password.toString().contains(invalidSubstring)) {
                    password = password.delete(4, password.length());
                    break;
                }
            }

        }
        return password.toString();
    }

    private ArrayList<String> generateThreeCharSubstrings(String input) {
        ArrayList<String> substrings = new ArrayList<String>();
        Integer length = input.length();
        for (Integer i=0; (i+3)<=length; i++) {
            substrings.add(input.substring(i, i+3));
        }
        return substrings;
    }


}
