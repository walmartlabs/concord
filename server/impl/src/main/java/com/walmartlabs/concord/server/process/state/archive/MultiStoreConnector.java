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

import com.amazonaws.ClientConfiguration;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import com.amazonaws.services.s3.model.Bucket;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.walmartlabs.concord.server.cfg.ProcessStateArchiveConfiguration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Named
@Singleton
public class MultiStoreConnector {

    private static final Logger log = LoggerFactory.getLogger(MultiStoreConnector.class);

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
        Exception lastError = null;

        // iterate over the stores until we get a response
        for (Connector c : clients) {
            try {
                return c.get(name);
            } catch (Exception e) {
                lastError = e;
            }
        }

        throw lastError != null ? new IOException(lastError) : new IOException("No response for " + name);
    }

    private static Connector createClient(Map<String, Object> cfg) {
        String url = (String) cfg.get("url");
        String accessKey = (String) cfg.get("accessKey");
        String secretKey = (String) cfg.get("secretKey");
        String bucketName = (String) cfg.get("bucketName");

        Supplier<AmazonS3> client = () -> {
            log.info("createClient -> connecting to {}...", url);

            AWSCredentials credentials = new BasicAWSCredentials(accessKey, secretKey);
            AmazonS3 s3 = AmazonS3ClientBuilder.standard()
                    .withClientConfiguration(new ClientConfiguration())
                    .withEndpointConfiguration(new AwsClientBuilder.EndpointConfiguration(url, "other-v2-signature"))
                    .withCredentials(new AWSStaticCredentialsProvider(credentials))
                    .enablePathStyleAccess()
                    .build();

            try {
                createBucket(s3, bucketName);
            } catch (Exception e) {
                log.error("createClient -> error while creating buckets", e);
                throw e;
            }

            return s3;
        };

        return new Connector(client, bucketName);
    }

    private static synchronized void createBucket(AmazonS3 s3, String bucketName) {
        List<Bucket> buckets = s3.listBuckets();
        if (buckets.stream().anyMatch(b -> bucketName.equals(b.getName()))) {
            return;
        }

        s3.createBucket(bucketName);
    }

    private static class Connector {

        private final Supplier<AmazonS3> client;
        private final String bucketName;

        private Connector(Supplier<AmazonS3> client, String bucketName) {
            this.bucketName = bucketName;
            this.client = client;
        }

        public void put(String name, InputStream in, String contentType, long size, Date expires) {
            ObjectMetadata meta = new ObjectMetadata();
            meta.setContentLength(size);
            meta.setContentType(contentType);
            meta.setExpirationTime(expires);

            client.get().putObject(bucketName, name, in, meta);
        }

        public InputStream get(String name) {
            return client.get().getObject(bucketName, name).getObjectContent();
        }
    }
}
