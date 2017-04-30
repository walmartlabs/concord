package com.walmartlabs.concord.plugins.boo.oneops;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Platform implements Serializable {
    private String name;
    private List<Compute> computes;
    private List<Fqdn> fqdns;

    public Platform () {
        computes = new ArrayList<>();
        fqdns = new ArrayList<>();
    }

    public void addFqdn(Fqdn fqdn) {
        this.fqdns.add(fqdn);
    }

    public void addCompute(Compute compute) {
        this.computes.add(compute);
    }

    public List<Compute> getComputes() {
        return computes;
    }

    public void setComputes(List<Compute> computes) {
        this.computes = computes;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<Fqdn> getFqdns() {
        return fqdns;
    }

    public void setFqdns(List<Fqdn> fqdns) {
        this.fqdns = fqdns;
    }

    @Override
    public String toString() {
        return "Platform{" +
                "name='" + name + '\'' +
                ", computes=" + computes +
                ", fqdns=" + fqdns +
                '}';
    }
}
