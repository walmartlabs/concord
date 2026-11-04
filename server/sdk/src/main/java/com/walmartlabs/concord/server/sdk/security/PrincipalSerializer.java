package com.walmartlabs.concord.server.sdk.security;

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

/**
 * Serializes one concrete principal class into a stable byte representation.
 *
 * <p>{@link #principalType()} must return the exact runtime class handled by this serializer - not a
 * superclass or an interface. Collections are dispatched by {@code principal.getClass()}, so a serializer
 * registered for a base class does not cover subclasses: a plugin using its own principal class (even a
 * subclass of a built-in one) must register a serializer for that exact class.
 *
 * <p>{@link #type()} is a stable, globally unique identifier persisted in principal snapshots such as
 * process state and "remember me" cookies. Never rename an existing identifier: data written under the
 * old name becomes unreadable. New serializers should use plugin-qualified identifiers, e.g.
 * {@code com.example.plugin.custom-user}, to avoid collisions with built-in and other plugins' types.
 *
 * <p>{@link #serialize(Object)} must never return {@code null}; collection serialization fails on invalid
 * codec results instead of silently dropping principals. {@link #deserialize(byte[])} must return a non-null
 * instance of the declared concrete class or throw - never {@code null} or a substitute value.
 *
 * <p>Implementations must treat the format as a compatibility contract: deserializers should continue to read
 * previously written data, fields should not be removed, renamed, reordered in ordered encodings, or change meaning
 * without a migration path, and newly added fields should be optional or have safe defaults.
 *
 * <p>Implementations are not discovered automatically - they must be registered explicitly with a Guice
 * multibinder:
 * <pre>{@code
 * Multibinder.newSetBinder(binder, new TypeLiteral<PrincipalSerializer<?>>() {})
 *         .addBinding().to(MyPrincipalSerializer.class);
 * }</pre>
 * The explicit {@code TypeLiteral} is required because this interface is generic: a plain
 * {@code newSetBinder(binder, PrincipalSerializer.class)} would create a separate, incompatible binder and
 * the serializer would never reach the principal collection codec.
 */
public interface PrincipalSerializer<T> {

    Class<T> principalType();

    String type();

    byte[] serialize(T principal);

    T deserialize(byte[] data) throws Exception;
}
