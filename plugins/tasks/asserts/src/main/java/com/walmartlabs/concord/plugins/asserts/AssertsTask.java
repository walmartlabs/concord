package com.walmartlabs.concord.plugins.asserts;

/*-
 * *****
 * Concord
 * -----
 * Copyright (C) 2017 - 2024 Walmart Inc.
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

import com.walmartlabs.concord.runtime.v2.sdk.Context;
import com.walmartlabs.concord.runtime.v2.sdk.DryRunReady;
import com.walmartlabs.concord.runtime.v2.sdk.Task;
import com.walmartlabs.concord.runtime.v2.sdk.UserDefinedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Inject;
import javax.inject.Named;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Paths;

@Named("asserts")
@DryRunReady
@SuppressWarnings("unused")
public class AssertsTask implements Task {

    private final static Logger log = LoggerFactory.getLogger(AssertsTask.class);

    private final Context context;

    @Inject
    public AssertsTask(Context context) {
        this.context = context;
    }

    public void hasVariable(String name) {
        boolean present = context.eval(String.format("${hasVariable('%s')}", name), Boolean.class);
        if (!present) {
            throw new UserDefinedException("Variable '" + name + "' not found");
        }

        Object value = context.eval("${" + name + "}", Object.class);
        if (value == null) {
            throw new UserDefinedException("Variable '" + name + "' is null value");
        }

        if (value instanceof String) {
            if (((String) value).isBlank()) {
                throw new UserDefinedException("Variable '" + name + "' is empty");
            }
        }
    }

    public void hasFile(String path) {
        if (Files.notExists(Paths.get(path))) {
            throw new UserDefinedException("File '" + path + "' does not exist");
        }
    }

    public void assertEquals(Object expected, Object actual) {
        if (expected == null && actual == null) {
            return;
        }

        if (expected == null) {
            throw new UserDefinedException("Expected value to be 'null' but is '" + actual + "'");
        } else if (actual == null) {
            throw new UserDefinedException("Expected value to be '" + expected + "' but is 'null'");
        }

        if (expected instanceof Number && actual instanceof Number) {
            if (numbersEquals((Number) expected, (Number) actual)) {
                return;
            }
        } else if (expected.equals(actual)) {
            return;
        }

        String msg = String.format("Expected value to be '%s' (class: %s) but is '%s' (class: %s)",
                expected, expected.getClass().getName(),
                actual, actual.getClass().getName());

        throw new UserDefinedException(msg);
    }

    public static void assertTrue(boolean condition) {
        if (!condition) {
            throw new UserDefinedException("Expected value to be true but is false");
        }
    }

    public static void assertTrue(String message, boolean condition) {
        if (!condition) {
            throw new UserDefinedException(message);
        }
    }

    public void dryRunMode() {
        if (!context.processConfiguration().dryRunMode()) {
            throw new UserDefinedException("Expected to run in the dry-run mode, running in the regular mode instead.");
        }
    }

    private static boolean isSpecialNumber(Number x) {
        boolean specialDouble = x instanceof Double
                && (Double.isNaN((Double) x) || Double.isInfinite((Double) x));
        boolean specialFloat = x instanceof Float
                && (Float.isNaN((Float) x) || Float.isInfinite((Float) x));
        return specialDouble || specialFloat;
    }

    private static BigDecimal toBigDecimal(Number number) {
        if (number instanceof BigDecimal) {
            return (BigDecimal) number;
        } else if (number instanceof BigInteger) {
            return new BigDecimal((BigInteger) number);
        } else if (number instanceof Byte || number instanceof Short
                || number instanceof Integer || number instanceof Long) {
            return BigDecimal.valueOf(number.longValue());
        } else if (number instanceof Float || number instanceof Double) {
            return BigDecimal.valueOf(number.doubleValue());
        }

        try {
            return new BigDecimal(number.toString());
        } catch (NumberFormatException e) {
            throw new RuntimeException("The given number (\"" + number + "\" of class " + number.getClass().getName() + ") does not have a parsable string representation", e);
        }
    }

    private boolean numbersEquals(Number expected, Number actual) {
        if (isSpecialNumber(expected) || isSpecialNumber(actual)) {
            return Double.compare(expected.doubleValue(), actual.doubleValue()) == 0;
        } else {
            return toBigDecimal(expected).compareTo(toBigDecimal(actual)) == 0;
        }
    }
}
