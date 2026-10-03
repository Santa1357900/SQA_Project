package com.google.javascript.rhino.jstype;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.google.common.base.Predicate;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;

public class JSTypeClaudeTest {

  private JSTypeRegistry registry;
  private JSType numberType;
  private JSType stringType;
  private JSType nullType;
  private JSType voidType;
  private JSType allType;
  private JSType unknownType;
  private JSType objectType;
  private JSType noType;
  private JSType noObjectType;

  @Before
  public void setUp() throws Throwable {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    compiler.initOptions(options);
    registry = compiler.getTypeRegistry();
    numberType = registry.getNativeType(JSTypeNative.NUMBER_TYPE);
    stringType = registry.getNativeType(JSTypeNative.STRING_TYPE);
    nullType = registry.getNativeType(JSTypeNative.NULL_TYPE);
    voidType = registry.getNativeType(JSTypeNative.VOID_TYPE);
    allType = registry.getNativeType(JSTypeNative.ALL_TYPE);
    unknownType = registry.getNativeType(JSTypeNative.UNKNOWN_TYPE);
    objectType = registry.getNativeType(JSTypeNative.OBJECT_TYPE);
    noType = registry.getNativeType(JSTypeNative.NO_TYPE);
    noObjectType = registry.getNativeType(JSTypeNative.NO_OBJECT_TYPE);
  }

  // default getJSDocInfo() returns null when not overridden
  @Test
  public void testGetJSDocInfo_default_returnsNull() throws Throwable {
    assertNull(numberType.getJSDocInfo());
  }





  // isEmptyType() true branch: isNoType()
  @Test
  public void testIsEmptyType_noType_returnsTrue() throws Throwable {
    assertTrue(noType.isEmptyType());
  }

  // isEmptyType() true branch: isNoObjectType()
  @Test
  public void testIsEmptyType_noObjectType_returnsTrue() throws Throwable {
    assertTrue(noObjectType.isEmptyType());
  }

  // isEmptyType() false branch for ordinary type
  @Test
  public void testIsEmptyType_numberType_returnsFalse() throws Throwable {
    assertFalse(numberType.isEmptyType());
  }

  // isString() true for string value type
  @Test
  public void testIsString_stringType_true() throws Throwable {
    assertTrue(stringType.isString());
  }

  // isString() false for number type
  @Test
  public void testIsString_numberType_false() throws Throwable {
    assertFalse(numberType.isString());
  }

  // isNumber() true for number value type
  @Test
  public void testIsNumber_numberType_true() throws Throwable {
    assertTrue(numberType.isNumber());
  }



  // matchesInt32Context() is a synonym for matchesNumberContext() per contract
  @Test
  public void testMatchesInt32Context_delegatesToMatchesNumberContext() throws Throwable {
    assertEquals(numberType.matchesNumberContext(), numberType.matchesInt32Context());
  }

  // matchesUint32Context() is a synonym for matchesNumberContext() per contract
  @Test
  public void testMatchesUint32Context_delegatesToMatchesNumberContext() throws Throwable {
    assertEquals(numberType.matchesNumberContext(), numberType.matchesUint32Context());
  }

  // isEquivalentTo same instance true
  @Test
  public void testIsEquivalentTo_sameInstance_true() throws Throwable {
    assertTrue(numberType.isEquivalentTo(numberType));
  }

  // isEquivalentTo with null argument returns false, no NPE
  @Test
  public void testIsEquivalentTo_null_false() throws Throwable {
    assertFalse(numberType.isEquivalentTo(null));
  }

  // static isEquivalent both null returns true
  @Test
  public void testIsEquivalent_static_bothNull_true() throws Throwable {
    assertTrue(JSType.isEquivalent(null, null));
  }

  // static isEquivalent one null returns false
  @Test
  public void testIsEquivalent_static_oneNull_false() throws Throwable {
    assertFalse(JSType.isEquivalent(null, numberType));
  }

  // static isEquivalent same instance returns true via isEquivalentTo
  @Test
  public void testIsEquivalent_static_sameInstance_true() throws Throwable {
    assertTrue(JSType.isEquivalent(numberType, numberType));
  }

  // equals() with non-JSType object returns false
  @Test
  public void testEquals_nonJSType_false() throws Throwable {
    assertFalse(numberType.equals("not a type"));
  }

  // equals() with equivalent JSType returns true
  @Test
  public void testEquals_sameInstance_true() throws Throwable {
    assertTrue(numberType.equals(numberType));
  }

  // hashCode() is consistent across calls on same instance
  @Test
  public void testHashCode_consistentAcrossCalls() throws Throwable {
    assertEquals(numberType.hashCode(), numberType.hashCode());
  }

  // canTestForShallowEqualityWith true when this is subtype of that (that.isAllType())
  @Test
  public void testCanTestForShallowEqualityWith_subtypeOfAllType_true() throws Throwable {
    assertTrue(numberType.canTestForShallowEqualityWith(allType));
  }

  // BUG TARGET: isNullable() must reflect that NULL_TYPE is a subtype of this type,
  // i.e. a (number|null) union must be considered nullable.
  @Test
  public void testIsNullable_unionWithNull_true() throws Throwable {
    JSType nullableNumber = registry.createUnionType(numberType, nullType);
    assertTrue(nullableNumber.isNullable());
  }

  // isNullable() false for a plain non-nullable value type
  @Test
  public void testIsNullable_numberType_false() throws Throwable {
    assertFalse(numberType.isNullable());
  }

  // isNullable() true for null type itself
  @Test
  public void testIsNullable_nullType_true() throws Throwable {
    assertTrue(nullType.isNullable());
  }

  // getLeastSupertype: number vs * = * (explicit Javadoc example)
  @Test
  public void testGetLeastSupertype_numberWithAllType_returnsAllType() throws Throwable {
    JSType result = numberType.getLeastSupertype(allType);
    assertTrue(result.isAllType());
  }

  // getLeastSupertype of equivalent types returns same instance (ref rule)
  @Test
  public void testGetLeastSupertype_sameType_returnsSameInstance() throws Throwable {
    JSType result = numberType.getLeastSupertype(numberType);
    assertSame(numberType, result);
  }

  // getGreatestSubtype of equivalent types returns same instance
  @Test
  public void testGetGreatestSubtype_sameType_returnsSameInstance() throws Throwable {
    JSType result = numberType.getGreatestSubtype(numberType);
    assertSame(numberType, result);
  }

  // getGreatestSubtype with unknown type returns the unknown type singleton
  @Test
  public void testGetGreatestSubtype_withUnknownType_returnsUnknownType() throws Throwable {
    JSType result = numberType.getGreatestSubtype(unknownType);
    assertSame(unknownType, result);
  }

  // getRestrictedTypeGivenToBooleanOutcome: null type can never be truthy -> NoType
  @Test
  public void testGetRestrictedTypeGivenToBooleanOutcome_nullTrue_returnsNoType() throws Throwable {
    JSType result = nullType.getRestrictedTypeGivenToBooleanOutcome(true);
    assertTrue(result.isNoType());
  }

  // getRestrictedTypeGivenToBooleanOutcome: void type can never be truthy -> NoType
  @Test
  public void testGetRestrictedTypeGivenToBooleanOutcome_voidTrue_returnsNoType() throws Throwable {
    JSType result = voidType.getRestrictedTypeGivenToBooleanOutcome(true);
    assertTrue(result.isNoType());
  }

  // getRestrictedTypeGivenToBooleanOutcome: Object is always truthy -> false outcome gives NoType
  @Test
  public void testGetRestrictedTypeGivenToBooleanOutcome_objectFalse_returnsNoType() throws Throwable {
    JSType result = objectType.getRestrictedTypeGivenToBooleanOutcome(false);
    assertTrue(result.isNoType());
  }

  // testForEqualityHelper: both empty types -> TRUE
  @Test
  public void testTestForEquality_bothNoType_returnsTrue() throws Throwable {
    assertEquals(TernaryValue.TRUE, noType.testForEquality(noType));
  }

  // testForEqualityHelper: exactly one side empty -> UNKNOWN
  @Test
  public void testTestForEquality_oneSideEmpty_returnsUnknown() throws Throwable {
    assertEquals(TernaryValue.UNKNOWN, noType.testForEquality(numberType));
  }

  // testForEqualityHelper: unknown type short-circuits to UNKNOWN
  @Test
  public void testTestForEquality_unknownType_returnsUnknown() throws Throwable {
    assertEquals(TernaryValue.UNKNOWN, unknownType.testForEquality(numberType));
  }

  // testForEqualityHelper: all type short-circuits to UNKNOWN
  @Test
  public void testTestForEquality_allType_returnsUnknown() throws Throwable {
    assertEquals(TernaryValue.UNKNOWN, allType.testForEquality(numberType));
  }

  // canTestForEqualityWith false when testForEquality() == TRUE
  @Test
  public void testCanTestForEqualityWith_bothNoType_false() throws Throwable {
    assertFalse(noType.canTestForEqualityWith(noType));
  }

  // canTestForEqualityWith true when testForEquality() == UNKNOWN
  @Test
  public void testCanTestForEqualityWith_unknownVsNumber_true() throws Throwable {
    assertTrue(unknownType.canTestForEqualityWith(numberType));
  }

  // getTypesUnderEquality: UNKNOWN case returns pair of (this, that) unchanged
  @Test
  public void testGetTypesUnderEquality_unknownCase_returnsOriginalPair() throws Throwable {
    JSType.TypePair pair = unknownType.getTypesUnderEquality(numberType);
    assertSame(unknownType, pair.typeA);
    assertSame(numberType, pair.typeB);
  }

  // getTypesUnderEquality: TRUE case also returns pair of (this, that) unchanged
  @Test
  public void testGetTypesUnderEquality_trueCase_returnsOriginalPair() throws Throwable {
    JSType.TypePair pair = noType.getTypesUnderEquality(noType);
    assertSame(noType, pair.typeA);
    assertSame(noType, pair.typeB);
  }

  // getTypesUnderInequality: TRUE case returns pair of NoType/NoType
  @Test
  public void testGetTypesUnderInequality_trueCase_returnsNoTypePair() throws Throwable {
    JSType.TypePair pair = noType.getTypesUnderInequality(noType);
    assertSame(noType, pair.typeA);
    assertSame(noType, pair.typeB);
  }

  // getTypesUnderInequality: UNKNOWN case returns pair of (this, that) unchanged
  @Test
  public void testGetTypesUnderInequality_unknownCase_returnsOriginalPair() throws Throwable {
    JSType.TypePair pair = unknownType.getTypesUnderInequality(numberType);
    assertSame(unknownType, pair.typeA);
    assertSame(numberType, pair.typeB);
  }

  // getTypesUnderShallowEquality: commonType computed via getGreatestSubtype
  @Test
  public void testGetTypesUnderShallowEquality_sameType_returnsCommonType() throws Throwable {
    JSType.TypePair pair = numberType.getTypesUnderShallowEquality(numberType);
    assertSame(numberType, pair.typeA);
    assertSame(numberType, pair.typeB);
  }

  // getTypesUnderShallowInequality: both null type -> (null, null)
  @Test
  public void testGetTypesUnderShallowInequality_bothNullType_returnsNullPair() throws Throwable {
    JSType.TypePair pair = nullType.getTypesUnderShallowInequality(nullType);
    assertNull(pair.typeA);
    assertNull(pair.typeB);
  }

  // getTypesUnderShallowInequality: both void type -> (null, null)
  @Test
  public void testGetTypesUnderShallowInequality_bothVoidType_returnsNullPair() throws Throwable {
    JSType.TypePair pair = voidType.getTypesUnderShallowInequality(voidType);
    assertNull(pair.typeA);
    assertNull(pair.typeB);
  }

  // getTypesUnderShallowInequality: mismatched types fall to else branch, pair unchanged
  @Test
  public void testGetTypesUnderShallowInequality_numberVsString_returnsOriginalPair() throws Throwable {
    JSType.TypePair pair = numberType.getTypesUnderShallowInequality(stringType);
    assertSame(numberType, pair.typeA);
    assertSame(stringType, pair.typeB);
  }

  // restrictByNotNullOrUndefined default implementation returns this
  @Test
  public void testRestrictByNotNullOrUndefined_default_returnsThis() throws Throwable {
    assertSame(numberType, numberType.restrictByNotNullOrUndefined());
  }

  // toObjectType() returns null for a non-ObjectType value type
  @Test
  public void testToObjectType_nonObjectType_returnsNull() throws Throwable {
    assertNull(numberType.toObjectType());
  }

  // toObjectType() returns an ObjectType instance for an object-family type
  @Test
  public void testToObjectType_objectType_returnsObjectType() throws Throwable {
    assertTrue(objectType.toObjectType() instanceof ObjectType);
  }

  // canAssignTo delegates to isSubtype; number can assign to the all type
  @Test
  public void testCanAssignTo_subtypeOfAllType_true() throws Throwable {
    assertTrue(numberType.canAssignTo(allType));
  }

  // clearResolved explicitly resets resolved state to false regardless of prior state
  @Test
  public void testClearResolved_thenIsResolved_false() throws Throwable {
    numberType.clearResolved();
    assertFalse(numberType.isResolved());
  }

  // setValidator returns true when the predicate accepts the type
  @Test
  public void testSetValidator_predicateAccepts_true() throws Throwable {
    Predicate<JSType> alwaysTrue = new Predicate<JSType>() {
      public boolean apply(JSType input) {
        return true;
      }
    };
    assertTrue(numberType.setValidator(alwaysTrue));
  }

  // setValidator returns false when the predicate rejects the type
  @Test
  public void testSetValidator_predicateRejects_false() throws Throwable {
    Predicate<JSType> alwaysFalse = new Predicate<JSType>() {
      public boolean apply(JSType input) {
        return false;
      }
    };
    assertFalse(numberType.setValidator(alwaysFalse));
  }

  // TypePair constructor stores given fields exactly
  @Test
  public void testTypePair_constructor_storesFields() throws Throwable {
    JSType.TypePair pair = new JSType.TypePair(numberType, stringType);
    assertSame(numberType, pair.typeA);
    assertSame(stringType, pair.typeB);
  }

  // toDebugHashCodeString formats as "{" + hashCode + "}"
  @Test
  public void testToDebugHashCodeString_format() throws Throwable {
    String expected = "{" + numberType.hashCode() + "}";
    assertEquals(expected, numberType.toDebugHashCodeString());
  }

  // Public static string constants match documented values
  @Test
  public void testStaticStringConstants_values() throws Throwable {
    assertEquals("Unknown class name", JSType.UNKNOWN_NAME);
    assertEquals("Not declared as a constructor", JSType.NOT_A_CLASS);
    assertEquals("Not declared as a type name", JSType.NOT_A_TYPE);
    assertEquals("Named type with empty name component", JSType.EMPTY_TYPE_COMPONENT);
  }

  // ENUMDECL/NOT_ENUMDECL integer flag constants
  @Test
  public void testEnumDeclConstants_values() throws Throwable {
    assertEquals(1, JSType.ENUMDECL);
    assertEquals(0, JSType.NOT_ENUMDECL);
  }
}
