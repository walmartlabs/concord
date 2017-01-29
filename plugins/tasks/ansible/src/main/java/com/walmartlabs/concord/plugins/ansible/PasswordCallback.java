package com.walmartlabs.concord.plugins.ansible;

import io.takari.bpm.api.ExecutionContext;

public interface PasswordCallback {

    byte[] getPassword(ExecutionContext ctx);
}
