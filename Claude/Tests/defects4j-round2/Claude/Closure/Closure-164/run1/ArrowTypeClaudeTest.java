package com.google.javascript.rhino.jstype;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.google.javascript.rhino.ErrorReporter;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;

public class ArrowTypeClaudeTest {

  private JSTypeRegistry registry;
  private JSType unknownType;
  private JSType numberType;
  private JSType stringType;

  @Before
  public void setUp() throws Throwable {
    registry = new JSTypeRegistry(new ErrorReporter() {
      public void warning(String message, String sourceName, int line, int lineOffset) {}
      public void error(String message, String sourceName, int line, int lineOffset) {}
    });
    unknownType = registry.getNativeType(JSTypeNative.UNKNOWN_TYPE);
    numberType = registry.getNativeType(JSTypeNative.NUMBER_TYPE);
    stringType = registry.getNativeType(JSTypeNative.STRING_TYPE);
  }

  // Constructor: null parameters defaults to a single unknown-typed var-args parameter; null return defaults to unknown
  @Test
  public void testConstructor_nullParameters_defaultsToVarArgsUnknown() throws Throwable {
    ArrowType at = new ArrowType(registry, null, null);
    Node p = at.parameters.getFirstChild();
    assertNotNull(p);
    assertTrue(p.isVarArgs());
    assertTrue(p.getJSType().isUnknownType());
    assertTrue(at.returnType.isUnknownType());
  }

  // Constructor: 3-arg constructor sets returnTypeInferred to false by default
  @Test
  public void testConstructor_defaultThreeArg_returnTypeInferredFalse() throws Throwable {
    ArrowType at = new ArrowType(registry, null, numberType);
    assertFalse(at.returnTypeInferred);
    assertTrue(at.returnType.isNumberType());
  }

  // Constructor: 4-arg constructor stores explicit returnTypeInferred=true
  @Test
  public void testConstructor_fourArgTrue_returnTypeInferredTrue() throws Throwable {
    ArrowType at = new ArrowType(registry, null, numberType, true);
    assertTrue(at.returnTypeInferred);
  }

  // isSubtype: other is not an ArrowType -> false
  @Test
  public void testIsSubtype_notArrowType_false() throws Throwable {
    ArrowType at = new ArrowType(registry, null, numberType);
    assertFalse(at.isSubtype(numberType));
  }

  // isSubtype: return type not covariant subtype -> false
  @Test
  public void testIsSubtype_returnTypeNotSubtype_false() throws Throwable {
    ArrowType at1 = new ArrowType(registry, null, numberType);
    ArrowType at2 = new ArrowType(registry, null, stringType);
    assertFalse(at1.isSubtype(at2));
  }

  // isSubtype: equal return types + both default var-args params -> true, covers both-varargs branch
  @Test
  public void testIsSubtype_bothVarArgsSameReturn_true() throws Throwable {
    ArrowType at1 = new ArrowType(registry, null, numberType);
    ArrowType at2 = new ArrowType(registry, null, numberType);
    assertTrue(at1.isSubtype(at2));
  }

  // isSubtype: contravariant param check fails (string not subtype of number) -> false
  @Test
  public void testIsSubtype_paramContravariantFails_false() throws Throwable {
    Node paramsNum = registry.createParameters(numberType);
    Node paramsStr = registry.createParameters(stringType);
    ArrowType withNumParam = new ArrowType(registry, paramsNum, unknownType);
    ArrowType withStrParam = new ArrowType(registry, paramsStr, unknownType);
    assertFalse(withNumParam.isSubtype(withStrParam));
  }

  // isSubtype: fewer required params is a subtype of more required params (per ES4 draft, g < f)
  @Test
  public void testIsSubtype_fewerRequiredParamsIsSubtypeOfMore_true() throws Throwable {
    Node paramsF = registry.createParameters(numberType, numberType);
    Node paramsG = registry.createParameters(numberType);
    ArrowType f = new ArrowType(registry, paramsF, numberType);
    ArrowType g = new ArrowType(registry, paramsG, numberType);
    assertTrue(g.isSubtype(f));
  }

  // BUG HUNT: more required params must NOT be a subtype of fewer required params (f !< g per spec)
  @Test
  public void testIsSubtype_moreRequiredParamsNotSubtypeOfFewer_false() throws Throwable {
    Node paramsF = registry.createParameters(numberType, numberType);
    Node paramsG = registry.createParameters(numberType);
    ArrowType f = new ArrowType(registry, paramsF, numberType);
    ArrowType g = new ArrowType(registry, paramsG, numberType);
    assertFalse(f.isSubtype(g));
  }

  // isSubtype: a var-args parameter in "this" absorbs extra required params in "that" -> true
  @Test
  public void testIsSubtype_varArgsAbsorbsExtraRequiredParams_true() throws Throwable {
    Node varArgParam = registry.createParametersWithVarArgs(numberType);
    Node requiredParams = registry.createParameters(numberType, numberType);
    ArrowType g = new ArrowType(registry, varArgParam, numberType);
    ArrowType f = new ArrowType(registry, requiredParams, numberType);
    assertTrue(g.isSubtype(f));
  }

  // hasEqualParameters: two zero-length parameter lists are equal (loop runs 0 times)
  @Test
  public void testHasEqualParameters_bothEmpty_true() throws Throwable {
    ArrowType empty1 = new ArrowType(registry, IR.block(), unknownType);
    ArrowType empty2 = new ArrowType(registry, IR.block(), unknownType);
    assertTrue(empty1.hasEqualParameters(empty2));
  }

  // hasEqualParameters: same single param type -> true
  @Test
  public void testHasEqualParameters_sameSingleParamType_true() throws Throwable {
    ArrowType a1 = new ArrowType(registry, registry.createParameters(numberType), unknownType);
    ArrowType a2 = new ArrowType(registry, registry.createParameters(numberType), unknownType);
    assertTrue(a1.hasEqualParameters(a2));
  }

  // hasEqualParameters: different param type -> false
  @Test
  public void testHasEqualParameters_differentParamType_false() throws Throwable {
    ArrowType a1 = new ArrowType(registry, registry.createParameters(numberType), unknownType);
    ArrowType a3 = new ArrowType(registry, registry.createParameters(stringType), unknownType);
    assertFalse(a1.hasEqualParameters(a3));
  }

  // hasEqualParameters: different parameter list lengths -> false (thisParam != otherParam at end)
  @Test
  public void testHasEqualParameters_differentLength_false() throws Throwable {
    ArrowType a1 = new ArrowType(registry, registry.createParameters(numberType), unknownType);
    ArrowType a4 = new ArrowType(registry, registry.createParameters(numberType, numberType), unknownType);
    assertFalse(a1.hasEqualParameters(a4));
  }

  // isEquivalentTo: object is not an ArrowType -> false
  @Test
  public void testIsEquivalentTo_notArrowType_false() throws Throwable {
    ArrowType at = new ArrowType(registry, null, numberType);
    assertFalse(at.isEquivalentTo(numberType));
  }

  // isEquivalentTo: different return types -> false
  @Test
  public void testIsEquivalentTo_differentReturnType_false() throws Throwable {
    ArrowType at1 = new ArrowType(registry, registry.createParameters(numberType), numberType);
    ArrowType at2 = new ArrowType(registry, registry.createParameters(numberType), stringType);
    assertFalse(at1.isEquivalentTo(at2));
  }

  // isEquivalentTo: same return type and same params -> true
  @Test
  public void testIsEquivalentTo_sameReturnAndParams_true() throws Throwable {
    ArrowType at1 = new ArrowType(registry, registry.createParameters(numberType), numberType);
    ArrowType at2 = new ArrowType(registry, registry.createParameters(numberType), numberType);
    assertTrue(at1.isEquivalentTo(at2));
  }

  // isEquivalentTo: same return type but different params -> false
  @Test
  public void testIsEquivalentTo_sameReturnDifferentParams_false() throws Throwable {
    ArrowType at1 = new ArrowType(registry, registry.createParameters(numberType), numberType);
    ArrowType at2 = new ArrowType(registry, registry.createParameters(stringType), numberType);
    assertFalse(at1.isEquivalentTo(at2));
  }

  // hashCode: returnTypeInferred adds exactly 1 to the hash code
  @Test
  public void testHashCode_returnTypeInferredAddsOne() throws Throwable {
    Node params = registry.createParameters(numberType);
    ArrowType inferred = new ArrowType(registry, params, numberType, true);
    ArrowType notInferred = new ArrowType(registry, registry.createParameters(numberType), numberType, false);
    assertEquals(notInferred.hashCode() + 1, inferred.hashCode());
  }

  // hashCode: sum of return type hash and each parameter type hash (loop over multiple params)
  @Test
  public void testHashCode_sumsReturnAndParamTypeHashes() throws Throwable {
    Node params = registry.createParameters(numberType, stringType);
    ArrowType at = new ArrowType(registry, params, unknownType, false);
    int expected = unknownType.hashCode() + numberType.hashCode() + stringType.hashCode();
    assertEquals(expected, at.hashCode());
  }

  // getLeastSupertype: always throws UnsupportedOperationException
  @Test
  public void testGetLeastSupertype_alwaysThrows() throws Throwable {
    ArrowType at = new ArrowType(registry, null, numberType);
    try {
      at.getLeastSupertype(numberType);
      fail("expected UnsupportedOperationException");
    } catch (UnsupportedOperationException expected) {
      // expected
    }
  }

  // getGreatestSubtype: always throws UnsupportedOperationException
  @Test
  public void testGetGreatestSubtype_alwaysThrows() throws Throwable {
    ArrowType at = new ArrowType(registry, null, numberType);
    try {
      at.getGreatestSubtype(numberType);
      fail("expected UnsupportedOperationException");
    } catch (UnsupportedOperationException expected) {
      // expected
    }
  }

  // testForEquality: always throws UnsupportedOperationException
  @Test
  public void testTestForEquality_alwaysThrows() throws Throwable {
    ArrowType at = new ArrowType(registry, null, numberType);
    try {
      at.testForEquality(numberType);
      fail("expected UnsupportedOperationException");
    } catch (UnsupportedOperationException expected) {
      // expected
    }
  }

  // visit: always throws UnsupportedOperationException, regardless of visitor argument
  @Test
  public void testVisit_alwaysThrows() throws Throwable {
    ArrowType at = new ArrowType(registry, null, numberType);
    try {
      at.visit(null);
      fail("expected UnsupportedOperationException");
    } catch (UnsupportedOperationException expected) {
      // expected
    }
  }

  // getPossibleToBooleanOutcomes: an arrow type is always truthy -> BooleanLiteralSet.TRUE
  @Test
  public void testGetPossibleToBooleanOutcomes_alwaysTrue() throws Throwable {
    ArrowType at = new ArrowType(registry, null, numberType);
    assertEquals(BooleanLiteralSet.TRUE, at.getPossibleToBooleanOutcomes());
  }

  // hasUnknownParamsOrReturn: default arrow type (unknown var-args param, unknown return) -> true
  @Test
  public void testHasUnknownParamsOrReturn_defaultArrowType_true() throws Throwable {
    ArrowType at = new ArrowType(registry, null, null);
    assertTrue(at.hasUnknownParamsOrReturn());
  }

  // hasUnknownParamsOrReturn: known param type, known return type -> false
  @Test
  public void testHasUnknownParamsOrReturn_allKnownTypes_false() throws Throwable {
    Node params = registry.createParameters(numberType);
    ArrowType at = new ArrowType(registry, params, numberType);
    assertFalse(at.hasUnknownParamsOrReturn());
  }

  // hasUnknownParamsOrReturn: known param, unknown return type -> true (returnType.isUnknownType() branch)
  @Test
  public void testHasUnknownParamsOrReturn_unknownReturn_true() throws Throwable {
    Node params = registry.createParameters(numberType);
    ArrowType at = new ArrowType(registry, params, unknownType);
    assertTrue(at.hasUnknownParamsOrReturn());
  }

  // hasUnknownParamsOrReturn: mixed param types, one unknown among known -> true (mid-loop return)
  @Test
  public void testHasUnknownParamsOrReturn_oneUnknownParamAmongKnown_true() throws Throwable {
    Node mixedParams = registry.createParameters(numberType, unknownType);
    ArrowType at = new ArrowType(registry, mixedParams, numberType);
    assertTrue(at.hasUnknownParamsOrReturn());
  }

  // hasUnknownParamsOrReturn: zero parameters with known return type -> false (loop runs 0 times)
  @Test
  public void testHasUnknownParamsOrReturn_zeroParamsKnownReturn_false() throws Throwable {
    ArrowType at = new ArrowType(registry, IR.block(), numberType);
    assertFalse(at.hasUnknownParamsOrReturn());
  }

  // toStringHelper: delegates to Object/JSType toString and never returns null
  @Test
  public void testToStringHelper_returnsNonNullString() throws Throwable {
    ArrowType at = new ArrowType(registry, null, numberType);
    assertNotNull(at.toStringHelper(true));
    assertNotNull(at.toStringHelper(false));
  }
}
