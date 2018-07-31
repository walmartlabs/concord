package com.walmartlabs.concord.server.org.secret;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2018 Walmart Inc.
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

import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;

public final class PasswordGenerator {

    private static final Random RANDOM = new SecureRandom();

    private static final int PASSWORD_LENGTH = 12;

    private static final String UPPER_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    private static final String LOWER_CHARS = "abcdefghijklmnopqrstuvwxyz";
    private static final String NUMBER_CHARS = "0123456789";
    private static final String OTHER_CHARS = "~`!@#$%^&*()-_=+[{]}|,<.>/?\\";

    private static final String CHARS = UPPER_CHARS + LOWER_CHARS + NUMBER_CHARS + OTHER_CHARS;

    public static String generate() {
        List<String> alphanumericChars = Arrays.asList(UPPER_CHARS, LOWER_CHARS, NUMBER_CHARS);

        Collections.shuffle(alphanumericChars, RANDOM);

        StringBuilder result = new StringBuilder();
        for (String chars : alphanumericChars) {
            result.append(chars.charAt(RANDOM.nextInt(chars.length())));
        }

        for (int i = 0; i < PASSWORD_LENGTH - alphanumericChars.size(); i++) {
            result.append(CHARS.charAt(RANDOM.nextInt(CHARS.length())));
        }

        return result.toString();
    }

    private PasswordGenerator() {
    }
}
