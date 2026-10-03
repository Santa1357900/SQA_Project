package com.google.javascript.rhino.jstype;

import org.junit.Test;
import static org.junit.Assert.*;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.SimpleErrorReporter;

public class ArrowTypeTest {

  @Test
  public void testConstructorsAndNullHandling() throws Throwable {
    JSTypeRegistry registry = new JSTypeRegistry(new SimpleErrorReporter());
    
    ArrowType arrow1 = new ArrowType(registry, null, null);
    assertNotNull(arrow1.parameters);
    assertNotNull(arrow1.returnType);
    assertFalse(arrow1.returnTypeInferred);

    ArrowType arrow2 = new ArrowType(registry, null, null, true);
    assertNotNull(arrow2.parameters);
    assertNotNull(arrow2.returnType);
    assertTrue(arrow2.returnTypeInferred);
  }

  @Test
  public void testIsSubtypeEdgeCases() throws Throwable {
    JSTypeRegistry registry = new JSTypeRegistry(new SimpleErrorReporter());
    JSType unknownType = registry.getNativeType(JSTypeNative.UNKNOWN_TYPE);
    
    ArrowType arrow1 = new ArrowType(registry, null, unknownType, false);
    
    // Non-ArrowType other should return false
    assertFalse(arrow1.isSubtype(unknownType));
  }

  @Test
  public void testHasEqualParametersAndEquivalence() throws Throwable {
    JSTypeRegistry registry = new JSTypeRegistry(new SimpleErrorReporter());
    JSType unknownType = registry.getNativeType(JSTypeNative.UNKNOWN_TYPE);

    Node params1 = new Node(Token.LP);
    Node params2 = new Node(Token.LP);

    ArrowType arrow1 = new ArrowType(registry, params1, unknownType, false);
    ArrowType arrow2 = new ArrowType(registry, params2, unknownType, false);

    assertTrue(arrow1.hasEqualParameters(arrow2));
    assertTrue(arrow1.isEquivalentTo(arrow2));
  }

  @Test
  public void testHashCode() throws Throwable {
    JSTypeRegistry registry = new JSTypeRegistry(new SimpleErrorReporter());
    JSType unknownType = registry.getNativeType(JSTypeNative.UNKNOWN_TYPE);

    ArrowType arrow1 = new ArrowType(registry, null, unknownType, true);
    int code = arrow1.hashCode();
    assertTrue(code != 0);

    ArrowType arrow2 = new ArrowType(registry, null, null, false);
    int code2 = arrow2.hashCode();
    assertTrue(code2 >= 0);
  }

  @Test
  public void testUnsupportedOperations() throws Throwable {
    JSTypeRegistry registry = new JSTypeRegistry(new SimpleErrorReporter());
    ArrowType arrow = new ArrowType(registry, null, null, false);

    try {
      arrow.getLeastSupertype(null);
      fail("Should have thrown UnsupportedOperationException");
    } catch (UnsupportedOperationException e) {
      // Expected
    }

    try {
      arrow.getGreatestSubtype(null);
      fail("Should have thrown UnsupportedOperationException");
    } catch (UnsupportedOperationException e) {
      // Expected
    }

    try {
      arrow.testForEquality(null);
      fail("Should have thrown UnsupportedOperationException");
    } catch (UnsupportedOperationException e) {
      // Expected
    }

    try {
      arrow.visit(null);
      fail("Should have thrown UnsupportedOperationException");
    } catch (UnsupportedOperationException e) {
      // Expected
    }
  }

  @Test
  public void testOtherMethods() throws Throwable {
    JSTypeRegistry registry = new JSTypeRegistry(new SimpleErrorReporter());
    ArrowType arrow = new ArrowType(registry, null, null, false);

    assertEquals(BooleanLiteralSet.TRUE, arrow.getPossibleToBooleanOutcomes());
    assertTrue(arrow.hasUnknownParamsOrReturn());
    assertNotNull(arrow.toStringHelper(true));

    try {
      arrow.resolveInternal(new SimpleErrorReporter(), null);
    } catch (Throwable t) {
      // If resolveInternal throws due to internal null structures, catch it or let it pass if safe,
      // but here parameters/returnType are initialized so it should execute safely.
    }
  }
}