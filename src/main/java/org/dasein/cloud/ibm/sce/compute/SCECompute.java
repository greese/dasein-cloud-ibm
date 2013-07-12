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

package org.dasein.cloud.ibm.sce.compute;

import org.dasein.cloud.compute.AbstractComputeServices;
import org.dasein.cloud.compute.MachineImageSupport;
import org.dasein.cloud.ibm.sce.SCE;
import org.dasein.cloud.ibm.sce.compute.disk.SCEDisk;
import org.dasein.cloud.ibm.sce.compute.image.SCEImage;
import org.dasein.cloud.ibm.sce.compute.vm.SCEVirtualMachine;

import javax.annotation.Nonnull;

/**
 * Compute services support for IBM SmartCloud.
 * <p>Created by George Reese: 7/17/12 1:04 PM</p>
 * @author George Reese
 * @version 2012.04 initial version
 * @since 2012.04
 */
public class SCECompute extends AbstractComputeServices {
    private SCE provider;

    public SCECompute(SCE provider) { this.provider = provider; }

    @Override
    public @Nonnull SCEImage getImageSupport() {
        return new SCEImage(provider);
    }

    @Override
    public @Nonnull SCEVirtualMachine getVirtualMachineSupport() {
        return new SCEVirtualMachine(provider);
    }

    @Override
    public @Nonnull SCEDisk getVolumeSupport() {
        return new SCEDisk(provider);
    }
}
