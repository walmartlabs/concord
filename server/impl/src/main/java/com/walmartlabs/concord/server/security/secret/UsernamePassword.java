package com.walmartlabs.concord.server.security.secret;

import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;

import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.StandardCharsets;

public class UsernamePassword implements Secret {

    public static byte[] serialize(UsernamePassword input) {
        ByteArrayDataOutput out = ByteStreams.newDataOutput();

        out.writeUTF(input.getUsername());

        ByteBuffer bb = StandardCharsets.UTF_8.encode(CharBuffer.wrap(input.getPassword()));
        byte[] ab = new byte[bb.remaining()];
        bb.get(ab);

        out.writeInt(ab.length);
        out.write(ab);

        return out.toByteArray();
    }

    public static UsernamePassword deserialize(byte[] input) {
        ByteArrayDataInput in = ByteStreams.newDataInput(input);

        String username = in.readUTF();

        int len = in.readInt();
        byte[] ab = new byte[len];
        in.readFully(ab);

        char[] password = StandardCharsets.UTF_8.decode(ByteBuffer.wrap(ab)).array();

        return new UsernamePassword(username, password);
    }

    private final String username;
    private final char[] password;

    public UsernamePassword(String username, char[] password) {
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
