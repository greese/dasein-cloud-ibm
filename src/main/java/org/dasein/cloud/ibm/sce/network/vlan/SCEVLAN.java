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
import org.dasein.cloud.Requirement;
import org.dasein.cloud.ResourceStatus;
import org.dasein.cloud.compute.VirtualMachine;
import org.dasein.cloud.ibm.sce.ExtendedRegion;
import org.dasein.cloud.ibm.sce.SCE;
import org.dasein.cloud.ibm.sce.SCEConfigException;
import org.dasein.cloud.ibm.sce.SCEMethod;
import org.dasein.cloud.identity.ServiceAction;
import org.dasein.cloud.network.AbstractVLANSupport;
import org.dasein.cloud.network.Firewall;
import org.dasein.cloud.network.FirewallSupport;
import org.dasein.cloud.network.IPVersion;
import org.dasein.cloud.network.IpAddressSupport;
import org.dasein.cloud.network.NICCreateOptions;
import org.dasein.cloud.network.NetworkInterface;
import org.dasein.cloud.network.NetworkServices;
import org.dasein.cloud.network.Networkable;
import org.dasein.cloud.network.RoutingTable;
import org.dasein.cloud.network.Subnet;
import org.dasein.cloud.network.SubnetCreateOptions;
import org.dasein.cloud.network.VLAN;
import org.dasein.cloud.network.VLANState;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;

/**
 * Support for IBM networks in SmartCloud.
 * <p>Created by George Reese: 7/17/12 7:52 PM</p>
 * @author George Reese
 * @version 2012.04 initial version
 * @version 2012.09 updates for the 2012.09 object model
 * @since 2012.04
 */
public class SCEVLAN extends AbstractVLANSupport {
    private SCE provider;

    public SCEVLAN(SCE provider) {
        super(provider);
        this.provider = provider;
    }

    @Override
    public void addRouteToAddress(@Nonnull String toRoutingTableId, @Nonnull IPVersion version, @Nullable String destinationCidr, @Nonnull String address) throws CloudException, InternalException {
        throw new OperationNotSupportedException("Routing tables are not supported");
    }

    @Override
    public void addRouteToGateway(@Nonnull String toRoutingTableId, @Nonnull IPVersion version, @Nullable String destinationCidr, @Nonnull String gatewayId) throws CloudException, InternalException {
        throw new OperationNotSupportedException("Routing tables are not supported");
    }

    @Override
    public void addRouteToNetworkInterface(@Nonnull String toRoutingTableId, @Nonnull IPVersion version, @Nullable String destinationCidr, @Nonnull String nicId) throws CloudException, InternalException {
        throw new OperationNotSupportedException("Routing tables are not supported");
    }

    @Override
    public void addRouteToVirtualMachine(@Nonnull String toRoutingTableId, @Nonnull IPVersion version, @Nullable String destinationCidr, @Nonnull String vmId) throws CloudException, InternalException {
        throw new OperationNotSupportedException("Routing tables are not supported");
    }

    @Override
    public boolean allowsNewNetworkInterfaceCreation() throws CloudException, InternalException {
        return false;
    }

    @Override
    public boolean allowsNewVlanCreation() throws CloudException, InternalException {
        return false;
    }

    @Override
    public boolean allowsNewSubnetCreation() throws CloudException, InternalException {
        return false;
    }

    @Override
    public void assignRoutingTableToSubnet(@Nonnull String subnetId, @Nonnull String routingTableId) throws CloudException, InternalException {
        throw new OperationNotSupportedException("Routing tables are not supported");
    }

    @Override
    public void assignRoutingTableToVlan(@Nonnull String vlanId, @Nonnull String routingTableId) throws CloudException, InternalException {
        throw new OperationNotSupportedException("Routing tables are not supported");
    }

    @Override
    public void attachNetworkInterface(@Nonnull String nicId, @Nonnull String vmId, int index) throws CloudException, InternalException {
        throw new OperationNotSupportedException("Network interfaces are not supported");
    }

    @Override
    public String createInternetGateway(@Nonnull String forVlanId) throws CloudException, InternalException {
        throw new OperationNotSupportedException("Internet gateways are not supported");
    }

    @Override
    public @Nonnull String createRoutingTable(@Nonnull String forVlanId, @Nonnull String name, @Nonnull String description) throws CloudException, InternalException {
        throw new OperationNotSupportedException("Routing tables are not supported");
    }

    @Override
    public @Nonnull NetworkInterface createNetworkInterface(@Nonnull NICCreateOptions options) throws CloudException, InternalException {
        throw new OperationNotSupportedException("Network interfaces are not supported");
    }

    @Override
    public @Nonnull Subnet createSubnet(@Nonnull SubnetCreateOptions options) throws CloudException, InternalException {
        throw new OperationNotSupportedException("Cannot create subnets");
    }

    @Override
    public @Nonnull VLAN createVlan(@Nonnull String cidr, @Nonnull String name, @Nonnull String description, @Nonnull String domainName, @Nonnull String[] dnsServers, @Nonnull String[] ntpServers) throws CloudException, InternalException {
        throw new OperationNotSupportedException("Cannot create VLANs");
    }

    @Override
    public void detachNetworkInterface(@Nonnull String nicId) throws CloudException, InternalException {
        throw new OperationNotSupportedException("Network interfaces are not supported");
    }

    @Override
    public int getMaxNetworkInterfaceCount() throws CloudException, InternalException {
        return -2;
    }

    @Override
    public int getMaxVlanCount() throws CloudException, InternalException {
        return -2;
    }

    @Override
    public @Nonnull String getProviderTermForNetworkInterface(@Nonnull Locale locale) {
        return "network interface";
    }

    @Override
    public @Nonnull String getProviderTermForSubnet(@Nonnull Locale locale) {
        return "subnet";
    }

    @Override
    public @Nonnull String getProviderTermForVlan(@Nonnull Locale locale) {
        return "VLAN";
    }

    @Override
    public NetworkInterface getNetworkInterface(@Nonnull String nicId) throws CloudException, InternalException {
        return null;
    }

    @Override
    public RoutingTable getRoutingTableForSubnet(@Nonnull String subnetId) throws CloudException, InternalException {
        return null;
    }

    @Override
    public @Nonnull Requirement getRoutingTableSupport() throws CloudException, InternalException {
        return Requirement.NONE;
    }

    @Override
    public RoutingTable getRoutingTableForVlan(@Nonnull String vlanId) throws CloudException, InternalException {
        return null;
    }

    @Override
    public Subnet getSubnet(@Nonnull String subnetId) throws CloudException, InternalException {
        return null;
    }

    @Override
    public @Nonnull Requirement getSubnetSupport() throws CloudException, InternalException {
        return Requirement.NONE;
    }

    @Override
    public VLAN getVlan(@Nonnull String vlanId) throws CloudException, InternalException {
        for( VLAN vlan : listVlans() ) {
            if( vlan.getProviderVlanId().equals(vlanId) ) {
                return vlan;
            }
        }
        return null;
    }

    @Override
    public boolean isNetworkInterfaceSupportEnabled() throws CloudException, InternalException {
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
    public boolean isSubnetDataCenterConstrained() throws CloudException, InternalException {
        return false;
    }

    @Override
    public boolean isVlanDataCenterConstrained() throws CloudException, InternalException {
        return false;
    }

    @Override
    public Collection<String> listFirewallIdsForNIC(@Nonnull String nicId) throws CloudException, InternalException {
        return Collections.emptyList();
    }

    @Override
    public @Nonnull Iterable<ResourceStatus> listNetworkInterfaceStatus() throws CloudException, InternalException {
        return Collections.emptyList();
    }

    @Override
    public @Nonnull Iterable<NetworkInterface> listNetworkInterfaces() throws CloudException, InternalException {
        return Collections.emptyList();
    }

    @Override
    public @Nonnull Iterable<NetworkInterface> listNetworkInterfacesForVM(@Nonnull String forVmId) throws CloudException, InternalException {
        return Collections.emptyList();
    }

    @Override
    public @Nonnull Iterable<NetworkInterface> listNetworkInterfacesInSubnet(@Nonnull String subnetId) throws CloudException, InternalException {
        return Collections.emptyList();
    }

    @Override
    public @Nonnull Iterable<NetworkInterface> listNetworkInterfacesInVLAN(@Nonnull String vlanId) throws CloudException, InternalException {
        return Collections.emptyList();
    }

    @Override
    public @Nonnull Iterable<Networkable> listResources(@Nonnull String inVlanId) throws CloudException, InternalException {
        ArrayList<Networkable> resources = new ArrayList<Networkable>();
        NetworkServices network = provider.getNetworkServices();

        FirewallSupport fwSupport = network.getFirewallSupport();

        if( fwSupport != null ) {
            for( Firewall fw : fwSupport.list() ) {
                if( inVlanId.equals(fw.getProviderVlanId()) ) {
                    resources.add(fw);
                }
            }
        }

        IpAddressSupport ipSupport = network.getIpAddressSupport();

        if( ipSupport != null ) {
            for( IPVersion version : ipSupport.listSupportedIPVersions() ) {
                for( org.dasein.cloud.network.IpAddress addr : ipSupport.listIpPool(version, false) ) {
                    if( inVlanId.equals(addr.getProviderVlanId()) ) {
                        resources.add(addr);
                    }
                }

            }
        }
        for( RoutingTable table : listRoutingTables(inVlanId) ) {
            resources.add(table);
        }
        Iterable<VirtualMachine> vms = provider.getComputeServices().getVirtualMachineSupport().listVirtualMachines();

        for( VirtualMachine vm : vms ) {
            if( inVlanId.equals(vm.getProviderVlanId()) ) {
                resources.add(vm);
            }
        }
        return resources;
    }


    @Override
    public @Nonnull Iterable<RoutingTable> listRoutingTables(@Nonnull String inVlanId) throws CloudException, InternalException {
        return Collections.emptyList();
    }

    @Override
    public @Nonnull Iterable<Subnet> listSubnets(@Nonnull String inVlanId) throws CloudException, InternalException {
        return Collections.emptyList();
    }

    @Override
    public @Nonnull Iterable<IPVersion> listSupportedIPVersions() throws CloudException, InternalException {
        return Collections.singletonList(IPVersion.IPV4);
    }

    @Override
    public @Nonnull Iterable<ResourceStatus> listVlanStatus() throws CloudException, InternalException {
        ProviderContext ctx = provider.getContext();

        if( ctx == null ) {
            throw new SCEConfigException("No context was configured for this request");
        }
        SCEMethod method = new SCEMethod(provider);

        Document xml = method.getAsXML("offerings/vlan");

        if( xml == null ) {
            return Collections.emptyList();
        }
        NodeList nodes = xml.getElementsByTagName("Vlan");
        ArrayList<ResourceStatus> list = new ArrayList<ResourceStatus>();

        for( int i=0; i<nodes.getLength(); i++ ) {
            Node item = nodes.item(i);
            ResourceStatus vlan = toVlanStatus(item);

            if( vlan != null ) {
                list.add(vlan);
            }
        }
        return list;
    }

    @Override
    public @Nonnull Iterable<VLAN> listVlans() throws CloudException, InternalException {
        ProviderContext ctx = provider.getContext();

        if( ctx == null ) {
            throw new SCEConfigException("No context was configured for this request");
        }
        SCEMethod method = new SCEMethod(provider);

        Document xml = method.getAsXML("offerings/vlan");

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
    public void removeInternetGateway(@Nonnull String forVlanId) throws CloudException, InternalException {
        throw new OperationNotSupportedException("Internet gateways are not supported");
    }

    @Override
    public void removeNetworkInterface(@Nonnull String nicId) throws CloudException, InternalException {
        throw new OperationNotSupportedException("Network interfaces are not supported");
    }

    @Override
    public void removeRoute(@Nonnull String inRoutingTableId, @Nonnull String destinationCidr) throws CloudException, InternalException {
        throw new OperationNotSupportedException("Routing tables are not supported");
    }

    @Override
    public void removeRoutingTable(@Nonnull String routingTableId) throws CloudException, InternalException {
        throw new OperationNotSupportedException("Routing tables are not supported");
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
    public boolean supportsInternetGatewayCreation() throws CloudException, InternalException {
        return false;
    }

    @Override
    public boolean supportsRawAddressRouting() throws CloudException, InternalException {
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

    private @Nullable ResourceStatus toVlanStatus(@Nullable Node node) throws CloudException, InternalException {
        if( node == null || !node.hasChildNodes() ) {
            return null;
        }
        NodeList attributes = node.getChildNodes();
        String vlanId = null;

        for( int i=0; i<attributes.getLength(); i++ ) {
            Node attr = attributes.item(i);
            String nodeName = attr.getNodeName();

            if( nodeName.equalsIgnoreCase("ID") && attr.hasChildNodes() ) {
                vlanId = attr.getFirstChild().getNodeValue().trim();
                break;
            }
        }
        if( vlanId == null ) {
            return null;
        }
        return new ResourceStatus(vlanId, VLANState.AVAILABLE);
    }
}
