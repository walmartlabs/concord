package com.walmartlabs.concord.server.org.secret;

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

import com.walmartlabs.concord.server.security.UserPrincipal;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

public final class PasswordChecker {

    private static final int CONSECUTIVE_CHARS_IN_USERNAME = 3;

    private enum Rule {
        LENGTH("Password must be at least seven (7) characters", p -> p.length() >= 7),
        UPPER("Password must contain an UPPER character", p -> p.chars().anyMatch(Character::isUpperCase)),
        LOWER("Password must contain a lowercase character", p -> p.chars().anyMatch(Character::isLowerCase)),
        DIGIT("Password must contain a numeric character", p -> p.chars().anyMatch(Character::isDigit)),
        USERNAME("Passwords may NOT contain three (" + CONSECUTIVE_CHARS_IN_USERNAME + ") consecutive characters from your user account name",
                p -> {
                    UserPrincipal currentUser = UserPrincipal.getCurrent();
                    if (currentUser == null) {
                        return true;
                    }
                    return split(currentUser.getUsername(), CONSECUTIVE_CHARS_IN_USERNAME).stream()
                        .noneMatch(p::contains);
        });

        private final String message;
        private final Predicate<String> p;

        Rule(String message, Predicate<String> p) {
            this.message = message;
            this.p = p;
        }

        public Predicate<String> getItem() {
            return p;
        }

        public String getMessage() {
            return message;
        }
    }

    public static void check(String password) throws CheckerException {
        for (Rule r : Rule.values()) {
            if (!r.getItem().test(password)) {
                throw new CheckerException(r.getMessage());
            }
        }
    }

    public static class CheckerException extends Exception {

        private static final long serialVersionUID = 1L;

        public CheckerException(String msg) {
            super(msg);
        }
    }

    private static List<String> split(String text, int charsCount) {
        List<String> result = new ArrayList<>();
        for (int i = 0; i <= text.length() - charsCount; i++) {
            result.add(text.substring(i, i + charsCount));
        }
        return result;
    }

    private PasswordChecker() {
    }
}
