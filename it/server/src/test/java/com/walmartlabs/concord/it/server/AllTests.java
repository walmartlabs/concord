package com.walmartlabs.concord.it.server;

import com.googlecode.junittoolbox.ParallelSuite;
import com.googlecode.junittoolbox.SuiteClasses;
import org.junit.runner.RunWith;

@RunWith(ParallelSuite.class)
@SuiteClasses("**/*IT.class")
public class AllTests {
}
