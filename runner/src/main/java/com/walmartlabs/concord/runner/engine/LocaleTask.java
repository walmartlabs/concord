package com.walmartlabs.concord.runner.engine;

import com.walmartlabs.concord.common.Task;

import javax.inject.Named;
import java.util.Locale;

@Named
public class LocaleTask implements Task {

    @Override
    public String getKey() {
        return "locale";
    }

    public String[] countries() {
        return Locale.getISOCountries();
    }
}
