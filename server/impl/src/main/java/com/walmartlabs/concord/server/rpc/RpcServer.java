package com.walmartlabs.concord.server.rpc;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 Wal-Mart Store, Inc.
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

import com.walmartlabs.concord.sdk.ClientException;
import com.walmartlabs.concord.server.cfg.RpcServerConfiguration;
import io.grpc.*;
import org.eclipse.sisu.EagerSingleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.Closeable;
import java.io.IOException;
import java.net.SocketAddress;

@Named
@EagerSingleton
public class RpcServer implements Closeable {

    private static final Logger log = LoggerFactory.getLogger(RpcServer.class);

    private final Server server;

    @Inject
    public RpcServer(RpcServerConfiguration cfg,
                     KvServiceImpl kvService,
                     SecretReaderServiceImpl secretReaderService,
                     EventServiceImpl eventService) throws ClientException {

        this.server = ServerBuilder
                .forPort(cfg.getPort())
                .addService(kvService)
                .addService(secretReaderService)
                .addService(eventService)
                .addTransportFilter(new LoggingTransportFilter())
                .build();

        try {
            this.server.start();
        } catch (IOException e) {
            throw new ClientException("Error while starting a server", e);
        }

        log.info("init -> server started on {}", cfg.getPort());
    }

    @Override
    public void close() {
        if (this.server != null) {
            this.server.shutdownNow();
        }
    }

    private static final class LoggingTransportFilter extends ServerTransportFilter {

        @Override
        public Attributes transportReady(Attributes attrs) {
            SocketAddress remoteAddr = attrs.get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR);
            if (remoteAddr != null) {
                log.info("transportReady -> {} connected", remoteAddr);
            }

            return super.transportReady(attrs);
        }

        @Override
        public void transportTerminated(Attributes attrs) {
            SocketAddress remoteAddr = attrs != null ? attrs.get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR) : null;
            if (remoteAddr != null) {
                log.info("transportTerminated -> {} disconnected", remoteAddr);
            }

            super.transportTerminated(attrs);
        }
    }
}
