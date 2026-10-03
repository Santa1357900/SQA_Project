package org.apache.commons.math.optimization.general;

import org.junit.Test;
import static org.junit.Assert.*;

public class LevenbergMarquardtOptimizerTest {

    @Test
    public void testConstructorAndSetters() throws Throwable {
        LevenbergMarquardtOptimizer optimizer = new LevenbergMarquardtOptimizer();
        optimizer.setInitialStepBoundFactor(50.0);
        optimizer.setCostRelativeTolerance(1.0e-8);
        optimizer.setParRelativeTolerance(1.0e-8);
        optimizer.setOrthoTolerance(1.0e-8);
        
        // No exception should be thrown
        assertTrue(true);
    }
}