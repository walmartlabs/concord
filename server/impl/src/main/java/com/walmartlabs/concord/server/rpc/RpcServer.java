package com.walmartlabs.concord.server.rpc;

import com.walmartlabs.concord.rpc.ClientException;
import io.grpc.*;
import org.eclipse.sisu.EagerSingleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.io.IOException;
import java.net.SocketAddress;

@Named
@EagerSingleton
public class RpcServer {

    private static final Logger log = LoggerFactory.getLogger(RpcServer.class);

    private final Server server;

    @Inject
    public RpcServer(CommandQueueImpl commandQueue,
                     JobQueueImpl jobQueue,
                     KvServiceImpl kvService,
                     SecretStoreServiceImpl secretStoreService) throws ClientException {

        // TODO cfg
        int port = 8101;

        this.server = ServerBuilder
                .forPort(port)
                .addService(commandQueue)
                .addService(jobQueue)
                .addService(kvService)
                .addService(secretStoreService)
                .addTransportFilter(new LoggingTransportFilter())
                .build();

        try {
            this.server.start();
        } catch (IOException e) {
            throw new ClientException("Error while starting a server", e);
        }

        log.info("init -> server started on {}", port);
    }

    private static final class LoggingTransportFilter extends ServerTransportFilter {

        @Override
        public Attributes transportReady(Attributes transportAttrs) {
            SocketAddress remoteAddr = transportAttrs.get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR);
            if (remoteAddr != null) {
                log.info("transportReady -> {} connected", remoteAddr);
            }

            return super.transportReady(transportAttrs);
        }

        @Override
        public void transportTerminated(Attributes transportAttrs) {
            SocketAddress remoteAddr = transportAttrs.get(Grpc.TRANSPORT_ATTR_REMOTE_ADDR);
            if (remoteAddr != null) {
                log.info("transportTerminated -> {} disconnected", remoteAddr);
            }

            super.transportTerminated(transportAttrs);
        }
    }
}
