package com.walmartlabs.concord.runtime.v2.sdk;

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

import java.util.Set;

/**
 * Provider for tasks. Responsible for creating task instances using
 * the supplied {@link Context} and the key.
 * <p>
 * Multiple task providers can exist in the same injector.
 * The {@code @Priority} annotation can be used to specify the order
 * in which each provider is called. Providers with lowest numbers are
 * called first.
 */
public interface TaskProvider {

    Task createTask(Context ctx, String key);

    Class<? extends Task> getTaskClass(Context ctx, String key);

    boolean hasTask(String key);

    Set<String> names();
}
