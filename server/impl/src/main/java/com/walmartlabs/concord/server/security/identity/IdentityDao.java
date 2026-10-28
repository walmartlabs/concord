package com.walmartlabs.concord.server.security.identity;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2026 Walmart Inc.
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

import com.walmartlabs.concord.db.AbstractDao;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.walmartlabs.concord.db.MainDB;
import com.walmartlabs.concord.server.sdk.security.*;
import com.walmartlabs.concord.server.security.provider.ProviderRegistry;
import com.walmartlabs.concord.server.user.UserDao;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.Record;

import javax.inject.Inject;
import javax.ws.rs.BadRequestException;
import javax.ws.rs.ClientErrorException;
import javax.ws.rs.NotFoundException;
import javax.ws.rs.core.Response;
import java.util.*;

/** Transactional ownership and retirement of external identity keys. */
public class IdentityDao extends AbstractDao {
    public record IdentityRecord(UUID identityId, UUID userId, ExternalIdentityKey identity, boolean active) {}
    public record ProfileSelection(UserProfile stored, String providerId, UUID identityId, ExternalIdentityKey identity) {}
    public record Binding(String sourceId, ExternalIdentityKey identity, boolean required) {}
    public record Resolution(UUID userId, boolean created) {}

    private final ProviderRegistry registry;
    private final UserDao users;

    @Inject
    public IdentityDao(@MainDB Configuration cfg, ProviderRegistry registry, UserDao users) {
        super(cfg);
        this.registry = registry;
        this.users = users;
    }

    private static ClientErrorException conflict(String message) {
        return new ClientErrorException(message, Response.Status.CONFLICT);
    }

    // Serializes missing-key checks as well as account namespace changes across all nodes.
    private static void lock(DSLContext tx) {
        tx.fetch("select pg_advisory_xact_lock(?)", 0x434f4e434f524449L);
    }

    private static IdentityRecord record(Record r) {
        if (r == null) return null;
        return new IdentityRecord(r.get("identity_id", UUID.class), r.get("user_id", UUID.class),
                new ExternalIdentityKey(r.get("provider_id", String.class), r.get("authority", String.class), r.get("subject", String.class)),
                r.get("is_active", Boolean.class));
    }

    private static IdentityRecord find(DSLContext tx, ExternalIdentityKey key) {
        return record(tx.fetchOne("select identity_id,user_id,provider_id,authority,subject,is_active from user_identities where provider_id=? and authority=? and subject=?", key.providerId(), key.authority(), key.subject()));
    }

    public Optional<IdentityRecord> find(ExternalIdentityKey key) {
        return Optional.ofNullable(find(dsl(), key));
    }

    public List<IdentityRecord> list(UUID userId) {
        requireUser(dsl(), userId);
        return dsl().fetch("select identity_id,user_id,provider_id,authority,subject,is_active from user_identities where user_id=? order by provider_id,authority,subject", userId).map(IdentityDao::record);
    }

    private static void requireUser(DSLContext tx, UUID id) {
        if (tx.fetchOne("select user_id from users where user_id=?", id) == null) throw new NotFoundException("User not found");
    }

    private static void requireEnabled(DSLContext tx, UUID id) {
        Record r = tx.fetchOne("select is_disabled,is_permanently_disabled from users where user_id=? for update", id);
        if (r == null || Boolean.TRUE.equals(r.get(0, Boolean.class)) || Boolean.TRUE.equals(r.get(1, Boolean.class)))
            throw new AuthenticationException("Account is unavailable or disabled");
    }

    private static void consume(DSLContext tx, UUID userId, ExternalIdentityKey key, boolean administrative) {
        Record enrolled = tx.fetchOne("select user_id from legacy_user_enrollment where user_id=?", userId);
        if (enrolled == null) return;
        Record old = tx.fetchOne("select subject from legacy_identity_adoptions where user_id=? and provider_id=? and authority=?", userId, key.providerId(), key.authority());
        if (old != null) {
            if (!administrative && !key.subject().equals(old.get(0, String.class))) throw conflict("Legacy identity namespace already consumed");
            return;
        }
        Record owner = tx.fetchOne("select user_id from legacy_identity_adoptions where provider_id=? and authority=? and subject=?", key.providerId(), key.authority(), key.subject());
        if (owner != null && !userId.equals(owner.get(0, UUID.class))) throw conflict("Identity already consumed");
        tx.execute("insert into legacy_identity_adoptions(user_id,provider_id,authority,subject,adopted_at) values (?,?,?,?,current_timestamp)", userId, key.providerId(), key.authority(), key.subject());
    }

    private static IdentityRecord attach(DSLContext tx, UUID userId, ExternalIdentityKey key, boolean administrative) {
        if ("local".equals(key.providerId()) && (!key.authority().isEmpty() || !userId.toString().equals(key.subject())))
            throw new BadRequestException("Local identity must be the account UUID");
        IdentityRecord old = find(tx, key);
        if (old != null) {
            if (old.active() && userId.equals(old.userId())) return old;
            if (!administrative || (old.userId() != null && !userId.equals(old.userId()))) throw conflict("Identity is retired or owned by another account");
        }
        Record namespace = tx.fetchOne("select identity_id from user_identities where user_id=? and provider_id=? and authority=? and is_active", userId, key.providerId(), key.authority());
        if (namespace != null) throw conflict("Account already has an identity in this namespace");
        consume(tx, userId, key, administrative);
        UUID id = old == null ? UUID.randomUUID() : old.identityId();
        if (old == null) tx.execute("insert into user_identities(identity_id,user_id,provider_id,authority,subject,is_active) values (?,?,?,?,?,true)", id, userId, key.providerId(), key.authority(), key.subject());
        else tx.execute("update user_identities set user_id=?,is_active=true where identity_id=?", userId, id);
        return new IdentityRecord(id, userId, key, true);
    }

    public IdentityRecord link(UUID userId, ExternalIdentityKey key) {
        return txResult(tx -> { lock(tx); requireUser(tx, userId); return attach(tx, userId, key, true); });
    }

    public void unlink(UUID userId, UUID identityId) {
        tx(tx -> {
            lock(tx); requireUser(tx, userId);
            Record r = tx.fetchOne("select identity_id from user_identities where identity_id=? and user_id=? and is_active for update", identityId, userId);
            if (r == null) throw new NotFoundException("Identity not found");
            if (tx.fetchOne("select binding_id from user_authorization_bindings where user_id=? and identity_id=? and is_required", userId, identityId) != null)
                throw conflict("Identity is required by an authorization source");
            tx.execute("delete from user_authorization_bindings where user_id=? and identity_id=?", userId, identityId);
            tx.execute("update users set profile_identity_id=null where user_id=? and profile_identity_id=?", userId, identityId);
            tx.execute("update user_identities set is_active=false where identity_id=?", identityId);
        });
    }

    public void setProfile(UUID userId, String providerId, UUID identityId) {
        tx(tx -> {
            lock(tx); requireUser(tx, userId);
            UserDirectory directory = providerId == null ? null : registry.directories().get(providerId);
            if (directory == null) throw new BadRequestException("Unknown profile directory");
            if ("local".equals(providerId)) {
                if (identityId != null) throw new BadRequestException("Local profiles do not have a profile identity");
            } else {
                if (identityId == null) throw new BadRequestException("Profile identity required");
                IdentityRecord identity = record(tx.fetchOne("select identity_id,user_id,provider_id,authority,subject,is_active from user_identities where identity_id=? and user_id=? and is_active", identityId, userId));
                if (identity == null || !directory.acceptsIdentity(identity.identity())) throw new BadRequestException("Invalid profile identity");
            }
            tx.execute("update users set profile_provider_id=?,profile_identity_id=? where user_id=?", providerId, identityId, userId);
        });
    }

    public Optional<ProfileSelection> profile(UUID userId) {
        Record r = dsl().fetchOne("select u.username,u.domain,u.display_name,u.user_email,u.profile_provider_id,u.profile_identity_id,i.provider_id,i.authority,i.subject from users u left join user_identities i on i.identity_id=u.profile_identity_id and i.user_id=u.user_id and i.is_active where u.user_id=?", userId);
        if (r == null) return Optional.empty();
        UserProfile p = new UserProfile(r.get(0, String.class), r.get(1, String.class), r.get(2, String.class), r.get(3, String.class), Map.of());
        String provider = r.get(6, String.class);
        ExternalIdentityKey identity = provider == null ? null : new ExternalIdentityKey(provider, r.get(7, String.class), r.get(8, String.class));
        return Optional.of(new ProfileSelection(p, r.get(4, String.class), r.get(5, UUID.class), identity));
    }

    public UUID enabledUser(UUID userId) {
        return txResult(tx -> { requireEnabled(tx, userId); return userId; });
    }

    /** Persist fresh verified claims only for bindings to the authenticated identity. */
    public void observe(UUID userId, VerifiedIdentity verified) {
        final String attributes;
        try {
            attributes = verified.attributesObservedAt() == null ? null : new ObjectMapper().writeValueAsString(verified.authorizationAttributes());
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Invalid verified attributes", e);
        }
        tx(tx -> {
            lock(tx);
            requireEnabled(tx, userId);
            IdentityRecord identity = find(tx, verified.identity());
            if (identity == null || !identity.active() || !userId.equals(identity.userId()))
                throw new AuthenticationException("Identity is no longer active");
            if (attributes == null) return;
            var bindings = tx.fetch("select binding_id,source_id from user_authorization_bindings where user_id=? and identity_id=?", userId, identity.identityId());
            for (Record binding : bindings) {
                UUID bindingId = binding.get(0, UUID.class);
                String source = binding.get(1, String.class);
                int updated = tx.execute("insert into user_authorization_state(user_id,source_id,binding_id,status,verified_attributes,attributes_observed_at,attributes_expire_at) values (?,?,?,'STALE',cast(? as jsonb),cast(? as timestamptz),cast(? as timestamptz)) on conflict (user_id,source_id) do update set status='STALE',valid_until=null,verified_attributes=excluded.verified_attributes,attributes_observed_at=excluded.attributes_observed_at,attributes_expire_at=excluded.attributes_expire_at where user_authorization_state.binding_id=excluded.binding_id and (user_authorization_state.attributes_observed_at is null or user_authorization_state.attributes_observed_at < excluded.attributes_observed_at)", userId, source, bindingId, attributes, java.time.OffsetDateTime.ofInstant(verified.attributesObservedAt(), java.time.ZoneOffset.UTC), java.time.OffsetDateTime.ofInstant(verified.attributesExpireAt(), java.time.ZoneOffset.UTC));
                if (updated != 0) tx.execute("update user_authorization_bindings set refresh_version=refresh_version+1 where binding_id=?", bindingId);
            }
        });
    }

    public Resolution resolve(VerifiedIdentity verified, ProvisioningPolicy policy, LegacyAccountSelector selector,
                              List<ExternalIdentityKey> directoryIdentities, List<Binding> bindings, ExternalIdentityKey profileIdentity) {
        return txResult(tx -> {
            lock(tx);
            ExternalIdentityKey primary = verified.identity();
            IdentityRecord existing = find(tx, primary);
            if (existing != null) {
                if (!existing.active() || existing.userId() == null) throw new AuthenticationException("Identity is retired");
                requireEnabled(tx, existing.userId());
                return new Resolution(existing.userId(), false);
            }
            Record consumed = tx.fetchOne("select user_id from legacy_identity_adoptions where provider_id=? and authority=? and subject=?", primary.providerId(), primary.authority(), primary.subject());
            if (consumed != null) throw new AuthenticationException("Identity has already been consumed");
            UUID userId = null;
            if (selector != null) {
                var candidates = tx.fetch("select user_id from legacy_user_enrollment where username=? and (cast(? as text) is null or domain=?) and user_type=?", selector.username(), selector.domain(), selector.domain(), selector.userType());
                if (candidates.size() > 1) throw conflict("Ambiguous legacy account");
                if (!candidates.isEmpty()) {
                    userId = candidates.get(0).get(0, UUID.class);
                    requireEnabled(tx, userId);
                    if (tx.fetchOne("select user_id from legacy_identity_adoptions where user_id=? and provider_id=? and authority=?", userId, primary.providerId(), primary.authority()) != null)
                        throw new AuthenticationException("Legacy identity namespace already consumed");
                }
            }
            boolean created = userId == null;
            if (created) {
                if (!policy.autoCreate()) throw new AuthenticationException("Account provisioning is disabled");
                userId = UUID.randomUUID();
                users.createIdentityAccount(tx, userId, verified.profile(), policy.legacyUserType());
            }
            attach(tx, userId, primary, false);
            for (ExternalIdentityKey key : directoryIdentities) attach(tx, userId, key, false);
            if (!"local".equals(policy.profileProviderId()) && profileIdentity != null) {
                IdentityRecord profile = find(tx, profileIdentity);
                tx.execute("update users set profile_provider_id=?,profile_identity_id=? where user_id=?", policy.profileProviderId(), profile.identityId(), userId);
            } else {
                tx.execute("update users set profile_provider_id=?,profile_identity_id=null where user_id=?", policy.profileProviderId(), userId);
            }
            for (Binding binding : bindings) {
                UUID identityId = binding.identity() == null ? null : Objects.requireNonNull(find(tx, binding.identity())).identityId();
                tx.execute("insert into authorization_sources(source_id,is_active) values (?,?) on conflict (source_id) do nothing", binding.sourceId(), registry.authorizationSources().get(binding.sourceId()).isActive());
                tx.execute("insert into user_authorization_bindings(binding_id,user_id,source_id,identity_id,is_required,origin,refresh_version) values (?,?,?,?,?,'DEFAULT',0) on conflict (user_id,source_id) do nothing", UUID.randomUUID(), userId, binding.sourceId(), identityId, binding.required());
            }
            return new Resolution(userId, created);
        });
    }
}
