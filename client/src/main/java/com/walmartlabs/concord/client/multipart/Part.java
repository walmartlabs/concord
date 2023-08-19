package com.walmartlabs.concord.client.multipart;

public interface Part {

    byte[] getBytes(String boundary);
}