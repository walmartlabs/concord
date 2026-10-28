package com.walmartlabs.concord.server.security.authorization;

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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.typesafe.config.Config;
import com.walmartlabs.concord.db.AbstractDao;
import com.walmartlabs.concord.db.MainDB;
import com.walmartlabs.concord.server.sdk.security.*;
import com.walmartlabs.concord.server.security.provider.ProviderRegistry;
import org.jooq.Configuration;
import org.jooq.DSLContext;
import org.jooq.Record;

import javax.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.*;

/** Transactional source observations. Provider calls are deliberately outside transactions. */
public class AuthorizationService extends AbstractDao {
    public record BindingSpec(String sourceId, UUID identityId, boolean required) {}
    public record Binding(UUID bindingId, String sourceId, UUID identityId, boolean required, String origin,
                          String status, Instant observedAt, Instant validUntil) {}
    public record EffectiveAuthorization(Set<String> roles, Set<String> permissions) {}
    private record Refresh(UUID bindingId, long version, String sourceId, ExternalIdentityKey identity,
                           Map<String, Object> attributes, Instant attributesObservedAt, Instant attributesExpireAt,
                           RuntimeException error) {}
    private record Publication(boolean published, RuntimeException error) {}

    private final ProviderRegistry registry;
    private final Config config;
    private final ObjectMapper mapper = new ObjectMapper();

    @Inject
    public AuthorizationService(@MainDB Configuration cfg, ProviderRegistry registry, Config config) {
        super(cfg);
        this.registry = registry;
        this.config = config;
    }

    private static Instant instant(Record r, String field) {
        OffsetDateTime t = r.get(field, OffsetDateTime.class);
        return t == null ? null : t.toInstant();
    }
    private static OffsetDateTime time(Instant t) { return t == null ? null : t.atOffset(ZoneOffset.UTC); }
    private static void user(DSLContext tx, UUID id) {
        Record r = tx.fetchOne("select is_disabled,is_permanently_disabled from users where user_id=?", id);
        if (r == null || Boolean.TRUE.equals(r.get(0, Boolean.class)) || Boolean.TRUE.equals(r.get(1, Boolean.class)))
            throw new AuthenticationException("Account is unavailable or disabled");
    }
    private Duration maxAge(AuthorizationProvider provider) {
        String path = "security.authorization.sources." + provider.id() + ".maxAge";
        String qualified = "com.walmartlabs.concord.server." + path;
        Duration age = config.hasPath(qualified) ? config.getDuration(qualified)
                : config.hasPath(path) ? config.getDuration(path) : provider.defaultMaxAge();
        if (age == null || age.isZero() || age.isNegative()) throw new IllegalArgumentException("Invalid authorization maxAge for " + provider.id());
        return age;
    }
    private boolean required(String source) {
        String path = "security.authorization.sources." + source + ".required";
        String qualified = "com.walmartlabs.concord.server." + path;
        if (config.hasPath(qualified)) return config.getBoolean(qualified);
        return config.hasPath(path) && config.getBoolean(path);
    }

    /** Called before serving work on each node. The configured capability set must agree across nodes. */
    public void reconcile() {
        for (var p : registry.authorizationSources().values()) maxAge(p);
        for (var policy : registry.policies().values()) {
            for (String source : policy.authorizationSources()) {
                AuthorizationProvider provider = registry.authorizationSources().get(source);
                if (required(source) && (provider == null || !provider.isActive()))
                    throw new IllegalArgumentException("Required authorization source is inactive: " + source);
            }
        }
        tx(tx -> {
            // Refresh and observation both lock bindings before their state rows.
            tx.fetch("select binding_id from user_authorization_bindings order by binding_id for update");
            tx.execute("update authorization_sources set is_active=false");
            for (var p : registry.authorizationSources().values()) {
                tx.execute("insert into authorization_sources(source_id,is_active) values (?,?) on conflict(source_id) do update set is_active=excluded.is_active", p.id(), p.isActive());
                tx.execute("update user_authorization_bindings set is_required=? where source_id=? and origin='DEFAULT'", required(p.id()), p.id());
            }
            // Restrict state invalidation to bindings whose versions this transaction locked.
            tx.execute("with invalidated as (update user_authorization_bindings set refresh_version=refresh_version+1 returning binding_id) update user_authorization_state s set status='STALE' from invalidated b where s.binding_id=b.binding_id");
        });
    }

    public List<Binding> listBindings(UUID userId) {
        if (dsl().fetchOne("select user_id from users where user_id=?", userId) == null) throw new NoSuchElementException("User not found");
        return dsl().fetch("select b.binding_id,b.source_id,b.identity_id,b.is_required,b.origin,s.status,s.observed_at,s.valid_until from user_authorization_bindings b left join user_authorization_state s on s.binding_id=b.binding_id where b.user_id=? order by b.source_id", userId)
                .map(r -> new Binding(r.get("binding_id", UUID.class), r.get("source_id", String.class), r.get("identity_id", UUID.class), r.get("is_required", Boolean.class), r.get("origin", String.class), r.get("status", String.class), instant(r,"observed_at"), instant(r,"valid_until")));
    }

    public void replaceBindings(UUID userId, List<BindingSpec> bindings) {
        Objects.requireNonNull(bindings);
        Set<String> seen = new HashSet<>();
        tx(tx -> {
            if (tx.fetchOne("select user_id from users where user_id=? for update", userId) == null) throw new NoSuchElementException("User not found");
            tx.execute("update users set authorization_bindings_administered=true where user_id=?", userId);
            for (BindingSpec b : bindings) {
                if (b == null || b.sourceId() == null || !seen.add(b.sourceId())) throw new IllegalArgumentException("Duplicate or invalid source");
                AuthorizationProvider p = registry.authorizationSources().get(b.sourceId());
                if (p == null || !p.isActive()) throw new IllegalArgumentException("Unknown or inactive source: " + b.sourceId());
                ExternalIdentityKey key = null;
                if (b.identityId() != null) {
                    Record r = tx.fetchOne("select provider_id,authority,subject from user_identities where identity_id=? and user_id=? and is_active for update", b.identityId(), userId);
                    if (r == null) throw new IllegalArgumentException("Identity is not active or owned by user");
                    key = new ExternalIdentityKey(r.get(0,String.class), r.get(1,String.class), r.get(2,String.class));
                }
                if (!p.acceptsIdentity(key)) throw new IllegalArgumentException("Identity is not accepted by source");
                Record old = tx.fetchOne("select binding_id,identity_id from user_authorization_bindings where user_id=? and source_id=? for update", userId, b.sourceId());
                if (old != null && Objects.equals(old.get(1,UUID.class), b.identityId())) {
                    tx.execute("update user_authorization_bindings set is_required=?,origin='ADMIN' where binding_id=?", b.required(), old.get(0,UUID.class));
                } else {
                    if (old != null) tx.execute("delete from user_authorization_bindings where binding_id=?", old.get(0,UUID.class));
                    tx.execute("insert into authorization_sources(source_id,is_active) values (?,true) on conflict(source_id) do nothing", b.sourceId());
                    tx.execute("insert into user_authorization_bindings(binding_id,user_id,source_id,identity_id,is_required,origin,refresh_version) values (?,?,?,?,?,'ADMIN',0)", UUID.randomUUID(),userId,b.sourceId(),b.identityId(),b.required());
                }
            }
            for (Record r : tx.fetch("select binding_id,source_id from user_authorization_bindings where user_id=? for update", userId))
                if (!seen.contains(r.get(1,String.class))) tx.execute("delete from user_authorization_bindings where binding_id=?", r.get(0,UUID.class));
        });
    }

    public Map<String, Boolean> sources() {
        Map<String, Boolean> result = new TreeMap<>();
        dsl().fetch("select source_id,is_active from authorization_sources").forEach(r -> result.put(r.get(0,String.class), r.get(1,Boolean.class)));
        registry.authorizationSources().forEach((id,p) -> result.put(id,p.isActive()));
        return result;
    }
    public boolean isActiveSource(String sourceId) {
        AuthorizationProvider provider = registry.authorizationSources().get(sourceId);
        return provider != null && provider.isActive();
    }
    public Set<GroupRef> searchGroups(String sourceId, String filter) {
        Set<GroupRef> result = new LinkedHashSet<>();
        dsl().fetch("select distinct source_id,group_id from user_source_groups where (cast(? as text) is null or source_id=?) and (cast(? as text) is null or group_id ilike ?) order by source_id,group_id limit 10", sourceId,sourceId,filter,filter == null ? null : "%" + filter + "%")
                .forEach(r -> result.add(new GroupRef(r.get(0,String.class),r.get(1,String.class))));
        return result;
    }

    private Refresh begin(UUID userId, String source) {
        return txResult(tx -> {
            user(tx,userId);
            Record r = tx.fetchOne("select b.binding_id,b.refresh_version,b.identity_id,i.provider_id,i.authority,i.subject,s.verified_attributes::text as attrs,s.attributes_observed_at,s.attributes_expire_at from user_authorization_bindings b left join user_identities i on i.identity_id=b.identity_id and i.user_id=b.user_id and i.is_active left join user_authorization_state s on s.binding_id=b.binding_id where b.user_id=? and b.source_id=? for update of b", userId,source);
            if (r == null) throw new NoSuchElementException("Authorization binding not found");
            UUID binding = r.get("binding_id",UUID.class);
            long version = r.get("refresh_version",Long.class) + 1;
            tx.execute("update user_authorization_bindings set refresh_version=? where binding_id=?",version,binding);
            tx.execute("update user_authorization_state set status='STALE' where binding_id=?", binding);
            ExternalIdentityKey identity = r.get("provider_id",String.class) == null ? null : new ExternalIdentityKey(r.get("provider_id",String.class),r.get("authority",String.class),r.get("subject",String.class));
            Map<String,Object> attrs = Map.of();
            RuntimeException error = null;
            try { if (r.get("attrs",String.class) != null) attrs = mapper.readValue(r.get("attrs",String.class),new TypeReference<Map<String,Object>>() {}); }
            catch (Exception e) { error = new AuthorizationUnavailableException("Invalid retained attributes",e); }
            if (r.get("identity_id",UUID.class) != null && identity == null) error = new AuthorizationUnavailableException("Identity is no longer active");
            return new Refresh(binding,version,source,identity,attrs,instant(r,"attributes_observed_at"),instant(r,"attributes_expire_at"),error);
        });
    }

    public void refresh(UUID userId, String sourceId, boolean force) {
        Refresh refresh = begin(userId,sourceId);
        AuthorizationProvider provider = registry.authorizationSources().get(sourceId);
        AuthorizationData data = null;
        RuntimeException failure = null;
        Instant now = Instant.now();
        try {
            if (refresh.error() != null) throw refresh.error();
            if (provider == null || !provider.isActive() || !provider.acceptsIdentity(refresh.identity())) throw new AuthorizationUnavailableException("Authorization source is unavailable: " + sourceId);
            data = Objects.requireNonNull(provider.resolve(new AuthorizationRequest(userId,refresh.identity(),refresh.attributes(),refresh.attributesObservedAt(),refresh.attributesExpireAt())), "Null authorization result");
            Instant deadline = now.plus(maxAge(provider));
            if (data.validUntil() != null && data.validUntil().isBefore(deadline)) deadline = data.validUntil();
            if (refresh.attributesExpireAt() != null && refresh.attributesExpireAt().isBefore(deadline)) deadline = refresh.attributesExpireAt();
            if (!deadline.isAfter(now)) throw new AuthorizationUnavailableException("Expired authorization observation");
            data = new AuthorizationData(data.groups(),data.roles(),data.teams(),deadline);
        } catch (AuthorizationSubjectNotFoundException e) { failure = e; }
        catch (RuntimeException e) { failure = e instanceof AuthorizationUnavailableException ? e : new AuthorizationUnavailableException("Invalid or unavailable authorization result",e); }
        final AuthorizationData result = data;
        final RuntimeException error = failure;
        Publication publication = txResult(tx -> publish(tx,userId,refresh,result,error,now));
        if (publication.published() && publication.error() != null && (force || requiredBinding(userId,sourceId))) throw publication.error();
    }

    private boolean requiredBinding(UUID userId, String source) {
        Record r = dsl().fetchOne("select is_required from user_authorization_bindings where user_id=? and source_id=?",userId,source);
        return r != null && Boolean.TRUE.equals(r.get(0,Boolean.class));
    }

    private Publication publish(DSLContext tx, UUID userId, Refresh refresh, AuthorizationData data, RuntimeException failure, Instant now) {
        Record binding = tx.fetchOne("select b.refresh_version,b.identity_id,i.identity_id as active_identity from user_authorization_bindings b left join user_identities i on i.identity_id=b.identity_id and i.user_id=b.user_id and i.is_active where b.binding_id=? and b.user_id=? and b.source_id=? for update of b",refresh.bindingId(),userId,refresh.sourceId());
        if (binding == null || binding.get("refresh_version",Long.class) != refresh.version()) return new Publication(false, null);
        if (binding.get("identity_id",UUID.class) != null && binding.get("active_identity",UUID.class) == null) failure = new AuthorizationUnavailableException("Identity is no longer active");
        if (failure == null) {
            for (String role : data.roles()) if (tx.fetchOne("select role_id from roles where role_name=?",role) == null) { failure = new AuthorizationUnavailableException("Unknown source role: " + role); break; }
            Set<UUID> teamIds = new HashSet<>();
            for (TeamGrant team : data.teams()) if (team == null || !Set.of("OWNER","MAINTAINER","MEMBER").contains(team.role()) || !teamIds.add(team.teamId()) || tx.fetchOne("select team_id from teams where team_id=?",team.teamId()) == null) { failure = new AuthorizationUnavailableException("Invalid source team"); break; }
            for (String group : data.groups()) if (group == null || group.isBlank()) { failure = new AuthorizationUnavailableException("Invalid source group"); break; }
        }
        String status = failure == null ? "VALID" : failure instanceof AuthorizationSubjectNotFoundException ? "NOT_FOUND" : "UNAVAILABLE";
        tx.execute("insert into user_authorization_state(user_id,source_id,binding_id,status,observed_at,valid_until) values (?,?,?,?,cast(? as timestamptz),cast(? as timestamptz)) on conflict(user_id,source_id) do update set binding_id=excluded.binding_id,status=excluded.status,observed_at=excluded.observed_at,valid_until=excluded.valid_until",userId,refresh.sourceId(),refresh.bindingId(),status,time(now),failure == null ? time(data.validUntil()) : null);
        if (failure == null || failure instanceof AuthorizationSubjectNotFoundException) {
            tx.execute("delete from user_source_groups where user_id=? and source_id=?",userId,refresh.sourceId());
            tx.execute("delete from user_source_roles where user_id=? and source_id=?",userId,refresh.sourceId());
            tx.execute("delete from user_source_teams where user_id=? and source_id=?",userId,refresh.sourceId());
            if (failure == null) {
                for (String group : data.groups()) tx.execute("insert into user_source_groups(user_id,source_id,group_id) values (?,?,?)",userId,refresh.sourceId(),group);
                for (String role : data.roles()) tx.execute("insert into user_source_roles(user_id,source_id,role_id) select ?,?,role_id from roles where role_name=?",userId,refresh.sourceId(),role);
                for (TeamGrant team : data.teams()) tx.execute("insert into user_source_teams(user_id,source_id,team_id,team_role) values (?,?,?,?)",userId,refresh.sourceId(),team.teamId(),team.role());
            }
        }
        return new Publication(true, failure);
    }

    public EffectiveAuthorization prepare(UUID userId) { return prepare(userId,false); }
    public EffectiveAuthorization prepare(UUID userId, boolean newAuthentication) {
        user(dsl(),userId);
        for (Binding b : listBindings(userId)) {
            AuthorizationProvider p = registry.authorizationSources().get(b.sourceId());
            boolean expired = b.validUntil() == null || !b.validUntil().isAfter(Instant.now());
            boolean loginOld = newAuthentication && p != null && (b.observedAt() == null || !b.observedAt().plus(p.defaultLoginMaxAge()).isAfter(Instant.now()));
            if (p == null || !p.isActive() || !"VALID".equals(b.status()) || expired || loginOld) refresh(userId,b.sourceId(),false);
            if (b.required()) {
                Binding current = listBindings(userId).stream().filter(x -> x.sourceId().equals(b.sourceId())).findFirst().orElseThrow(() -> new AuthorizationUnavailableException("Binding removed"));
                if ("NOT_FOUND".equals(current.status())) throw new AuthorizationSubjectNotFoundException("Authorization subject not found");
                if (!"VALID".equals(current.status()) || current.validUntil() == null || !current.validUntil().isAfter(Instant.now())) throw new AuthorizationUnavailableException("Authorization source unavailable");
            }
        }
        Set<String> roles = new HashSet<>(dsl().fetch("select distinct r.role_name from v_user_roles v join roles r on r.role_id=v.role_id where v.user_id=?",userId).getValues(0,String.class));
        Set<String> permissions = new HashSet<>(dsl().fetch("select distinct p.permission_name from v_user_roles v join role_permissions rp on rp.role_id=v.role_id join permissions p on p.permission_id=rp.permission_id where v.user_id=?",userId).getValues(0,String.class));
        return new EffectiveAuthorization(Set.copyOf(roles),Set.copyOf(permissions));
    }
    public Set<GroupRef> getGroups(UUID userId) {
        prepare(userId);
        Set<GroupRef> groups = new HashSet<>();
        dsl().fetch("select g.source_id,g.group_id from user_source_groups g join user_authorization_bindings b on b.user_id=g.user_id and b.source_id=g.source_id join authorization_sources a on a.source_id=b.source_id and a.is_active join user_authorization_state s on s.binding_id=b.binding_id and s.status='VALID' and s.valid_until>current_timestamp left join user_identities i on i.identity_id=b.identity_id and i.user_id=b.user_id and i.is_active where g.user_id=? and (b.identity_id is null or i.identity_id is not null)",userId).forEach(r -> groups.add(new GroupRef(r.get(0,String.class),r.get(1,String.class))));
        return Set.copyOf(groups);
    }
}
