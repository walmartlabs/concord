package com.walmartlabs.concord.it.common;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;

public final class GitHubUtils {

    // from github.secret configuration parameter
    private static final String GITHUB_WEBHOOK_SECRET = "12345";
    private static final String HMAC_SHA1_ALGORITHM = "HmacSHA1";

    public static String sign(String payload) throws Exception {
        SecretKeySpec signingKey = new SecretKeySpec(GITHUB_WEBHOOK_SECRET.getBytes(), HMAC_SHA1_ALGORITHM);
        Mac mac = Mac.getInstance(HMAC_SHA1_ALGORITHM);
        mac.init(signingKey);
        byte[] digest = mac.doFinal(payload.getBytes());
        return hex(digest);
    }

    private static String hex(byte[] str){
        return String.format("%040x", new BigInteger(1, str));
    }

    private GitHubUtils() {
    }
}
