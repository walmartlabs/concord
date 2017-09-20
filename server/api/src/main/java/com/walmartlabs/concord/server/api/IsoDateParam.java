package com.walmartlabs.concord.server.api;

import javax.xml.bind.DatatypeConverter;
import java.io.Serializable;
import java.util.Calendar;

public class IsoDateParam implements Serializable {

    public static IsoDateParam valueOf(String s) {
        Calendar calendar = DatatypeConverter.parseDateTime(s);
        return new IsoDateParam(calendar);
    }

    public IsoDateParam(Calendar value) {
        this.value = value;
    }

    private final Calendar value;

    public Calendar getValue() {
        return value;
    }
}
