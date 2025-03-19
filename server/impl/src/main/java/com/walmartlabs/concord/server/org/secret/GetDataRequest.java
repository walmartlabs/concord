package com.walmartlabs.concord.server.org.secret;

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

import com.walmartlabs.concord.sdk.Constants;
import com.walmartlabs.concord.server.MultipartUtils;
import io.swagger.v3.oas.annotations.media.Schema;
import org.jboss.resteasy.plugins.providers.multipart.MultipartInput;

public class GetDataRequest {

    public static GetDataRequest from(MultipartInput input) {
        return new GetDataRequest(input);
    }

    private final MultipartInput input;

    private GetDataRequest(MultipartInput input) {
        this.input = input;
    }

    @Schema(name = Constants.Multipart.STORE_PASSWORD)
    public String getPassword() {
        return MultipartUtils.getString(input, Constants.Multipart.STORE_PASSWORD);
    }
}
