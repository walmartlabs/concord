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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sun.security.krb5.KrbAsReqBuilder;
import sun.security.krb5.KrbException;
import sun.security.krb5.PrincipalName;
import sun.security.krb5.RealmException;
import sun.security.krb5.internal.HostAddresses;
import sun.security.krb5.internal.KDCOptions;
import sun.security.krb5.internal.ccache.CredentialsCache;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Date;
import java.util.concurrent.TimeUnit;

import static java.nio.file.StandardCopyOption.ATOMIC_MOVE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

public class KerberosAuth implements AnsibleAuth {

    private static final Logger log = LoggerFactory.getLogger(KerberosAuth.class);

    private static final long ERROR_RETRY = TimeUnit.SECONDS.toMillis(30);
    private static final long RENEW = TimeUnit.MINUTES.toMillis(1);

    private final String username;
    private final PrincipalName principal;
    private final char[] password;
    private final Path tgtCacheFile;
    private final Path tgtTmpCacheFile;

    private Thread renewThread;

    public KerberosAuth(String username, String password, Path tmpDir, boolean debug) throws RealmException {
        this.username = username;
        this.principal = new PrincipalName(username);
        this.password = password.toCharArray();
        this.tgtCacheFile = tmpDir.resolve("tgt-ticket");
        this.tgtTmpCacheFile = tmpDir.resolve("tmp-tgt-ticket");
        if (debug) {
            System.setProperty("sun.security.krb5.debug", "true");
        }
    }

    @Override
    public KerberosAuth enrich(AnsibleEnv env) {
        env.put("KRB5CCNAME", tgtCacheFile.toString());
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

    private long storeTgt(Path dest) throws KrbException, IOException {
        Files.deleteIfExists(dest);

        KDCOptions opt = new KDCOptions();
        opt.set(KDCOptions.FORWARDABLE, false);
        opt.set(KDCOptions.PROXIABLE, false);

        KrbAsReqBuilder builder = new KrbAsReqBuilder(principal, password);
        builder.setOptions(opt);

        String realm = principal.getRealmString();

        PrincipalName sname = PrincipalName.tgsService(realm, realm);
        builder.setTarget(sname);
        builder.setAddresses(HostAddresses.getLocalAddresses());

        builder.action();

        sun.security.krb5.internal.ccache.Credentials credentials = builder.getCCreds();
        builder.destroy();

        CredentialsCache cache = CredentialsCache.create(principal, dest.toString());
        if (cache == null) {
            throw new IOException("Unable to create the cache file " + dest);
        }
        cache.update(credentials);
        cache.save();

        return credentials.getEndTime().getTime();
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
                Thread.sleep(ms);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
