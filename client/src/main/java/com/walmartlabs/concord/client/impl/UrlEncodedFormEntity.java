package com.walmartlabs.concord.client.impl;

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

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.List;

public class UrlEncodedFormEntity implements HttpEntity {

    private static final char QUERY_PARAM_SEPARATOR = '&';
    private static final char PARAM_VALUE_SEPARATOR = '=';

    private final String contentType;
    private final byte[] content;

    public UrlEncodedFormEntity(
            List<NameValuePair> parameters,
            Charset charset) {

        this.contentType = ContentType.APPLICATION_FORM_URLENCODED.withCharset(charset).toString();
        this.content = format(parameters, charset).getBytes(charset);
    }

    @Override
    public String getContentType() {
        return contentType;
    }

    @Override
    public void writeTo(OutputStream outStream) throws IOException {
        outStream.write(this.content);
        outStream.flush();
    }


    private static String format(List<NameValuePair> params, Charset charset) {
        StringBuilder buf = new StringBuilder();
        formatQuery(buf, params, charset);
        return buf.toString();
    }

    private static void formatQuery(StringBuilder buf, List<NameValuePair> params, Charset charset) {
        int i = 0;
        for (NameValuePair parameter : params) {
            if (i > 0) {
                buf.append(QUERY_PARAM_SEPARATOR);
            }
            buf.append(urlEncode(parameter.getName(), charset));
            if (parameter.getValue() != null) {
                buf.append(PARAM_VALUE_SEPARATOR);
                buf.append(urlEncode(parameter.getValue(), charset));
            }
            i++;
        }
    }

    private static String urlEncode(String s, Charset charset) {
        return URLEncoder.encode(s, charset).replaceAll("\\+", "%20");
    }
}
