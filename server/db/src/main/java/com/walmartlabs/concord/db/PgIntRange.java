package com.walmartlabs.concord.db;

import java.io.Serializable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PgIntRange implements Serializable {

    private static final long serialVersionUID = 1L;
    private static final Pattern PATTERN = Pattern.compile("([\\[(])(\\d+),(\\d+)([])])");

    public static PgIntRange parse(String s) {
        Matcher m = PATTERN.matcher(s);
        if (!m.matches()) {
            throw new IllegalArgumentException("Invalid range value: " + s);
        }

        Mode lowerMode = Mode.parse(m.group(1));
        int lower = Integer.parseInt(m.group(2));
        int upper = Integer.parseInt(m.group(3));
        Mode upperMode = Mode.parse(m.group(4));

        return new PgIntRange(lowerMode, lower, upper, upperMode);
    }

    private final Mode lowerMode;
    private final int lower;
    private final int upper;
    private final Mode upperMode;

    private PgIntRange(Mode lowerMode, int lower, int upper, Mode upperMode) {
        this.lower = lower;
        this.lowerMode = lowerMode;
        this.upper = upper;
        this.upperMode = upperMode;
    }

    public Mode getLowerMode() {
        return lowerMode;
    }

    public int getLower() {
        return lower;
    }

    public int getUpper() {
        return upper;
    }

    public Mode getUpperMode() {
        return upperMode;
    }

    public enum Mode {

        INCLUSIVE,
        EXCLUSIVE;

        private static Mode parse(String s) {
            if ("[".equals(s) || "]".equals(s)) {
                return Mode.INCLUSIVE;
            } else if ("(".equals(s) || ")".equals(s)) {
                return Mode.EXCLUSIVE;
            } else {
                throw new IllegalArgumentException("Invalid range mode string: " + s);
            }
        }
    }
}
