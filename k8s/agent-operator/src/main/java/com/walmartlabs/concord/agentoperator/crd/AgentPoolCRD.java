package com.walmartlabs.concord.agentoperator.crd;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinition;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinitionBuilder;

public class AgentPoolCRD {

    public static final String CONCORD_GROUP = "concord.walmartlabs.com";

    public static final String SERVICE_KIND = "AgentPool";
    public static final String SERVICE_LIST_KIND = "AgentPoolList";
    public static final String SERVICE_SINGULAR_NAME = "agentpool";
    public static final String SERVICE_PLURAL_NAME = "agentpools";
    public static final String SERVICE_FULL_NAME = SERVICE_PLURAL_NAME + "." + CONCORD_GROUP;
    public static final String VERSION = "v1alpha1";

    // we have to keep the CRD definitions here to be able to subscribe to CR events
    public static final CustomResourceDefinition SERVICE_DEFINITION = new CustomResourceDefinitionBuilder()
            .withApiVersion("apiextensions.k8s.io/v1beta1")
            .withNewMetadata()
                .withName(SERVICE_FULL_NAME)
            .endMetadata()
            .withNewSpec()
                .withGroup(CONCORD_GROUP)
                .withVersion(VERSION)
                .withScope("Namespaced")
                .withNewNames()
                    .withKind(SERVICE_KIND)
                    .withListKind(SERVICE_LIST_KIND)
                    .withSingular(SERVICE_SINGULAR_NAME)
                    .withPlural(SERVICE_PLURAL_NAME)
                .endNames()
            .endSpec()
            .build();
}
