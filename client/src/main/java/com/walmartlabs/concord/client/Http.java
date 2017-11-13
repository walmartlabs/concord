package com.walmartlabs.concord.client;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

public class Http {

    private static final String CRLF = "\r\n";

    public static <T> T getJson(URL url, String sessionToken, Class<T> type) throws IOException {
        HttpURLConnection conn = null;
        try {
            conn = prepareGet(url, sessionToken);
            ObjectMapper om = new ObjectMapper();
            return om.readValue(conn.getInputStream(), type);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    public static HttpURLConnection post(URL url, String sessionToken, InputStream in) throws IOException {
        HttpURLConnection conn = preparePost(url, sessionToken);
        conn.setDoInput(true);

        conn.setRequestProperty("Content-Type", "application/octet-stream");
        try (OutputStream out = conn.getOutputStream()) {
            byte[] buf = new byte[4096];
            int read;
            while ((read = in.read(buf)) > 0) {
                out.write(buf, 0, read);
            }
        }

        assertOk(conn);
        return conn;
    }

    public static HttpURLConnection postJson(URL url, String sessionToken, Map<String, Object> req) throws IOException {
        HttpURLConnection conn = preparePost(url, sessionToken);
        conn.setDoInput(true);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectMapper om = new ObjectMapper();
        om.writeValue(baos, req);

        conn.setRequestProperty("Content-Type", "application/json");
        try (OutputStream out = conn.getOutputStream()) {
            out.write(baos.toByteArray());
        }

        assertOk(conn);
        return conn;
    }

    public static HttpURLConnection postMultipart(URL url, String sessionToken, Map<String, Object> input) throws IOException {
        HttpURLConnection conn = preparePost(url, sessionToken);
        conn.setDoInput(true);

        String boundary = "-------" + System.currentTimeMillis();
        conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);

        OutputStream out = conn.getOutputStream();
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(out));

        for (Map.Entry<String, Object> i : input.entrySet()) {
            addFormField(out, writer, boundary, i.getKey(), i.getValue());
        }

        writer.append(CRLF).flush();
        writer.append("--" + boundary + "--").append(CRLF);
        writer.close();

        return conn;
    }

    public static HttpURLConnection delete(URL url, String sessionToken) throws IOException {
        return prepareDelete(url, sessionToken);
    }

    private static void addFormField(OutputStream out, PrintWriter writer, String boundary, String key, Object value) throws IOException {
        writer.append("--" + boundary).append(CRLF);
        writer.append("Content-Disposition: form-data; name=\"").append(key).append("\"").append(CRLF);

        String contentType = "application/octet-stream";
        if (value instanceof String) {
            contentType = "text/plain; charset=utf-8";
        }
        writer.append("Content-Type: ").append(contentType).append(CRLF);

        writeFormValue(out, writer, value);
    }

    private static void writeFormValue(OutputStream out, PrintWriter writer, Object value) throws IOException {
        if (value instanceof Map) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ObjectMapper om = new ObjectMapper();
            om.writeValue(baos, value);
            writeFormValue(out, writer, baos.toByteArray());
        } else if (value instanceof InputStream || value instanceof byte[]) {
            writer.append("Content-Transfer-Encoding: binary").append(CRLF);
            writer.append(CRLF);
            writer.flush();

            if (value instanceof InputStream) {
                InputStream in = (InputStream) value;
                int read;
                byte[] ab = new byte[4096];
                while ((read = in.read(ab)) > 0) {
                    out.write(ab, 0, read);
                }
                out.flush();
            } else {
                byte[] ab = (byte[]) value;
                out.write(ab);
                out.flush();
            }

            writer.append(CRLF);
        } else if (value instanceof String) {
            writer.append(CRLF);
            writer.append((String) value).append(CRLF);
        } else {
            throw new IllegalArgumentException("Unsupported value type: " + value);
        }

        writer.flush();
    }

    public static HttpURLConnection post(URL url, String sessionToken) throws IOException {
        HttpURLConnection conn = preparePost(url, sessionToken);
        assertOk(conn);
        return conn;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> readMap(HttpURLConnection conn) throws IOException {
        try (InputStream in = conn.getInputStream()) {
            ObjectMapper om = new ObjectMapper();
            return om.readValue(in, Map.class);
        }
    }

    public static <T> T read(HttpURLConnection conn, Class<T> clazz) throws IOException {
        try (InputStream in = conn.getInputStream()) {
            ObjectMapper om = new ObjectMapper();
            return clazz.cast(om.readValue(in, clazz));
        }
    }

    private static HttpURLConnection prepareGet(URL url, String sessionToken) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setUseCaches(false);
        conn.setRequestProperty("X-Concord-SessionToken", sessionToken);
        return conn;
    }

    private static HttpURLConnection preparePost(URL url, String sessionToken) throws IOException {
        return prepare("POST", url, sessionToken);
    }

    private static HttpURLConnection prepareDelete(URL url, String sessionToken) throws IOException {
        return prepare("DELETE", url, sessionToken);
    }

    private static HttpURLConnection prepare(String method, URL url, String sessionToken) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setUseCaches(false);
        conn.setDoOutput(true);

        if (sessionToken != null) {
            conn.setRequestProperty("X-Concord-SessionToken", sessionToken);
        }
        
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestMethod(method);
        return conn;
    }

    public static void assertOk(HttpURLConnection conn) throws IOException {
        int response = conn.getResponseCode();
        if (response < 200 || response >= 300) {
            throw new IOException("Query error. Server response: " + response);
        }
    }

    private Http() {
    }
}
