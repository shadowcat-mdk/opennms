/*******************************************************************************
 * This file is part of OpenNMS(R).
 *
 * Copyright (C) 2014 The OpenNMS Group, Inc.
 * OpenNMS(R) is Copyright (C) 1999-2014 The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is a registered trademark of The OpenNMS Group, Inc.
 *
 * OpenNMS(R) is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License,
 * or (at your option) any later version.
 *
 * OpenNMS(R) is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with OpenNMS(R).  If not, see:
 *      http://www.gnu.org/licenses/
 *
 * For more information contact:
 *     OpenNMS(R) Licensing <license@opennms.org>
 *     http://www.opennms.org/
 *     http://www.opennms.com/
 *******************************************************************************/

package org.opennms.netmgt.poller.monitors;

import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.net.URISyntaxException;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.Credentials;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.utils.URIBuilder;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.ssl.AllowAllHostnameVerifier;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.conn.ssl.TrustSelfSignedStrategy;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.client.DefaultHttpRequestRetryHandler;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.CoreConnectionPNames;
import org.apache.http.params.HttpParams;
import org.opennms.core.utils.InetAddressUtils;
import org.opennms.core.utils.ParameterMap;
import org.opennms.core.utils.TimeoutTracker;
import org.opennms.netmgt.poller.Distributable;
import org.opennms.netmgt.poller.DistributionContext;
import org.opennms.netmgt.poller.MonitoredService;
import org.opennms.netmgt.poller.NetworkInterface;
import org.opennms.netmgt.poller.NetworkInterfaceNotSupportedException;
import org.opennms.netmgt.poller.PollStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This class is designed to be used by the service poller framework to test the
 * availability of a url by posting a generic payload and evaulating the http response code and banner. The class
 * implements the ServiceMonitor interface that allows it to be used along with
 * other plug-ins by the service poller framework.
 * 
 * @author <A HREF="mailto:jeffg@opennms.org">Jeff Gehlbach</A>
 * @author <A HREF="mailto:cliles@capario.com">Chris Liles</A>
 * @author <A HREF="http://www.opennms.org/">OpenNMS</a>
 */

@Distributable(DistributionContext.DAEMON)
final public class HttpPostMonitor extends AbstractServiceMonitor {

    /**
     * Default port.
     */
    private static final int DEFAULT_PORT = 80;

    /**
     * Default retries.
     */
    private static final int DEFAULT_RETRY = 0;

    /**
     * Default timeout. Specifies how long (in milliseconds) to block waiting
     * for data from the monitored interface.
     */
    private static final int DEFAULT_TIMEOUT = 3000;

    public static final String DEFAULT_MIMETYPE = "text/xml";
    public static final String DEFAULT_CHARSET = "utf-8";
    public static final String DEFAULT_URI = "/";
    public static final String DEFAULT_SCHEME = "http";
    public static final boolean DEFAULT_SSLFILTER = false;

    public static final String PARAMETER_SCHEME = "scheme";
    public static final String PARAMETER_PORT = "port";
    public static final String PARAMETER_URI = "uri";
    public static final String PARAMETER_PAYLOAD = "payload";
    public static final String PARAMETER_MIMETYPE = "mimetype";
    public static final String PARAMETER_CHARSET = "charset";
    public static final String PARAMETER_BANNER = "banner";
    public static final String PARAMETER_SSLFILTER = "usesslfiler";

    public static final String PARAMETER_USERNAME = "auth-username";
    public static final String PARAMETER_PASSWORD = "auth-password";

    private static final Logger LOG = LoggerFactory.getLogger(HttpPostMonitor.class);


    /**
     * {@inheritDoc}
     *
     * Poll the specified address for service availability.
     *
     * During the poll an attempt is made to execute the named method (with optional input) connect on the specified port. If
     * the exec on request is successful, the banner line generated by the
     * interface is parsed and if the banner text indicates that we are talking
     * to Provided that the interface's response is valid we set the service
     * status to SERVICE_AVAILABLE and return.
     */
    public PollStatus poll(MonitoredService svc, Map<String, Object> parameters) {
        NetworkInterface<InetAddress> iface = svc.getNetInterface();

        // Process parameters

        // Get interface address from NetworkInterface
        if (iface.getType() != NetworkInterface.TYPE_INET)
            throw new NetworkInterfaceNotSupportedException("Unsupported interface type, only TYPE_INET currently supported");

        TimeoutTracker tracker = new TimeoutTracker(parameters, DEFAULT_RETRY, DEFAULT_TIMEOUT);

        // Port
        int port = ParameterMap.getKeyedInteger(parameters, PARAMETER_PORT, DEFAULT_PORT);

        //URI
        String strURI = ParameterMap.getKeyedString(parameters, PARAMETER_URI, DEFAULT_URI);

        //Username
        String strUser = ParameterMap.getKeyedString(parameters, PARAMETER_USERNAME, null);

        //Password
        String strPasswd = ParameterMap.getKeyedString(parameters, PARAMETER_PASSWORD, null);

        //BannerMatch
        String strBannerMatch = ParameterMap.getKeyedString(parameters, PARAMETER_BANNER, null);

        //Scheme
        String strScheme = ParameterMap.getKeyedString(parameters, PARAMETER_SCHEME, DEFAULT_SCHEME);

        //Payload
        String strPayload = ParameterMap.getKeyedString(parameters, PARAMETER_PAYLOAD, null);

        //Mimetype
        String strMimetype = ParameterMap.getKeyedString(parameters, PARAMETER_MIMETYPE, DEFAULT_MIMETYPE);

        //Charset
        String strCharset = ParameterMap.getKeyedString(parameters, PARAMETER_CHARSET, DEFAULT_CHARSET);

        //SSLFilter
        boolean boolSSLFilter = ParameterMap.getKeyedBoolean(parameters, PARAMETER_SSLFILTER, DEFAULT_SSLFILTER);

        // Get the address instance.
        InetAddress ipv4Addr = (InetAddress) iface.getAddress();

        final String hostAddress = InetAddressUtils.str(ipv4Addr);

        LOG.debug("poll: address = " + hostAddress + ", port = " + port + ", " + tracker);

        // Give it a whirl
        PollStatus serviceStatus = PollStatus.unavailable();

        for (tracker.reset(); tracker.shouldRetry() && !serviceStatus.isAvailable(); tracker.nextAttempt()) {
            try {
                tracker.startAttempt();

                HttpParams clientParams = new BasicHttpParams();
                clientParams.setIntParameter(CoreConnectionPNames.CONNECTION_TIMEOUT, tracker.getSoTimeout());
                clientParams.setIntParameter(CoreConnectionPNames.SO_TIMEOUT, tracker.getSoTimeout());
                DefaultHttpClient client = new DefaultHttpClient(clientParams);
                client.setHttpRequestRetryHandler(new DefaultHttpRequestRetryHandler(DEFAULT_RETRY, false));
                if (boolSSLFilter) 
                    client.getConnectionManager().getSchemeRegistry().register(new Scheme(strScheme, port, new SSLSocketFactory(new TrustSelfSignedStrategy(), new AllowAllHostnameVerifier())));
                HttpEntity postReq;

                if (strUser != null && strPasswd != null) {
                    Credentials defaultcreds = new UsernamePasswordCredentials(strUser, strPasswd);
                    client.getCredentialsProvider().setCredentials(AuthScope.ANY, defaultcreds);
                }

                try {
                    postReq = new StringEntity(strPayload, strMimetype, strCharset);
                } catch (UnsupportedEncodingException e) {
                    serviceStatus = PollStatus.unavailable("Unsupported encoding encountered while constructing POST body " + e);
                    break;
                }

                URIBuilder ub = new URIBuilder();
                ub.setScheme(strScheme);
                ub.setHost(hostAddress);
                ub.setPort(port);
                ub.setPath(strURI);

                LOG.debug("HttpPostMonitor: Constructed URL is " + ub.toString());

                HttpPost post = new HttpPost(ub.build());
                post.setEntity(postReq);
                HttpResponse response = client.execute(post);

                LOG.debug("HttpPostMonitor: Status Line is " + response.getStatusLine());

                if (response.getStatusLine().getStatusCode() > 399) {
                    LOG.info("HttpPostMonitor: Got response status code " + response.getStatusLine().getStatusCode());
                    LOG.debug("HttpPostMonitor: Received server response: " + response.getStatusLine());
                    LOG.debug("HttpPostMonitor: Failing on bad status code");
                    serviceStatus = PollStatus.unavailable("HTTP(S) Status code " + response.getStatusLine().getStatusCode());
                    break;
                }

                LOG.debug("HttpPostMonitor: Response code is valid");
                double responseTime = tracker.elapsedTimeInMillis();

                HttpEntity entity = response.getEntity();
                InputStream responseStream = entity.getContent();
                String Strresponse = IOUtils.toString(responseStream);

                if (Strresponse == null)
                    continue;

                LOG.debug("HttpPostMonitor: banner = " + Strresponse);
                LOG.debug("HttpPostMonitor: responseTime= " + responseTime + "ms");

                //Could it be a regex?
                if (strBannerMatch.charAt(0)=='~'){
                    if (!Strresponse.matches(strBannerMatch.substring(1))) {
                        serviceStatus = PollStatus.unavailable("Banner does not match Regex '"+strBannerMatch+"'");
                        break;
                    }
                    else {
                        serviceStatus = PollStatus.available(responseTime);
                    }
                }
                else {
                    if (Strresponse.indexOf(strBannerMatch) > -1) {
                        serviceStatus = PollStatus.available(responseTime);
                    }
                    else {
                        serviceStatus = PollStatus.unavailable("Did not find expected Text '"+strBannerMatch+"'");
                        break;
                    }
                }

            } catch (URISyntaxException e) {
                String reason = "URISyntaxException for URI: " + strURI + " " + e.getMessage();
                LOG.debug(reason, e);
                serviceStatus = PollStatus.unavailable(reason);
                break;
            } catch (Exception e) {
                String reason = "Exception: " + e.getMessage();
                LOG.debug(reason, e);
                serviceStatus = PollStatus.unavailable(reason);
                break;
            }
        }

        // return the status of the service
        return serviceStatus;
    }

}
