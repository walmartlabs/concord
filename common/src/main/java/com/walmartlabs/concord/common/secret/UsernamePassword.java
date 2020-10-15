package com.walmartlabs.concord.common.secret;

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

import com.walmartlabs.concord.sdk.Secret;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;

public class UsernamePassword implements Secret {

    public static byte[] serialize(UsernamePassword input) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutput out = new DataOutputStream(baos);

            out.writeUTF(input.getUsername());

            ByteBuffer bb = StandardCharsets.UTF_8.encode(CharBuffer.wrap(input.getPassword()));
            byte[] ab = new byte[bb.remaining()];
            bb.get(ab);

            out.writeInt(ab.length);
            out.write(ab);

            return baos.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static UsernamePassword deserialize(byte[] input) {
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(input))) {

            String username = in.readUTF();

            int len = in.readInt();
            byte[] ab = new byte[len];
            in.readFully(ab);

            char[] password = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(ab)).array();

            return new UsernamePassword(username, password);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private final String username;
    private final char[] password;

    public UsernamePassword(String username, char[] password) { // NOSONAR
        this.username = username;
        this.password = password;
    }

    public String getUsername() {
        return username;
    }

    public char[] getPassword() {
        return password;
    }
}
