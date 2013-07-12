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

package org.dasein.cloud.ibm.sce.identity;

import org.dasein.cloud.ibm.sce.SCE;
import org.dasein.cloud.ibm.sce.identity.keys.SSHKeys;
import org.dasein.cloud.identity.AbstractIdentityServices;

import javax.annotation.Nonnull;

/**
 * Identity and access support for IBM SmartCloud.
 * <p>Created by George Reese: 7/17/12 3:38 PM</p>
 * @author George Reese
 * @version 2012.04 initial version
 * @since 2012.04
 */
public class SCEIdentity extends AbstractIdentityServices {
    private SCE provider;

    public SCEIdentity(SCE provider) { this.provider = provider; }

    @Override
    public @Nonnull
    SSHKeys getShellKeySupport() {
        return new SSHKeys(provider);
    }
}
