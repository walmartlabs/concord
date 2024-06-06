package com.walmartlabs.concord.svm;

@FunctionalInterface
public interface FinalCommandHandler {

    void handle(FinalCommand cmd) throws Exception;
}
