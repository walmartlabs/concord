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

import org.javers.core.metamodel.annotation.Id;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
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
    public void propertyIdUpdateForEntityTest() {
        UUID personId = UUID.randomUUID();
        UUID personId2 = UUID.randomUUID();

        PersonEntity left = PersonBuilder.Person()
                .withId(personId)
                .withName("Bravo")
                .buildEntity();

        PersonEntity right = PersonBuilder.Person()
                .withId(personId2)
                .withName("Bravo")
                .buildEntity();

        Map<String, Object> result = DiffUtils.compare(left, right);


        assertEquals(personId, getProperty(result, "prev.id"));
        assertEquals(personId2, getProperty(result, "new.id"));

        assertEquals("Bravo", getProperty(result, "prev.name"));
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

    private static class PersonEntity {
        @Id
        UUID id;
        String name;
        Map<String, Car> cars;

        PersonEntity(UUID id, String name, Map<String, Car> cars) {
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

        public PersonEntity buildEntity() {
            return new PersonEntity(id, name, cars);
        }
    }
}
