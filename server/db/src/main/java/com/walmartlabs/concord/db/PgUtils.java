package com.walmartlabs.concord.db;

import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.impl.DSL;

public final class PgUtils {

    public static Condition contains(Field<String[]> left, String[] right) {
        return DSL.condition("{0} @> {1}::text[]", left, DSL.val(right, left.getDataType()));
    }

    private PgUtils() {
    }
}
