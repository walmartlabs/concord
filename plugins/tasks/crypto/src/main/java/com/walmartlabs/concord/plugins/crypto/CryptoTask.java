package com.walmartlabs.concord.plugins.crypto;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2023 Walmart Inc.
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

import com.walmartlabs.concord.sdk.*;

import javax.inject.Inject;
import javax.inject.Named;
import java.util.Map;

@Named("crypto")
public class CryptoTask implements Task {

   private final SecretService secretService;

    @InjectVariable(Constants.Context.CONTEXT_KEY)
    Context context;

    @Inject
    public CryptoTask(SecretService secretService) {
        this.secretService = secretService;
    }

    public String exportAsString(@InjectVariable("txId") String instanceId,
                                 String name,
                                 String password) throws Exception {

        return exportAsString(instanceId, null, name, password);
    }

    public String exportAsString(@InjectVariable("txId") String instanceId,
                                 String orgName,
                                 String name,
                                 String password) throws Exception {
        return secretService.exportAsString(context, instanceId, orgName, name, password);
    }

    public Map<String, String> exportKeyAsFile(@InjectVariable("txId") String instanceId,
                                               @InjectVariable("workDir") String workDir,
                                               String name,
                                               String password) throws Exception {

        return exportKeyAsFile(instanceId, workDir, null, name, password);
    }

    public Map<String, String> exportKeyAsFile(@InjectVariable("txId") String instanceId,
                                               @InjectVariable("workDir") String workDir,
                                               String orgName,
                                               String name,
                                               String password) throws Exception {
        return secretService.exportKeyAsFile(context, instanceId, workDir, orgName, name, password);
    }

    public Map<String, String> exportCredentials(@InjectVariable("txId") String instanceId,
                                                 @InjectVariable("workDir") String workDir,
                                                 String name,
                                                 String password) throws Exception {

        return exportCredentials(instanceId, workDir, null, name, password);
    }

    public Map<String, String> exportCredentials(@InjectVariable("txId") String instanceId,
                                                 @InjectVariable("workDir") String workDir,
                                                 String orgName,
                                                 String name,
                                                 String password) throws Exception {
        return secretService.exportCredentials(context, instanceId, workDir, orgName, name, password);
    }

    public String exportAsFile(@InjectVariable("txId") String instanceId,
                               @InjectVariable("workDir") String workDir,
                               String name,
                               String password) throws Exception {

        return exportAsFile(instanceId, workDir, null, name, password);
    }

    public String exportAsFile(@InjectVariable("txId") String instanceId,
                               @InjectVariable("workDir") String workDir,
                               String orgName,
                               String name,
                               String password) throws Exception {
        return secretService.exportAsFile(context, instanceId, workDir, orgName, name, password);
    }

    public String decryptString(@InjectVariable("txId") String instanceId, String s) throws Exception {
        return secretService.decryptString(context, instanceId, s);
    }

    public String encryptString(@InjectVariable("txId") String instanceId, String orgName, String projName, String value) throws Exception {
        return secretService.encryptString(context, instanceId, orgName, projName, value);
    }
}
