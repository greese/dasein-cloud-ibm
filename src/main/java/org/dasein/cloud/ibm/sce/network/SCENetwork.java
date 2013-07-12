/**
 * Copyright (C) 2012-2013 Dell, Inc.
 * See annotations for authorship information
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

package org.dasein.cloud.ibm.sce.network;

import org.dasein.cloud.ibm.sce.SCE;
import org.dasein.cloud.ibm.sce.network.staticip.SCEStaticIP;
import org.dasein.cloud.ibm.sce.network.vlan.SCEVLAN;
import org.dasein.cloud.network.AbstractNetworkServices;

import javax.annotation.Nonnull;

/**
 * [Class Documentation]
 * <p>Created by George Reese: 7/17/12 3:41 PM</p>
 *
 * @author George Reese
 * @version 2012.02 (bugzid: [FOGBUGZID])
 * @since 2012.02
 */
public class SCENetwork extends AbstractNetworkServices {
    private SCE provider;

    public SCENetwork(SCE provider) { this.provider = provider; }

    @Override
    public @Nonnull SCEStaticIP getIpAddressSupport() {
        return new SCEStaticIP(provider);
    }

    @Override
    public @Nonnull SCEVLAN getVlanSupport() {
        return new SCEVLAN(provider);
    }
}
