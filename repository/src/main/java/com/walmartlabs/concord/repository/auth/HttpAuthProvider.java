package com.walmartlabs.concord.repository.auth;

import com.walmartlabs.concord.sdk.Secret;

import javax.annotation.Nullable;

public interface HttpAuthProvider {

    boolean canHandle(String gitHost);

    String get(String gitHost, @Nullable Secret secret);

}
