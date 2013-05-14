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

package org.dasein.cloud.ibm.sce.compute.image;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.dasein.cloud.AsynchronousTask;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.OperationNotSupportedException;
import org.dasein.cloud.ProviderContext;
import org.dasein.cloud.Requirement;
import org.dasein.cloud.ResourceStatus;
import org.dasein.cloud.Tag;
import org.dasein.cloud.compute.Architecture;
import org.dasein.cloud.compute.ImageClass;
import org.dasein.cloud.compute.ImageCreateOptions;
import org.dasein.cloud.compute.MachineImage;
import org.dasein.cloud.compute.MachineImageFormat;
import org.dasein.cloud.compute.MachineImageState;
import org.dasein.cloud.compute.MachineImageSupport;
import org.dasein.cloud.compute.MachineImageType;
import org.dasein.cloud.compute.Platform;
import org.dasein.cloud.compute.VirtualMachine;
import org.dasein.cloud.ibm.sce.ExtendedRegion;
import org.dasein.cloud.ibm.sce.SCE;
import org.dasein.cloud.ibm.sce.SCEConfigException;
import org.dasein.cloud.ibm.sce.SCEMethod;
import org.dasein.cloud.identity.ServiceAction;
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
 * Support for IBM smart cloud machine images.
 * <p>Created by George Reese: 7/17/12 1:10 PM</p>
 * @author George Reese
 * @version 2012.04
 * @since 2012.04
 */
public class SCEImage implements MachineImageSupport {
    private SCE provider;

    public SCEImage(SCE provider) { this.provider = provider; }

    @Override
    public void addImageShare(@Nonnull String providerImageId, @Nonnull String accountNumber) throws CloudException, InternalException {
        throw new OperationNotSupportedException("No direct image sharing is supported");
    }

    @Override
    public void addPublicShare(@Nonnull String providerImageId) throws CloudException, InternalException {
        throw new OperationNotSupportedException("No public image sharing is supported");
    }

    @Override
    public @Nonnull String bundleVirtualMachine(@Nonnull String virtualMachineId, @Nonnull MachineImageFormat format, @Nonnull String bucket, @Nonnull String name) throws CloudException, InternalException {
        throw new OperationNotSupportedException("Image bundling is not supported");
    }

    @Override
    public void bundleVirtualMachineAsync(@Nonnull String virtualMachineId, @Nonnull MachineImageFormat format, @Nonnull String bucket, @Nonnull String name, @Nonnull AsynchronousTask<String> trackingTask) throws CloudException, InternalException {
        throw new OperationNotSupportedException("Image bundling is not supported");
    }

    @Override
    public @Nonnull MachineImage captureImage(@Nonnull ImageCreateOptions options) throws CloudException, InternalException {
        MachineImage img = getImage(image(options));

        if( img == null ) {
            throw new CloudException("Imaging completed successfully, but no such image exists");
        }
        return img;
    }

    @Override
    public void captureImageAsync(final @Nonnull ImageCreateOptions options, final @Nonnull AsynchronousTask<MachineImage> taskTracker) throws CloudException, InternalException {
        Thread t = new Thread() {
            public void run() {
                try {
                    MachineImage img = captureImage(options);

                    taskTracker.completeWithResult(img);
                }
                catch( Throwable t ) {
                    taskTracker.complete(t);
                }
            }
        };

        t.setName("IBM SCE Image Bundler (VM ID #" + options.getVirtualMachineId() + ")");
        t.setDaemon(true);
        t.start();
    }

    @Override
    public @Nullable MachineImage getImage(@Nonnull String providerImageId) throws CloudException, InternalException {
        ProviderContext ctx = provider.getContext();

        if( ctx == null ) {
            throw new SCEConfigException("No context was configured for this request");
        }
        SCEMethod method = new SCEMethod(provider);

        Document xml = method.getAsXML("/offerings/image/" + providerImageId);

        if( xml == null ) {
            return null;
        }
        NodeList items = xml.getElementsByTagName("Image");

        for( int i=0; i<items.getLength(); i++ ) {
            Node item = items.item(i);

            MachineImage img = toMachineImage(ctx, item, null, null, null, null);

            if( img != null ) {
                return img;
            }
        }
        return null;
    }

    @Override
    @Deprecated
    public @Nullable MachineImage getMachineImage(@Nonnull String machineImageId) throws CloudException, InternalException {
        return getImage(machineImageId);
    }

    @Override
    @Deprecated
    public @Nonnull String getProviderTermForImage(@Nonnull Locale locale) {
        return getProviderTermForImage(locale, ImageClass.MACHINE);
    }

    @Override
    public @Nonnull String getProviderTermForImage(@Nonnull Locale locale, @Nonnull ImageClass cls) {
        return (cls.equals(ImageClass.MACHINE) ? "image" : "unsupported image type");
    }

    @Override
    public @Nonnull String getProviderTermForCustomImage(@Nonnull Locale locale, @Nonnull ImageClass cls) {
        return getProviderTermForImage(locale, cls);
    }

    @Override
    public boolean hasPublicLibrary() {
        return true;
    }

    @Override
    public @Nonnull Requirement identifyLocalBundlingRequirement() throws CloudException, InternalException {
        return Requirement.NONE;
    }

    @Override
    public @Nonnull AsynchronousTask<String> imageVirtualMachine(final @Nonnull String vmId, final @Nonnull String name, final @Nonnull String description) throws CloudException, InternalException {
        final VirtualMachine vm = provider.getComputeServices().getVirtualMachineSupport().getVirtualMachine(vmId);

        if( vm == null ) {
            throw new CloudException("No such virtual machine: " + vmId);
        }
        final AsynchronousTask<String> task = new AsynchronousTask<String>();
        Thread t = new Thread() {
            public void run() {
                try {
                    task.completeWithResult(image(ImageCreateOptions.getInstance(vm,  name, description)));
                }
                catch( Throwable t ) {
                    task.complete(t);
                }
            }
        };

        t.setName("Image bundler " + vmId);
        t.setDaemon(true);
        t.start();

        return task;
    }

    private @Nonnull String image(@Nonnull ImageCreateOptions options) throws CloudException, InternalException {
        ProviderContext ctx = provider.getContext();

        if( ctx == null ) {
            throw new SCEConfigException("No context was configured for this request");
        }
        SCEMethod method = new SCEMethod(provider);

        ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();

        params.add(new BasicNameValuePair("state", "save"));
        params.add(new BasicNameValuePair("name", options.getName()));
        params.add(new BasicNameValuePair("description", options.getDescription()));

        String body = method.put("/instances/" + options.getVirtualMachineId(), params);

        if( body == null ) {
            throw new CloudException("No response body when bundling image");
        }
        Document xml = method.parseResponse(body, true);
        NodeList items = xml.getElementsByTagName("Image");

        for( int i=0; i<items.getLength(); i++ ) {
            Node item = items.item(i);

            MachineImage img = toMachineImage(ctx, item, null, null, null, null);

            if( img != null ) {
                return img.getProviderMachineImageId();
            }
        }
        throw new CloudException("No image was in the XML response from the cloud");
    }

    @Override
    public boolean isImageSharedWithPublic(@Nonnull String machineImageId) throws CloudException, InternalException {
        ProviderContext ctx = provider.getContext();

        if( ctx == null ) {
            throw new SCEConfigException("No context was configured for this request");
        }
        SCEMethod method = new SCEMethod(provider);

        Document xml = method.getAsXML("/offerings/image/" + machineImageId);

        if( xml == null ) {
            return false;
        }
        NodeList items = xml.getElementsByTagName("Image");

        return (items.getLength() > 0 && isShared(items.item(0)));
    }

    private boolean isShared(@Nullable Node node) {
        if( node == null ) {
            return false;
        }

        NodeList attributes = node.getChildNodes();

        for( int i=0; i<attributes.getLength(); i++ ) {
            Node attr = attributes.item(i);

            if( attr.getNodeName().equalsIgnoreCase("Visibility") ) {
                return (attr.hasChildNodes() && !"PRIVATE".equalsIgnoreCase(attr.getFirstChild().getNodeValue().trim()));
            }
        }
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
    public @Nonnull Iterable<ResourceStatus> listImageStatus(@Nonnull ImageClass cls) throws CloudException, InternalException {
        ProviderContext ctx = provider.getContext();

        if( ctx == null ) {
            throw new SCEConfigException("No context was configured for this request");
        }
        SCEMethod method = new SCEMethod(provider);

        Document xml = method.getAsXML("/offerings/image");

        if( xml == null ) {
            return Collections.emptyList();
        }
        NodeList items = xml.getElementsByTagName("Image");
        ArrayList<ResourceStatus> images = new ArrayList<ResourceStatus>();

        for( int i=0; i<items.getLength(); i++ ) {
            Node item = items.item(i);

            ResourceStatus img = toStatus(item, ctx.getAccountNumber(), ctx.getRegionId());

            if( img != null ) {
                images.add(img);
            }
        }
        return images;
    }

    @Override
    public @Nonnull Iterable<MachineImage> listImages(@Nonnull ImageClass cls) throws CloudException, InternalException {
        ProviderContext ctx = provider.getContext();

        if( ctx == null ) {
            throw new SCEConfigException("No context was configured for this request");
        }
        SCEMethod method = new SCEMethod(provider);

        Document xml = method.getAsXML("/offerings/image");

        if( xml == null ) {
            return Collections.emptyList();
        }
        NodeList items = xml.getElementsByTagName("Image");
        ArrayList<MachineImage> images = new ArrayList<MachineImage>();

        for( int i=0; i<items.getLength(); i++ ) {
            Node item = items.item(i);

            MachineImage img = toMachineImage(ctx, item, ctx.getAccountNumber(), null, null, null);

            if( img != null ) {
                images.add(img);
            }
        }
        return images;
    }

    @Override
    public @Nonnull Iterable<MachineImage> listImages(@Nonnull ImageClass cls, @Nonnull String ownedBy) throws CloudException, InternalException {
        if( !cls.equals(ImageClass.MACHINE) ) {
            return Collections.emptyList();
        }
        ProviderContext ctx = provider.getContext();

        if( ctx == null ) {
            throw new SCEConfigException("No context was configured for this request");
        }
        SCEMethod method = new SCEMethod(provider);

        Document xml = method.getAsXML("/offerings/image");

        if( xml == null ) {
            return Collections.emptyList();
        }
        NodeList items = xml.getElementsByTagName("Image");
        ArrayList<MachineImage> images = new ArrayList<MachineImage>();

        for( int i=0; i<items.getLength(); i++ ) {
            Node item = items.item(i);

            MachineImage img = toMachineImage(ctx, item, ownedBy, null, null, null);

            if( img != null ) {
                images.add(img);
            }
        }
        return images;
    }

    @Override
    @Deprecated
    public @Nonnull Iterable<MachineImage> listMachineImages() throws CloudException, InternalException {
        ProviderContext ctx = provider.getContext();

        if( ctx == null ) {
            throw new SCEConfigException("No context was specified for this request");
        }
        return listImages(ImageClass.MACHINE, ctx.getAccountNumber());
    }

    @Override
    @Deprecated
    public @Nonnull Iterable<MachineImage> listMachineImagesOwnedBy(String accountId) throws CloudException, InternalException {
        if( accountId == null ) {
            return listImages(ImageClass.MACHINE);
        }
        else {
            return listImages(ImageClass.MACHINE, accountId);
        }
    }

    @Override
    public @Nonnull Iterable<MachineImageFormat> listSupportedFormats() throws CloudException, InternalException {
        return Collections.emptyList();
    }

    @Override
    public @Nonnull Iterable<MachineImageFormat> listSupportedFormatsForBundling() throws CloudException, InternalException {
        return Collections.emptyList();
    }

    @Override
    public @Nonnull Iterable<String> listShares(@Nonnull String forMachineImageId) throws CloudException, InternalException {
        return Collections.emptyList();
    }

    @Override
    public @Nonnull Iterable<ImageClass> listSupportedImageClasses() throws CloudException, InternalException {
        return Collections.singletonList(ImageClass.MACHINE);
    }

    @Override
    public @Nonnull Iterable<MachineImageType> listSupportedImageTypes() throws CloudException, InternalException {
        return Collections.singletonList(MachineImageType.VOLUME);
    }

    @Override
    public @Nonnull MachineImage registerImageBundle(@Nonnull ImageCreateOptions options) throws CloudException, InternalException {
        throw new OperationNotSupportedException("This operation is not currently supported");
    }

    @Override
    public void remove(@Nonnull String machineImageId) throws CloudException, InternalException {
        remove(machineImageId, false);
    }

    @Override
    public void remove(@Nonnull String providerImageId, boolean checkState) throws CloudException, InternalException {
        ProviderContext ctx = provider.getContext();

        if( ctx == null ) {
            throw new SCEConfigException("No context was configured for this request");
        }

        SCEMethod method = new SCEMethod(provider);

        method.delete("/offerings/image/" + providerImageId);
    }

    @Override
    public void removeAllImageShares(@Nonnull String providerImageId) throws CloudException, InternalException {
        throw new OperationNotSupportedException("No direct image sharing is supported");
    }

    @Override
    public void removeImageShare(@Nonnull String providerImageId, @Nonnull String accountNumber) throws CloudException, InternalException {
        throw new OperationNotSupportedException("No direct image sharing is supported");
    }

    @Override
    public void removePublicShare(@Nonnull String providerImageId) throws CloudException, InternalException {
        throw new OperationNotSupportedException("No public image sharing is supported");
    }

    @Override
    @Deprecated
    public @Nonnull Iterable<MachineImage> searchMachineImages(@Nullable String keyword, @Nullable Platform platform, @Nullable Architecture architecture) throws CloudException, InternalException {
        return searchImages(null, keyword, platform, architecture);
    }

    @Override
    public @Nonnull Iterable<MachineImage> searchImages(@Nullable String accountNumber, @Nullable String keyword, @Nullable Platform platform, @Nullable Architecture architecture, @Nullable ImageClass... imageClasses) throws CloudException, InternalException {
        if( accountNumber == null ) {
            return searchPublicImages(keyword, platform, architecture, imageClasses);
        }
        if( imageClasses != null ) {
            boolean ok = false;

            for( ImageClass cls : imageClasses ) {
                if( cls.equals(ImageClass.MACHINE) ) {
                    ok = true;
                    break;
                }
            }
            if( !ok ) {
                return Collections.emptyList();
            }
        }
        ProviderContext ctx = provider.getContext();

        if( ctx == null ) {
            throw new SCEConfigException("No context was configured for this request");
        }
        SCEMethod method = new SCEMethod(provider);

        Document xml = method.getAsXML("/offerings/image");

        if( xml == null ) {
            return Collections.emptyList();
        }
        NodeList items = xml.getElementsByTagName("Image");
        ArrayList<MachineImage> images = new ArrayList<MachineImage>();

        for( int i=0; i<items.getLength(); i++ ) {
            Node item = items.item(i);

            MachineImage img = toMachineImage(ctx, item, accountNumber, keyword, platform, architecture);

            if( img != null ) {
                images.add(img);
            }
        }
        return images;
    }

    @Override
    public @Nonnull Iterable<MachineImage> searchPublicImages(@Nullable String keyword, @Nullable Platform platform, @Nullable Architecture architecture, @Nullable ImageClass... imageClasses) throws CloudException, InternalException {
        System.out.println("Searching public images: " + keyword + "/" + platform + "/" + architecture);
        ProviderContext ctx = provider.getContext();

        if( ctx == null ) {
            throw new SCEConfigException("No context was configured for this request");
        }
        SCEMethod method = new SCEMethod(provider);

        Document xml = method.getAsXML("/offerings/image");

        if( xml == null ) {
            return Collections.emptyList();
        }
        NodeList items = xml.getElementsByTagName("Image");
        ArrayList<MachineImage> images = new ArrayList<MachineImage>();

        for( int i=0; i<items.getLength(); i++ ) {
            Node item = items.item(i);

            MachineImage img = toMachineImage(ctx, item, null, keyword, platform, architecture);

            if( img != null ) {
                if( imageClasses != null && imageClasses.length > 0 ) {
                    boolean contained = false;

                    for( ImageClass cls : imageClasses ) {
                        if( cls.equals(img.getImageClass()) ) {
                            contained = true;
                            break;
                        }
                    }
                    if( !contained ) {
                        continue;
                    }
                }
                images.add(img);
            }
        }
        System.out.println("Got: " + images);
        return images;
    }

    @Override
    @Deprecated
    public void shareMachineImage(@Nonnull String machineImageId, @Nullable String withAccountId, boolean allow) throws CloudException, InternalException {
        throw new OperationNotSupportedException("There is no API to share machine images");
    }

    @Override
    public boolean supportsCustomImages() {
        return true;
    }

    @Override
    public boolean supportsDirectImageUpload() throws CloudException, InternalException {
        return false;
    }

    @Override
    public boolean supportsImageCapture(@Nonnull MachineImageType type) throws CloudException, InternalException {
        return MachineImageType.VOLUME.equals(type);
    }

    @Override
    public boolean supportsImageSharing() {
        return false;
    }

    @Override
    public boolean supportsImageSharingWithPublic() {
        return true;
    }

    @Override
    public boolean supportsPublicLibrary(@Nonnull ImageClass cls) throws CloudException, InternalException {
        return ImageClass.MACHINE.equals(cls);
    }

    @Override
    public void updateTags(@Nonnull String imageId, @Nonnull Tag... tags) throws CloudException, InternalException {
        // NO-OP
    }

    @Override
    public @Nonnull String[] mapServiceAction(@Nonnull ServiceAction action) {
        return new String[0];
    }

    private @Nullable MachineImage toMachineImage(@Nonnull ProviderContext ctx, @Nullable Node node, @Nullable String accountId, @Nullable String keyword, @Nullable Platform platform, @Nullable Architecture architecture) throws CloudException, InternalException {
        if( node == null || !node.hasChildNodes() ) {
            return null;
        }
        NodeList attributes = node.getChildNodes();
        MachineImage img = new MachineImage();

        img.setPlatform(Platform.UNKNOWN);
        img.setSoftware("");
        img.setType(MachineImageType.VOLUME);
        img.setCurrentState(MachineImageState.PENDING);
        img.setImageClass(ImageClass.MACHINE);
        for( int i=0; i<attributes.getLength(); i++ ) {
            Node attr = attributes.item(i);
            String nodeName = attr.getNodeName();

            if( nodeName.equalsIgnoreCase("ID") && attr.hasChildNodes() ) {
                img.setProviderMachineImageId(attr.getFirstChild().getNodeValue().trim());
            }
            else if( nodeName.equalsIgnoreCase("Name") && attr.hasChildNodes() ) {
                img.setName(attr.getFirstChild().getNodeValue().trim());
            }
            else if( nodeName.equalsIgnoreCase("Description") && attr.hasChildNodes() ) {
                img.setDescription(attr.getFirstChild().getNodeValue().trim());
            }
            else if( nodeName.equalsIgnoreCase("Location") && attr.hasChildNodes() ) {
                img.setProviderRegionId(attr.getFirstChild().getNodeValue().trim());
            }
            else if( nodeName.equalsIgnoreCase("State") && attr.hasChildNodes() ) {
                String status = attr.getFirstChild().getNodeValue().trim();

                img.setCurrentState(toMachineImageState(status));
            }
            else if( nodeName.equalsIgnoreCase("Owner") && attr.hasChildNodes() ) {
                img.setProviderOwnerId(attr.getFirstChild().getNodeValue().trim());
            }
            else if( nodeName.equalsIgnoreCase("Platform") && attr.hasChildNodes() ) {
                String p = attr.getFirstChild().getNodeValue().trim();

                img.setPlatform(toPlatform(p));
            }
            else if( nodeName.equalsIgnoreCase("Architecture") && attr.hasChildNodes() ) {
                String a = attr.getFirstChild().getNodeValue().trim();

                img.setArchitecture(toArchitecture(a));
            }
            else if( nodeName.equalsIgnoreCase("CreatedTime") && attr.hasChildNodes() ) {
                // will eventually support created time
            }
        }
        if( img.getProviderMachineImageId() == null ) {
            return null;
        }
        String regionId = img.getProviderRegionId();

        if( regionId == null ) {
            return null;
        }
        if( !regionId.equals(ctx.getRegionId()) ) {
            return null;
        }
        if( accountId != null && !accountId.equals(img.getProviderOwnerId()) ) {
            return null;
        }
        if( img.getName() == null ) {
            img.setName(img.getProviderMachineImageId());
        }
        if( img.getDescription() == null ) {
            img.setDescription(img.getName() + " [#" + img.getProviderMachineImageId() + "]");
        }
        if( keyword != null ) {
            keyword = keyword.toLowerCase();
            if( !img.getName().toLowerCase().contains(keyword) && !img.getDescription().contains(keyword) ) {
                return null;
            }
        }
        if( img.getPlatform() == null || img.getPlatform().equals(Platform.UNKNOWN) ) {
            img.setPlatform(toPlatform(img.getName() + " " + img.getDescription()));
        }
        if( platform != null && !platform.equals(Platform.UNKNOWN)) {
            Platform p = img.getPlatform();

            if( !platform.equals(p) ) {
                if( !(platform.equals(Platform.UNIX) && p.isUnix()) ) {
                    return null;
                }
            }
        }
        if( architecture != null && !architecture.equals(img.getArchitecture()) ) {
            return null;
        }
        return img;
    }

    private @Nonnull Platform toPlatform(@Nonnull String p) {
        return Platform.guess(p);
    }

    private Architecture toArchitecture(String a) {
        if( a.equals("i386") ) {
            return Architecture.I32;
        }
        else if( a.startsWith("x86") ) {
            return Architecture.I64;
        }
        System.out.println("DEBUG: Unknown architecture: " + a);
        return Architecture.I64;
    }

    private @Nonnull MachineImageState toMachineImageState(@Nonnull String status) {
        if( status.equals("0") || status.equals("2") || status.equals("4") || status.equals("5") || status.equals("6") ) {
            return MachineImageState.PENDING;
        }
        else if( status.equals("1") ) {
            return MachineImageState.ACTIVE;
        }
        else if( status.equals("3") || status.equals("7") ) {
            return MachineImageState.DELETED;
        }
        System.out.println("DEBUG: Unknown machine image state: " + status);
        return MachineImageState.PENDING;
    }

    private @Nullable ResourceStatus toStatus(@Nullable Node node, @Nullable String accountId, @Nullable String regionId) throws CloudException, InternalException {
        if( node == null || !node.hasChildNodes() ) {
            return null;
        }
        NodeList attributes = node.getChildNodes();
        MachineImageState state = null;
        String ownerId = null;
        String imgId = null;
        String rid = null;

        for( int i=0; i<attributes.getLength(); i++ ) {
            Node attr = attributes.item(i);
            String nodeName = attr.getNodeName();

            if( nodeName.equalsIgnoreCase("ID") && attr.hasChildNodes() ) {
                imgId = attr.getFirstChild().getNodeValue().trim();
            }
            else if( nodeName.equalsIgnoreCase("State") && attr.hasChildNodes() ) {
                String status = attr.getFirstChild().getNodeValue().trim();

                state = toMachineImageState(status);
            }
            else if( nodeName.equalsIgnoreCase("Owner") && attr.hasChildNodes() ) {
                ownerId = attr.getFirstChild().getNodeValue().trim();
            }
            else if( nodeName.equalsIgnoreCase("Location") && attr.hasChildNodes() ) {
                rid = attr.getFirstChild().getNodeValue().trim();
            }
            if( state != null && imgId != null && ownerId != null && rid != null ) {
                break;
            }
        }
        if( imgId == null ) {
            return null;
        }
        if( rid == null || !rid.equals(regionId) ) {
            return null;
        }
        if( accountId != null && !accountId.equals(ownerId) ) {
            return null;
        }
        return new ResourceStatus(imgId, state == null ? MachineImageState.PENDING : state);
    }
}
