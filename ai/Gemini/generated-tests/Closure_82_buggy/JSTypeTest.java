package com.google.javascript.rhino.jstype;

import org.junit.Test;
import static org.junit.Assert.*;

import com.google.common.base.Predicate;
import com.google.javascript.rhino.ErrorReporter;
import com.google.javascript.rhino.JSDocInfo;

public class JSTypeTest {

  private static class DummyJSType extends JSType {
    private static final long serialVersionUID = 1L;
    private final boolean subtypeResult;
    private final boolean unknown;

    public DummyJSType(JSTypeRegistry registry, boolean subtypeResult, boolean unknown) {
      super(registry);
      this.subtypeResult = subtypeResult;
      this.unknown = unknown;
    }

    @Override
    public boolean isSubtype(JSType that) {
      return subtypeResult;
    }

    @Override
    public boolean isUnknownType() {
      return unknown;
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
  public void testConstantsAndBasicGetters() throws Throwable {
    assertNotNull(JSType.UNKNOWN_NAME);
    assertNotNull(JSType.NOT_A_CLASS);
    assertNotNull(JSType.NOT_A_TYPE);
    assertNotNull(JSType.EMPTY_TYPE_COMPONENT);
    assertEquals(1, JSType.ENUMDECL);
    assertEquals(0, JSType.NOT_ENUMDECL);
  }

  @Test
  public void testAlphaComparator() throws Throwable {
    JSTypeRegistry registry = new JSTypeRegistry(null);
    DummyJSType type1 = new DummyJSType(registry, true, false) {
      public String toString() { return "A"; }
    };
    DummyJSType type2 = new DummyJSType(registry, true, false) {
      public String toString() { return "B"; }
    };
    DummyJSType type3 = new DummyJSType(registry, true, false) {
      public String toString() { return "A"; }
    };

    assertTrue(JSType.ALPHA.compare(type1, type2) < 0);
    assertTrue(JSType.ALPHA.compare(type2, type1) > 0);
    assertEquals(0, JSType.ALPHA.compare(type1, type3));
  }

  @Test
  public void testDefaultBooleansAndProperties() throws Throwable {
    JSTypeRegistry registry = new JSTypeRegistry(null);
    DummyJSType type = new DummyJSType(registry, false, false);

    assertNull(type.getJSDocInfo());
    assertNull(type.getDisplayName());
    assertFalse(type.hasDisplayName());
    
    assertFalse(type.isNoType());
    assertFalse(type.isNoResolvedType());
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
    
    assertTrue(type.matchesInt32Context() == type.matchesNumberContext());
    assertTrue(type.matchesUint32Context() == type.matchesNumberContext());
    
    assertNull(type.autoboxesTo());
    assertNull(type.unboxesTo());
    assertNull(type.toObjectType());
    assertNull(type.findPropertyType("nonExistent"));
  }

  @Test
  public void testEqualityAndEquivalence() throws Throwable {
    JSTypeRegistry registry = new JSTypeRegistry(null);
    DummyJSType type1 = new DummyJSType(registry, false, false);
    DummyJSType type2 = new DummyJSType(registry, false, false);

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

    assertEquals(System.identityHashCode(type1), type1.hashCode());
    assertNotNull(type1.toDebugHashCodeString());
  }

  @Test
  public void testCanAssignAndSubtypeHelpers() throws Throwable {
    JSTypeRegistry registry = new JSTypeRegistry(null);
    DummyJSType typeTrue = new DummyJSType(registry, true, false);
    DummyJSType typeFalse = new DummyJSType(registry, false, false);

    assertTrue(typeFalse.canAssignTo(typeTrue));
    assertFalse(typeFalse.canAssignTo(typeFalse));

    assertTrue(JSType.isSubtype(typeFalse, typeTrue));
    assertFalse(JSType.isSubtype(typeFalse, typeFalse));
  }

  @Test
  public void testDiffersFrom() throws Throwable {
    JSTypeRegistry registry = new JSTypeRegistry(null);
    DummyJSType normal1 = new DummyJSType(registry, false, false);
    DummyJSType normal2 = new DummyJSType(registry, false, false);
    DummyJSType unknown = new DummyJSType(registry, false, true);

    assertFalse(normal1.differsFrom(normal1));
    assertTrue(normal1.differsFrom(normal2));
    assertTrue(normal1.differsFrom(unknown));
    assertTrue(unknown.differsFrom(normal1));
    assertFalse(unknown.differsFrom(unknown));
  }

  @Test
  public void testRestrictedTypeAndEqualityPairs() throws Throwable {
    JSTypeRegistry registry = new JSTypeRegistry(null);
    DummyJSType type = new DummyJSType(registry, false, false);

    assertEquals(type, type.getRestrictedTypeGivenToBooleanOutcome(true));
    assertEquals(type, type.restrictByNotNullOrUndefined());

    JSType.TypePair pair = type.getTypesUnderEquality(type);
    assertNotNull(pair);
    assertEquals(type, pair.typeA);
    assertEquals(type, pair.typeB);

    JSType.TypePair ineqPair = type.getTypesUnderInequality(type);
    assertNotNull(ineqPair);
    assertEquals(type, ineqPair.typeA);
    assertEquals(type, ineqPair.typeB);

    JSType.TypePair shallowPair = type.getTypesUnderShallowEquality(type);
    assertNotNull(shallowPair);

    JSType.TypePair shallowIneqPair = type.getTypesUnderShallowInequality(type);
    assertNotNull(shallowIneqPair);
  }

  @Test
  public void testResolutionAndValidator() throws Throwable {
    JSTypeRegistry registry = new JSTypeRegistry(null);
    DummyJSType type = new DummyJSType(registry, false, false);

    assertFalse(type.isResolved());
    
    DummyJSType resolved = (DummyJSType) type.resolve(null, null);
    assertTrue(type.isResolved());
    assertEquals(type, resolved);

    type.clearResolved();
    assertFalse(type.isResolved());

    DummyJSType safeRes = (DummyJSType) JSType.safeResolve(type, null, null);
    assertNotNull(safeRes);
    assertNull(JSType.safeResolve(null, null, null));

    boolean validatorResult = type.setValidator(new Predicate<JSType>() {
      public boolean apply(JSType input) {
        return input != null;
      }
    });
    assertTrue(validatorResult);

    type.forgiveUnknownNames();
  }

  @Test
  public void testTestForEqualityAndHelpers() throws Throwable {
    JSTypeRegistry registry = new JSTypeRegistry(null);
    DummyJSType type = new DummyJSType(registry, false, false);

    TernaryValue val = type.testForEquality(type);
    assertNotNull(val);

    assertTrue(type.canTestForEqualityWith(type) || !type.canTestForEqualityWith(type));
    assertTrue(type.canTestForShallowEqualityWith(type));
  }
}