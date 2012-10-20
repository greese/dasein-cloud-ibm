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
import org.dasein.cloud.CloudProvider;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.OperationNotSupportedException;
import org.dasein.cloud.ProviderContext;
import org.dasein.cloud.compute.Architecture;
import org.dasein.cloud.compute.MachineImage;
import org.dasein.cloud.compute.MachineImageFormat;
import org.dasein.cloud.compute.MachineImageState;
import org.dasein.cloud.compute.MachineImageSupport;
import org.dasein.cloud.compute.MachineImageType;
import org.dasein.cloud.compute.Platform;
import org.dasein.cloud.dc.Region;
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
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;

/**
 * [Class Documentation]
 * <p>Created by George Reese: 7/17/12 1:10 PM</p>
 *
 * @author George Reese
 * @version 2012.02
 * @since 2012.02
 */
public class SCEImage implements MachineImageSupport {
    private SCE provider;

    public SCEImage(SCE provider) { this.provider = provider; }

    @Override
    public void downloadImage(@Nonnull String machineImageId, @Nonnull OutputStream toOutput) throws CloudException, InternalException {
        throw new OperationNotSupportedException("Unable to download images from cloud");
    }

    @Override
    public MachineImage getMachineImage(@Nonnull String machineImageId) throws CloudException, InternalException {
        ProviderContext ctx = provider.getContext();

        if( ctx == null ) {
            throw new SCEConfigException("No context was configured for this request");
        }
        SCEMethod method = new SCEMethod(provider);

        Document xml = method.getAsXML("/offerings/image/" + machineImageId);

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
    public @Nonnull String getProviderTermForImage(@Nonnull Locale locale) {
        return "image";
    }

    @Override
    public boolean hasPublicLibrary() {
        return true;
    }

    @Override
    public @Nonnull AsynchronousTask<String> imageVirtualMachine(final @Nonnull String vmId, final @Nonnull String name, final @Nonnull String description) throws CloudException, InternalException {
        final AsynchronousTask<String> task = new AsynchronousTask<String>();
        Thread t = new Thread() {
            public void run() {
                try {
                    task.completeWithResult(image(vmId, name, description));
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

    private @Nonnull String image(@Nonnull String vmId, @Nonnull String name, @Nonnull String description) throws CloudException, InternalException {
        ProviderContext ctx = provider.getContext();

        if( ctx == null ) {
            throw new SCEConfigException("No context was configured for this request");
        }
        SCEMethod method = new SCEMethod(provider);

        ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();

        params.add(new BasicNameValuePair("state", "save"));
        params.add(new BasicNameValuePair("name", name));
        params.add(new BasicNameValuePair("description", description));

        String body = method.put("/instances/" + vmId, params);

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
    public @Nonnull AsynchronousTask<String> imageVirtualMachineToStorage(String vmId, String name, String description, String directory) throws CloudException, InternalException {
        throw new OperationNotSupportedException("This cloud does not support imaging to storage");
    }

    @Override
    public @Nonnull String installImageFromUpload(@Nonnull MachineImageFormat format, @Nonnull InputStream imageStream) throws CloudException, InternalException {
        throw new OperationNotSupportedException("This cloud does not support image installs from uploads");
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
    public @Nonnull Iterable<MachineImage> listMachineImages() throws CloudException, InternalException {
        ProviderContext ctx = provider.getContext();

        if( ctx == null ) {
            throw new SCEConfigException("No context was specified for this request");
        }
        return listMachineImagesOwnedBy(ctx.getAccountNumber());
    }

    @Override
    public @Nonnull Iterable<MachineImage> listMachineImagesOwnedBy(String accountId) throws CloudException, InternalException {
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

            MachineImage img = toMachineImage(ctx, item, accountId, null, null, null);

            if( img != null ) {
                images.add(img);
            }
        }
        return images;
    }

    @Override
    public @Nonnull Iterable<MachineImageFormat> listSupportedFormats() throws CloudException, InternalException {
        return Collections.emptyList();
    }

    @Override
    public @Nonnull Iterable<String> listShares(@Nonnull String forMachineImageId) throws CloudException, InternalException {
        return Collections.emptyList();
    }

    @Override
    public @Nonnull String registerMachineImage(String atStorageLocation) throws CloudException, InternalException {
        throw new OperationNotSupportedException("Images from storage are not supported");
    }

    @Override
    public void remove(@Nonnull String machineImageId) throws CloudException, InternalException {
        ProviderContext ctx = provider.getContext();

        if( ctx == null ) {
            throw new SCEConfigException("No context was configured for this request");
        }

        SCEMethod method = new SCEMethod(provider);

        method.delete("/offerings/image/" + machineImageId);
    }

    @Override
    public @Nonnull Iterable<MachineImage> searchMachineImages(@Nullable String keyword, @Nullable Platform platform, @Nullable Architecture architecture) throws CloudException, InternalException {
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
                images.add(img);
            }
        }
        return images;
    }

    @Override
    public void shareMachineImage(@Nonnull String machineImageId, @Nonnull String withAccountId, boolean allow) throws CloudException, InternalException {
        throw new OperationNotSupportedException("There is no API to share machine images");
    }

    @Override
    public boolean supportsCustomImages() {
        return true;
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
    public @Nonnull String transfer(@Nonnull CloudProvider fromCloud, @Nonnull String machineImageId) throws CloudException, InternalException {
        throw new OperationNotSupportedException("Image transfers between regions are not supported");
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
        img.setType(MachineImageType.STORAGE);
        img.setCurrentState(MachineImageState.PENDING);
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
}
