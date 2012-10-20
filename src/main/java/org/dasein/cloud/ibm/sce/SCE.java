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

import org.apache.log4j.Logger;
import org.dasein.cloud.AbstractCloud;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.ProviderContext;
import org.dasein.cloud.ibm.sce.compute.SCECompute;
import org.dasein.cloud.ibm.sce.identity.SCEIdentity;
import org.dasein.cloud.ibm.sce.network.SCENetwork;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.text.ParseException;
import java.text.SimpleDateFormat;

/**
 * Provider class for integration with the IBM SmartCloud platform.
 * <p>Created by George Reese: 7/16/12 7:32 PM</p>
 * @author George Reese
 * @version 2012.02 initial version
 * @since 2012.02
 */
public class SCE extends AbstractCloud {
    static private @Nonnull String getLastItem(@Nonnull String name) {
        int idx = name.lastIndexOf('.');

        if( idx < 0 ) {
            return name;
        }
        else if( idx == (name.length()-1) ) {
            return "";
        }
        return name.substring(idx+1);
    }

    static public @Nonnull Logger getLogger(@Nonnull Class<?> cls, @Nonnull String type) {
        String pkg = getLastItem(cls.getPackage().getName());

        if( pkg.equals("sce") ) {
            pkg = "";
        }
        else {
            pkg = pkg + ".";
        }
        return Logger.getLogger("dasein.cloud.ibm.sce." + type + "." + pkg + getLastItem(cls.getName()));
    }

    public SCE() { }

    @Override
    public @Nonnull String getCloudName() {
        return "SmartCloud";
    }

    @Override
    public @Nonnull SCECompute getComputeServices() {
        return new SCECompute(this);
    }

    @Override
    public @Nonnull Locations getDataCenterServices() {
        return new Locations(this);
    }

    @Override
    public @Nonnull SCEIdentity getIdentityServices() {
        return new SCEIdentity(this);
    }

    @Override
    public @Nonnull SCENetwork getNetworkServices() {
        return new SCENetwork(this);
    }

    @Override
    public @Nonnull String getProviderName() {
        return "IBM";
    }

    public long parseTimestamp(@Nullable String time) throws CloudException {
        if( time == null ) {
            return 0L;
        }
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

        if( time.length() > 0 ) {
            try {
                return fmt.parse(time).getTime();
            }
            catch( ParseException e ) {
                fmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
                try {
                    return fmt.parse(time).getTime();
                }
                catch( ParseException encore ) {
                    throw new CloudException("Could not parse date: " + time);
                }
            }
        }
        return 0L;
    }

    @Override
    public @Nullable String testContext() {
        Logger logger = getLogger(SCE.class, "std");

        if( logger.isTraceEnabled() ) {
            logger.trace("ENTER: " + SCE.class.getName() + ".testContext()");
        }
        try {
            try {
                SCEMethod method = new SCEMethod(this);
                ProviderContext ctx = getContext();

                if( ctx == null ) {
                    logger.error("No context was specified for a context test");
                    return null;
                }
                if( method.getAsXML("/locations") == null ) {
                    logger.warn("Account number was invalid for context test: " + ctx.getAccountNumber());
                    return null;
                }
                if( logger.isDebugEnabled() ) {
                    logger.debug("Valid account: " + ctx.getAccountNumber());
                }
                return ctx.getAccountNumber();
            }
            catch( Exception e ) {
                logger.error("Failed to test context: " + e.getMessage());
                e.printStackTrace();
                return null;
            }
        }
        finally {
            if( logger.isTraceEnabled() ) {
                logger.trace("EXIT: " + SCE.class.getName() + ".testContext()");
            }
        }
    }
}
