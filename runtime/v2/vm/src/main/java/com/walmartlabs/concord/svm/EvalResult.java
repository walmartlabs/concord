package com.walmartlabs.concord.svm;

import java.io.Serial;
import java.io.Serializable;

public class EvalResult implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final Frame lastFrame;

    public EvalResult(Frame lastFrame) {
        this.lastFrame = lastFrame;
    }

    public Frame lastFrame() {
        return lastFrame;
    }
}
