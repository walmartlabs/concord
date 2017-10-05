package com.walmartlabs.concord.sdk;

import java.util.Map;

public interface SecretStore {

    String exportAsString(String instanceId,
                          String name,
                          String password) throws Exception;

    Map<String, String> exportKeyAsFile(String instanceId,
                                        String workDir,
                                        String name,
                                        String password) throws Exception;

    Map<String, String> exportCredentials(String instanceId,
                                          String workDir,
                                          String name,
                                          String password) throws Exception;

    String decryptString(String instanceId, String s) throws Exception;
}
