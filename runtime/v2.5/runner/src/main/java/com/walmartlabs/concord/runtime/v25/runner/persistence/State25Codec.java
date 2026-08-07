package com.walmartlabs.concord.runtime.v25.runner.persistence;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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

import com.walmartlabs.concord.common.ObjectInputStreamWithClassLoader;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.InputStream;
import java.io.ObjectInputFilter;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * Versioned state codec. Deserialization is constrained to a depth of 128, one million object
 * references, and 64 MiB of stream data; these limits protect both restored and newly persisted
 * state.
 */
public final class State25Codec {

    private static final byte[] MAGIC = {'C', 'V', '2', '5'};
    private static final int CHECKSUM_LENGTH = 32;
    private static final long MAX_DEPTH = 128;
    private static final long MAX_REFERENCES = 1_000_000;
    private static final long MAX_ARRAY_LENGTH = 1_000_000;
    private static final long MAX_STREAM_BYTES = 64L * 1024 * 1024;

    private final ClassLoader classLoader;
    private final ThreadLocal<String> rejectedLimit = new ThreadLocal<>();

    public State25Codec() {
        this(Thread.currentThread().getContextClassLoader());
    }

    public State25Codec(ClassLoader classLoader) {
        this.classLoader = classLoader == null ? State25Codec.class.getClassLoader() : classLoader;
    }

    public void write(OutputStream output, State25 state) throws IOException {
        try {
            State25Validator.validate(state);
        } catch (RuntimeException e) {
            throw new StateFormatException("State persistence verification failed: " + e.getMessage(), e);
        }
        var body = new ByteArrayOutputStream();
        try (var objects = new ObjectOutputStream(body)) {
            objects.writeObject(state);
        }
        var bytes = body.toByteArray();
        verifyReadable(bytes);
        var data = new DataOutputStream(output);
        data.write(MAGIC);
        data.writeInt(State25.CURRENT_FORMAT);
        data.write(sha256(bytes));
        data.write(bytes);
    }

    public State25 read(InputStream input) throws IOException {
        var data = new DataInputStream(input);
        var magic = data.readNBytes(MAGIC.length);
        if (!Arrays.equals(MAGIC, magic)) {
            var kind = magic.length >= 2 && (magic[0] & 0xff) == 0xac && (magic[1] & 0xff) == 0xed
                    ? "runtime-v2 Java snapshot"
                    : "unknown state";
            throw new StateFormatException("Expected a concord-v2.5 state header, found " + kind);
        }
        var version = data.readInt();
        if (version != State25.CURRENT_FORMAT) {
            throw new StateFormatException("Unsupported concord-v2.5 state format " + version
                    + "; supported format is " + State25.CURRENT_FORMAT);
        }
        var checksum = data.readNBytes(CHECKSUM_LENGTH);
        if (checksum.length != CHECKSUM_LENGTH) {
            throw new StateFormatException("State header is missing its SHA-256 checksum");
        }
        var body = data.readNBytes((int) MAX_STREAM_BYTES + 1);
        if (body.length > MAX_STREAM_BYTES) {
            throw new StateFormatException("State exceeds the maximum stream size of " + MAX_STREAM_BYTES + " bytes limit");
        }
        if (!MessageDigest.isEqual(checksum, sha256(body))) {
            throw new StateFormatException("State body SHA-256 checksum does not match its header");
        }
        return readBody(body, "State contains an unavailable value type: ");
    }

    private void verifyReadable(byte[] body) throws IOException {
        try {
            var state = readBody(body, "State contains an unavailable value type: ");
            State25Validator.validate(state);
        } catch (RuntimeException | IOException e) {
            throw new StateFormatException("State persistence verification failed: " + e.getMessage(), e);
        }
    }

    private State25 readBody(byte[] body, String unavailableTypePrefix) throws IOException {
        rejectedLimit.remove();
        try (var objects = new ObjectInputStreamWithClassLoader(new ByteArrayInputStream(body), classLoader)) {
            objects.setObjectInputFilter(this::filterInput);
            var value = objects.readObject();
            if (!(value instanceof State25 state)) {
                throw new StateFormatException("State body is not a concord-v2.5 State25 value");
            }
            if (state.formatVersion() != State25.CURRENT_FORMAT) {
                throw new StateFormatException("State header format " + State25.CURRENT_FORMAT
                        + " does not match body format " + state.formatVersion());
            }
            State25Validator.validate(state);
            return state;
        } catch (InvalidClassException e) {
            var limit = rejectedLimit.get();
            if (limit != null) {
                throw new StateFormatException("State exceeds the " + limit + " limit", e);
            }
            throw e;
        } catch (ClassNotFoundException e) {
            throw new StateFormatException(unavailableTypePrefix + e.getMessage(), e);
        } finally {
            rejectedLimit.remove();
        }
    }

    private ObjectInputFilter.Status filterInput(ObjectInputFilter.FilterInfo info) {
        if (info.depth() > MAX_DEPTH) {
            rejectedLimit.set("maximum depth of " + MAX_DEPTH);
            return ObjectInputFilter.Status.REJECTED;
        }
        if (info.references() > MAX_REFERENCES) {
            rejectedLimit.set("maximum references of " + MAX_REFERENCES);
            return ObjectInputFilter.Status.REJECTED;
        }
        if (info.arrayLength() > MAX_ARRAY_LENGTH) {
            rejectedLimit.set("maximum array length of " + MAX_ARRAY_LENGTH);
            return ObjectInputFilter.Status.REJECTED;
        }
        if (info.streamBytes() > MAX_STREAM_BYTES) {
            rejectedLimit.set("maximum stream size of " + MAX_STREAM_BYTES + " bytes");
            return ObjectInputFilter.Status.REJECTED;
        }
        var type = info.serialClass();
        if (type != null && denied(type.getName())) {
            rejectedLimit.set("denied class " + type.getName());
            return ObjectInputFilter.Status.REJECTED;
        }
        return ObjectInputFilter.Status.UNDECIDED;
    }

    private static boolean denied(String name) {
        return name.startsWith("org.apache.commons.collections.functors.")
                || name.startsWith("org.apache.commons.collections4.functors.")
                || name.startsWith("org.apache.commons.beanutils.")
                || name.startsWith("com.sun.org.apache.xalan.")
                || name.startsWith("javax.naming.")
                || name.startsWith("java.rmi.")
                || name.startsWith("sun.rmi.")
                || name.startsWith("org.springframework.aop.")
                || name.startsWith("com.mchange.")
                || name.equals("org.codehaus.groovy.runtime.ConvertedClosure")
                || name.equals("org.codehaus.groovy.runtime.MethodClosure");
    }

    private static byte[] sha256(byte[] bytes) throws StateFormatException {
        try {
            return MessageDigest.getInstance("SHA-256").digest(bytes);
        } catch (NoSuchAlgorithmException e) {
            throw new StateFormatException("SHA-256 is unavailable", e);
        }
    }

    public static final class StateFormatException extends IOException {

        public StateFormatException(String message) {
            super(message);
        }

        public StateFormatException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
