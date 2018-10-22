package com.walmartlabs.concord.server;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status.Family;

public enum ExtraStatus implements Response.StatusType {

    TOO_MANY_REQUESTS(429, "Too Many Requests");

    private final int statusCode;
    private final Family family;
    private final String reasonPhrase;

    private ExtraStatus(int statusCode, String reasonPhrase) {
        this.statusCode = statusCode;
        this.family = Family.familyOf(statusCode);
        this.reasonPhrase = reasonPhrase;
    }

    @Override
    public int getStatusCode() {
        return statusCode;
    }

    @Override
    public Family getFamily() {
        return family;
    }

    @Override
    public String getReasonPhrase() {
        return reasonPhrase;
    }
}
