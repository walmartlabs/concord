package com.walmartlabs.concord.server.security.apikey;

import com.google.common.base.Throwables;
import com.walmartlabs.concord.db.AbstractDao;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;

import javax.inject.Inject;
import javax.inject.Named;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Base64.Encoder;
import java.util.UUID;

import static com.walmartlabs.concord.server.jooq.tables.ApiKeys.API_KEYS;

@Named
public class ApiKeyDao extends AbstractDao {

    private final SecureRandom rnd;

    @Inject
    public ApiKeyDao(Configuration cfg, SecureRandom rnd) {
        super(cfg);
        this.rnd = rnd;
    }

    public String newApiKey() {
        byte[] ab = new byte[16];
        rnd.nextBytes(ab);

        Encoder e = Base64.getEncoder().withoutPadding();
        return e.encodeToString(ab);
    }

    public UUID insert(UUID userId, String key) {
        return txResult(tx -> tx.insertInto(API_KEYS)
                .columns(API_KEYS.USER_ID, API_KEYS.API_KEY)
                .values(userId, hash(key))
                .returning(API_KEYS.KEY_ID)
                .fetchOne()
                .getKeyId());
    }

    public void delete(UUID id) {
        tx(tx -> tx.deleteFrom(API_KEYS)
                .where(API_KEYS.KEY_ID.eq(id))
                .execute());
    }

    public UUID findUserId(String key) {
        try (DSLContext tx = DSL.using(cfg)) {
            UUID id = tx.select(API_KEYS.USER_ID)
                    .from(API_KEYS)
                    .where(API_KEYS.API_KEY.eq(hash(key)))
                    .fetchOne(API_KEYS.USER_ID);

            if (id == null) {
                return null;
            }

            return id;
        }
    }

    public boolean existsById(UUID id) {
        try (DSLContext tx = DSL.using(cfg)) {
            int cnt = tx.fetchCount(tx.selectFrom(API_KEYS)
                    .where(API_KEYS.KEY_ID.eq(id)));

            return cnt > 0;
        }
    }

    private static String hash(String s) {
        MessageDigest md;
        try {
            md = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw Throwables.propagate(e);
        }

        byte[] ab = Base64.getDecoder().decode(s);
        ab = md.digest(ab);

        return Base64.getEncoder().withoutPadding().encodeToString(ab);
    }
}
