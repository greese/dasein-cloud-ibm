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

package org.dasein.cloud.ibm.sce.network.staticip;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.OperationNotSupportedException;
import org.dasein.cloud.ProviderContext;
import org.dasein.cloud.ibm.sce.ExtendedRegion;
import org.dasein.cloud.ibm.sce.SCE;
import org.dasein.cloud.ibm.sce.SCEConfigException;
import org.dasein.cloud.ibm.sce.SCEMethod;
import org.dasein.cloud.identity.ServiceAction;
import org.dasein.cloud.network.AddressType;
import org.dasein.cloud.network.IpAddress;
import org.dasein.cloud.network.IpAddressSupport;
import org.dasein.cloud.network.IpForwardingRule;
import org.dasein.cloud.network.Protocol;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * [Class Documentation]
 * <p>Created by George Reese: 7/17/12 3:41 PM</p>
 *
 * @author George Reese
 * @version 2012.02
 * @since 2012.02
 */
public class SCEStaticIP implements IpAddressSupport {
    private SCE provider;

    public SCEStaticIP(SCE provider) { this.provider = provider; }

    @Override
    public void assign(@Nonnull String addressId, @Nonnull String serverId) throws InternalException, CloudException {
        throw new OperationNotSupportedException("Unable to assign IP address to server");
    }

    @Override
    public @Nonnull String forward(@Nonnull String addressId, int publicPort, @Nonnull Protocol protocol, int privatePort, @Nonnull String onServerId) throws InternalException, CloudException {
        throw new OperationNotSupportedException("This cloud does not support IP forwarding");
    }

    @Override
    public ExtendedIpAddress getIpAddress(@Nonnull String addressId) throws InternalException, CloudException {
        ProviderContext ctx = provider.getContext();

        if( ctx == null ) {
            throw new SCEConfigException("No context was configured for this request");
        }
        SCEMethod method = new SCEMethod(provider);

        Document xml = method.getAsXML("/addresses");

        if( xml == null ) {
            return null;
        }
        List<AddressOffering> offerings = listOfferings();
        NodeList nodes = xml.getElementsByTagName("Address");

        for( int i=0; i<nodes.getLength(); i++ ) {
            Node item = nodes.item(i);
            ExtendedIpAddress address = toAddress(ctx, item, offerings);

            if( address != null ) {
                if( addressId.equals(address.getProviderIpAddressId()) ) {
                    return address;
                }
            }
        }
        return null;
    }

    @Override
    public @Nonnull String getProviderTermForIpAddress(@Nonnull Locale locale) {
        return "IP address";
    }

    @Override
    public boolean isAssigned(@Nonnull AddressType type) {
        return true;
    }

    @Override
    public boolean isForwarding() {
        return false;
    }

    @Override
    public boolean isRequestable(@Nonnull AddressType type) {
        try {
            for( AddressOffering offering : listOfferings() ) {
                if( offering.type.equals(type) ) {
                    return true;
                }
            }
            return false;
        }
        catch( Exception e ) {
            throw new RuntimeException(e);
        }
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

    private class AddressOffering {
        public AddressType type;
        public String offeringId;
    }

    public List<AddressOffering> listOfferings() throws CloudException, InternalException {
        ProviderContext ctx = provider.getContext();

        if( ctx == null ) {
            throw new SCEConfigException("No context was configured for this request");
        }
        SCEMethod method = new SCEMethod(provider);

        Document xml = method.getAsXML("/offerings/address");

        if( xml == null ) {
            return Collections.emptyList();
        }
        NodeList nodes = xml.getElementsByTagName("Offerings");
        ArrayList<AddressOffering> offerings = new ArrayList<AddressOffering>();

        for( int i=0; i<nodes.getLength(); i++ ) {
            Node item = nodes.item(i);

            if( item.hasChildNodes() ) {
                NodeList attributes = item.getChildNodes();
                AddressOffering offering = new AddressOffering();

                for( int j=0; j<attributes.getLength(); j++ ) {
                    Node attr = attributes.item(j);
                    String nodeName = attr.getNodeName();

                    if( nodeName.equalsIgnoreCase("ID") && attr.hasChildNodes() ) {
                        offering.offeringId = attr.getFirstChild().getNodeValue().trim();
                    }
                    else if( nodeName.equalsIgnoreCase("ipType") && attr.hasChildNodes() ) {
                        String t = attr.getFirstChild().getNodeValue().trim();

                        offering.type = (t.equals("1") ? AddressType.PRIVATE : AddressType.PUBLIC);
                    }
                }
                if( offering.offeringId != null && offering.type != null ) {
                    offerings.add(offering);
                }
            }

        }
        return offerings;
    }

    @Override
    public @Nonnull Iterable<IpAddress> listPrivateIpPool(boolean unassignedOnly) throws InternalException, CloudException {
        ProviderContext ctx = provider.getContext();

        if( ctx == null ) {
            throw new SCEConfigException("No context was configured for this request");
        }
        SCEMethod method = new SCEMethod(provider);

        Document xml = method.getAsXML("/addresses");

        if( xml == null ) {
            return Collections.emptyList();
        }
        List<AddressOffering> offerings = listOfferings();
        NodeList nodes = xml.getElementsByTagName("Address");
        ArrayList<IpAddress> list = new ArrayList<IpAddress>();

        for( int i=0; i<nodes.getLength(); i++ ) {
            Node item = nodes.item(i);
            IpAddress address = toAddress(ctx, item, offerings);

            if( address != null ) {
                if( address.getAddressType().equals(AddressType.PRIVATE) && (!unassignedOnly || (address.getProviderLoadBalancerId() == null && address.getServerId() == null)) ) {
                    list.add(address);
                }
            }
        }
        return list;
    }

    @Override
    public @Nonnull Iterable<IpAddress> listPublicIpPool(boolean unassignedOnly) throws InternalException, CloudException {
        ProviderContext ctx = provider.getContext();

        if( ctx == null ) {
            throw new SCEConfigException("No context was configured for this request");
        }
        SCEMethod method = new SCEMethod(provider);

        Document xml = method.getAsXML("/addresses");

        if( xml == null ) {
            return Collections.emptyList();
        }
        List<AddressOffering> offerings = listOfferings();
        NodeList nodes = xml.getElementsByTagName("Address");
        ArrayList<IpAddress> list = new ArrayList<IpAddress>();

        for( int i=0; i<nodes.getLength(); i++ ) {
            Node item = nodes.item(i);
            IpAddress address = toAddress(ctx, item, offerings);

            if( address != null ) {
                if( address.getAddressType().equals(AddressType.PUBLIC) && (!unassignedOnly || (address.getProviderLoadBalancerId() == null && address.getServerId() == null)) ) {
                    list.add(address);
                }
            }
        }
        return list;
    }

    @Override
    public @Nonnull Iterable<IpForwardingRule> listRules(@Nonnull String addressId) throws InternalException, CloudException {
        return Collections.emptyList();
    }

    @Override
    public void releaseFromPool(@Nonnull String addressId) throws InternalException, CloudException {
        ProviderContext ctx = provider.getContext();

        if( ctx == null ) {
            throw new SCEConfigException("No context was configured for this request");
        }
        ExtendedIpAddress ip = getIpAddress(addressId);

        while( ip != null && !ip.getRealState().equals("2") && !ip.getRealState().equals("4") && !ip.getRealState().equals("7") && !ip.getRealState().equals("5")) {
            try { Thread.sleep(15000L); }
            catch( InterruptedException ignore ) { }
            ip = getIpAddress(addressId);
        }
        if( ip == null || ip.getRealState().equals("7") || ip.getRealState().equals("5") || ip.getRealState().equals("4")) {
            return;
        }
        SCEMethod method = new SCEMethod(provider);

        method.delete("/addresses/" + addressId);
    }

    @Override
    public void releaseFromServer(@Nonnull String addressId) throws InternalException, CloudException {
        //To change body of implemented methods use File | Settings | File Templates.        throw new OperationNotSupportedException("Unable to release IP address from server");
    }

    @Override
    public @Nonnull String request(@Nonnull AddressType typeOfAddress) throws InternalException, CloudException {
        ProviderContext ctx = provider.getContext();

        if( ctx == null ) {
            throw new SCEConfigException("No context was configured for this request");
        }
        List<AddressOffering> offerings = listOfferings();
        AddressOffering offering = null;

        for( AddressOffering o : offerings ) {
            if( o.type.equals(typeOfAddress) ) {
                offering = o;
                break;
            }
        }
        if( offering == null ) {
            throw new CloudException("No offering exists for " + typeOfAddress);
        }
        ArrayList<NameValuePair> parameters = new ArrayList<NameValuePair>();

        parameters.add(new BasicNameValuePair("offeringID", offering.offeringId));
        parameters.add(new BasicNameValuePair("location", ctx.getRegionId()));

        SCEMethod method = new SCEMethod(provider);
        String response = method.post("addresses", parameters);

        if( response == null ) {
            throw new CloudException("Cloud accepted the post, but no body was in the response");
        }

        Document doc = method.parseResponse(response, true);

        NodeList nodes = doc.getElementsByTagName("Address");

        for( int i=0; i<nodes.getLength(); i++ ) {
            Node item = nodes.item(i);
            ExtendedIpAddress address = toAddress(ctx, item, offerings);

            if( address != null ) {
                while( address != null && address.getRealState().equals("0") ) {
                    try { Thread.sleep(15000L); }
                    catch( InterruptedException ignore ) { }
                    address = getIpAddress(address.getProviderIpAddressId());
                }
                if( address != null ) {
                    return address.getProviderIpAddressId();
                }
            }
        }
        throw new CloudException("No address was found in the response");
    }

    @Override
    public void stopForward(@Nonnull String ruleId) throws InternalException, CloudException {
        throw new OperationNotSupportedException("This cloud does not support forwarding rules");
    }

    @Override
    public @Nonnull String[] mapServiceAction(@Nonnull ServiceAction action) {
        return new String[0];
    }

    private @Nullable ExtendedIpAddress toAddress(@Nonnull ProviderContext ctx, @Nullable Node node, List<AddressOffering> offerings) throws CloudException, InternalException {
        if( node == null || !node.hasChildNodes() ) {
            return null;
        }
        String regionId = ctx.getRegionId();

        if( regionId == null ) {
            return null;
        }

        NodeList attributes = node.getChildNodes();
        ExtendedIpAddress address = new ExtendedIpAddress();
        AddressType type = null;
        String id = null;

        for( int i=0; i<attributes.getLength(); i++ ) {
            Node attr = attributes.item(i);
            String nodeName = attr.getNodeName();

            if( nodeName.equalsIgnoreCase("ID") && attr.hasChildNodes() ) {
                id = attr.getFirstChild().getNodeValue().trim();
            }
            else if( nodeName.equalsIgnoreCase("IP") && attr.hasChildNodes() ) {
                address.setAddress(attr.getFirstChild().getNodeValue().trim());
            }
            else if( nodeName.equalsIgnoreCase("InstanceID") && attr.hasChildNodes() ) {
                address.setServerId(attr.getFirstChild().getNodeValue().trim());
            }
            else if( nodeName.equalsIgnoreCase("Location") && attr.hasChildNodes() ) {
                address.setRegionId(attr.getFirstChild().getNodeValue().trim());
            }
            else if( nodeName.equalsIgnoreCase("OfferingID") && attr.hasChildNodes() ) {
                String t = attr.getFirstChild().getNodeValue().trim();

                for( AddressOffering offering : offerings ) {
                    if( offering.offeringId.equals(t) ) {
                        type = offering.type;
                        break;
                    }
                }
            }
            else if( nodeName.equalsIgnoreCase("State") && attr.hasChildNodes() ) {
                String s = attr.getFirstChild().getNodeValue().trim();

                if( s == null || s.equals("4") || s.equals("5") || s.equals("6") || s.equals("7") ) {
                    return null;
                }
                address.setRealState(s);
            }
        }
        if( id == null || type == null ) {
            return null;
        }
        address.setIpAddressId(id);
        if( !regionId.equals(address.getRegionId()) ) {
            return null;
        }
        address.setAddressType(type);
        return address;
    }
}
