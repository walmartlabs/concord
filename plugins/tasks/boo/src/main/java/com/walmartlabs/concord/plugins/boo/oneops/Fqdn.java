package com.walmartlabs.concord.plugins.boo.oneops;

import java.io.Serializable;

public class Fqdn implements Serializable {
    private String entries;
    private String aliases;
    private String fullAliases;

    public String getEntries() {
        return entries;
    }

    public void setEntries(String entries) {
        this.entries = entries;
    }

    public String getAliases() {
        return aliases;
    }

    public void setAliases(String aliases) {
        this.aliases = aliases;
    }

    public String getFullAliases() {
        return fullAliases;
    }

    public void setFullAliases(String fullAliases) {
        this.fullAliases = fullAliases;
    }

    @Override
    public String toString() {
        return "Fqdn{" +
                "entries='" + entries + '\'' +
                ", aliases='" + aliases + '\'' +
                ", fullAliases='" + fullAliases + '\'' +
                '}';
    }
}
