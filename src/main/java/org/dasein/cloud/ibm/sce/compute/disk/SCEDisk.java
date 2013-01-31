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

package org.dasein.cloud.ibm.sce.compute.disk;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.ProviderContext;
import org.dasein.cloud.Requirement;
import org.dasein.cloud.ResourceStatus;
import org.dasein.cloud.compute.AbstractVolumeSupport;
import org.dasein.cloud.compute.Platform;
import org.dasein.cloud.compute.Volume;
import org.dasein.cloud.compute.VolumeCreateOptions;
import org.dasein.cloud.compute.VolumeFormat;
import org.dasein.cloud.compute.VolumeProduct;
import org.dasein.cloud.compute.VolumeState;
import org.dasein.cloud.compute.VolumeType;
import org.dasein.cloud.ibm.sce.ExtendedRegion;
import org.dasein.cloud.ibm.sce.SCE;
import org.dasein.cloud.ibm.sce.SCEConfigException;
import org.dasein.cloud.ibm.sce.SCEException;
import org.dasein.cloud.ibm.sce.SCEMethod;
import org.dasein.cloud.identity.ServiceAction;
import org.dasein.util.CalendarWrapper;
import org.dasein.util.uom.storage.Gigabyte;
import org.dasein.util.uom.storage.Storage;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;

/**
 * Implements disk/volume management according to the Dasein Cloud API for IBM SmartCloud storage.
 * <p>Created by George Reese: 7/17/12 3:46 PM</p>
 * @author George Reese
 * @version 2012.04 initial version
 * @version 2012.09 updated to new object model (George Reese)
 * @since 2012.04
 */
public class SCEDisk extends AbstractVolumeSupport {
    private SCE provider;

    public SCEDisk(SCE provider) {
        super(provider);
        this.provider = provider;
    }

    @Override
    public void attach(@Nonnull String volumeId, @Nonnull String toServer, @Nonnull String device) throws InternalException, CloudException {
        ProviderContext ctx = provider.getContext();

        if( ctx == null ) {
            throw new SCEConfigException("No context was configured for this request");
        }

        ExtendedVolume v = getVolume(volumeId);

        if( v == null ) {
            throw new CloudException("No such volume: " + volumeId);
        }
        if( v.getProviderVirtualMachineId() != null || v.getRealState().equals("5") ) {
            throw new CloudException("Volume is already attached to a virtual machine");
        }
        long timeout = System.currentTimeMillis() + (CalendarWrapper.MINUTE * 20L);

        while( v != null && !v.getRealState().equals("4") ) {
            if( System.currentTimeMillis() >= timeout ) {
                break;
            }
            try { Thread.sleep(15000L); }
            catch( InterruptedException ignore ) { }
            v = getVolume(volumeId);
        }
        if( v == null ) {
            throw new CloudException("Volume went away");
        }
        if( v.getRealState().equals("5") ) {
            return;
        }

        ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
        SCEMethod method = new SCEMethod(provider);

        params.add(new BasicNameValuePair("type", "attach"));
        params.add(new BasicNameValuePair("storageID", volumeId));
        method.put("instances/" + toServer, params);
    }

    static private class SCEOffering {
        public String offeringId;
        public int size;
        public String format;
    }

    private SCEOffering findOffering(int sizeInGb) throws InternalException, CloudException {
        SCEOffering offering = null;

        ProviderContext ctx = provider.getContext();

        if( ctx == null ) {
            throw new SCEConfigException("No context was configured for this request");
        }
        SCEMethod method = new SCEMethod(provider);

        Document xml = method.getAsXML("offerings/storage");

        if( xml == null ) {
            throw new CloudException("No storage offerings exist");
        }
        NodeList nodes = xml.getElementsByTagName("Offerings");

        for( int i=0; i<nodes.getLength(); i++ ) {
            Node item = nodes.item(i);

            if( item.hasChildNodes() ) {
                NodeList attrs = item.getChildNodes();
                String id = null, format = null;
                int[] sizes = new int[0];

                for( int j=0; j<attrs.getLength(); j++ ) {
                    Node attr = attrs.item(j);
                    String n = attr.getNodeName();

                    if( n.equalsIgnoreCase("ID") ) {
                        id = attr.getFirstChild().getNodeValue().trim();
                    }
                    else if( n.equalsIgnoreCase("SupportedSizes") ) {
                        String s = attr.getFirstChild().getNodeValue().trim();
                        String[] parts;

                        if( s.contains(",") ) {
                            parts = s.split(",");
                        }
                        else {
                            parts = new String[] { s };
                        }
                        sizes = new int[parts.length];
                        for( int k=0; k<parts.length; k++ ) {
                            sizes[k] = Integer.parseInt(parts[k].trim());
                        }
                    }
                    else if( n.equalsIgnoreCase("SupportedFormats") && attr.hasChildNodes() ) {
                        NodeList formats = attr.getChildNodes();

                        for( int k=0; k<formats.getLength(); k++ ) {
                            Node fmt = formats.item(k);

                            if( fmt.getNodeName().equalsIgnoreCase("Format") && fmt.hasChildNodes() ) {
                                NodeList fa = fmt.getChildNodes();

                                for( int l=0; l<fa.getLength(); l++ ) {
                                    Node fan = fa.item(l);

                                    if( fan.getNodeName().equalsIgnoreCase("ID") && fan.hasChildNodes() ) {
                                        format = fan.getFirstChild().getNodeValue().trim();
                                        if( !format.equalsIgnoreCase("RAW") ) {
                                            format = null;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if( sizes.length > 0 && format != null ) {
                    if( offering == null ) {
                        offering = new SCEOffering();
                        offering.format = format;
                        offering.offeringId = id;
                        if( sizes[0] > sizeInGb ) {
                            offering.size = sizes[0];
                        }
                        else {
                            int sz = 0;

                            for( int s : sizes ) {
                                if( s < sizeInGb && s >= sz ) {
                                    sz = s;
                                }
                                else {
                                    break;
                                }
                            }
                            offering.size = sz;
                        }
                    }
                }
            }
        }
        if( offering == null ) {
            throw new CloudException("No storage offerings exist");
        }
        return offering;
    }

    private SCEOffering findOffering(@Nonnull String productId) throws InternalException, CloudException {
        SCEOffering offering;

        ProviderContext ctx = provider.getContext();

        if( ctx == null ) {
            throw new SCEConfigException("No context was configured for this request");
        }
        SCEMethod method = new SCEMethod(provider);

        Document xml = method.getAsXML("offerings/storage");

        if( xml == null ) {
            throw new CloudException("No storage offerings exist");
        }
        NodeList nodes = xml.getElementsByTagName("Offerings");

        for( int i=0; i<nodes.getLength(); i++ ) {
            Node item = nodes.item(i);

            if( item.hasChildNodes() ) {
                NodeList attrs = item.getChildNodes();
                String id = null, format = null;
                int[] sizes = new int[0];

                for( int j=0; j<attrs.getLength(); j++ ) {
                    Node attr = attrs.item(j);
                    String n = attr.getNodeName();

                    if( n.equalsIgnoreCase("ID") ) {
                        id = attr.getFirstChild().getNodeValue().trim();
                    }
                    else if( n.equalsIgnoreCase("SupportedSizes") ) {
                        String s = attr.getFirstChild().getNodeValue().trim();
                        String[] parts;

                        if( s.contains(",") ) {
                            parts = s.split(",");
                        }
                        else {
                            parts = new String[] { s };
                        }
                        sizes = new int[parts.length];
                        for( int k=0; k<parts.length; k++ ) {
                            sizes[k] = Integer.parseInt(parts[k].trim());
                        }
                    }
                    else if( n.equalsIgnoreCase("SupportedFormats") && attr.hasChildNodes() ) {
                        NodeList formats = attr.getChildNodes();

                        for( int k=0; k<formats.getLength(); k++ ) {
                            Node fmt = formats.item(k);

                            if( fmt.getNodeName().equalsIgnoreCase("Format") && fmt.hasChildNodes() ) {
                                NodeList fa = fmt.getChildNodes();

                                for( int l=0; l<fa.getLength(); l++ ) {
                                    Node fan = fa.item(l);

                                    if( fan.getNodeName().equalsIgnoreCase("ID") && fan.hasChildNodes() ) {
                                        format = fan.getFirstChild().getNodeValue().trim();
                                        if( !format.equalsIgnoreCase("RAW") ) {
                                            format = null;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if( id == null || !id.equals(productId) ) {
                    continue;
                }
                offering = new SCEOffering();
                offering.format = format;
                offering.offeringId = id;
                offering.size = sizes[0];
                return offering;
            }
        }
        return null;
    }

    @Override
    public @Nonnull String createVolume(@Nonnull VolumeCreateOptions options) throws InternalException, CloudException {
        ProviderContext ctx = provider.getContext();

        if( ctx == null ) {
            throw new SCEConfigException("No context was configured for this request");
        }
        ArrayList<NameValuePair> parameters = new ArrayList<NameValuePair>();
        String fromSnapshot = options.getSnapshotId();


        parameters.add(new BasicNameValuePair("name", options.getName()));
        if( fromSnapshot == null ) {
            String productId = options.getVolumeProductId();
            SCEOffering offering;

            if( productId != null ) {
                offering = findOffering(productId);
            }
            else {
                offering = findOffering(options.getVolumeSize().intValue());
            }
            parameters.add(new BasicNameValuePair("location", ctx.getRegionId()));
            parameters.add(new BasicNameValuePair("offeringID", offering.offeringId));
            parameters.add(new BasicNameValuePair("format", offering.format));
            parameters.add(new BasicNameValuePair("size", String.valueOf(offering.size)));
        }
        else {
            parameters.add(new BasicNameValuePair("targetLocationID", ctx.getRegionId()));
            parameters.add(new BasicNameValuePair("sourceDiskID", fromSnapshot));
            parameters.add(new BasicNameValuePair("type", "clone"));
        }
        SCEMethod method = new SCEMethod(provider);
        String response = method.post("storage", parameters);

        if( response == null ) {
            throw new CloudException("Cloud accepted the post, but no body was in the response");
        }

        Document doc = method.parseResponse(response, true);

        NodeList locations = doc.getElementsByTagName("Volume");

        for( int i=0; i<locations.getLength(); i++ ) {
            Node item = locations.item(i);
            Volume volume = toVolume(ctx, item);

            if( volume != null ) {
                return volume.getProviderVolumeId();
            }
        }
        throw new CloudException("No volume was in the XML response");
    }

    @Override
    public void detach(@Nonnull String volumeId, boolean force) throws InternalException, CloudException {
        ProviderContext ctx = provider.getContext();

        if( ctx == null ) {
            throw new SCEConfigException("No context was configured for this request");
        }
        ExtendedVolume v = getVolume(volumeId);

        if( v == null ) {
            throw new CloudException("No such volume: " + volumeId);
        }
        if( v.getProviderVirtualMachineId() == null ) {
            throw new CloudException("Not sure to which VM " + volumeId + " is attached");
        }
        String virtualMachineId = v.getProviderVirtualMachineId();
        long timeout = System.currentTimeMillis() + (CalendarWrapper.MINUTE * 20L);

        while( v != null && !v.getRealState().equals("5") ) {
            if( System.currentTimeMillis() >= timeout ) {
                break;
            }
            try { Thread.sleep(15000L); }
            catch( InterruptedException ignore ) { }
            v = getVolume(volumeId);
        }
        if( v == null ) {
            throw new CloudException("Volume went away");
        }
        if( v.getRealState().equals("4") ) {
            return;
        }
        ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
        SCEMethod method = new SCEMethod(provider);

        params.add(new BasicNameValuePair("type", "detach"));
        params.add(new BasicNameValuePair("storageID", volumeId));
        method.put("instances/" + virtualMachineId, params);
    }

    @Override
    public int getMaximumVolumeCount() throws InternalException, CloudException {
        return -2;
    }

    @Override
    public Storage<Gigabyte> getMaximumVolumeSize() throws InternalException, CloudException {
        return new Storage<Gigabyte>(5000, Storage.GIGABYTE);
    }

    @Override
    public @Nonnull Storage<Gigabyte> getMinimumVolumeSize() throws InternalException, CloudException {
        return new Storage<Gigabyte>(1, Storage.GIGABYTE);
    }

    @Override
    public @Nonnull String getProviderTermForVolume(@Nonnull Locale locale) {
        return "disk";
    }

    @Override
    public ExtendedVolume getVolume(@Nonnull String volumeId) throws InternalException, CloudException {
        ProviderContext ctx = provider.getContext();

        if( ctx == null ) {
            throw new SCEConfigException("No context was configured for this request");
        }
        SCEMethod method = new SCEMethod(provider);
        Document xml;
        try {
            xml = method.getAsXML("storage/" + volumeId);
        }
        catch( SCEException e ) {
            for( Volume v : listVolumes() ) {
                if( v.getProviderVolumeId().equals(volumeId) ) {
                    return (ExtendedVolume)v;
                }
            }
            return null;
        }
        if( xml == null ) {
            return null;
        }
        NodeList volumes = xml.getElementsByTagName("Volume");

        for( int i=0; i<volumes.getLength(); i++ ) {
            Node item = volumes.item(i);
            ExtendedVolume v = toVolume(ctx, item);

            if( v != null ) {
                return v;
            }
        }
        return null;
    }

    @Override
    public @Nonnull Requirement getVolumeProductRequirement() throws InternalException, CloudException {
        return Requirement.REQUIRED;
    }

    @Override
    public boolean isVolumeSizeDeterminedByProduct() throws InternalException, CloudException {
        return true;
    }

    @Override
    public @Nonnull Iterable<String> listPossibleDeviceIds(@Nonnull Platform platform) throws InternalException, CloudException {
        ArrayList<String> list = new ArrayList<String>();

        if( platform.isWindows() ) {
            list.add("xvdf");
            list.add("xvdg");
            list.add("xvdh");
            list.add("xvdi");
            list.add("xvdj");
        }
        else {
            list.add("/dev/sdf");
            list.add("/dev/sdg");
            list.add("/dev/sdh");
            list.add("/dev/sdi");
            list.add("/dev/sdj");
        }
        return list;
    }

    @Override
    public @Nonnull Iterable<VolumeFormat> listSupportedFormats() throws InternalException, CloudException {
        return Collections.singletonList(VolumeFormat.BLOCK);
    }

    @Override
    public @Nonnull Iterable<VolumeProduct> listVolumeProducts() throws InternalException, CloudException {
        ArrayList<VolumeProduct> products = new ArrayList<VolumeProduct>();

        ProviderContext ctx = provider.getContext();

        if( ctx == null ) {
            throw new SCEConfigException("No context was configured for this request");
        }
        SCEMethod method = new SCEMethod(provider);

        Document xml = method.getAsXML("offerings/storage");

        if( xml == null ) {
            throw new CloudException("No storage offerings exist");
        }
        NodeList nodes = xml.getElementsByTagName("Offerings");

        for( int i=0; i<nodes.getLength(); i++ ) {
            Node item = nodes.item(i);

            if( item.hasChildNodes() ) {
                NodeList attrs = item.getChildNodes();
                String id = null, format = null;
                int[] sizes = new int[0];

                for( int j=0; j<attrs.getLength(); j++ ) {
                    Node attr = attrs.item(j);
                    String n = attr.getNodeName();

                    if( n.equalsIgnoreCase("ID") ) {
                        id = attr.getFirstChild().getNodeValue().trim();
                    }
                    else if( n.equalsIgnoreCase("SupportedSizes") ) {
                        String s = attr.getFirstChild().getNodeValue().trim();
                        String[] parts;

                        if( s.contains(",") ) {
                            parts = s.split(",");
                        }
                        else {
                            parts = new String[] { s };
                        }
                        sizes = new int[parts.length];
                        for( int k=0; k<parts.length; k++ ) {
                            sizes[k] = Integer.parseInt(parts[k].trim());
                        }
                    }
                    else if( n.equalsIgnoreCase("SupportedFormats") && attr.hasChildNodes() ) {
                        NodeList formats = attr.getChildNodes();

                        for( int k=0; k<formats.getLength(); k++ ) {
                            Node fmt = formats.item(k);

                            if( fmt.getNodeName().equalsIgnoreCase("Format") && fmt.hasChildNodes() ) {
                                NodeList fa = fmt.getChildNodes();

                                for( int l=0; l<fa.getLength(); l++ ) {
                                    Node fan = fa.item(l);

                                    if( fan.getNodeName().equalsIgnoreCase("ID") && fan.hasChildNodes() ) {
                                        format = fan.getFirstChild().getNodeValue().trim();
                                        if( !format.equalsIgnoreCase("RAW") ) {
                                            format = null;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                if( sizes.length > 0 && format != null ) {
                    products.add(VolumeProduct.getInstance(id, id + " - " + format, id + " - " + format + " - " + sizes[0], VolumeType.HDD, new Storage<Gigabyte>(sizes[0], Storage.GIGABYTE)));
                }
            }
        }
        return products;
    }

    @Override
    public @Nonnull Iterable<ResourceStatus> listVolumeStatus() throws InternalException, CloudException {
        ProviderContext ctx = provider.getContext();

        if( ctx == null ) {
            throw new SCEConfigException("No context was configured for this request");
        }
        SCEMethod method = new SCEMethod(provider);

        Document xml = method.getAsXML("storage");

        if( xml == null ) {
            return Collections.emptyList();
        }
        NodeList volumes = xml.getElementsByTagName("Volume");
        ArrayList<ResourceStatus> list = new ArrayList<ResourceStatus>();

        for( int i=0; i<volumes.getLength(); i++ ) {
            Node item = volumes.item(i);
            ResourceStatus v = toStatus(item);

            if( v != null ) {
                list.add(v);
            }
        }
        return list;
    }

    @Override
    public @Nonnull Iterable<Volume> listVolumes() throws InternalException, CloudException {
        ProviderContext ctx = provider.getContext();

        if( ctx == null ) {
            throw new SCEConfigException("No context was configured for this request");
        }
        SCEMethod method = new SCEMethod(provider);

        Document xml = method.getAsXML("storage");

        if( xml == null ) {
            return Collections.emptyList();
        }
        NodeList volumes = xml.getElementsByTagName("Volume");
        ArrayList<Volume> list = new ArrayList<Volume>();

        for( int i=0; i<volumes.getLength(); i++ ) {
            Node item = volumes.item(i);
            Volume v = toVolume(ctx, item);

            if( v != null ) {
                list.add(v);
            }
        }
        return list;
    }

    @Override
    public boolean isSubscribed() throws CloudException, InternalException {
        ProviderContext ctx = provider.getContext();

        if( ctx == null ) {
            throw new SCEConfigException("No context was specified for this request");
        }
        try {
            ExtendedRegion region = provider.getDataCenterServices().getRegion(ctx.getRegionId());

            return (region != null && region.isStorage());
        }
        catch( CloudException e ) {
            if( e.getHttpCode() == HttpServletResponse.SC_FORBIDDEN || e.getHttpCode() == HttpServletResponse.SC_UNAUTHORIZED ) {
                return false;
            }
            throw e;
        }
    }

    @Override
    public void remove(@Nonnull String volumeId) throws InternalException, CloudException {
        ProviderContext ctx = provider.getContext();

        if( ctx == null ) {
            throw new SCEConfigException("No context was configured for this request");
        }
        long timeout = System.currentTimeMillis() + (CalendarWrapper.MINUTE * 20L);
        ExtendedVolume v = getVolume(volumeId);

        while( v != null && !v.getRealState().equals("5") && !v.getRealState().equals("4") && !v.getRealState().equals("6") ) {
            if( System.currentTimeMillis() >= timeout ) {
                break;
            }
            try { Thread.sleep(15000L); }
            catch( InterruptedException ignore ) { }
            v = getVolume(volumeId);
        }
        if( v == null ) {
            throw new CloudException("Volume went away");
        }
        SCEMethod method = new SCEMethod(provider);

        method.delete("storage/" + volumeId);
    }

    @Override
    public @Nonnull String[] mapServiceAction(@Nonnull ServiceAction action) {
        return new String[0];
    }

    private @Nullable ExtendedVolume toVolume(@Nonnull ProviderContext ctx, @Nullable Node node) throws CloudException, InternalException {
        if( node == null || !node.hasChildNodes() ) {
            return null;
        }
        NodeList attributes = node.getChildNodes();
        ExtendedVolume volume = new ExtendedVolume();

        volume.setCurrentState(VolumeState.PENDING);
        volume.setType(VolumeType.HDD);
        volume.setFormat(VolumeFormat.BLOCK);
        for( int i=0; i<attributes.getLength(); i++ ) {
            Node attr = attributes.item(i);
            String nodeName = attr.getNodeName();

            if( nodeName.equalsIgnoreCase("ID") && attr.hasChildNodes()) {
                volume.setProviderVolumeId(attr.getFirstChild().getNodeValue().trim());
            }
            else if( nodeName.equalsIgnoreCase("Name") && attr.hasChildNodes()) {
                volume.setName(attr.getFirstChild().getNodeValue().trim());
            }
            else if( nodeName.equalsIgnoreCase("Description") && attr.hasChildNodes()) {
                volume.setName(attr.getFirstChild().getNodeValue().trim());
            }
            else if( nodeName.equalsIgnoreCase("Location") && attr.hasChildNodes()) {
                volume.setProviderRegionId(attr.getFirstChild().getNodeValue().trim());
                volume.setProviderDataCenterId(volume.getProviderRegionId());
            }
            else if( nodeName.equalsIgnoreCase("Size") && attr.hasChildNodes()) {
                volume.setSize(new Storage<Gigabyte>(Integer.parseInt(attr.getFirstChild().getNodeValue().trim()), Storage.GIGABYTE));
            }
            else if( nodeName.equalsIgnoreCase("State") && attr.hasChildNodes()) {
                String status = attr.getFirstChild().getNodeValue().trim();

                volume.setCurrentState(toState(status));
                volume.setRealState(status);
            }
            else if( nodeName.equalsIgnoreCase("CreatedTime") && attr.hasChildNodes()) {
                volume.setCreationTimestamp(provider.parseTimestamp(attr.getFirstChild().getNodeValue().trim()));
            }
            else if( nodeName.equalsIgnoreCase("InstanceID") && attr.hasChildNodes()) {
                volume.setProviderVirtualMachineId(attr.getFirstChild().getNodeValue().trim());
            }
        }
        if( volume.getProviderVolumeId() == null ) {
            return null;
        }
        String regionId = volume.getProviderRegionId();

        if( regionId == null || !regionId.equals(ctx.getRegionId()) ) {
            return null;
        }
        if( volume.getName() == null ) {
            volume.setName(volume.getProviderVolumeId());
        }
        return volume;
    }

    private @Nonnull VolumeState toState(@Nonnull String id) {
        if( id.equals("0") || id.equals("1") || id.equals("2") || id.equals("7") || id.equals("12") || id.equals("13") || id.equals("14") ) {
            return VolumeState.PENDING;
        }
        else if( id.equals("3") || id.equals("6") ) {
            return VolumeState.DELETED;
        }
        else if( id.equals("4") || id.equals("5") || id.equals("8") || id.equals("9") || id.equals("10") || id.equals("11")  ) {
            return VolumeState.AVAILABLE;
        }
        System.out.println("DEBUG: Unknown volume state: " + id);
        return VolumeState.PENDING;
    }

    private @Nullable ResourceStatus toStatus(@Nullable Node node) throws CloudException, InternalException {
        if( node == null || !node.hasChildNodes() ) {
            return null;
        }
        NodeList attributes = node.getChildNodes();
        VolumeState state = null;
        String volumeId = null;

        for( int i=0; i<attributes.getLength(); i++ ) {
            Node attr = attributes.item(i);
            String nodeName = attr.getNodeName();

            if( nodeName.equalsIgnoreCase("ID") && attr.hasChildNodes()) {
                volumeId = attr.getFirstChild().getNodeValue().trim();
            }
            else if( nodeName.equalsIgnoreCase("State") && attr.hasChildNodes()) {
                String status = attr.getFirstChild().getNodeValue().trim();

                state = toState(status);
            }
            if( volumeId != null && state != null ) {
                break;
            }
        }
        if( volumeId == null ) {
            return null;
        }
        return new ResourceStatus(volumeId, state == null ? VolumeState.PENDING : state);
    }
}
