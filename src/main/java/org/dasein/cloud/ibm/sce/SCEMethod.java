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

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpHost;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.NameValuePair;
import org.apache.http.StatusLine;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.conn.params.ConnRoutePNames;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.apache.log4j.Logger;
import org.dasein.cloud.CloudErrorType;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.ProviderContext;
import org.dasein.cloud.util.APITrace;
import org.w3c.dom.Document;
import org.xml.sax.SAXException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Iterator;
import java.util.List;
import java.util.Properties;

/**
 * IBM SmartCloud REST API interaction management. Encapsulates the authentication and wire protocol for talking to
 * IBM SmartCloud.
 * <p>Created by George Reese: 7/16/12 7:38 PM</p>
 * @author George Reese
 * @version 2012.04 initial version
 * @since 2012.04
 */
public class SCEMethod {
    private String endpoint;
    private SCE provider;

    public SCEMethod(SCE cloud) throws InternalException {
        provider = cloud;
        ProviderContext ctx = provider.getContext();

        if( ctx == null ) {
            throw new SCEConfigException("No context was provided for this request");
        }
        endpoint = ctx.getEndpoint();
        if( endpoint == null ) {
            throw new SCEConfigException("No endpoint was provided for this request");
        }
        if( !endpoint.endsWith("/") ) {
            endpoint = endpoint + "/";
        }
    }

    public void delete(@Nonnull String resource) throws CloudException, InternalException {
        Logger std = SCE.getLogger(SCEMethod.class, "std");
        Logger wire = SCE.getLogger(SCEMethod.class, "wire");

        if( std.isTraceEnabled() ) {
            std.trace("enter - " + SCEMethod.class.getName() + ".post(" +resource + ")");
        }
        if( wire.isDebugEnabled() ) {
            wire.debug("POST --------------------------------------------------------> " + endpoint + resource);
            wire.debug("");
        }
        try {
            HttpClient client = getClient();
            HttpDelete method = new HttpDelete(endpoint + resource);

            method.addHeader("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8");
            if( wire.isDebugEnabled() ) {
                wire.debug(method.getRequestLine().toString());
                for( Header header : method.getAllHeaders() ) {
                    wire.debug(header.getName() + ": " + header.getValue());
                }
                wire.debug("");
            }
            HttpResponse response;
            StatusLine status;

            try {
                APITrace.trace(provider, resource);
                response = client.execute(method);
                status = response.getStatusLine();
            }
            catch( IOException e ) {
                std.error("post(): Failed to execute HTTP request due to a cloud I/O error: " + e.getMessage());
                if( std.isTraceEnabled() ) {
                    e.printStackTrace();
                }
                throw new CloudException(e);
            }
            if( std.isDebugEnabled() ) {
                std.debug("post(): HTTP Status " + status);
            }
            Header[] headers = response.getAllHeaders();

            if( wire.isDebugEnabled() ) {
                wire.debug(status.toString());
                for( Header h : headers ) {
                    if( h.getValue() != null ) {
                        wire.debug(h.getName() + ": " + h.getValue().trim());
                    }
                    else {
                        wire.debug(h.getName() + ":");
                    }
                }
                wire.debug("");
            }
            if( status.getStatusCode() != HttpServletResponse.SC_OK && status.getStatusCode() != HttpServletResponse.SC_CREATED && status.getStatusCode() != HttpServletResponse.SC_ACCEPTED ) {
                std.error("post(): Expected OK for GET request, got " + status.getStatusCode());

                HttpEntity entity = response.getEntity();

                if( entity == null ) {
                    throw new SCEException(CloudErrorType.GENERAL, status.getStatusCode(), status.getReasonPhrase(), "An error was returned without explanation");
                }
                String body;

                try {
                    body = EntityUtils.toString(entity);
                }
                catch( IOException e ) {
                    throw new SCEException(CloudErrorType.GENERAL, status.getStatusCode(), status.getReasonPhrase(), e.getMessage());
                }
                if( wire.isDebugEnabled() ) {
                    wire.debug(body);
                }
                wire.debug("");
                throw new SCEException(CloudErrorType.GENERAL, status.getStatusCode(), status.getReasonPhrase(), body);
            }
        }
        finally {
            if( std.isTraceEnabled() ) {
                std.trace("exit - " + SCEMethod.class.getName() + ".post()");
            }
            if( wire.isDebugEnabled() ) {
                wire.debug("");
                wire.debug("POST --------------------------------------------------------> " + endpoint + resource);
            }
        }
    }

    public @Nullable Document getAsXML(@Nonnull String resource) throws CloudException, InternalException {
        try {
            return getAsXML(new URI(endpoint + resource), resource);
        }
        catch( URISyntaxException e ) {
            throw new InternalException("Endpoint misconfiguration (" + endpoint + resource + "): " + e.getMessage());
        }
    }

    public @Nullable Document getAsXML(@Nonnull URI uri, @Nonnull String resource) throws CloudException, InternalException {
        Logger std = SCE.getLogger(SCEMethod.class, "std");
        Logger wire = SCE.getLogger(SCEMethod.class, "wire");

        if( std.isTraceEnabled() ) {
            std.trace("enter - " + SCEMethod.class.getName() + ".get(" + uri + ")");
        }
        if( wire.isDebugEnabled() ) {
            wire.debug("--------------------------------------------------------> " + uri.toASCIIString());
            wire.debug("");
        }
        try {
            HttpClient client = getClient();
            HttpUriRequest get = new HttpGet(uri);

            get.addHeader("Accept", "text/xml");
            if( wire.isDebugEnabled() ) {
                wire.debug(get.getRequestLine().toString());
                for( Header header : get.getAllHeaders() ) {
                    wire.debug(header.getName() + ": " + header.getValue());
                }
                wire.debug("");
            }
            HttpResponse response;
            StatusLine status;

            try {
                APITrace.trace(provider, resource);
                response = client.execute(get);
                status = response.getStatusLine();
            }
            catch( IOException e ) {
                std.error("get(): Failed to execute HTTP request due to a cloud I/O error: " + e.getMessage());
                if( std.isTraceEnabled() ) {
                    e.printStackTrace();
                }
                throw new CloudException(e);
            }
            if( std.isDebugEnabled() ) {
                std.debug("get(): HTTP Status " + status);
            }
            Header[] headers = response.getAllHeaders();

            if( wire.isDebugEnabled() ) {
                wire.debug(status.toString());
                for( Header h : headers ) {
                    if( h.getValue() != null ) {
                        wire.debug(h.getName() + ": " + h.getValue().trim());
                    }
                    else {
                        wire.debug(h.getName() + ":");
                    }
                }
                wire.debug("");
            }
            if( status.getStatusCode() == HttpServletResponse.SC_NOT_FOUND ) {
                return null;
            }
            if( status.getStatusCode() != HttpServletResponse.SC_OK && status.getStatusCode() != HttpServletResponse.SC_NON_AUTHORITATIVE_INFORMATION ) {
                std.error("get(): Expected OK for GET request, got " + status.getStatusCode());

                HttpEntity entity = response.getEntity();
                String body;

                if( entity == null ) {
                    throw new SCEException(CloudErrorType.GENERAL, status.getStatusCode(), status.getReasonPhrase(), "An error was returned without explanation");
                }
                try {
                    body = EntityUtils.toString(entity);
                }
                catch( IOException e ) {
                    throw new SCEException(CloudErrorType.GENERAL, status.getStatusCode(), status.getReasonPhrase(), e.getMessage());
                }
                if( wire.isDebugEnabled() ) {
                    wire.debug(body);
                }
                wire.debug("");
                throw new SCEException(CloudErrorType.GENERAL, status.getStatusCode(), status.getReasonPhrase(), body);
            }
            else {
                HttpEntity entity = response.getEntity();

                if( entity == null ) {
                    return null;
                }
                InputStream input;

                try {
                    input = entity.getContent();
                }
                catch( IOException e ) {
                    std.error("get(): Failed to read response error due to a cloud I/O error: " + e.getMessage());
                    if( std.isTraceEnabled() ) {
                        e.printStackTrace();
                    }
                    throw new CloudException(e);
                }
                return parseResponse(input, true);
            }
        }
        finally {
            if( std.isTraceEnabled() ) {
                std.trace("exit - " + SCEMethod.class.getName() + ".getStream()");
            }
            if( wire.isDebugEnabled() ) {
                wire.debug("");
                wire.debug("--------------------------------------------------------> " + uri.toASCIIString());
            }
        }
    }

    protected @Nonnull HttpClient getClient() throws InternalException {
        ProviderContext ctx = provider.getContext();

        if( ctx == null ) {
            throw new SCEConfigException("No context was defined for this request");
        }
        String endpoint = ctx.getEndpoint();

        if( endpoint == null ) {
            throw new SCEConfigException("No cloud endpoint was defined");
        }
        boolean ssl = endpoint.startsWith("https");
        int targetPort;
        URI uri;

        try {
            uri = new URI(endpoint);
            targetPort = uri.getPort();
            if( targetPort < 1 ) {
                targetPort = (ssl ? 443 : 80);
            }
        }
        catch( URISyntaxException e ) {
            throw new SCEConfigException(e);
        }
        HttpHost targetHost = new HttpHost(uri.getHost(), targetPort, uri.getScheme());
        HttpParams params = new BasicHttpParams();

        HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
        //noinspection deprecation
        HttpProtocolParams.setContentCharset(params, HTTP.UTF_8);
        HttpProtocolParams.setUserAgent(params, "");

        Properties p = ctx.getCustomProperties();

        if( p != null ) {
            String proxyHost = p.getProperty("proxyHost");
            String proxyPort = p.getProperty("proxyPort");

            if( proxyHost != null ) {
                int port = 0;

                if( proxyPort != null && proxyPort.length() > 0 ) {
                    port = Integer.parseInt(proxyPort);
                }
                params.setParameter(ConnRoutePNames.DEFAULT_PROXY, new HttpHost(proxyHost, port, ssl ? "https" : "http"));
            }
        }
        DefaultHttpClient client = new DefaultHttpClient(params);

        try {
            String userName = new String(ctx.getAccessPublic(), "utf-8");
            String password = new String(ctx.getAccessPrivate(), "utf-8");

            client.getCredentialsProvider().setCredentials(new AuthScope(targetHost.getHostName(), targetHost.getPort()), new UsernamePasswordCredentials(userName, password));
        }
        catch( UnsupportedEncodingException e ) {
            throw new InternalException(e);
        }
        return client;
    }

    public @Nonnull Document parseResponse(@Nonnull String responseBody, boolean withWireLogging) throws CloudException, InternalException {
        Logger wire = (withWireLogging ? SCE.getLogger(SCEMethod.class, "wire") : null);

        try {
            if( wire != null && wire.isDebugEnabled() ) {
                String[] lines = responseBody.split("\n");

                if( lines.length < 1 ) {
                    lines = new String[] { responseBody };
                }
                for( String l : lines ) {
                    wire.debug(l);
                }
                wire.debug("");
            }
            ByteArrayInputStream bas = new ByteArrayInputStream(responseBody.getBytes());

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder parser = factory.newDocumentBuilder();
            Document doc = parser.parse(bas);

            bas.close();
            return doc;
        }
        catch( IOException e ) {
            throw new CloudException(e);
        }
        catch( ParserConfigurationException e ) {
            throw new CloudException(e);
        }
        catch( SAXException e ) {
            throw new CloudException(e);
        }
    }

    public @Nonnull Document parseResponse(@Nonnull InputStream responseBodyAsStream, boolean withWireLogging) throws CloudException, InternalException {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(responseBodyAsStream));
            StringBuilder sb = new StringBuilder();
            String line;

            while( (line = in.readLine()) != null ) {
                sb.append(line);
                sb.append("\n");
            }
            in.close();

            return parseResponse(sb.toString(), withWireLogging);
        }
        catch( IOException e ) {
            throw new CloudException(e);
        }
    }

    public @Nullable String post(@Nonnull String resource, @Nonnull List<NameValuePair> parameters) throws CloudException, InternalException {
        Logger std = SCE.getLogger(SCEMethod.class, "std");
        Logger wire = SCE.getLogger(SCEMethod.class, "wire");

        if( std.isTraceEnabled() ) {
            std.trace("enter - " + SCEMethod.class.getName() + ".post(" +resource + ")");
        }
        if( wire.isDebugEnabled() ) {
            wire.debug("POST --------------------------------------------------------> " + endpoint + resource);
            wire.debug("");
        }
        try {
            HttpClient client = getClient();
            HttpPost post = new HttpPost(endpoint + resource);

            post.addHeader("Content-Type", "application/x-www-form-urlencoded");
            post.addHeader("Accept", "text/xml");

            if( wire.isDebugEnabled() ) {
                wire.debug(post.getRequestLine().toString());
                for( Header header : post.getAllHeaders() ) {
                    wire.debug(header.getName() + ": " + header.getValue());
                }
                wire.debug("");
                if( parameters != null ) {
                    Iterator<NameValuePair> it = parameters.iterator();
                    StringBuilder str = new StringBuilder();

                    while( it.hasNext() ) {
                        NameValuePair p = it.next();

                        str.append(p.getName()).append("=").append(p.getValue());
                        if( it.hasNext() ) {
                            str.append("&");
                        }
                    }
                    wire.debug(str.toString());
                    wire.debug("");
                }
            }
            if( parameters != null ) {
                try {
                    post.setEntity(new UrlEncodedFormEntity(parameters, "utf-8"));
                }
                catch( UnsupportedEncodingException e ) {
                    throw new InternalException(e);
                }
            }
            HttpResponse response;
            StatusLine status;

            try {
                APITrace.trace(provider, resource);
                response = client.execute(post);
                status = response.getStatusLine();
            }
            catch( IOException e ) {
                std.error("post(): Failed to execute HTTP request due to a cloud I/O error: " + e.getMessage());
                if( std.isTraceEnabled() ) {
                    e.printStackTrace();
                }
                throw new CloudException(e);
            }
            if( std.isDebugEnabled() ) {
                std.debug("post(): HTTP Status " + status);
            }
            Header[] headers = response.getAllHeaders();

            if( wire.isDebugEnabled() ) {
                wire.debug(status.toString());
                for( Header h : headers ) {
                    if( h.getValue() != null ) {
                        wire.debug(h.getName() + ": " + h.getValue().trim());
                    }
                    else {
                        wire.debug(h.getName() + ":");
                    }
                }
                wire.debug("");
            }
            if( status.getStatusCode() != HttpServletResponse.SC_OK && status.getStatusCode() != HttpServletResponse.SC_CREATED && status.getStatusCode() != HttpServletResponse.SC_ACCEPTED ) {
                std.error("post(): Expected OK for GET request, got " + status.getStatusCode());

                HttpEntity entity = response.getEntity();
                String body;

                if( entity == null ) {
                    throw new SCEException(CloudErrorType.GENERAL, status.getStatusCode(), status.getReasonPhrase(), "An error was returned without explanation");
                }
                try {
                    body = EntityUtils.toString(entity);
                }
                catch( IOException e ) {
                    throw new SCEException(CloudErrorType.GENERAL, status.getStatusCode(), status.getReasonPhrase(), e.getMessage());
                }
                if( wire.isDebugEnabled() ) {
                    wire.debug(body);
                }
                wire.debug("");
                throw new SCEException(CloudErrorType.GENERAL, status.getStatusCode(), status.getReasonPhrase(), body);
            }
            else if( status.getStatusCode() == HttpServletResponse.SC_NO_CONTENT ) {
                return null;
            }
            else {
                HttpEntity entity = response.getEntity();

                if( entity == null ) {
                    return null;
                }
                try {
                    return EntityUtils.toString(entity);
                }
                catch( IOException e ) {
                    throw new SCEException(CloudErrorType.GENERAL, status.getStatusCode(), status.getReasonPhrase(), e.getMessage());
                }
            }
        }
        finally {
            if( std.isTraceEnabled() ) {
                std.trace("exit - " + SCEMethod.class.getName() + ".post()");
            }
            if( wire.isDebugEnabled() ) {
                wire.debug("");
                wire.debug("POST --------------------------------------------------------> " + endpoint + resource);
            }
        }
    }

    public @Nullable String put(@Nonnull String resource, @Nullable List<NameValuePair> parameters) throws CloudException, InternalException {
        Logger std = SCE.getLogger(SCEMethod.class, "std");
        Logger wire = SCE.getLogger(SCEMethod.class, "wire");

        if( std.isTraceEnabled() ) {
            std.trace("enter - " + SCEMethod.class.getName() + ".post(" +resource + ")");
        }
        if( wire.isDebugEnabled() ) {
            wire.debug("POST --------------------------------------------------------> " + endpoint + resource);
            wire.debug("");
        }
        try {
            HttpClient client = getClient();

            HttpPut method = new HttpPut(endpoint + resource);

            method.addHeader("Content-Type", "application/x-www-form-urlencoded");
            method.addHeader("Accept", "text/xml");

            if( wire.isDebugEnabled() ) {
                wire.debug(method.getRequestLine().toString());
                for( Header header : method.getAllHeaders() ) {
                    wire.debug(header.getName() + ": " + header.getValue());
                }
                wire.debug("");
                if( parameters != null ) {
                    Iterator<NameValuePair> it = parameters.iterator();
                    StringBuilder str = new StringBuilder();

                    while( it.hasNext() ) {
                        NameValuePair p = it.next();

                        str.append(p.getName()).append("=").append(p.getValue());
                        if( it.hasNext() ) {
                            str.append("&");
                        }
                    }
                    wire.debug(str.toString());
                    wire.debug("");
                }
            }
            if( parameters != null ) {
                try {
                    method.setEntity(new UrlEncodedFormEntity(parameters, "utf-8"));
                }
                catch( UnsupportedEncodingException e ) {
                    throw new InternalException(e);
                }
            }
            HttpResponse response;
            StatusLine status;

            try {
                APITrace.trace(provider, resource);
                response = client.execute(method);
                status = response.getStatusLine();
            }
            catch( IOException e ) {
                std.error("post(): Failed to execute HTTP request due to a cloud I/O error: " + e.getMessage());
                if( std.isTraceEnabled() ) {
                    e.printStackTrace();
                }
                throw new CloudException(e);
            }
            if( std.isDebugEnabled() ) {
                std.debug("post(): HTTP Status " + status);
            }
            Header[] headers = response.getAllHeaders();

            if( wire.isDebugEnabled() ) {
                wire.debug(status.toString());
                for( Header h : headers ) {
                    if( h.getValue() != null ) {
                        wire.debug(h.getName() + ": " + h.getValue().trim());
                    }
                    else {
                        wire.debug(h.getName() + ":");
                    }
                }
                wire.debug("");
            }
            if( status.getStatusCode() != HttpServletResponse.SC_OK && status.getStatusCode() != HttpServletResponse.SC_CREATED && status.getStatusCode() != HttpServletResponse.SC_ACCEPTED ) {
                std.error("post(): Expected OK for GET request, got " + status.getStatusCode());

                HttpEntity entity = response.getEntity();
                String body;

                if( entity == null ) {
                    throw new SCEException(CloudErrorType.GENERAL, status.getStatusCode(), status.getReasonPhrase(), "An error was returned without explanation");
                }
                try {
                    body = EntityUtils.toString(entity);
                }
                catch( IOException e ) {
                    throw new SCEException(CloudErrorType.GENERAL, status.getStatusCode(), status.getReasonPhrase(), e.getMessage());
                }
                if( wire.isDebugEnabled() ) {
                    wire.debug(body);
                }
                wire.debug("");
                throw new SCEException(CloudErrorType.GENERAL, status.getStatusCode(), status.getReasonPhrase(), body);
            }
            else if( status.getStatusCode() == HttpServletResponse.SC_NO_CONTENT ) {
                return null;
            }
            else {
                HttpEntity entity = response.getEntity();

                if( entity == null ) {
                    return null;
                }
                try {
                    return EntityUtils.toString(entity);
                }
                catch( IOException e ) {
                    throw new SCEException(CloudErrorType.GENERAL, status.getStatusCode(), status.getReasonPhrase(), e.getMessage());
                }
            }
        }
        finally {
            if( std.isTraceEnabled() ) {
                std.trace("exit - " + SCEMethod.class.getName() + ".post()");
            }
            if( wire.isDebugEnabled() ) {
                wire.debug("");
                wire.debug("POST --------------------------------------------------------> " + endpoint + resource);
            }
        }
    }
}
