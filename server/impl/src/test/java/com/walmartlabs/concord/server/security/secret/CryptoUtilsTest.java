package com.walmartlabs.concord.server.security.secret;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertArrayEquals;

public class CryptoUtilsTest {

    @Test
    public void testRoundtrip() throws Exception {
        byte[] passwd = ("hello#" + System.currentTimeMillis()).getBytes();
        byte[] salt = ("salt#" + System.currentTimeMillis()).getBytes();

        byte[] input = {0, 1, 2, 3, 4, 5, 6, 7};

        byte[] crypted = CryptoUtils.encrypt(input, passwd, salt);
        System.out.println(Arrays.toString(crypted));
        byte[] decrypted = CryptoUtils.decrypt(crypted, passwd, salt);

        assertArrayEquals(input, decrypted);
    }
}
