package com.walmartlabs.concord.console3;

import static com.google.common.base.Preconditions.checkArgument;

/**
 *
 * @param base - the base path of the page, e.g. /console3. Without a trailing slash.
 */
public record PageContext(String base) {

    public PageContext(String base) {
        checkArgument(base != null && !base.isBlank(), "base is required");
        if (base.endsWith("/")) {
            this.base = base.substring(0, base.length() - 1);
        } else {
            this.base = base;
        }
    }
}
