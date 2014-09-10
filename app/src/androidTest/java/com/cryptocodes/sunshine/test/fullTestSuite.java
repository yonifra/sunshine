package com.cryptocodes.sunshine.test;

import android.test.suitebuilder.TestSuiteBuilder;

import junit.framework.Test;

/**
 * Created by jonathanf on 23/7/2014.
 */
public class fullTestSuite {
    public fullTestSuite() {
        super();
    }

    public static Test suite() {
        return new TestSuiteBuilder(fullTestSuite.class)
                .includeAllPackagesUnderHere().build();
    }
}
