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
import org.dasein.cloud.AsynchronousTask;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.OperationNotSupportedException;
import org.dasein.cloud.ProviderContext;
import org.dasein.cloud.Tag;
import org.dasein.cloud.compute.Architecture;
import org.dasein.cloud.compute.MachineImage;
import org.dasein.cloud.compute.MachineImageState;
import org.dasein.cloud.compute.Platform;
import org.dasein.cloud.compute.VirtualMachine;
import org.dasein.cloud.compute.VirtualMachineProduct;
import org.dasein.cloud.compute.VirtualMachineSupport;
import org.dasein.cloud.compute.VmState;
import org.dasein.cloud.compute.VmStatistics;
import org.dasein.cloud.ibm.sce.ExtendedRegion;
import org.dasein.cloud.ibm.sce.SCE;
import org.dasein.cloud.ibm.sce.SCEConfigException;
import org.dasein.cloud.ibm.sce.SCEMethod;
import org.dasein.cloud.ibm.sce.identity.keys.SSHKeys;
import org.dasein.cloud.identity.SSHKeypair;
import org.dasein.cloud.identity.ServiceAction;
import org.dasein.util.CalendarWrapper;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.annotation.Nonnegative;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Implementation of the Dasein Cloud virtual machine support for IBM SmartCloud.
 * <p>Created by George Reese: 7/16/12 7:37 PM</p>
 * @author George Reese
 * @version 2012.02 initial version
 * @since 2012.02
 */
public class SCEVirtualMachine implements VirtualMachineSupport {
    private SCE provider;

    public SCEVirtualMachine(SCE provider) { this.provider = provider; }

    @Override
    public void boot(@Nonnull String vmId) throws InternalException, CloudException {
        throw new OperationNotSupportedException("Starting/stopping VMs is not supported in this cloud");
    }

    @Override
    public @Nonnull VirtualMachine clone(@Nonnull String vmId, @Nonnull String intoDcId, @Nonnull String name, @Nonnull String description, boolean powerOn, @Nullable String... firewallIds) throws InternalException, CloudException {
        AsynchronousTask<String> t = provider.getComputeServices().getImageSupport().imageVirtualMachine(vmId, name, description);
        VirtualMachine vm = getVirtualMachine(vmId);

        if( vm == null ) {
            throw new CloudException("No such virtual machine: " + vmId);
        }
        VirtualMachineProduct prd = vm.getProduct();

        if( prd == null ) {
            throw new CloudException("Unknown product associated with VM");
        }
        long timeout = System.currentTimeMillis() + (CalendarWrapper.MINUTE * 20L);

        while( !t.isComplete() ) {
            if( System.currentTimeMillis() > timeout ) {
                throw new CloudException("Cloud timed out while creating an image for cloning");
            }
            try { Thread.sleep(15000L); }
            catch( InterruptedException ignore ) { }
        }
        Throwable error = t.getTaskError();

        if( error != null ) {
            throw new CloudException(error);
        }
        String machineImageId = t.getResult();

        if( machineImageId == null || machineImageId.equals("") ) {
            throw new CloudException("No machine image from which to clone");
        }
        MachineImage img = provider.getComputeServices().getImageSupport().getMachineImage(machineImageId);

        timeout = System.currentTimeMillis() + (CalendarWrapper.MINUTE * 10L);
        while( img == null || !MachineImageState.ACTIVE.equals(img.getCurrentState()) ) {
            if( System.currentTimeMillis() > timeout ) {
                throw new CloudException("Cloud timed out while waiting for cloning image");
            }
            try { Thread.sleep(15000L); }
            catch( InterruptedException ignore ) { }
            img = provider.getComputeServices().getImageSupport().getMachineImage(machineImageId);
        }
        return launch(machineImageId, prd, intoDcId, name, description, null, null, false, false, firewallIds, new Tag[0]);
    }

    @Override
    public void disableAnalytics(String vmId) throws InternalException, CloudException {
        // NO-OP
    }

    @Override
    public void enableAnalytics(String vmId) throws InternalException, CloudException {
        // NO-OP
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
                if( prd.getProductId().equals(productId) ) {
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
    public VmStatistics getVMStatistics(String vmId, long from, long to) throws InternalException, CloudException {
        return null;
    }

    @Override
    public @Nonnull Iterable<VmStatistics> getVMStatisticsForPeriod(@Nonnull String vmId, @Nonnegative long from, @Nonnegative long to) throws InternalException, CloudException {
        return Collections.emptyList();
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
    public @Nonnull VirtualMachine launch(@Nonnull String fromMachineImageId, @Nonnull VirtualMachineProduct product, @Nonnull String dataCenterId, @Nonnull String name, @Nonnull String description, @Nullable String withKeypairId, @Nullable String inVlanId, boolean withAnalytics, boolean asSandbox, @Nullable String... firewallIds) throws InternalException, CloudException {
        return this.launch(fromMachineImageId, product, dataCenterId, name, description, withKeypairId, inVlanId, withAnalytics, asSandbox, firewallIds, new Tag[0]);
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
    public @Nonnull VirtualMachine launch(@Nonnull String fromMachineImageId, @Nonnull VirtualMachineProduct product, @Nonnull String dataCenterId, @Nonnull String name, @Nonnull String description, @Nullable String withKeypairId, @Nullable String inVlanId, boolean withAnalytics, boolean asSandbox, @Nullable String[] firewallIds, @Nullable Tag... tags) throws InternalException, CloudException {
        ProviderContext ctx = provider.getContext();

        if( ctx == null ) {
            throw new SCEConfigException("No context was configured for this request");
        }
        ArrayList<NameValuePair> parameters = new ArrayList<NameValuePair>();

        parameters.add(new BasicNameValuePair("name", name));
        parameters.add(new BasicNameValuePair("instanceType", product.getProductId()));
        parameters.add(new BasicNameValuePair("imageID", fromMachineImageId));
        parameters.add(new BasicNameValuePair("location", ctx.getRegionId()));
        if( withKeypairId == null ) {
            withKeypairId = identifyKeypair();
        }
        parameters.add(new BasicNameValuePair("publicKey", withKeypairId));
        if( inVlanId != null ) {
            parameters.add(new BasicNameValuePair("vlanID", inVlanId));
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
                return vm;
            }
        }
        throw new CloudException("No instance was in the XML response");
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
                                        prd.setProductId(node.getFirstChild().getNodeValue().trim());
                                    }
                                    else if( node.getNodeName().equals("Label") && node.hasChildNodes() ) {
                                        prd.setName(node.getFirstChild().getNodeValue().trim());
                                    }
                                    else if( node.getNodeName().equals("Detail") && node.hasChildNodes() ) {
                                        prd.setDescription(node.getFirstChild().getNodeValue().trim());
                                    }
                                }
                                if( prd.getProductId() != null ) {
                                    String[] parts = prd.getProductId().split("/");

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
                                            prd.setRamInMb(Integer.parseInt(parts[1]));
                                        }
                                        catch( NumberFormatException ignore ) {
                                            // ignore
                                        }
                                        try {
                                            int idx = parts[2].indexOf("*");

                                            if( idx < 1 ) {
                                                prd.setDiskSizeInGb(Integer.parseInt(parts[2]));
                                            }
                                            else {
                                                prd.setDiskSizeInGb(Integer.parseInt(parts[2].substring(0,idx)));
                                            }
                                        }
                                        catch( NumberFormatException ignore ) {
                                            // ignore
                                        }
                                    }
                                    prdMap.put(prd.getProductId(), prd);
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
    public void pause(@Nonnull String vmId) throws InternalException, CloudException {
        throw new OperationNotSupportedException("Starting/stopping VMs is not supported in this cloud");
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
    public boolean supportsAnalytics() throws CloudException, InternalException {
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

    private @Nonnull String[] parseAddress(@Nullable String[] current, @Nonnull Node ipNode) {
        NodeList attrs = ipNode.getChildNodes();
        String address = null;

        for( int j=0; j<attrs.getLength(); j++ ) {
            Node attr= attrs.item(j);
            String n = attr.getNodeName();

            if( n.equalsIgnoreCase("Address") && attr.hasChildNodes() ) {
                NodeList items = attr.getChildNodes();

                for( int k=0; k<items.getLength(); k++ ) {
                    Node item = items.item(k);

                    if( item.getNodeName().equalsIgnoreCase("IP") && item.hasChildNodes() ) {
                        address = item.getFirstChild().getNodeValue().trim();
                    }
                }
            }
        }
        if( address == null ) {
            return (current == null ? new String[0] : current);
        }

        String[] addresses = new String[current == null ? 1 : current.length];
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
                String[] addrs = vm.getPublicIpAddresses();

                if( addrs == null || addrs.length == 0 ) {
                    vm.setPublicIpAddresses(new String[]{ ip });
                }
                else {
                    String[] tmp = new String[addrs.length + 1];

                    //noinspection ManualArrayCopy
                    for(int idx=0; idx<addrs.length; idx++ ) {
                        tmp[idx] = addrs[idx];
                    }
                    tmp[tmp.length-1] = ip;
                    addrs = tmp;
                }
                vm.setPublicIpAddresses(addrs);
            }
            else if( nodeName.equalsIgnoreCase("ImageID") && attr.hasChildNodes() ) {
                vm.setProviderMachineImageId(attr.getFirstChild().getNodeValue().trim());
            }
            else if( nodeName.equalsIgnoreCase("InstanceType") && attr.hasChildNodes() ) {
                vm.setProduct(getProduct(attr.getFirstChild().getNodeValue().trim()));
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
                vm.setPublicIpAddresses(parseAddress(vm.getPublicIpAddresses(), attr));
            }
            else if( nodeName.equalsIgnoreCase("SecondaryIP") && attr.hasChildNodes() ) {
                vm.setPublicIpAddresses(parseAddress(vm.getPublicIpAddresses(), attr));
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
}
