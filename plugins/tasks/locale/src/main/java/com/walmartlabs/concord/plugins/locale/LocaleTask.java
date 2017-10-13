package com.walmartlabs.concord.plugins.locale;

import com.walmartlabs.concord.sdk.Task;

import javax.inject.Named;
import java.util.Locale;

@Named("locale")
public class LocaleTask implements Task {

    public String[] countries() {
        return Locale.getISOCountries();
    }
}
