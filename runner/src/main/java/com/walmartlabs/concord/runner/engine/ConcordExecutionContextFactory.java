package com.walmartlabs.concord.runner.engine;

import com.walmartlabs.concord.sdk.Context;
import io.takari.bpm.context.ExecutionContextFactory;
import io.takari.bpm.context.ExecutionContextImpl;
import io.takari.bpm.el.ExpressionManager;
import io.takari.bpm.state.Variables;

public class ConcordExecutionContextFactory implements ExecutionContextFactory<ConcordExecutionContextFactory.ConcordExecutionContext> {

    private final ExpressionManager expressionManager;

    public ConcordExecutionContextFactory(ExpressionManager expressionManager) {
        this.expressionManager = expressionManager;
    }

    @Override
    public ConcordExecutionContext create(Variables source) {
        return new ConcordExecutionContext(expressionManager, source);
    }

    public static class ConcordExecutionContext extends ExecutionContextImpl implements Context {

        public ConcordExecutionContext(ExpressionManager expressionManager, Variables source) {
            super(expressionManager, source);
        }
    }
}
