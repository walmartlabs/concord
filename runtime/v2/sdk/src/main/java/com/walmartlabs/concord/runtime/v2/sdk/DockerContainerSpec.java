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

import org.immutables.value.Value;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

@Value.Immutable
@Value.Style(jdkOnly = true)
public interface DockerContainerSpec {

    String image();

    @Nullable
    String name();

    @Nullable
    String user();

    @Nullable
    String workdir();

    @Nullable
    String entryPoint();

    @Nullable
    String cpu();

    @Nullable
    String memory();

    @Nullable
    String stdOutFilePath();

    @Nullable
    List<String> args();

    @Nullable
    Map<String, String> env();

    @Nullable
    String envFile();

    @Nullable
    Map<String, String> labels();

    @Nullable
    Options options();

    @Value.Default
    default int pullRetryCount() {
        return 3;
    }

    @Value.Default
    default long pullRetryInterval() {
        return 10_000;
    }

    @Value.Default
    default boolean debug() {
        return false;
    }

    @Value.Default
    default boolean forcePull() {
        return true;
    }

    @Value.Default
    default boolean redirectErrorStream() {
        return true;
    }

    static ImmutableDockerContainerSpec.Builder builder() {
        return ImmutableDockerContainerSpec.builder();
    }

    @Value.Immutable
    @Value.Style(jdkOnly = true)
    interface Options {

        /**
         * Extra {@code /etc/hosts} entries.
         * Same as {@code --add-host} option in {@code docker run}
         *
         * @return list of extra {@code /etc/hosts} entries.
         */
        @Nullable
        List<String> hosts();

        static ImmutableOptions.Builder builder() {
            return ImmutableOptions.builder();
        }

        @SuppressWarnings("unchecked")
        static Options from(Map<String, Object> m) {
            ImmutableOptions.Builder b = ImmutableOptions.builder();

            if (m == null) {
                return b.build();
            }

            Object v = m.get("hosts");
            if (v == null) {
                return b.build();
            }

            if (!(v instanceof Iterable)) {
                throw new IllegalArgumentException("Unexpected 'hosts' value: " + v);
            }

            b.hosts((Iterable<String>) v);

            return b.build();
        }
    }
}
