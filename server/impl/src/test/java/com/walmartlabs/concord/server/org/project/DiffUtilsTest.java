package com.walmartlabs.concord.server.org.project;

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

import com.walmartlabs.concord.server.org.OrganizationEntry;
import com.walmartlabs.concord.server.user.RoleEntry;
import com.walmartlabs.concord.server.user.UserEntry;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

public class DiffUtilsTest {

    @Test
    public void addTest() {
        UUID personId = UUID.randomUUID();

        Person left = PersonBuilder.Person().build();

        Person right = PersonBuilder.Person()
                .withId(personId)
                .withName("Bravo")
                .build();

        Map<String, Object> result = DiffUtils.compare(left, right);


        assertNull(getProperty(result, "prev"));
        assertEquals(personId, getProperty(result, "new.id"));
        assertEquals("Bravo", getProperty(result, "new.name"));
    }

    @Test
    public void deleteTest() {
        UUID personId = UUID.randomUUID();

        Person left = PersonBuilder.Person()
                .withId(personId)
                .withName("Bravo")
                .build();
        Person right = PersonBuilder.Person().build();

        Map<String, Object> result = DiffUtils.compare(left, right);


        assertNull(getProperty(result, "new"));
        assertEquals(personId, getProperty(result, "prev.id"));
        assertEquals("Bravo", getProperty(result, "prev.name"));
    }

    @Test
    public void propertyUpdateTest() {
        UUID personId = UUID.randomUUID();

        Person left = PersonBuilder.Person()
                .withId(personId)
                .withName("Alpha")
                .build();

        Person right = PersonBuilder.Person()
                .withId(personId)
                .withName("Bravo")
                .build();

        Map<String, Object> result = DiffUtils.compare(left, right);


        assertNull(getProperty(result, "prev.id"));
        assertNull(getProperty(result, "new.id"));

        assertEquals("Alpha", getProperty(result, "prev.name"));
        assertEquals("Bravo", getProperty(result, "new.name"));
    }

    @Test
    public void propertyAddTest() {
        UUID personId = UUID.randomUUID();

        Person left = PersonBuilder.Person()
                .withId(personId)
                .build();

        Person right = PersonBuilder.Person()
                .withId(personId)
                .withName("Bravo")
                .build();

        Map<String, Object> result = DiffUtils.compare(left, right);


        assertNull(getProperty(result, "prev.id"));
        assertNull(getProperty(result, "new.id"));

        assertNull(getProperty(result, "prev.name"));
        assertEquals("Bravo", getProperty(result, "new.name"));
    }

    @Test
    public void propertyIdUpdateForVOTest() {
        UUID personId = UUID.randomUUID();
        UUID personId2 = UUID.randomUUID();

        Person left = PersonBuilder.Person()
                .withId(personId)
                .withName("Bravo")
                .build();

        Person right = PersonBuilder.Person()
                .withId(personId2)
                .withName("Bravo")
                .build();

        Map<String, Object> result = DiffUtils.compare(left, right);


        assertEquals(personId, getProperty(result, "prev.id"));
        assertEquals(personId2, getProperty(result, "new.id"));

        assertNull(getProperty(result, "prev.name"));
        assertNull(getProperty(result, "new.name"));
    }

    @Test
    public void propertyDeleteTest() {
        UUID personId = UUID.randomUUID();

        Person left = PersonBuilder.Person()
                .withId(personId)
                .withName("Alpha")
                .build();

        Person right = PersonBuilder.Person()
                .withId(personId)
                .build();

        Map<String, Object> result = DiffUtils.compare(left, right);


        assertNull(getProperty(result, "prev.id"));
        assertNull(getProperty(result, "new.id"));

        assertEquals("Alpha", getProperty(result, "prev.name"));
        assertNull(getProperty(result, "new.name"));
    }

    @Test
    public void mapUpdateEntryTest() {
        UUID personId = UUID.randomUUID();
        UUID carId = UUID.randomUUID();

        Person left = PersonBuilder.Person()
                .withId(personId)
                .withName("Alpha Bravo")
                .addCar(carId, "Toyota")
                .build();

        Person right = PersonBuilder.Person()
                .withId(personId)
                .withName("Alpha Bravo")
                .addCar(carId, "Honda")
                .build();

        Map<String, Object> result = DiffUtils.compare(left, right);


        assertEquals("Toyota", getProperty(result, "prev.cars." + carId + ".make"));
        assertEquals("Honda", getProperty(result, "new.cars." + carId + ".make"));

        assertNull(getProperty(result, "prev.cars." + carId + ".id"));
        assertNull(getProperty(result, "new.cars." + carId + ".id"));

        assertNull(getProperty(result, "prev.id"));
        assertNull(getProperty(result, "prev.name"));

        assertNull(getProperty(result, "new.id"));
        assertNull(getProperty(result, "new.name"));
    }

    @Test
    public void mapAddEntryTest() {
        UUID personId = UUID.randomUUID();
        UUID carId1 = UUID.randomUUID();
        UUID carId2 = UUID.randomUUID();

        Person left = PersonBuilder.Person()
                .withId(personId)
                .withName("Alpha Bravo")
                .addCar(carId1, "Toyota")
                .build();

        Person right = PersonBuilder.Person()
                .withId(personId)
                .withName("Alpha Bravo")
                .addCar(carId1, "Toyota")
                .addCar(carId2, "Honda")
                .build();

        Map<String, Object> result = DiffUtils.compare(left, right);

        assertNull(getProperty(result, "prev.id"));
        assertNull(getProperty(result, "prev.name"));

        assertNull(getProperty(result, "new.id"));
        assertNull(getProperty(result, "new.name"));

        assertEquals(carId2, getProperty(result, "new.cars." + carId2 + ".id"));
        assertNull(getProperty(result, "prev.cars"));
    }

    @Test
    public void mapAddFirstEntryTest() {
        UUID personId = UUID.randomUUID();
        UUID carId1 = UUID.randomUUID();
        UUID carId2 = UUID.randomUUID();

        Person left = PersonBuilder.Person()
                .withId(personId)
                .withName("Alpha Bravo")
                .build();

        Person right = PersonBuilder.Person()
                .withId(personId)
                .withName("Alpha Bravo")
                .addCar(carId1, "Toyota")
                .addCar(carId2, "Honda")
                .build();

        Map<String, Object> result = DiffUtils.compare(left, right);

        assertNull(getProperty(result, "prev.id"));
        assertNull(getProperty(result, "prev.name"));

        assertNull(getProperty(result, "new.id"));
        assertNull(getProperty(result, "new.name"));

        assertEquals(carId1, getProperty(result, "new.cars." + carId1 + ".id"));
        assertEquals(carId2, getProperty(result, "new.cars." + carId2 + ".id"));
        assertNull(getProperty(result, "prev.cars"));
    }

    @Test
    public void mapDeleteEntryTest() {
        UUID personId = UUID.randomUUID();
        UUID carId1 = UUID.randomUUID();
        UUID carId2 = UUID.randomUUID();

        Person left = PersonBuilder.Person()
                .withId(personId)
                .withName("Alpha Bravo")
                .addCar(carId1, "Toyota")
                .addCar(carId2, "Honda")
                .build();

        Person right = PersonBuilder.Person()
                .withId(personId)
                .withName("Alpha Bravo")
                .addCar(carId1, "Toyota")
                .build();

        Map<String, Object> result = DiffUtils.compare(left, right);

        assertNull(getProperty(result, "prev.id"));
        assertNull(getProperty(result, "prev.name"));

        assertNull(getProperty(result, "new.id"));
        assertNull(getProperty(result, "new.name"));

        assertEquals(carId2, getProperty(result, "prev.cars." + carId2 + ".id"));
        assertNull(getProperty(result, "new.cars"));
    }

    @Test
    public void recordChangesKeepOriginalValueTypesAndOmitNulls() {
        UUID id = UUID.randomUUID();
        AuditRecord left = new AuditRecord(id, "before", null);
        AuditRecord right = new AuditRecord(id, null, "added");

        Map<String, Object> result = DiffUtils.compare(left, right);

        assertEquals(Map.of("name", "before"), result.get("prev"));
        assertEquals(Map.of("description", "added"), result.get("new"));
    }

    @Test
    public void scalarContainerChangesAreNotReported() {
        ContainerRecord left = new ContainerRecord(Map.of("a", "one"), List.of("one"), Set.of("one"), new int[]{1});
        ContainerRecord right = new ContainerRecord(Map.of("a", "two"), List.of("two"), Set.of("two"), new int[]{2});

        assertEquals(Map.of(), DiffUtils.compare(left, right));
    }

    @Test
    public void nullRootReportsTypedScalarChanges() {
        UUID id = UUID.randomUUID();
        AuditRecord entry = new AuditRecord(id, "name", null);

        assertEquals(id, getProperty(DiffUtils.compare(null, entry), "new.id"));
        assertEquals(id, getProperty(DiffUtils.compare(entry, null), "prev.id"));
    }

    @Test
    public void nullRootOmitsScalarMapEntries() {
        OrganizationEntry entry = new OrganizationEntry("org-one", Map.of("scalar", "before"));

        assertEquals(Map.of("new", Map.of("name", "org-one")), DiffUtils.compare(null, entry));
        assertEquals(Map.of("prev", Map.of("name", "org-one")), DiffUtils.compare(entry, null));
    }

    @Test
    public void nullRootReportsOffsetDateTimeWithoutSnapshotConversion() {
        OffsetDateTime time = OffsetDateTime.parse("2024-01-02T03:04:05Z");
        TimedRecord entry = new TimedRecord(time);

        assertEquals(Map.of("new", Map.of("time", time)), DiffUtils.compare(null, entry));
        assertEquals(Map.of("prev", Map.of("time", time)), DiffUtils.compare(entry, null));
    }

    @Test
    public void nestedObjectCreationAndRemovalReportOnlyValueChanges() {
        NestedRecord child = new NestedRecord("child", Map.of("scalar", "value"));
        NestedContainer empty = new NestedContainer(null);
        NestedContainer populated = new NestedContainer(child);
        Map<String, Object> changes = Map.of("child", Map.of("name", "child"));

        assertEquals(Map.of("new", changes), DiffUtils.compare(empty, populated));
        assertEquals(Map.of("prev", changes), DiffUtils.compare(populated, empty));
    }

    @Test
    public void nestedContainerAdditionsAndRemovalsOmitDefaultsAndScalarContainers() {
        NestedValues defaultChild = new NestedValues("default", false, 0, List.of("ignored"), Map.of("key", "ignored"));
        NestedValues changedChild = new NestedValues("changed", true, 7, List.of("ignored"), Map.of("key", "ignored"));
        NestedValuesContainer empty = new NestedValuesContainer(null, null, null);
        NestedValuesContainer populated = new NestedValuesContainer(
                Map.of("one", defaultChild), List.of(changedChild), Set.of(defaultChild));
        String setKey = AuditObjectHash.hash(defaultChild);
        Map<String, Object> changes = Map.of(
                "map", Map.of("one", Map.of("name", "default")),
                "list", Map.of("0", Map.of("name", "changed", "enabled", true, "count", 7)),
                "set", Map.of(setKey, Map.of("name", "default")));

        assertEquals(Map.of("new", changes), DiffUtils.compare(empty, populated));
        assertEquals(Map.of("prev", changes), DiffUtils.compare(populated, empty));
    }

    @Test
    public void projectCreationAndRemovalOmitRepositoryDefaults() {
        ProjectEntry entry = new ProjectEntry("project", Map.of("repo",
                new RepositoryEntry("repo", "https://example.com/old.git")));
        Map<String, Object> changes = Map.of(
                "name", "project",
                "repositories", Map.of("repo", Map.of("name", "repo", "url", "https://example.com/old.git")),
                "rawPayloadMode", entry.getRawPayloadMode(),
                "outVariablesMode", entry.getOutVariablesMode(),
                "processExecMode", entry.getProcessExecMode());

        assertEquals(Map.of("new", changes), DiffUtils.compare(null, entry));
        assertEquals(Map.of("prev", changes), DiffUtils.compare(entry, null));
    }

    private record NestedValues(String name, boolean enabled, int count, List<String> permissions,
                                Map<String, String> meta) {
    }

    private record NestedValuesContainer(Map<String, NestedValues> map, List<NestedValues> list,
                                         Set<NestedValues> set) {
    }

    private record TimedRecord(OffsetDateTime time) {
    }

    private record NestedContainer(NestedRecord child) {
    }

    private record NestedRecord(String name, Map<String, String> meta) {
    }

    private record AuditRecord(UUID id, String name, String description) {
    }

    private record ContainerRecord(Map<String, String> map, List<String> list, Set<String> set, int[] array) {
    }

    @Test
    public void roleSetChangesUseSnapshotIdentityAndOmitPermissions() {
        UUID id = UUID.fromString("12345678-1234-1234-9234-123456789abc");
        RoleEntry before = new RoleEntry(id, "role", Set.of("read"));
        RoleEntry after = new RoleEntry(id, "role", Set.of("write"));
        UserEntry left = new UserEntry(id, "user", "domain", "Display", Set.of(), null,
                "user@example.com", Set.of(before), false, null, false);
        UserEntry right = new UserEntry(id, "user", "domain", "Display", Set.of(), null,
                "user@example.com", Set.of(after), false, null, false);
        Map<String, Object> role = Map.of("name", "role", "id", id);

        assertEquals(Map.of(
                "prev", Map.of("roles", Map.of("4224af9a2f7d9827fe99bdc371a63fb5", role)),
                "new", Map.of("roles", Map.of("4818fe2607a42dba94f7af81105b818d", role))),
                DiffUtils.compare(left, right));
        assertEquals(Map.of(
                "prev", Map.of("roles", Map.of("4818fe2607a42dba94f7af81105b818d", role)),
                "new", Map.of("roles", Map.of("4224af9a2f7d9827fe99bdc371a63fb5", role))),
                DiffUtils.compare(right, left));
    }

    @Test
    public void listValueObjectChangesUseIndexPaths() {
        MemberList left = new MemberList(List.of(new Member(1, "before")));
        MemberList right = new MemberList(List.of(new Member(1, "after")));

        Map<String, Object> result = DiffUtils.compare(left, right);

        assertEquals("before", getProperty(result, "prev.members.0.name"));
        assertEquals("after", getProperty(result, "new.members.0.name"));
    }

    private record MemberList(List<Member> members) {
    }

    private record Member(int id, String name) {
        @Override
        public int hashCode() {
            return id;
        }
    }

    @SuppressWarnings("unchecked")
    private Object getProperty(Map<String, Object> map, String dottedPath) {
        String[] path = dottedPath.split("\\.");
        Object currentObject = map;
        for (String p : path) {
            currentObject = ((Map<String, Object>) currentObject).get(p);
            if (currentObject == null) {
                return null;
            }
        }
        return currentObject;
    }

    private static class Car {
        UUID id;
        String make;

        Car(UUID id, String make) {
            this.id = id;
            this.make = make;
        }

        public UUID getId() {
            return id;
        }

        public String getName() {
            return make;
        }

    }

    private static class Person {
        UUID id;
        String name;
        Map<String, Car> cars;

        Person(UUID id, String name, Map<String, Car> cars) {
            this.id = id;
            this.name = name;
            this.cars = cars;
        }

        public UUID getId() {
            return id;
        }

        public String getName() {
            return name;
        }
    }


    private static class PersonBuilder {
        UUID id;
        String name;
        Map<String, Car> cars;

        private PersonBuilder() {

        }

        static PersonBuilder Person() {
            return new PersonBuilder();
        }

        public PersonBuilder withId(UUID id) {
            this.id = id;
            return this;
        }

        public PersonBuilder withName(String name) {
            this.name = name;
            return this;
        }

        public PersonBuilder addCar(UUID id, String make) {
            if (Objects.isNull(cars)) {
                this.cars = new HashMap<>();
            }
            this.cars.put(id.toString(), new Car(id, make));
            return this;
        }

        public Person build() {
            return new Person(id, name, cars);
        }

    }
}
