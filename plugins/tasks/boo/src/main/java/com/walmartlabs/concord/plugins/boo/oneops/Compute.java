package com.walmartlabs.concord.plugins.boo.oneops;

import java.io.Serializable;

public class Compute implements Serializable {
    String privateIp;
    String publicIp;


    public String getPrivateIp() {
        return privateIp;
    }

    public void setPrivateIp(String privateIp) {
        this.privateIp = privateIp;
    }

    public String getPublicIp() {
        return publicIp;
    }

    public void setPublicIp(String publicIp) {
        this.publicIp = publicIp;
    }

    @Override
    public String toString() {
        return "Compute{" +
                "privateIp='" + privateIp + '\'' +
                ", publicIp='" + publicIp + '\'' +
                '}';
    }
}
