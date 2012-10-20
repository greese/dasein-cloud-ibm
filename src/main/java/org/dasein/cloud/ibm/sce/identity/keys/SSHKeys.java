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

package org.dasein.cloud.ibm.sce.identity.keys;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.OperationNotSupportedException;
import org.dasein.cloud.ProviderContext;
import org.dasein.cloud.Requirement;
import org.dasein.cloud.ibm.sce.ExtendedRegion;
import org.dasein.cloud.ibm.sce.SCE;
import org.dasein.cloud.ibm.sce.SCEConfigException;
import org.dasein.cloud.ibm.sce.SCEMethod;
import org.dasein.cloud.identity.SSHKeypair;
import org.dasein.cloud.identity.ServiceAction;
import org.dasein.cloud.identity.ShellKeySupport;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletResponse;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;
import java.util.UUID;

/**
 * SSH key support in the IBM SmartCloud.
 * <p>Created by George Reese: 7/17/12 3:38 PM</p>
 * @author George Reese
 * @version 2012.04 initial version
 * @version 2012.09 updated for 2012.09 object model (George Reese)
 * @since 2012.04
 */
public class SSHKeys implements ShellKeySupport {
    private SCE provider;

    public SSHKeys(SCE provider) { this.provider = provider; }

    @Override
    public @Nonnull SSHKeypair createKeypair(@Nonnull String name) throws InternalException, CloudException {
        ProviderContext ctx = provider.getContext();

        if( ctx == null ) {
            throw new SCEConfigException("No context was configured for this request");
        }
        ArrayList<NameValuePair> parameters = new ArrayList<NameValuePair>();

        parameters.add(new BasicNameValuePair("name", name));

        SCEMethod method = new SCEMethod(provider);
        String response = method.post("/keys", parameters);

        if( response == null ) {
            throw new CloudException("Cloud accepted the post, but no body was in the response");
        }

        Document doc = method.parseResponse(response, true);

        NodeList nodes = doc.getElementsByTagName("PrivateKey");

        for( int i=0; i<nodes.getLength(); i++ ) {
            Node item = nodes.item(i);
            SSHKeypair kp = toKeyPair(ctx, item, true);

            if( kp != null ) {
                SSHKeypair withPublic = getKeypair(name);
                String publicKey = (withPublic == null ? null : withPublic.getPublicKey());

                if( publicKey != null ) {
                    kp.setPublicKey(publicKey);
                }
                return kp;
            }
        }
        throw new CloudException("No key pair was in the XML response");
    }

    @Override
    public void deleteKeypair(@Nonnull String providerId) throws InternalException, CloudException {
        ProviderContext ctx = provider.getContext();

        if( ctx == null ) {
            throw new SCEConfigException("No context was configured for this request");
        }

        SCEMethod method = new SCEMethod(provider);

        method.delete("/keys/" + providerId);
    }

    @Override
    public String getFingerprint(@Nonnull String providerId) throws InternalException, CloudException {
        SSHKeypair k = getKeypair(providerId);

        return (k == null ? null : k.getName());
    }

    @Override
    public Requirement getKeyImportSupport() throws CloudException, InternalException {
        return Requirement.NONE;
    }

    @Override
    public SSHKeypair getKeypair(@Nonnull String providerId) throws InternalException, CloudException {
        ProviderContext ctx = provider.getContext();

        if( ctx == null ) {
            throw new SCEConfigException("No context was configured for this request");
        }
        SCEMethod method = new SCEMethod(provider);

        Document xml = method.getAsXML("/keys/" + providerId);

        if( xml == null ) {
            return null;
        }
        NodeList nodes = xml.getElementsByTagName("PublicKey");

        for( int i=0; i<nodes.getLength(); i++ ) {
            Node item = nodes.item(i);
            SSHKeypair kp = toKeyPair(ctx, item, false);

            if( kp != null ) {
                return kp;
            }
        }
        return null;
    }

    @Override
    public @Nonnull String getProviderTermForKeypair(@Nonnull Locale locale) {
        return "public key";
    }

    @Override
    public @Nonnull SSHKeypair importKeypair(@Nonnull String name, @Nonnull String publicKey) throws InternalException, CloudException {
        // TODO: support import of keyspairs. Remember to change the requirement to optional
        throw new OperationNotSupportedException("Not currently supported in Dasein Cloud");
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
    public @Nonnull Collection<SSHKeypair> list() throws InternalException, CloudException {
        ProviderContext ctx = provider.getContext();

        if( ctx == null ) {
            throw new SCEConfigException("No context was configured for this request");
        }
        SCEMethod method = new SCEMethod(provider);

        Document xml = method.getAsXML("/keys");

        if( xml == null ) {
            return Collections.emptyList();
        }
        NodeList nodes = xml.getElementsByTagName("PublicKey");
        ArrayList<SSHKeypair> list = new ArrayList<SSHKeypair>();

        for( int i=0; i<nodes.getLength(); i++ ) {
            Node item = nodes.item(i);
            SSHKeypair kp = toKeyPair(ctx, item, false);

            if( kp != null ) {
                list.add(kp);
            }
        }
        return list;
    }

    @Override
    public @Nonnull String[] mapServiceAction(@Nonnull ServiceAction action) {
        return new String[0];
    }

    private @Nullable SSHKeypair toKeyPair(@Nonnull ProviderContext ctx, @Nullable Node node, boolean post) throws CloudException, InternalException {
        if( node == null || !node.hasChildNodes() ) {
            return null;
        }
        NodeList attributes = node.getChildNodes();
        String regionId = ctx.getRegionId();

        if( regionId == null ) {
            throw new CloudException("No region established for context");
        }
        SSHKeypair kp = new SSHKeypair();

        kp.setProviderOwnerId(ctx.getAccountNumber());
        kp.setProviderRegionId(regionId);
        kp.setFingerprint("Fake out test cases because SCE does not provide a fingerprint");
        for( int i=0; i<attributes.getLength(); i++ ) {
            Node attr = attributes.item(i);
            String nodeName = attr.getNodeName();

            if( nodeName.equalsIgnoreCase("KeyName") && attr.hasChildNodes() ) {
                String id = attr.getFirstChild().getNodeValue().trim();

                kp.setProviderKeypairId(id);
                kp.setName(id);
            }
            else if( nodeName.equalsIgnoreCase("KeyMaterial") && attr.hasChildNodes() ) {
                String material = attr.getFirstChild().getNodeValue().trim();

                try {
                    if( post ) {
                        kp.setPrivateKey(material.getBytes("utf-8"));
                    }
                    else {
                        kp.setPublicKey(material);
                    }
                }
                catch( UnsupportedEncodingException e ) {
                    throw new InternalException(e);
                }
            }
        }
        if( kp.getProviderKeypairId() == null ) {
            return null;
        }
        return kp;
    }
}
