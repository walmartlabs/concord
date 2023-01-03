package com.walmartlabs.concord.runtime.v2.sdk;

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

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.PARAMETER;

/**
 * This annotation can be used to prevent task arguments values from
 * being recorded in process events.
 * <p>
 * Currently, it is applicable only for task methods called directly
 * via expressions. For example:
 * <pre>{@code
 * flows:
 *   default:
 *     - "${crypto.exportAsString('myOrg', 'mySecret', 'mySecretPassword')}"
 * }</pre>
 * Assuming the task method has the third argument annotated with
 * {@link SensitiveData}, running this flow will result with
 * the third argument's value being masked in process events.
 */
@Target({PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface SensitiveData {
}
