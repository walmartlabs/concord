package com.walmartlabs.concord.server.security.apikey;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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

import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;
import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.server.ExclusiveLock;
import com.walmartlabs.concord.server.PeriodicTask;
import com.walmartlabs.concord.server.cfg.ApiKeyConfiguration;
import com.walmartlabs.concord.server.user.UserDao;
import org.jooq.Configuration;
import org.jooq.Record4;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.StringWriter;
import java.sql.Timestamp;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.walmartlabs.concord.server.jooq.Tables.API_KEYS;
import static com.walmartlabs.concord.server.jooq.Tables.API_KEYS_NOTIFIER_LOCK;
import static org.jooq.impl.DSL.currentTimestamp;
import static org.jooq.impl.DSL.trunc;

@Named
@Singleton
public class ApiKeyExpirationNotifier extends PeriodicTask {

    private static final Logger log = LoggerFactory.getLogger(ApiKeyExpirationNotifier.class);

    private static final String EMAIL_SUBJECT = "Your Concord API Token Is Expiring";
    private static final long POLL_INTERVAL = TimeUnit.DAYS.toMillis(1);
    private static final long RETRY_INTERVAL = TimeUnit.SECONDS.toMillis(10);

    private final ApiKeyConfiguration cfg;
    private final ExclusiveLock exclusiveLock;
    private final ExpiredKeysDao dao;
    private final UserDao userDao;
    private final EmailNotifier notifier;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("YYYY-MM-dd");

    @Inject
    public ApiKeyExpirationNotifier(ApiKeyConfiguration cfg, ExclusiveLock exclusiveLock, ExpiredKeysDao dao, UserDao userDao, EmailNotifier notifier) {
        super(POLL_INTERVAL, RETRY_INTERVAL);
        this.cfg = cfg;
        this.exclusiveLock = exclusiveLock;
        this.dao = dao;
        this.userDao = userDao;
        this.notifier = notifier;
    }

    @Override
    public void start() {
        if (!cfg.isExpirationEnabled()) {
            log.info("start -> notifications of API key expiration is disabled");
            return;
        }

        super.start();
    }

    @Override
    protected void performTask() {
        exclusiveLock.withTryLock(API_KEYS_NOTIFIER_LOCK, this::processKeys);
    }

    private void processKeys() {
        for (int days : cfg.getNotifyBeforeDays()) {
            List<ApiKeyEntry> keys = dao.poll(days);

            Map<UUID, List<ApiKeyEntry>> keysByUser = new HashMap<>();
            keys.forEach(e -> keysByUser.computeIfAbsent(e.userId, k -> new ArrayList<>()).add(e));

            for (Map.Entry<UUID, List<ApiKeyEntry>> e : keysByUser.entrySet()) {
                UUID userId = e.getKey();
                if (sendNotification(userId, days, e.getValue())) {
                    List<UUID> keyIds = e.getValue().stream().map(k -> k.id).collect(Collectors.toList());
                    dao.markNotified(keyIds, Instant.now());
                }
            }

            log.info("processKeys -> {} keys, for days {}", keys.size(), days);
        }
    }

    private boolean sendNotification(UUID userId, int days, List<ApiKeyEntry> keys) {
        String userEmail = userDao.getEmail(userId);
        if (userEmail == null) {
            log.info("sendNotification ['{}'] -> user email not found", userId);
            return true;
        }

        return notifier.send(userEmail, EMAIL_SUBJECT, getMessage(days, keys));
    }

    private String getMessage(int days, List<ApiKeyEntry> keys) {
        try (InputStreamReader in = new InputStreamReader(this.getClass().getResourceAsStream("/com/walmartlabs/concord/server/email/api-key-expiration.mustache"))) {
            MustacheFactory mf = new DefaultMustacheFactory();
            Mustache mustache = mf.compile(in, "api-key-notifier");

            Map<String, Object> ctx = new HashMap<>();
            ctx.put("days", days);
            ctx.put("keys", keys.stream().map(e -> {
                Map<String, String> r = new HashMap<>();
                r.put("name", e.getName());
                r.put("expiredAt", dateFormat.format(e.getExpiredAt()));
                return r;
            }).collect(Collectors.toList()));

            StringWriter out = new StringWriter();
            mustache.execute(out, ctx);
            return out.toString();
        } catch (IOException e) {
            log.error("getMessage -> error", e);
            throw new RuntimeException("get message error: " + e.getMessage());
        }
    }

    @Named
    private static class ExpiredKeysDao extends AbstractDao {

        @Inject
        public ExpiredKeysDao(@Named("app") Configuration cfg) {
            super(cfg);
        }

        public List<ApiKeyEntry> poll(int days) {
            return txResult(tx ->
                    tx.select(API_KEYS.KEY_ID,
                            API_KEYS.KEY_NAME,
                            API_KEYS.EXPIRED_AT,
                            API_KEYS.USER_ID)
                            .from(API_KEYS)
                            .where(API_KEYS.EXPIRED_AT.isNotNull()
                                    .and(currentTimestamp().greaterOrEqual(trunc(API_KEYS.EXPIRED_AT).minus(days))
                                            .and(API_KEYS.LAST_NOTIFIED_AT.isNull()
                                                    .or(API_KEYS.LAST_NOTIFIED_AT.lessOrEqual(API_KEYS.EXPIRED_AT.minus(days))))))
                            .fetch(this::toEntry));
        }

        public void markNotified(List<UUID> keyIds, Instant date) {
            tx(tx -> tx.update(API_KEYS)
                    .set(API_KEYS.LAST_NOTIFIED_AT, Timestamp.from(date))
                    .where(API_KEYS.KEY_ID.in(keyIds))
                    .execute());
        }

        private ApiKeyEntry toEntry(Record4<UUID, String, Timestamp, UUID> r) {
            return new ApiKeyEntry(r.value1(), r.value2(), r.value3(), r.value4());
        }
    }

    private static class ApiKeyEntry {
        private final UUID id;
        private final String name;
        private final Date expiredAt;
        private final UUID userId;

        public ApiKeyEntry(UUID id, String name, Date expiredAt, UUID userId) {
            this.id = id;
            this.name = name;
            this.expiredAt = expiredAt;
            this.userId = userId;
        }

        public UUID getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public Date getExpiredAt() {
            return expiredAt;
        }

        public UUID getUserId() {
            return userId;
        }

        @Override
        public String toString() {
            return "ApiKeyEntry{" +
                    "id=" + id +
                    ", name='" + name + '\'' +
                    ", expiredAt=" + expiredAt +
                    ", userId=" + userId +
                    '}';
        }
    }
}
