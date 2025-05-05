package com.walmartlabs.concord.plugins.ansible;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2019 Walmart Inc.
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

import com.walmartlabs.concord.common.TimeProvider;
import org.apache.kerby.KOptions;
import org.apache.kerby.kerberos.kerb.KrbException;
import org.apache.kerby.kerberos.kerb.ccache.CredentialCache;
import org.apache.kerby.kerberos.kerb.client.ClientUtil;
import org.apache.kerby.kerberos.kerb.client.KrbClient;
import org.apache.kerby.kerberos.kerb.client.KrbKdcOption;
import org.apache.kerby.kerberos.kerb.client.KrbOption;
import org.apache.kerby.kerberos.kerb.type.ticket.TgtTicket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import static com.walmartlabs.concord.sdk.MapUtils.getString;
import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public class KerberosAuth implements AnsibleAuth {

    private static final Logger log = LoggerFactory.getLogger(KerberosAuth.class);

    private static final long ERROR_RETRY = TimeUnit.SECONDS.toMillis(30);
    private static final long RENEW = TimeUnit.MINUTES.toMillis(1);

    private final TimeProvider timeProvider;
    private final String username;
    private final String password;
    private final Path tgtCacheFile;
    private final Path tgtTmpCacheFile;

    private Thread renewThread;

    public KerberosAuth(TimeProvider timeProvider, String username, String password, Path tmpDir, boolean debug) {
        this.timeProvider = timeProvider;
        this.username = username;
        this.password = password;
        this.tgtCacheFile = tmpDir.resolve("tgt-ticket");
        this.tgtTmpCacheFile = tmpDir.resolve("tmp-tgt-ticket");
        if (debug) {
            System.setProperty("sun.security.krb5.debug", "true");
        }
    }

    @Override
    public KerberosAuth enrich(AnsibleEnv env, AnsibleContext context) {
        String dockerImage = getString(context.args(), TaskParams.DOCKER_IMAGE_KEY.getKey());
        if (dockerImage != null) {
            env.put("KRB5CCNAME", Paths.get("/workspace").resolve(context.workDir().relativize(tgtCacheFile)).toString());
        } else {
            env.put("KRB5CCNAME", tgtCacheFile.toString());
        }
        return this;
    }

    @Override
    public KerberosAuth enrich(PlaybookScriptBuilder p) {
        p.withExtraSshArgs("-o GSSAPIAuthentication=yes")
                .withUser(username);
        return this;
    }

    @Override
    public void prepare() throws Exception {
        long expiredAt = storeTgt(tgtCacheFile);
        log.info("TGT obtained, expired at '{}'", new Date(expiredAt));

        renewThread = new Thread(new TgtRenew(tgtTmpCacheFile, tgtCacheFile, expiredAt), "tgt-renew");
        renewThread.start();
    }

    public void postProcess() {
        if (renewThread != null) {
            renewThread.interrupt();
            try {
                renewThread.join();
            } catch (Exception e) {
                log.warn("postProcess -> error", e);
            }
            renewThread = null;
        }

        try {
            Files.deleteIfExists(tgtCacheFile);
        } catch (Exception e) {
            log.warn("postProcess -> error", e);
        }

        try {
            Files.deleteIfExists(tgtTmpCacheFile);
        } catch (Exception e) {
            log.warn("postProcess -> error", e);
        }
    }

    private long storeTgt(Path dest) throws IOException, KrbException {
        Files.deleteIfExists(dest);

        KOptions requestOptions = new KOptions();
        requestOptions.add(KrbOption.CLIENT_PRINCIPAL, username);
        requestOptions.add(KrbOption.USE_PASSWD, true);
        requestOptions.add(KrbOption.USER_PASSWD, password);
        requestOptions.add(KrbOption.CONN_TIMEOUT, 30000);
        requestOptions.add(KrbKdcOption.FORWARDABLE, false);
        requestOptions.add(KrbKdcOption.PROXIABLE, false);

        KrbClient krbClient = new KrbClient(ClientUtil.getDefaultConfig());
        krbClient.init();

        TgtTicket ticket = krbClient.requestTgt(requestOptions);

        CredentialCache cache = new CredentialCache(ticket);
        try {
            cache.store(dest.toFile());
        } catch (IOException e) {
            throw new KrbException("Failed to store tgt", e);
        }

        return ticket.getEncKdcRepPart().getEndTime().getTime();
    }

    private class TgtRenew implements Runnable {

        private final Path tgtTmpCacheFile;
        private final Path tgtCacheFile;

        private long expiredAt;

        public TgtRenew(Path tgtTmpCacheFile, Path tgtCacheFile, long expiredAt) {
            this.tgtTmpCacheFile = tgtTmpCacheFile;
            this.tgtCacheFile = tgtCacheFile;
            this.expiredAt = expiredAt;
        }

        @Override
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    long now = System.currentTimeMillis();
                    long renewAt = expiredAt - RENEW;
                    if (renewAt <= now) {
                        expiredAt = storeTgt(tgtTmpCacheFile);
                        Files.move(tgtTmpCacheFile, tgtCacheFile, ATOMIC_MOVE, REPLACE_EXISTING);
                        log.info("TGT obtained, expires at '{}'", new Date(expiredAt));
                    } else {
                        long timeToSleep = renewAt - now;
                        if (timeToSleep > 0) {
                            log.info("TGT renew at {}", new Date(renewAt));
                            sleep(timeToSleep);
                        }
                    }
                } catch (Exception e) {
                    log.error("TGT get error: {}, retry in {} ms", e.getMessage(), ERROR_RETRY);
                    sleep(ERROR_RETRY);
                }
            }
        }

        private void sleep(long ms) {
            try {
                timeProvider.sleep(ms);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
