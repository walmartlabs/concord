package com.walmartlabs.concord.plugins.ansible;

import com.walmartlabs.concord.sdk.Context;

public interface PasswordCallback {

    byte[] getPassword(Context ctx);
}
