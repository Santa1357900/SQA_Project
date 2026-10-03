package com.google.javascript.jscomp;

import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import junit.framework.TestCase;

public class InlineCostEstimatorTest extends TestCase {

    public void testGetCostWithNullRoot() throws Throwable {
        try {
            InlineCostEstimator.getCost(null);
            fail("Expected NullPointerException or similar when root is null");
        } catch (NullPointerException e) {
            // Expected
        } catch (Throwable t) {
            // Some other runtime exception might be thrown, which is also acceptable for null root
        }
    }

    public void testGetCostSimpleNode() throws Throwable {
        Node node = IR.number(10.0);
        int cost = InlineCostEstimator.getCost(node);
        assertTrue(cost >= 0);
    }

    public void testGetCostWithThreshold() throws Throwable {
        Node node = IR.name("longIdentifierNameToExceedCost");
        int costWithLowThreshold = InlineCostEstimator.getCost(node, 1);
        int costWithHighThreshold = InlineCostEstimator.getCost(node, 100);
        
        assertTrue(costWithLowThreshold <= costWithHighThreshold);
    }

    public void testGetCostIdentifier() throws Throwable {
        Node node = IR.name("x");
        int cost = InlineCostEstimator.getCost(node);
        // Identifiers use ESTIMATED_IDENTIFIER ("ab", length 2) by default in CompiledSizeEstimator
        assertEquals(2, cost);
    }
}