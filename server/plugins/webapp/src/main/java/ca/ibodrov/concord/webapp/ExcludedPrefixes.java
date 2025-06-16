package ca.ibodrov.concord.webapp;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2025 Walmart Inc.
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

import com.google.common.annotations.VisibleForTesting;
import com.walmartlabs.concord.server.sdk.rest.ApiDescriptor;
import org.eclipse.jetty.ee8.servlet.ServletHolder;

import javax.inject.Inject;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static java.util.Comparator.reverseOrder;
import static java.util.Objects.requireNonNull;

/**
 * Collects all URI prefixes that should NOT be handled by {@link WebappFilter}.
 */
public class ExcludedPrefixes {

    private final List<Pattern> prefixes;

    @Inject
    public ExcludedPrefixes(Set<ApiDescriptor> apiDescriptors,
                            Set<HttpServlet> servlets,
                            Set<ServletHolder> servletHolders) {
        this(collectPrefixes(apiDescriptors, servlets, servletHolders));
    }

    @VisibleForTesting
    ExcludedPrefixes(Stream<String> prefixes) {
        this.prefixes = prefixes.map(prefix -> {
                    if (prefix.endsWith("/*")) {
                        prefix = Pattern.quote(prefix.substring(0, prefix.length() - 1));
                        return prefix + ".*";
                    }
                    return prefix;
                })
                .sorted(reverseOrder())
                .map(Pattern::compile)
                .toList();
    }

    public boolean matches(String path) {
        return prefixes.stream().anyMatch(prefix -> prefix.matcher(path).matches());
    }

    private static Stream<String> collectPrefixes(Set<ApiDescriptor> apiDescriptors,
                                                  Set<HttpServlet> servlets,
                                                  Set<ServletHolder> servletHolders) {
        var apiPrefixes = requireNonNull(apiDescriptors).stream().flatMap(descriptor -> Arrays.stream(descriptor.paths()));
        var servletPrefixes = webServletAnnotated(requireNonNull(servlets));
        var servletHolderPrefixes = webServletAnnotated(requireNonNull(servletHolders));
        return Stream.of(apiPrefixes, servletPrefixes, servletHolderPrefixes).flatMap(p -> p);
    }

    private static Stream<String> webServletAnnotated(Set<?> instances) {
        return instances.stream()
                .flatMap(i -> Stream.ofNullable(i.getClass().getAnnotation(WebServlet.class)))
                .flatMap(a -> Arrays.stream(Optional.ofNullable(a.value())
                        .filter(values -> values.length != 0)
                        .orElse(a.urlPatterns())));
    }
}
