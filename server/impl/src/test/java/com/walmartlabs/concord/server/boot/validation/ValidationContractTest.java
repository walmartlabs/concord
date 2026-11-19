package com.walmartlabs.concord.server.boot.validation;

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

import com.google.inject.Guice;
import com.walmartlabs.concord.common.validation.ConcordId;
import com.walmartlabs.concord.server.console.ProcessCardEntry;
import com.walmartlabs.concord.server.org.project.ProjectEntry;
import com.walmartlabs.concord.server.org.project.RepositoryEntry;
import com.walmartlabs.concord.server.sdk.validation.Validate;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.NotNull;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

@ResourceLock(Resources.LOCALE)
class ValidationContractTest {

    private Locale previousLocale;
    private ValidatorFactory factory;
    private Validator validator;
    private ValidatedService service;

    @BeforeEach
    void setUp() {
        previousLocale = Locale.getDefault();
        Locale.setDefault(Locale.ENGLISH);
        var injector = Guice.createInjector(new ValidationModule());
        factory = injector.getInstance(ValidatorFactory.class);
        validator = injector.getInstance(Validator.class);
        service = injector.getInstance(ValidatedService.class);
    }

    @AfterEach
    void tearDown() {
        try {
            if (factory != null) {
                factory.close();
            }
        } finally {
            Locale.setDefault(previousLocale);
        }
    }

    @Test
    void preservesRepositorySizeAndNullBoundaries() {
        for (var url : new String[]{"", "https://example.org/repo.git", "x".repeat(2048)}) {
            assertTrue(validator.validate(new RepositoryEntry("repo", url)).isEmpty());
        }
        assertEquals(Map.of("url", "size must be between 0 and 2048"),
                errors(validator.validate(new RepositoryEntry("repo", "x".repeat(2049)))));
        assertEquals(Map.of("url", "must not be null"),
                errors(validator.validate(new RepositoryEntry("repo", (String) null))));
    }

    @Test
    void validatesComposedKeysAndRepositoryPatterns() {
        for (var name : new String[]{"abc", "a".repeat(128)}) {
            assertTrue(validator.validate(new RepositoryEntry(name, "url")).isEmpty());
        }
        for (var name : new String[]{"", "ab", "@repo", "a".repeat(129)}) {
            // ConcordKey is composed without @ReportAsSingleViolation: the nested Pattern message is exposed.
            assertEquals(Map.of("name", "must match \"^[0-9a-zA-Z][0-9a-zA-Z_@.\\-~]{2,127}$\""),
                    errors(validator.validate(new RepositoryEntry(name, "url"))));
        }
        var valid = new RepositoryEntry(new RepositoryEntry("repo", "url"), "feature/test", "a01f");
        assertTrue(validator.validate(valid).isEmpty());
        var invalid = new RepositoryEntry(valid, "bad branch", "xyz");
        assertEquals(Map.of(
                "branch", "Branch must be a valid git ref name (no whitespace or special characters ~^:?*[\\)",
                "commitId", "Commit ID must be a hexadecimal SHA value"), errors(validator.validate(invalid)));
    }

    @Test
    void cascadesIntoProjectRepositoryMap() {
        var invalid = new ProjectEntry("project", Map.of("repo", new RepositoryEntry("repo", (String) null)));
        assertEquals(Set.of("repositories[repo].url"), paths(validator.validate(invalid)));
        var valid = new ProjectEntry("project", Map.of("repo", new RepositoryEntry("repo", "url")));
        assertTrue(validator.validate(valid).isEmpty());
    }

    @Test
    void recognizesConstraintsOnRealFluentGetters() {
        var valid = ProcessCardEntry.builder().id(UUID.randomUUID()).name("a".repeat(128)).isCustomForm(false).build();
        assertTrue(validator.validate(valid).isEmpty());
        var invalid = ProcessCardEntry.builder().from(valid).name("a".repeat(129)).build();
        assertEquals(Map.of("name", "size must be between 0 and 128"), errors(validator.validate(invalid)));
        assertEquals(Set.of("name"), paths(validator.validateProperty(invalid, "name")));
    }

    @Test
    void keepsBeanGettersAndIgnoresMethodsWithArguments() {
        assertEquals(Set.of("description"), paths(validator.validate(new BeanGetters())));
    }

    @Test
    void rejectsInvalidArgumentsBeforeCallingTheMethod() {
        var repository = new RepositoryEntry("repo", "url");
        var invalidId = assertThrows(ConstraintViolationException.class,
                () -> service.accept("not-a-uuid", repository));
        assertEquals(Set.of("accept.arg0"), paths(invalidId.getConstraintViolations()));
        var invalidDto = assertThrows(ConstraintViolationException.class,
                () -> service.accept(UUID.randomUUID().toString(), new RepositoryEntry("repo", (String) null)));
        assertEquals(Set.of("accept.arg1.url"), paths(invalidDto.getConstraintViolations()));
        assertEquals(0, service.calls);

        service.accept(UUID.randomUUID().toString(), repository);
        assertEquals(1, service.calls);
    }

    @Test
    void honorsExplicitGroupsForArgumentsAndReturnValues() {
        assertThrows(ConstraintViolationException.class, () -> service.grouped(null, "default", false));
        assertEquals(0, service.calls);
        // The default-group constraint on the second argument must not run for an explicit group.
        assertEquals("value", service.grouped("value", null, false));
        assertEquals(1, service.calls);

        var invalidResult = assertThrows(ConstraintViolationException.class,
                () -> service.grouped("value", null, true));
        assertEquals(Set.of("grouped.<return value>"), paths(invalidResult.getConstraintViolations()));
        assertEquals(2, service.calls, "Return values are validated after the method executes");
    }

    private static Map<String, String> errors(Set<? extends ConstraintViolation<?>> violations) {
        return violations.stream().collect(Collectors.toMap(v -> v.getPropertyPath().toString(), ConstraintViolation::getMessage));
    }

    private static Set<String> paths(Set<? extends ConstraintViolation<?>> violations) {
        return violations.stream().map(v -> v.getPropertyPath().toString()).collect(Collectors.toSet());
    }

    public static class BeanGetters {
        @NotNull
        public String getDescription() {
            return null;
        }

        @NotNull
        public String ignored(String argument) {
            return null;
        }
    }

    public interface ExplicitGroup {
    }

    public static class ValidatedService {
        private int calls;

        @Validate
        public void accept(@ConcordId String id, @Valid RepositoryEntry repository) {
            calls++;
        }

        @Validate(groups = ExplicitGroup.class)
        @NotNull(groups = ExplicitGroup.class)
        public String grouped(@NotNull(groups = ExplicitGroup.class) String value,
                              @NotNull String defaultOnly, boolean invalidResult) {
            calls++;
            return invalidResult ? null : value;
        }
    }
}
