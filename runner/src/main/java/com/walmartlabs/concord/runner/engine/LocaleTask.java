package com.walmartlabs.concord.runner.engine;

import com.walmartlabs.concord.common.Task;

import javax.inject.Named;
import java.util.Locale;

@Named("locale")
public class LocaleTask implements Task {

    public String[] countries() {
        return Locale.getISOCountries();
    }
}
