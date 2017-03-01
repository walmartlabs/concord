package com.walmartlabs.concord.cli;

import com.beust.jcommander.IParameterValidator;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.ParameterException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.common.Constants;
import com.walmartlabs.concord.common.IOUtils;
import com.walmartlabs.concord.server.api.process.ErrorMessage;
import com.walmartlabs.concord.server.api.process.ProcessResource;
import com.walmartlabs.concord.server.api.process.StartProcessResponse;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;

import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.ProcessingException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.ClientRequestFilter;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.ConnectException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipOutputStream;

public class Main {

    private static final String DEFAULT_API_KEY = "auBy4eDWrKWsyhiDp3AQiw";

    public static void main(String[] args) throws Exception {
        JCommander jc = new JCommander();
        jc.setProgramName("concord.sh");

        RunCommand runCmd = new RunCommand();
        jc.addCommand(RunCommand.COMMAND_NAME, runCmd);
        jc.parse(args);

        String cmd = jc.getParsedCommand();
        if (cmd == null) {
            jc.usage();
            return;
        }

        switch (cmd) {
            case RunCommand.COMMAND_NAME:
                run(runCmd);
                break;
            default:
                throw new IllegalArgumentException("Unknown command: " + cmd);
        }
    }

    private static void run(RunCommand cmd) throws IOException {
        Path baseDir = Paths.get(System.getProperty("user.dir"));

        Path buildFile = baseDir.resolve(cmd.getDescriptor());
        if (!Files.exists(buildFile)) {
            throw new IllegalArgumentException("File not found: " + buildFile);
        }

        BuildDescriptor b = readBuildDescriptor(buildFile);

        System.out.print("Creating a payload archive... ");
        Path payloadFile = createPayloadArchive(baseDir, b);
        System.out.println(payloadFile + " (" + Files.size(payloadFile) + " bytes)");

        Client client = null;
        try {
            client = createClient(DEFAULT_API_KEY);
            WebTarget t = client.target("http://" + cmd.getServerAddr());
            ProcessResource processResource = ((ResteasyWebTarget) t).proxy(ProcessResource.class);

            try (InputStream in = Files.newInputStream(payloadFile)) {
                StartProcessResponse resp = processResource.start(in);
                String id = resp.getInstanceId();
                System.out.println("Started. Instance ID: " + id);
            }
        } catch (InternalServerErrorException e) {
            handleServerError(e);
        } catch (ProcessingException e) {
            handleProcessingError(e);
        } finally {
            if (client != null) {
                client.close();
            }
        }
    }

    private static void handleServerError(InternalServerErrorException e) {
        Response resp = e.getResponse();
        if (resp == null || !resp.hasEntity()) {
            throw e;
        }

        ErrorMessage msg = resp.readEntity(ErrorMessage.class);

        System.err.println("\nSERVER ERROR: " + resp.getStatus());
        System.err.println("Message: " + msg.getMessage());
        System.err.println("Details: " + msg.getDetails());
        System.exit(110);
    }

    private static void handleProcessingError(ProcessingException e) {
        Throwable cause = e.getCause();

        if (cause instanceof ConnectException) {
            System.err.println("CLIENT ERROR: " + cause.getMessage());
            System.exit(100);
        }

        throw e;
    }

    private static Path createPayloadArchive(Path baseDir, BuildDescriptor b) throws IOException {
        Path tmpDir = Files.createTempDirectory("payload");
        if (b.getFiles() != null) {
            for (String f : b.getFiles()) {
                DirectoryStream<Path> dir = Files.newDirectoryStream(baseDir, f);
                for (Path src : dir) {
                    Path dst = tmpDir.resolve(baseDir.relativize(src));
                    IOUtils.copy(src, dst);
                }
            }
        }

        if (b.getDependencies() != null) {
            Path p = tmpDir.resolve(Constants.DEPENDENCIES_FILE_NAME);
            try (BufferedWriter w = Files.newBufferedWriter(p)) {
                for (String d : b.getDependencies()) {
                    w.write(d);
                }
            }
        }

        Path tmpFile = Files.createTempFile("payload", ".zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(tmpFile))) {
            IOUtils.zip(zip, tmpDir);
        }
        return tmpFile;
    }

    private static BuildDescriptor readBuildDescriptor(Path p) throws IOException {
        ObjectMapper om = new ObjectMapper();
        try (InputStream in = Files.newInputStream(p)) {
            return om.readValue(in, BuildDescriptor.class);
        }
    }

    private static Client createClient(String apiKey) {
        return ClientBuilder.newClient()
                .register((ClientRequestFilter) requestContext -> {
                    MultivaluedMap<String, Object> headers = requestContext.getHeaders();
                    headers.putSingle("Authorization", apiKey);
                });
    }

    public static class ServerAddressValidator implements IParameterValidator {

        @Override
        public void validate(String name, String value) throws ParameterException {
            if (!value.matches("[^:]+:[0-9]{1,5}")) {
                throw new ParameterException("Invalid server address: " + value + ", expected: host:port");
            }
        }
    }
}
