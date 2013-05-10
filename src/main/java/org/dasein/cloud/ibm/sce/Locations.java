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

package org.dasein.cloud.ibm.sce;

import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.ProviderContext;
import org.dasein.cloud.dc.DataCenter;
import org.dasein.cloud.dc.DataCenterServices;
import org.dasein.cloud.dc.Region;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Locale;

/**
 * Support for the SmartCloud locations request.
 * <p>Created by George Reese: 7/16/12 10:12 PM</p>
 * @author George Reese
 * @version 2012.04 initial version
 * @since 2012.04
 */
public class Locations implements DataCenterServices {
    private SCE provider;

    protected Locations(SCE provider) { this.provider = provider; }

    @Override
    public @Nullable DataCenter getDataCenter(@Nonnull String providerDataCenterId) throws InternalException, CloudException {
        ProviderContext ctx = provider.getContext();

        if( ctx == null ) {
            throw new SCEConfigException("No context was configured for this request");
        }
        String regionId = ctx.getRegionId();

        for( DataCenter dc : listDataCenters(regionId) ) {
            if( dc.getProviderDataCenterId().equals(providerDataCenterId) ) {
                return dc;
            }
        }
        return null;
    }

    @Override
    public String getProviderTermForDataCenter(Locale locale) {
        return "data center";
    }

    @Override
    public String getProviderTermForRegion(Locale locale) {
        return "location";
    }

    @Override
    public ExtendedRegion getRegion(String providerRegionId) throws InternalException, CloudException {
        try {
            ProviderContext ctx = provider.getContext();

            if( ctx == null ) {
                throw new SCEConfigException("No context was configured for this request");
            }
            SCEMethod method = new SCEMethod(provider);

            Document xml = method.getAsXML("locations/" + providerRegionId);

            if( xml == null ) {
                return null;
            }
            NodeList locations = xml.getElementsByTagName("Location");

            for( int i=0; i<locations.getLength(); i++ ) {
                Node item = locations.item(i);
                ExtendedRegion region = toRegion(ctx, item);

                if( region != null ) {
                    return region;
                }
            }
            return null;
        }
        catch( SCEException e ) {
            // IBM incomprehensibly throws a 500 exception if it gets an invalid region id, so this verifies the situation
            for( Region r : listRegions() ) {
                if( r.getProviderRegionId().equals(providerRegionId) ) {
                    return (ExtendedRegion)r;
                }
            }
            return null;
        }
    }

    @Override
    public Collection<DataCenter> listDataCenters(String providerRegionId) throws InternalException, CloudException {
        Region region = getRegion(providerRegionId);

        if( region == null ) {
            return Collections.emptyList();
        }
        DataCenter dc = new DataCenter();

        dc.setActive(true);
        dc.setAvailable(true);
        dc.setName(region.getName());
        dc.setProviderDataCenterId(region.getProviderRegionId());
        dc.setRegionId(providerRegionId);
        return Collections.singletonList(dc);
    }

    @Override
    public @Nonnull Collection<Region> listRegions() throws InternalException, CloudException {
        ProviderContext ctx = provider.getContext();

        if( ctx == null ) {
            throw new SCEConfigException("No context was configured for this request");
        }
        SCEMethod method = new SCEMethod(provider);

        Document xml = method.getAsXML("/locations");

        if( xml == null ) {
            return Collections.emptyList();
        }
        NodeList locations = xml.getElementsByTagName("Location");
        ArrayList<Region> regions = new ArrayList<Region>();

        for( int i=0; i<locations.getLength(); i++ ) {
            Node item = locations.item(i);
            Region region = toRegion(ctx, item);

            if( region != null ) {
                regions.add(region);
            }
        }
        return regions;
    }

    private @Nullable ExtendedRegion toRegion(@SuppressWarnings("UnusedParameters") @Nonnull ProviderContext ctx, @Nullable Node node) throws CloudException, InternalException {
        if( node == null ) {
            return null;
        }
        ExtendedRegion region = new ExtendedRegion();

        region.setActive(true);
        region.setAvailable(true);
        region.setJurisdiction("US");

        NodeList nodes = node.getChildNodes();

        for( int i=0; i<nodes.getLength(); i++ ) {
            Node attr = nodes.item(i);
            String nodeName = attr.getNodeName();

            if( nodeName.equalsIgnoreCase("ID") && attr.hasChildNodes() ) {
                region.setProviderRegionId(attr.getFirstChild().getNodeValue().trim());
            }
            else if( nodeName.equalsIgnoreCase("Name") && attr.hasChildNodes() ) {
                region.setName(attr.getFirstChild().getNodeValue().trim());
            }
            else if( nodeName.equalsIgnoreCase("Capabilities") && attr.hasChildNodes() ) {
                NodeList capabilities = attr.getChildNodes();

                for( int j=0; j<capabilities.getLength(); j++ ) {
                    Node capability = capabilities.item(j);

                    if( capability.getNodeName().equalsIgnoreCase("Capability") && capability.hasAttributes() ) {
                        Node id = capability.getAttributes().getNamedItem("id");

                        if( id != null ) {
                            if( id.getNodeValue().startsWith("oss.storage") ) {
                                    region.setStorage(true);
                            }
                            else if( id.getNodeValue().startsWith("oss.instance.spec") ) {
                                region.setCompute(true);
                            }
                        }
                    }
                    if( region.isStorage() && region.isCompute() ) {
                        break;
                    }
                }
            }
        }
        if( region.getProviderRegionId() == null ) {
            return null;
        }
        if( region.getName() == null ) {
            region.setName(region.getProviderRegionId());
        }
        if( region.getName().contains("Canada") ) {
            region.setJurisdiction("CA");
        }
        else if( region.getName().contains("Germany") ) {
            region.setJurisdiction("EU");
        }
        else if( region.getName().contains("Singapore") ) {
            region.setJurisdiction("SG");
        }
        else if( region.getName().contains("Japan") ) {
            region.setJurisdiction("JP");
        }
        return region;
    }
}
