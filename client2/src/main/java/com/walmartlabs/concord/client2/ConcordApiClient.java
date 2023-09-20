package com.walmartlabs.concord.client2;

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

import javax.net.ssl.*;
import java.net.http.HttpClient;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

public class ConcordApiClient extends ApiClient {

    private boolean verifyingSsl = true;
    private SSLContext sslContext;

    public ConcordApiClient(String baseUrl) {
        updateBaseUri(baseUrl);
    }

    public ApiClient setVerifyingSsl(boolean verifyingSsl) {
        this.verifyingSsl = verifyingSsl;
        applySslSettings();
        return this;
    }

    @Override
    protected HttpClient.Builder createDefaultHttpClientBuilder() {
        HttpClient.Builder result = super.createDefaultHttpClientBuilder();
        if (sslContext != null) {
            result.sslContext(sslContext);
        }
        return result;
    }

    private void applySslSettings() {
        try {
            TrustManager[] trustManagers;
            if (!verifyingSsl) {
                TrustManager trustAll = new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                    @Override
                    public X509Certificate[] getAcceptedIssuers() { return null; }
                };
                SSLContext sslContext = SSLContext.getInstance("TLS");
                trustManagers = new TrustManager[]{ trustAll };
                System.getProperties().setProperty("jdk.internal.httpclient.disableHostnameVerification", Boolean.TRUE.toString());

                sslContext.init(null, trustManagers, new SecureRandom());
            }
        } catch (GeneralSecurityException e) {
            throw new RuntimeException(e);
        }
    }
}
