package com.walmartlabs.concord.server.rpc;

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
                     CommandQueueImpl commandQueue,
                     JobQueueImpl jobQueue,
                     ProcessHeartbeatServiceImpl heartbeatService,
                     KvServiceImpl kvService,
                     SecretStoreServiceImpl secretStoreService,
                     EventServiceImpl eventService,
                     SlackServiceImpl slackService) throws ClientException {

        this.server = ServerBuilder
                .forPort(cfg.getPort())
                .addService(commandQueue)
                .addService(jobQueue)
                .addService(heartbeatService)
                .addService(kvService)
                .addService(secretStoreService)
                .addService(eventService)
                .addService(slackService)
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
