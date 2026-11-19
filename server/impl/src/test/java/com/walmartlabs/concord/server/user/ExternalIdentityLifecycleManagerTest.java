package com.walmartlabs.concord.server.user;

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

import com.walmartlabs.concord.server.cfg.ExternalIdentityConfiguration;
import com.walmartlabs.concord.server.sdk.identity.ExternalIdentityException;
import com.walmartlabs.concord.server.sdk.identity.ExternalIdentityRef;
import com.walmartlabs.concord.server.sdk.identity.ExternalIdentitySnapshot.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExternalIdentityLifecycleManagerTest {

    private static final ExternalIdentityRef IDENTITY = new ExternalIdentityRef("provider", "authority", "subject");
    private static final UUID USER_ID = UUID.randomUUID();
    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-11-19T12:00:00Z");
    private static final Duration MAX_AGE = Duration.ofMinutes(15);

    private final ExternalIdentityDao dao = mock(ExternalIdentityDao.class);
    private final ExternalIdentitySynchronizer synchronizer = mock(ExternalIdentitySynchronizer.class);
    private ExternalIdentityLifecycleManager manager;

    @BeforeEach
    void setUp() {
        manager = manager(MAX_AGE);
    }

    @Test
    void snapshotAgeMustBePositive() {
        for (var age : new Duration[]{null, Duration.ZERO, Duration.ofSeconds(-1)}) {
            assertThrows(IllegalArgumentException.class, () -> manager(age));
        }
    }

    @Test
    void freshActiveSnapshotDoesNotFetchProvider() throws Exception {
        for (var age : new Duration[]{Duration.ZERO, MAX_AGE.minusNanos(1)}) {
            when(dao.getLifecycleState(IDENTITY)).thenReturn(Optional.of(state(Status.ACTIVE, NOW.minus(age))));
            assertEquals(USER_ID, manager.requireActive(IDENTITY));
        }
        verify(synchronizer, times(2)).assertProviderRegistered(IDENTITY);
        verify(synchronizer, never()).refresh(any());
    }

    @Test
    void exactAgeLimitAndOlderSnapshotsRefresh() throws Exception {
        for (var age : new Duration[]{MAX_AGE, MAX_AGE.plusNanos(1), Duration.ofDays(1)}) {
            when(dao.getLifecycleState(IDENTITY)).thenReturn(
                    Optional.of(state(Status.ACTIVE, NOW.minus(age))),
                    Optional.of(state(Status.ACTIVE, NOW)));
            assertEquals(USER_ID, manager.requireActive(IDENTITY));
        }
        verify(synchronizer, times(3)).refresh(IDENTITY);
    }

    @Test
    void configuredAgeIsUsedInsteadOfDefault() throws Exception {
        manager = manager(Duration.ofMinutes(1));
        when(dao.getLifecycleState(IDENTITY)).thenReturn(
                Optional.of(state(Status.ACTIVE, NOW.minusMinutes(1))),
                Optional.of(state(Status.ACTIVE, NOW)));
        assertEquals(USER_ID, manager.requireActive(IDENTITY));
        verify(synchronizer).refresh(IDENTITY);
    }

    @Test
    void missingMetadataAndFutureTimestampRequireRefresh() throws Exception {
        for (var stale : new ExternalIdentityDao.LifecycleState[]{
                state(null, null), state(Status.ACTIVE, null), state(null, NOW), state(Status.ACTIVE, NOW.plusNanos(1))}) {
            when(dao.getLifecycleState(IDENTITY)).thenReturn(Optional.of(stale), Optional.of(state(Status.ACTIVE, NOW)));
            assertEquals(USER_ID, manager.requireActive(IDENTITY));
        }
        verify(synchronizer, times(4)).refresh(IDENTITY);
    }

    @Test
    void nonActiveFreshSourceIsDeniedWithoutFetch() throws Exception {
        for (var status : new Status[]{Status.INACTIVE, Status.NOT_FOUND}) {
            when(dao.getLifecycleState(IDENTITY)).thenReturn(Optional.of(state(status, NOW)));
            var error = assertThrows(ExternalIdentityException.class, () -> manager.requireActive(IDENTITY));
            assertTrue(error.getMessage().contains("source is not active"));
        }
        verify(synchronizer, never()).refresh(any());
    }

    @Test
    void staleInactiveSourceCanBeReactivatedOnlyBySuccessfulRefresh() throws Exception {
        when(dao.getLifecycleState(IDENTITY)).thenReturn(
                Optional.of(state(Status.INACTIVE, NOW.minus(MAX_AGE))), Optional.of(state(Status.ACTIVE, NOW)));
        assertEquals(USER_ID, manager.requireActive(IDENTITY));
        verify(synchronizer).refresh(IDENTITY);
    }

    @Test
    void refreshFailureIsPropagatedWithoutReusingOldState() throws Exception {
        when(dao.getLifecycleState(IDENTITY)).thenReturn(Optional.of(state(Status.ACTIVE, NOW.minus(MAX_AGE))));
        var failure = new ExternalIdentityException("Authority unavailable");
        doThrow(failure).when(synchronizer).refresh(IDENTITY);

        assertSame(failure, assertThrows(ExternalIdentityException.class, () -> manager.requireActive(IDENTITY)));
        verify(dao).getLifecycleState(IDENTITY);
    }

    @Test
    void refreshedStateMustStillBeFreshAndActive() throws Exception {
        var stale = state(Status.ACTIVE, NOW.minus(MAX_AGE));
        for (var result : new ExternalIdentityDao.LifecycleState[]{
                state(null, null), stale, state(Status.ACTIVE, NOW.plusNanos(1)),
                state(Status.INACTIVE, NOW), state(Status.NOT_FOUND, NOW)}) {
            when(dao.getLifecycleState(IDENTITY)).thenReturn(Optional.of(stale), Optional.of(result));
            assertThrows(ExternalIdentityException.class, () -> manager.requireActive(IDENTITY));
        }
        verify(synchronizer, times(5)).refresh(IDENTITY);
    }

    @Test
    void missingProviderDeniesEvenFreshCacheWithoutReadingAccount() throws Exception {
        var failure = new ExternalIdentityException("Provider removed");
        doThrow(failure).when(synchronizer).assertProviderRegistered(IDENTITY);
        assertSame(failure, assertThrows(ExternalIdentityException.class, () -> manager.requireActive(IDENTITY)));
        verifyNoInteractions(dao);
    }

    @Test
    void missingBindingWrongTypeAndEitherDisableFlagDenyBeforeRefresh() throws Exception {
        when(dao.getLifecycleState(IDENTITY)).thenReturn(Optional.empty());
        assertThrows(ExternalIdentityException.class, () -> manager.requireActive(IDENTITY));
        for (var invalid : new ExternalIdentityDao.LifecycleState[]{
                account(USER_ID, UserType.LDAP, false, false),
                account(USER_ID, UserType.LOCAL, false, false),
                account(USER_ID, UserType.SSO, false, false),
                account(USER_ID, UserType.EXTERNAL, true, false),
                account(USER_ID, UserType.EXTERNAL, false, true)}) {
            when(dao.getLifecycleState(IDENTITY)).thenReturn(Optional.of(invalid));
            assertThrows(ExternalIdentityException.class, () -> manager.requireActive(IDENTITY));
        }
        verify(synchronizer, never()).refresh(any());
    }

    @Test
    void accountAndBindingAreRecheckedAfterProviderReturns() throws Exception {
        var stale = state(Status.ACTIVE, NOW.minus(MAX_AGE));
        for (var result : new ExternalIdentityDao.LifecycleState[]{
                account(USER_ID, UserType.EXTERNAL, true, false),
                account(USER_ID, UserType.EXTERNAL, false, true),
                account(USER_ID, UserType.LOCAL, false, false),
                account(UUID.randomUUID(), UserType.EXTERNAL, false, false)}) {
            when(dao.getLifecycleState(IDENTITY)).thenReturn(Optional.of(stale), Optional.of(result));
            assertThrows(ExternalIdentityException.class, () -> manager.requireActive(IDENTITY));
        }
        when(dao.getLifecycleState(IDENTITY)).thenReturn(Optional.of(stale), Optional.empty());
        assertThrows(ExternalIdentityException.class, () -> manager.requireActive(IDENTITY));
        verify(synchronizer, times(5)).refresh(IDENTITY);
    }

    private ExternalIdentityLifecycleManager manager(Duration age) {
        var cfg = mock(ExternalIdentityConfiguration.class);
        when(cfg.getMaxSnapshotAge()).thenReturn(age);
        return new ExternalIdentityLifecycleManager(dao, synchronizer, cfg);
    }

    private ExternalIdentityDao.LifecycleState state(Status status, OffsetDateTime lastSuccessfulSync) {
        return new ExternalIdentityDao.LifecycleState(USER_ID, UserType.EXTERNAL, false, false,
                status, lastSuccessfulSync, NOW);
    }

    private ExternalIdentityDao.LifecycleState account(UUID id, UserType type, boolean disabled, boolean permanentlyDisabled) {
        return new ExternalIdentityDao.LifecycleState(id, type, disabled, permanentlyDisabled, Status.ACTIVE, NOW, NOW);
    }
}
