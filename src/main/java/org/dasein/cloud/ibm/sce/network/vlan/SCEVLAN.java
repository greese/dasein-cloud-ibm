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

package org.dasein.cloud.ibm.sce.network.vlan;

import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.OperationNotSupportedException;
import org.dasein.cloud.ProviderContext;
import org.dasein.cloud.ibm.sce.ExtendedRegion;
import org.dasein.cloud.ibm.sce.SCE;
import org.dasein.cloud.ibm.sce.SCEConfigException;
import org.dasein.cloud.ibm.sce.SCEMethod;
import org.dasein.cloud.identity.ServiceAction;
import org.dasein.cloud.network.NetworkInterface;
import org.dasein.cloud.network.Subnet;
import org.dasein.cloud.network.VLAN;
import org.dasein.cloud.network.VLANSupport;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Locale;
import java.util.UUID;

/**
 * [Class Documentation]
 * <p>Created by George Reese: 7/17/12 7:52 PM</p>
 * @author George Reese
 * @version 2012.02 initial version
 * @since 2012.02
 */
public class SCEVLAN implements VLANSupport {
    private SCE provider;

    public SCEVLAN(SCE provider) { this.provider = provider; }

    @Override
    public boolean allowsNewVlanCreation() throws CloudException, InternalException {
        return false;
    }

    @Override
    public boolean allowsNewSubnetCreation() throws CloudException, InternalException {
        return false;
    }

    @Override
    public Subnet createSubnet(String cidr, String inProviderVlanId, String name, String description) throws CloudException, InternalException {
        throw new OperationNotSupportedException("Cannot create subnets");
    }

    @Override
    public VLAN createVlan(String cidr, String name, String description, String domainName, String[] dnsServers, String[] ntpServers) throws CloudException, InternalException {
        throw new OperationNotSupportedException("Cannot create VLANs");
    }

    @Override
    public int getMaxVlanCount() throws CloudException, InternalException {
        return -2;
    }

    @Override
    public String getProviderTermForNetworkInterface(Locale locale) {
        return "network interface";
    }

    @Override
    public String getProviderTermForSubnet(Locale locale) {
        return "subnet";
    }

    @Override
    public String getProviderTermForVlan(Locale locale) {
        return "VLAN";
    }

    @Override
    public Subnet getSubnet(@Nonnull String subnetId) throws CloudException, InternalException {
        return null;
    }

    @Override
    public VLAN getVlan(@Nonnull String vlanId) throws CloudException, InternalException {
        ProviderContext ctx = provider.getContext();

        if( ctx == null ) {
            throw new SCEConfigException("No context was configured for this request");
        }
        SCEMethod method = new SCEMethod(provider);

        Document xml = method.getAsXML("/offerings/vlan/" + vlanId);

        if( xml == null ) {
            return null;
        }
        NodeList nodes = xml.getElementsByTagName("Vlan");

        for( int i=0; i<nodes.getLength(); i++ ) {
            Node item = nodes.item(i);
            VLAN vlan = toVlan(ctx, item);

            if( vlan != null ) {
                return vlan;
            }
        }
        return null;
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
    public boolean isSubnetDataCenterConstrained() throws CloudException, InternalException {
        return false;
    }

    @Override
    public boolean isVlanDataCenterConstrained() throws CloudException, InternalException {
        return false;
    }

    @Override
    public @Nonnull Iterable<NetworkInterface> listNetworkInterfaces(@Nonnull String forVmId) throws CloudException, InternalException {
        return Collections.emptyList();
    }

    @Override
    public @Nonnull Iterable<Subnet> listSubnets(@Nonnull String inVlanId) throws CloudException, InternalException {
        return Collections.emptyList();
    }

    @Override
    public @Nonnull Iterable<VLAN> listVlans() throws CloudException, InternalException {
        ProviderContext ctx = provider.getContext();

        if( ctx == null ) {
            throw new SCEConfigException("No context was configured for this request");
        }
        SCEMethod method = new SCEMethod(provider);

        Document xml = method.getAsXML("/offerings/vlan");

        if( xml == null ) {
            return Collections.emptyList();
        }
        NodeList nodes = xml.getElementsByTagName("Vlan");
        ArrayList<VLAN> list = new ArrayList<VLAN>();

        for( int i=0; i<nodes.getLength(); i++ ) {
            Node item = nodes.item(i);
            VLAN vlan = toVlan(ctx, item);

            if( vlan != null ) {
                list.add(vlan);
            }
        }
        return list;
    }

    @Override
    public void removeSubnet(String providerSubnetId) throws CloudException, InternalException {
        throw new OperationNotSupportedException("Cannot remove subnets");
    }

    @Override
    public void removeVlan(String vlanId) throws CloudException, InternalException {
        throw new OperationNotSupportedException("Cannot remove VLANs");
    }

    @Override
    public boolean supportsVlansWithSubnets() throws CloudException, InternalException {
        return false;
    }

    @Override
    public @Nonnull String[] mapServiceAction(@Nonnull ServiceAction action) {
        return new String[0];
    }

    private @Nullable VLAN toVlan(@Nonnull ProviderContext ctx, @Nullable Node node) throws CloudException, InternalException {
        if( node == null || !node.hasChildNodes() ) {
            return null;
        }
        NodeList attributes = node.getChildNodes();
        VLAN vlan = new VLAN();

        vlan.setProviderOwnerId(ctx.getAccountNumber());
        for( int i=0; i<attributes.getLength(); i++ ) {
            Node attr = attributes.item(i);
            String nodeName = attr.getNodeName();

            if( nodeName.equalsIgnoreCase("ID") && attr.hasChildNodes() ) {
                vlan.setProviderVlanId(attr.getFirstChild().getNodeValue().trim());
            }
            else if( nodeName.equalsIgnoreCase("Name") && attr.hasChildNodes() ) {
                vlan.setName(attr.getFirstChild().getNodeValue().trim());
            }
            else if( nodeName.equalsIgnoreCase("Location") && attr.hasChildNodes() ) {
                String regionId = attr.getFirstChild().getNodeValue().trim();

                vlan.setProviderRegionId(regionId);
                vlan.setProviderDataCenterId(regionId);
            }
        }
        if( vlan.getProviderVlanId() == null ) {
            return null;
        }
        if( vlan.getName() == null ) {
            vlan.setName(vlan.getProviderVlanId());
        }
        if( vlan.getDescription() == null ) {
            vlan.setDescription(vlan.getName() + " [#" + vlan.getProviderVlanId() + "]");
        }
        return vlan;
    }
}
