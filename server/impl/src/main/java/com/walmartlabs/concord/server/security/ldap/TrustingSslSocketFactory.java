package com.walmartlabs.concord.server.security.ldap;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
 * -----
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * =====
 */

import javax.net.SocketFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;

/**
 * A socket factory that creates SSL sockets using {@link DummyTrustManager}.
 * It implements both connected and unconnected socket creation methods
 * to support, for example, connection timeout for LDAP connections.
 */
public class TrustingSslSocketFactory extends SocketFactory {

    private final SSLSocketFactory delegate;

    public static synchronized SocketFactory getDefault() {
        return new TrustingSslSocketFactory();
    }

    public TrustingSslSocketFactory() {
        try {
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, new TrustManager[]{new DummyTrustManager()}, new SecureRandom());
            delegate = ctx.getSocketFactory();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Socket createSocket() throws IOException {
        return delegate.createSocket();
    }

    @Override
    public Socket createSocket(String s, int i) throws IOException {
        return delegate.createSocket(s, i);
    }

    @Override
    public Socket createSocket(String s, int i, InetAddress inetAddress, int i1) throws IOException {
        return delegate.createSocket(s, i, inetAddress, i1);
    }

    @Override
    public Socket createSocket(InetAddress inetAddress, int i) throws IOException {
        return delegate.createSocket(inetAddress, i);
    }

    @Override
    public Socket createSocket(InetAddress inetAddress, int i, InetAddress inetAddress1, int i1) throws IOException {
        return delegate.createSocket(inetAddress, i, inetAddress1, i1);
    }

    private static class DummyTrustManager implements X509TrustManager {
        public void checkClientTrusted(X509Certificate[] xcs, String string) throws CertificateException {  // NOSONAR
        }

        public void checkServerTrusted(X509Certificate[] xcs, String string) throws CertificateException { // NOSONAR
        }

        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }
}
