package com.walmartlabs.concord.server.plugins.eventsink.kafka;

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

import com.walmartlabs.concord.server.sdk.BackgroundTask;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Properties;

public class KafkaConnector implements BackgroundTask {

    private static final Logger log = LoggerFactory.getLogger(KafkaConnector.class);

    private final KafkaEventSinkConfiguration cfg;
    private final boolean enabled;

    private KafkaProducer<String, String> producer;

    @Inject
    public KafkaConnector(KafkaEventSinkConfiguration cfg) {
        this.cfg = cfg;
        this.enabled = cfg.getEnabled() != null ? cfg.getEnabled() : false;
    }

    @Override
    public void start() {
        if (!enabled) {
            return;
        }

        String clientId = cfg.getClientId();
        if (clientId == null) {
            try {
                clientId = InetAddress.getLocalHost().getHostName();
            } catch (UnknownHostException e) {
                throw new RuntimeException("Error while starting the Kafka connector: " + e.getMessage(), e);
            }
        }

        String bootstrapServers = cfg.getBootstrapServers();
        if (bootstrapServers == null) {
            throw new IllegalArgumentException("bootstrapServers value is required");
        }

        try {
            Properties props = new Properties();
            props.put(ProducerConfig.CLIENT_ID_CONFIG, clientId);
            props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
            props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
            props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, "org.apache.kafka.common.serialization.StringSerializer");
            producer = new KafkaProducer<>(props);
        } catch (Exception e) {
            log.warn("start -> error creating a Kafka producer: {}", e.getMessage(), e);
            throw new RuntimeException(e);
        }

        log.info("started the Kafka connector using {}...", bootstrapServers);
    }

    @Override
    public void stop() {
        if (!enabled) {
            return;
        }

        if (producer != null) {
            producer.close();
        }
    }

    public void send(String topic, String key, String value) {
        if (!enabled || producer == null || topic == null) {
            return;
        }

        producer.send(new ProducerRecord<>(topic, key, value));
    }
}
