package com.walmartlabs.concord.plugins.ansible;

import com.walmartlabs.concord.sdk.RpcConfiguration;
import com.walmartlabs.concord.sdk.SecretStore;

import javax.inject.Inject;
import javax.inject.Named;

@Named("ansible")
public class AnsibleTask extends RunPlaybookTask2 {

    @Inject
    public AnsibleTask(RpcConfiguration rpcCfg, SecretStore secretStore) {
        super(rpcCfg, secretStore);
    }
}
