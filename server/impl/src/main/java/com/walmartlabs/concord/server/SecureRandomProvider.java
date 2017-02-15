package com.walmartlabs.concord.server;

import com.google.common.base.Throwables;

import javax.inject.Named;
import javax.inject.Provider;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;

@Named
public class SecureRandomProvider implements Provider<SecureRandom> {

    @Override
    public SecureRandom get() {
        try {
            return SecureRandom.getInstance("NativePRNGNonBlocking");
        } catch (NoSuchAlgorithmException e) {
            throw Throwables.propagate(e);
        }
    }
}
