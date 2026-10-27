package com.walmartlabs.concord.common;

/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy at https://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 */

import com.walmartlabs.concord.common.email.EmailValidator;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EmailValidatorTest {
    @Test
    void localPartSyntaxAndLength() {
        assertFalse(EmailValidator.isValid(null));
        assertFalse(EmailValidator.isValid(""));
        assertTrue(EmailValidator.isValid("a".repeat(64) + "@example.com"));
        assertFalse(EmailValidator.isValid("a".repeat(65) + "@example.com"));
        assertTrue(EmailValidator.isValid("\"first last\".o'brien@example.com"));
        assertTrue(EmailValidator.isValid("first\\@last@example.com"));
        assertTrue(EmailValidator.isValid("用户@example.com"));
        assertFalse(EmailValidator.isValid("a..b@example.com"));
        assertFalse(EmailValidator.isValid("a b@example.com"));
        assertFalse(EmailValidator.isValid("a@example.com."));
    }

    @Test
    void domainsAndIdn() {
        assertTrue(EmailValidator.isValid("a@example.COM"));
        assertTrue(EmailValidator.isValid("a@例子.中国"));
        assertTrue(EmailValidator.isValid("a@example.arpa"));
        assertFalse(EmailValidator.isValid("a@localhost"));
        assertFalse(EmailValidator.isValid("a@com"));
        assertFalse(EmailValidator.isValid("a@example.notarealtld"));
        assertFalse(EmailValidator.isValid("a@-example.com"));
        assertFalse(EmailValidator.isValid("a@" + "a".repeat(64) + ".com"));
        assertTrue(EmailValidator.isValid("a@" + "a".repeat(63) + ".com"));
        assertFalse(EmailValidator.isValid("a@" + ("a".repeat(63) + ".").repeat(4) + "com"));
    }

    @Test
    void addressLiterals() {
        assertTrue(EmailValidator.isValid("a@[192.168.1.1]"));
        assertTrue(EmailValidator.isValid("a@[2001:db8::1]"));
        assertTrue(EmailValidator.isValid("a@[::ffff:192.0.2.1]"));
        assertTrue(EmailValidator.isValid("a@[fe80::1%eth0/64]"));
        assertTrue(EmailValidator.isValid("a@[::1/128]"));
        assertTrue(EmailValidator.isValid("a@[::1%42/0]"));
        assertFalse(EmailValidator.isValid("a@[192.168.01.1]"));
        assertFalse(EmailValidator.isValid("a@[256.1.1.1]"));
        assertFalse(EmailValidator.isValid("a@[2001:db8::1::2]"));
        assertFalse(EmailValidator.isValid("a@[::1/129]"));
        assertFalse(EmailValidator.isValid("a@[::1%]"));
        assertFalse(EmailValidator.isValid("a@[::1%eth0%other]"));
        assertFalse(EmailValidator.isValid("a@[::1/64/64]"));
        assertFalse(EmailValidator.isValid("a@[::1/abc]"));
        assertFalse(EmailValidator.isValid("a@[1:2:3:4:5:6:7]"));
        assertFalse(EmailValidator.isValid("a@[IPv6:2001:db8::1]"));
        assertFalse(EmailValidator.isValid("a@[::1%eth0\u0085x]"));
        assertFalse(EmailValidator.isValid("a@[::1%eth0\u2028x]"));
        assertFalse(EmailValidator.isValid("a@[::1%eth0\u2029x]"));
    }
}
