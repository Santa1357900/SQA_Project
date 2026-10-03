package com.google.javascript.rhino.jstype;

import org.junit.Test;
import static org.junit.Assert.*;

import com.google.javascript.rhino.ErrorReporter;
import com.google.javascript.rhino.JSDocInfo;

public class JSTypeTest {

  private static class ConcreteJSType extends JSType {
    private static final long serialVersionUID = 1L;
    private final boolean subtypeResult;

    public ConcreteJSType(JSTypeRegistry registry, boolean subtypeResult) {
      super(registry);
      this.subtypeResult = subtypeResult;
    }

    @Override
    public boolean isSubtype(JSType that) {
      return subtypeResult;
    }

    @Override
    public BooleanLiteralSet getPossibleToBooleanOutcomes() {
      return BooleanLiteralSet.TRUE;
    }

    @Override
    public <T> T visit(Visitor<T> visitor) {
      return null;
    }

    @Override
    JSType resolveInternal(ErrorReporter t, StaticScope<JSType> scope) {
      return this;
    }
  }

  @Test
  public void testBasicTypePredicates() throws Throwable {
    JSTypeRegistry registry = new JSTypeRegistry(new ErrorReporter() {
      public void warning(String message, String sourceName, int line, int characterNo) {}
      public void error(String message, String sourceName, int line, int characterNo) {}
      public org.mozilla.javascript.EvaluatorException runtimeError(String message, String sourceName, int line, int characterNo) {
        return null;
      }
    });

    ConcreteJSType type = new ConcreteJSType(registry, false);

    assertFalse(type.isNoType());
    assertFalse(type.isNoObjectType());
    assertFalse(type.isEmptyType());
    assertFalse(type.isNumberObjectType());
    assertFalse(type.isNumberValueType());
    assertFalse(type.isFunctionPrototypeType());
    assertFalse(type.isStringObjectType());
    assertFalse(type.isStringValueType());
    assertFalse(type.isArrayType());
    assertFalse(type.isBooleanObjectType());
    assertFalse(type.isBooleanValueType());
    assertFalse(type.isRegexpType());
    assertFalse(type.isDateType());
    assertFalse(type.isNullType());
    assertFalse(type.isVoidType());
    assertFalse(type.isAllType());
    assertFalse(type.isUnknownType());
    assertFalse(type.isCheckedUnknownType());
    assertFalse(type.isUnionType());
    assertFalse(type.isFunctionType());
    assertFalse(type.isEnumElementType());
    assertFalse(type.isEnumType());
    assertFalse(type.isRecordType());
    assertFalse(type.isTemplateType());
    assertFalse(type.isObject());
    assertFalse(type.isConstructor());
    assertFalse(type.isNominalType());
    assertFalse(type.isInstanceType());
    assertFalse(type.isInterface());
    assertFalse(type.isOrdinaryFunction());
    assertFalse(type.matchesNumberContext());
    assertFalse(type.matchesStringContext());
    assertFalse(type.matchesObjectContext());
    assertFalse(type.canBeCalled());
    assertNull(type.getJSDocInfo());
    assertNull(type.autoboxesTo());
    assertNull(type.unboxesTo());
    assertNull(type.toObjectType());
  }

  @Test
  public void testEqualityAndEquivalence() throws Throwable {
    JSTypeRegistry registry = new JSTypeRegistry(new ErrorReporter() {
      public void warning(String message, String sourceName, int line, int characterNo) {}
      public void error(String message, String sourceName, int line, int characterNo) {}
      public org.mozilla.javascript.EvaluatorException runtimeError(String message, String sourceName, int line, int characterNo) {
        return null;
      }
    });

    ConcreteJSType type1 = new ConcreteJSType(registry, true);
    ConcreteJSType type2 = new ConcreteJSType(registry, false);

    assertTrue(type1.isEquivalentTo(type1));
    assertFalse(type1.isEquivalentTo(type2));

    assertTrue(JSType.isEquivalent(null, null));
    assertFalse(JSType.isEquivalent(type1, null));
    assertFalse(JSType.isEquivalent(null, type1));
    assertTrue(JSType.isEquivalent(type1, type1));
    assertFalse(JSType.isEquivalent(type1, type2));

    assertTrue(type1.equals(type1));
    assertFalse(type1.equals(type2));
    assertFalse(type1.equals(new Object()));

    assertNotNull(type1.hashCode());
    assertNotNull(type1.toDebugHashCodeString());
  }

  @Test
  public void testCanAssignAndDiffers() throws Throwable {
    JSTypeRegistry registry = new JSTypeRegistry(new ErrorReporter() {
      public void warning(String message, String sourceName, int line, int characterNo) {}
      public void error(String message, String sourceName, int line, int characterNo) {}
      public org.mozilla.javascript.EvaluatorException runtimeError(String message, String sourceName, int line, int characterNo) {
        return null;
      }
    });

    ConcreteJSType subType = new ConcreteJSType(registry, true);
    ConcreteJSType nonSubtype = new ConcreteJSType(registry, false);

    assertTrue(subType.canAssignTo(nonSubtype));
    assertFalse(nonSubtype.canAssignTo(subType));

    assertFalse(subType.differsFrom(subType));
  }

  @Test
  public void testRestrictedTypeAndToBoolean() throws Throwable {
    JSTypeRegistry registry = new JSTypeRegistry(new ErrorReporter() {
      public void warning(String message, String sourceName, int line, int characterNo) {}
      public void error(String message, String sourceName, int line, int characterNo) {}
      public org.mozilla.javascript.EvaluatorException runtimeError(String message, String sourceName, int line, int characterNo) {
        return null;
      }
    });

    ConcreteJSType type = new ConcreteJSType(registry, false);
    assertEquals(type, type.getRestrictedTypeGivenToBooleanOutcome(true));
    assertEquals(type, type.restrictByNotNullOrUndefined());
  }

  @Test
  public void testTypesUnderEqualityAndInequality() throws Throwable {
    JSTypeRegistry registry = new JSTypeRegistry(new ErrorReporter() {
      public void warning(String message, String sourceName, int line, int characterNo) {}
      public void error(String message, String sourceName, int line, int characterNo) {}
      public org.mozilla.javascript.EvaluatorException runtimeError(String message, String sourceName, int line, int characterNo) {
        return null;
      }
    });

    ConcreteJSType type1 = new ConcreteJSType(registry, true);
    ConcreteJSType type2 = new ConcreteJSType(registry, true);

    JSType.TypePair pairEq = type1.getTypesUnderEquality(type2);
    assertNotNull(pairEq);

    JSType.TypePair pairIneq = type1.getTypesUnderInequality(type2);
    assertNotNull(pairIneq);

    JSType.TypePair pairShallowEq = type1.getTypesUnderShallowEquality(type2);
    assertNotNull(pairShallowEq);

    JSType.TypePair pairShallowIneq = type1.getTypesUnderShallowInequality(type2);
    assertNotNull(pairShallowIneq);
  }

  @Test
  public void testResolveAndSafeResolve() throws Throwable {
    JSTypeRegistry registry = new JSTypeRegistry(new ErrorReporter() {
      public void warning(String message, String sourceName, int line, int characterNo) {}
      public void error(String message, String sourceName, int line, int characterNo) {}
      public org.mozilla.javascript.EvaluatorException runtimeError(String message, String sourceName, int line, int characterNo) {
        return null;
      }
    });

    ConcreteJSType type = new ConcreteJSType(registry, true);
    assertFalse(type.isResolved());

    JSType resolved = type.resolve(null, null);
    assertNotNull(resolved);
    assertTrue(type.isResolved());

    // Resolve again to hit the cached resolved path
    JSType resolvedAgain = type.resolve(null, null);
    assertNotNull(resolvedAgain);

    type.clearResolved();
    assertFalse(type.isResolved());

    JSType safeRes = JSType.safeResolve(type, null, null);
    assertNotNull(safeRes);

    JSType safeResNull = JSType.safeResolve(null, null, null);
    assertNull(safeResNull);
  }
}