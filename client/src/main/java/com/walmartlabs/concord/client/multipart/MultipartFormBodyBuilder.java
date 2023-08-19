package com.walmartlabs.concord.client.multipart;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.http.HttpRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class MultipartFormBodyBuilder {

    private final String boundary;
    private final List<Part> parts = new ArrayList<>();

    public MultipartFormBodyBuilder(String boundary) {
        this.boundary = boundary;
    }

    public void addTextPart(String name, String value) {
        parts.add(new TextPart(name, value));
    }

    public void addFilePart(String name, Path file) throws IOException {
        byte[] content = Files.readAllBytes(file);
        parts.add(new FilePart(name, file.getFileName().toString(), content));
    }

    public void addPart(String name, Part part) {
        parts.add()
    }

    public HttpRequest.BodyPublisher build() throws IOException {
        byte[] boundaryBytes = ("--" + boundary + "\r\n").getBytes();

        byte[] closingBoundaryBytes = ("--" + boundary + "--").getBytes();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        outputStream.write(boundaryBytes);
        for (Part part : parts) {
            outputStream.write(part.getBytes(boundary));
        }
        outputStream.write(closingBoundaryBytes);

        return HttpRequest.BodyPublishers.ofByteArray(outputStream.toByteArray());
    }
}

