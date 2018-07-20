package com.walmartlabs.concord.server.process.state.archive;

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

import com.walmartlabs.concord.server.cfg.ProcessStateArchiveConfiguration;
import org.jclouds.ContextBuilder;
import org.jclouds.blobstore.BlobStore;
import org.jclouds.blobstore.BlobStoreContext;
import org.jclouds.blobstore.domain.Blob;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Named
@Singleton
public class MultiStoreConnector {

    private final List<Connector> clients;

    @Inject
    public MultiStoreConnector(ProcessStateArchiveConfiguration cfg) {
        List<Map<String, Object>> dst = cfg.getDestinations();
        if (cfg.isEnabled() && (dst == null || dst.isEmpty())) {
            throw new IllegalStateException("Archive destinations must contain at least one entry");
        }

        this.clients = cfg.getDestinations().stream()
                .map(MultiStoreConnector::createClient)
                .collect(Collectors.toList());
    }

    public void put(Path src, String name, String contentType, long size, Date expires) {
        clients.parallelStream().forEach(c -> {
            try (InputStream in = Files.newInputStream(src)) {
                c.put(name, in, contentType, size, expires);
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    public InputStream get(String name) throws IOException {
        IOException lastError = null;

        // iterate over the stores until we get a response
        for (Connector c : clients) {
            try {
                return c.get(name);
            } catch (IOException e) {
                lastError = e;
            }
        }

        throw lastError != null ? lastError : new IOException("No response for " + name);
    }

    private static Connector createClient(Map<String, Object> cfg) {
        String url = (String) cfg.get("url");
        String accessKey = (String) cfg.get("accessKey");
        String secretKey = (String) cfg.get("secretKey");
        String bucketName = (String) cfg.get("bucketName");

        Supplier<BlobStore> client = () -> {
            Properties props = new Properties();
            props.put("jclouds.s3.virtual-host-buckets", false);

            BlobStoreContext ctx = ContextBuilder.newBuilder("aws-s3")
                    .endpoint(url)
                    .credentials(accessKey, secretKey)
                    .overrides(props)
                    .buildView(BlobStoreContext.class);

            BlobStore store = ctx.getBlobStore();

            if (!store.containerExists(bucketName)) {
                store.createContainerInLocation(null, bucketName);
            }

            return store;
        };

        return new Connector(client, bucketName);
    }

    private static class Connector {

        private final String bucketName;
        private final Supplier<BlobStore> client;

        private Connector(Supplier<BlobStore> client, String bucketName) {
            this.bucketName = bucketName;
            this.client = client;
        }

        public void put(String name, InputStream in, String contentType, long size, Date expires) {
            Blob blob = client.get().blobBuilder(name)
                    .payload(in)
                    .contentType(contentType)
                    .contentLength(size)
                    .expires(expires)
                    .build();

            client.get().putBlob(bucketName, blob);
        }

        public InputStream get(String name) throws IOException {
            Blob blob = client.get().getBlob(bucketName, name);
            return blob.getPayload().openStream();
        }
    }
}
