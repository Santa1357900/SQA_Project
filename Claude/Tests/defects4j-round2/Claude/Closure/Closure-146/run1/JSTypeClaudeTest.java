package com.google.javascript.rhino.jstype;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import com.google.javascript.rhino.ErrorReporter;

import org.junit.Before;
import org.junit.Test;

public class JSTypeClaudeTest {

  private JSTypeRegistry registry;
  private JSType numberType;
  private JSType stringType;
  private JSType booleanType;
  private JSType voidType;
  private JSType nullType;
  private JSType allType;
  private JSType noType;
  private JSType noObjectType;
  private JSType unknownType;
  private JSType objectType;

  @Before
  public void setUp() throws Throwable {
    ErrorReporter reporter = new ErrorReporter() {
      public void warning(String message, String sourceName, int line, int lineOffset) {}
      public void error(String message, String sourceName, int line, int lineOffset) {}
    };
    registry = new JSTypeRegistry(reporter);
    numberType = registry.getNativeType(JSTypeNative.NUMBER_TYPE);
    stringType = registry.getNativeType(JSTypeNative.STRING_TYPE);
    booleanType = registry.getNativeType(JSTypeNative.BOOLEAN_TYPE);
    voidType = registry.getNativeType(JSTypeNative.VOID_TYPE);
    nullType = registry.getNativeType(JSTypeNative.NULL_TYPE);
    allType = registry.getNativeType(JSTypeNative.ALL_TYPE);
    noType = registry.getNativeType(JSTypeNative.NO_TYPE);
    noObjectType = registry.getNativeType(JSTypeNative.NO_OBJECT_TYPE);
    unknownType = registry.getNativeType(JSTypeNative.UNKNOWN_TYPE);
    objectType = registry.getNativeType(JSTypeNative.OBJECT_TYPE);
  }

  // ตรวจค่าคงที่ string ทุกตัวตรงตามที่ประกาศในซอร์ส
  @Test
  public void testConstants_haveExpectedStringValues() throws Throwable {
    assertEquals("Unknown class name", JSType.UNKNOWN_NAME);
    assertEquals("Not declared as a constructor", JSType.NOT_A_CLASS);
    assertEquals("Not declared as a type name", JSType.NOT_A_TYPE);
    assertEquals("Named type with empty name component", JSType.EMPTY_TYPE_COMPONENT);
  }

  // ตรวจค่าคงที่ int ENUMDECL / NOT_ENUMDECL
  @Test
  public void testEnumDeclConstants_haveExpectedIntValues() throws Throwable {
    assertEquals(1, JSType.ENUMDECL);
    assertEquals(0, JSType.NOT_ENUMDECL);
  }

  // isEmptyType: branch isNoType() == true
  @Test
  public void testIsEmptyType_noType_isTrue() throws Throwable {
    assertTrue(noType.isEmptyType());
  }

  // isEmptyType: branch isNoObjectType() == true
  @Test
  public void testIsEmptyType_noObjectType_isTrue() throws Throwable {
    assertTrue(noObjectType.isEmptyType());
  }

  // isEmptyType: ทั้งสองเงื่อนไขเป็น false
  @Test
  public void testIsEmptyType_numberType_isFalse() throws Throwable {
    assertFalse(numberType.isEmptyType());
  }

  // isString() อาศัย isSubtype(STRING_VALUE_OR_OBJECT_TYPE)
  @Test
  public void testIsString_stringTypeTrue_numberTypeFalse() throws Throwable {
    assertTrue(stringType.isString());
    assertFalse(numberType.isString());
  }

  // isNumber() อาศัย isSubtype(NUMBER_VALUE_OR_OBJECT_TYPE)
  @Test
  public void testIsNumber_numberTypeTrue_stringTypeFalse() throws Throwable {
    assertTrue(numberType.isNumber());
    assertFalse(stringType.isNumber());
  }

  // isEquivalentTo: object เดียวกัน -> true
  @Test
  public void testIsEquivalentTo_sameInstance_true() throws Throwable {
    assertTrue(numberType.isEquivalentTo(numberType));
  }

  // isEquivalentTo: native type ต่างชนิด -> false
  @Test
  public void testIsEquivalentTo_differentNativeTypes_false() throws Throwable {
    assertFalse(numberType.isEquivalentTo(stringType));
  }

  // isEquivalent static: ทั้งคู่ null -> true (typeA == typeB)
  @Test
  public void testIsEquivalentStatic_bothNull_true() throws Throwable {
    assertTrue(JSType.isEquivalent(null, null));
  }

  // isEquivalent static: หนึ่งใน typeA/typeB เป็น null -> false
  @Test
  public void testIsEquivalentStatic_oneNull_false() throws Throwable {
    assertFalse(JSType.isEquivalent(numberType, null));
  }

  // equals: object ที่ไม่ใช่ JSType -> false
  @Test
  public void testEquals_nonJSTypeObject_false() throws Throwable {
    assertFalse(numberType.equals("not a type"));
  }

  // equals: instance เดียวกัน -> true
  @Test
  public void testEquals_sameInstance_true() throws Throwable {
    assertTrue(numberType.equals(numberType));
  }

  // hashCode ต้องตรงกับ System.identityHashCode(this) ตามโค้ด
  @Test
  public void testHashCode_equalsIdentityHashCode() throws Throwable {
    assertEquals(System.identityHashCode(numberType), numberType.hashCode());
  }

  // matchesInt32Context/matchesUint32Context ต้อง delegate ไปที่ matchesNumberContext()
  @Test
  public void testMatchesInt32AndUint32Context_numberType_true() throws Throwable {
    assertTrue(numberType.matchesInt32Context());
    assertTrue(numberType.matchesUint32Context());
  }

  // canBeCalled: ค่า default เป็น false
  @Test
  public void testCanBeCalled_defaultFalse() throws Throwable {
    assertFalse(allType.canBeCalled());
  }

  // canAssignTo: isSubtype(that) == true -> true (ทุกชนิดเป็น subtype ของ ALL)
  @Test
  public void testCanAssignTo_subtypeOfAll_true() throws Throwable {
    assertTrue(numberType.canAssignTo(allType));
  }

  // canAssignTo: isSubtype(that) == false -> false (number/string ไม่สัมพันธ์กัน)
  @Test
  public void testCanAssignTo_unrelatedTypes_false() throws Throwable {
    assertFalse(numberType.canAssignTo(stringType));
  }

  // autoboxesTo: ค่า default ของ Object type คือ null
  @Test
  public void testAutoboxesTo_objectType_null() throws Throwable {
    assertNull(objectType.autoboxesTo());
  }

  // toObjectType: this instanceof ObjectType -> คืน this
  @Test
  public void testToObjectType_objectType_returnsSelf() throws Throwable {
    assertSame(objectType, objectType.toObjectType());
  }

  // dereference: ไม่มี autobox และไม่ต้อง restrict -> คืน this เอง
  @Test
  public void testDereference_objectType_returnsSelf() throws Throwable {
    assertSame(objectType, objectType.dereference());
  }

  // findPropertyType: autoboxesTo() เป็น null -> คืน null ตรง ๆ
  @Test
  public void testFindPropertyType_objectTypeNoAutobox_null() throws Throwable {
    assertNull(objectType.findPropertyType("anyProp"));
  }

  // canTestForShallowEqualityWith: this.isSubtype(that) เป็น true -> true
  @Test
  public void testCanTestForShallowEqualityWith_subtypeOfAll_true() throws Throwable {
    assertTrue(numberType.canTestForShallowEqualityWith(allType));
  }

  // canTestForShallowEqualityWith: ไม่มีทิศทางใดเป็น subtype -> false
  @Test
  public void testCanTestForShallowEqualityWith_unrelated_false() throws Throwable {
    assertFalse(numberType.canTestForShallowEqualityWith(stringType));
  }

  // isNullable: nullType เป็น subtype ของ NULL_TYPE เอง -> true, numberType -> false
  @Test
  public void testIsNullable_nullTypeTrue_numberTypeFalse() throws Throwable {
    assertTrue(nullType.isNullable());
    assertFalse(numberType.isNullable());
  }

  // getLeastSupertype: thatType.isAllType() -> defer ไปยัง ALL, ผล supertype ต้องเป็น ALL
  @Test
  public void testGetLeastSupertype_withAllType_isAllType() throws Throwable {
    JSType result = numberType.getLeastSupertype(allType);
    assertTrue(result.isAllType());
  }

  // getLeastSupertype: that.isUnionType() -> ต้อง delegate และคืนค่าไม่เป็น null
  @Test
  public void testGetLeastSupertype_withUnionType_notNull() throws Throwable {
    JSType union = registry.createUnionType(stringType, booleanType);
    JSType result = numberType.getLeastSupertype(union);
    assertNotNull(result);
  }

  // getGreatestSubtype: thatType.isEmptyType() -> defer ไปยัง NO_TYPE, ผลต้องเป็น NO_TYPE
  @Test
  public void testGetGreatestSubtype_withNoType_isNoType() throws Throwable {
    JSType result = numberType.getGreatestSubtype(noType);
    assertTrue(result.isNoType());
  }

  // getGreatestSubtype: thisType/thatType.isUnknownType() และไม่เท่ากัน -> UNKNOWN_TYPE
  @Test
  public void testGetGreatestSubtype_withUnknownType_isUnknownType() throws Throwable {
    JSType result = numberType.getGreatestSubtype(unknownType);
    assertTrue(result.isUnknownType());
  }

  // getRestrictedTypeGivenToBooleanOutcome: void มีแค่ {false}; outcome=true ไม่อยู่ในเซต -> NO_TYPE
  @Test
  public void testGetRestrictedTypeGivenToBooleanOutcome_voidTrue_isNoType() throws Throwable {
    JSType result = voidType.getRestrictedTypeGivenToBooleanOutcome(true);
    assertTrue(result.isNoType());
  }

  // getRestrictedTypeGivenToBooleanOutcome: outcome=false อยู่ในเซตของ void -> คืน this
  @Test
  public void testGetRestrictedTypeGivenToBooleanOutcome_voidFalse_returnsSelf() throws Throwable {
    JSType result = voidType.getRestrictedTypeGivenToBooleanOutcome(false);
    assertSame(voidType, result);
  }

  // getTypesUnderEquality: testForEquality == FALSE -> TypePair(null, null)
  @Test
  public void testGetTypesUnderEquality_falseCase_returnsNullPair() throws Throwable {
    JSType.TypePair pair = voidType.getTypesUnderEquality(numberType);
    assertNull(pair.typeA);
    assertNull(pair.typeB);
  }

  // getTypesUnderEquality: testForEquality == TRUE (null == undefined) -> TypePair(this, that)
  @Test
  public void testGetTypesUnderEquality_trueCase_returnsOriginalPair() throws Throwable {
    JSType.TypePair pair = nullType.getTypesUnderEquality(voidType);
    assertSame(nullType, pair.typeA);
    assertSame(voidType, pair.typeB);
  }

  // getTypesUnderEquality: that instanceof UnionType -> ต้องคืน TypePair ที่ไม่เป็น null เสมอ
  @Test
  public void testGetTypesUnderEquality_unionBranch_pairNotNull() throws Throwable {
    JSType union = registry.createUnionType(nullType, voidType);
    JSType.TypePair pair = numberType.getTypesUnderEquality(union);
    assertNotNull(pair);
  }

  // getTypesUnderInequality: testForEquality == TRUE -> TypePair(null, null)
  @Test
  public void testGetTypesUnderInequality_trueCase_returnsNullPair() throws Throwable {
    JSType.TypePair pair = nullType.getTypesUnderInequality(voidType);
    assertNull(pair.typeA);
    assertNull(pair.typeB);
  }

  // getTypesUnderInequality: testForEquality == FALSE -> TypePair(this, that)
  @Test
  public void testGetTypesUnderInequality_falseCase_returnsOriginalPair() throws Throwable {
    JSType.TypePair pair = voidType.getTypesUnderInequality(numberType);
    assertSame(voidType, pair.typeA);
    assertSame(numberType, pair.typeB);
  }

  // getTypesUnderShallowEquality: commonType = getGreatestSubtype(that), ใช้ซ้ำทั้งสองฝั่ง
  @Test
  public void testGetTypesUnderShallowEquality_withNoType_bothNoType() throws Throwable {
    JSType.TypePair pair = numberType.getTypesUnderShallowEquality(noType);
    assertTrue(pair.typeA.isNoType());
    assertSame(pair.typeA, pair.typeB);
  }

  // getTypesUnderShallowInequality: this/that ทั้งคู่เป็น void -> TypePair(null, null)
  @Test
  public void testGetTypesUnderShallowInequality_bothVoid_returnsNullPair() throws Throwable {
    JSType.TypePair pair = voidType.getTypesUnderShallowInequality(voidType);
    assertNull(pair.typeA);
    assertNull(pair.typeB);
  }

  // getTypesUnderShallowInequality: เงื่อนไข null/void ไม่เข้าทั้งคู่ -> TypePair(this, that)
  @Test
  public void testGetTypesUnderShallowInequality_differentTypes_returnsOriginalPair() throws Throwable {
    JSType.TypePair pair = nullType.getTypesUnderShallowInequality(voidType);
    assertSame(nullType, pair.typeA);
    assertSame(voidType, pair.typeB);
  }

  // isSubtype: thatType.isUnknownType() -> ทุกชนิดเป็น subtype ของ unknown
  @Test
  public void testIsSubtype_withUnknownTarget_true() throws Throwable {
    assertTrue(numberType.isSubtype(unknownType));
  }

  // isSubtype: thatType.isAllType() -> ทุกชนิดเป็น subtype ของ ALL
  @Test
  public void testIsSubtype_withAllTarget_true() throws Throwable {
    assertTrue(numberType.isSubtype(allType));
  }

  // differsFrom: ไม่มีฝั่งใดเป็น unknown และ equivalent กัน -> false
  @Test
  public void testDiffersFrom_sameType_false() throws Throwable {
    assertFalse(numberType.differsFrom(numberType));
  }

  // differsFrom: มีฝั่งเดียวเป็น unknown (xor true) -> true
  @Test
  public void testDiffersFrom_oneUnknown_true() throws Throwable {
    assertTrue(numberType.differsFrom(unknownType));
  }

  // differsFrom: ทั้งสองฝั่งเป็น unknown (xor false) -> false
  @Test
  public void testDiffersFrom_bothUnknown_false() throws Throwable {
    assertFalse(unknownType.differsFrom(unknownType));
  }

  // resolve: ครั้งแรก resolveInternal แล้ว cache, ครั้งที่สองต้องคืน object เดิม
  @Test
  public void testResolve_cachesSameInstanceOnSecondCall() throws Throwable {
    JSType first = numberType.resolve(null, null);
    JSType second = numberType.resolve(null, null);
    assertSame(first, second);
    assertTrue(numberType.isResolved());
  }

  // resolve: ผลลัพธ์ต้อง equivalent กับ type เดิมตามสัญญาของ resolve()
  @Test
  public void testResolve_resultEquivalentToOriginal() throws Throwable {
    JSType result = stringType.resolve(null, null);
    assertTrue(result.isEquivalentTo(stringType));
  }

  // clearResolved: ล้างสถานะ resolved กลับเป็น false
  @Test
  public void testClearResolved_resetsFlag() throws Throwable {
    booleanType.resolve(null, null);
    booleanType.clearResolved();
    assertFalse(booleanType.isResolved());
  }

  // forceResolve: ผลลัพธ์ต้อง equivalent กับ type เดิม
  @Test
  public void testForceResolve_returnsEquivalentType() throws Throwable {
    JSType result = objectType.forceResolve(null, null);
    assertTrue(result.isEquivalentTo(objectType));
  }

  // toDebugHashCodeString: รูปแบบ "{" + hashCode() + "}"
  @Test
  public void testToDebugHashCodeString_format() throws Throwable {
    String expected = "{" + numberType.hashCode() + "}";
    assertEquals(expected, numberType.toDebugHashCodeString());
  }

  // TypePair: constructor เก็บค่า typeA/typeB ตรงตามที่ส่งเข้าไป
  @Test
  public void testTypePair_constructor_fieldsSet() throws Throwable {
    JSType.TypePair pair = new JSType.TypePair(numberType, stringType);
    assertSame(numberType, pair.typeA);
    assertSame(stringType, pair.typeB);
  }

  // ล่าบั๊ก: testForEquality ต้องคืนค่า result ที่คำนวณได้จาก union loop ไม่ใช่ null
  @Test
  public void testTestForEquality_unionBothFalse_returnsFalseNotNull() throws Throwable {
    JSType union = registry.createUnionType(numberType, booleanType);
    TernaryValue result = nullType.testForEquality(union);
    assertNotNull(result);
    assertEquals(TernaryValue.FALSE, result);
  }

  // ล่าบั๊ก: ถ้า testForEquality คืน null, canTestForEqualityWith จะ throw NPE ซึ่งผิดสัญญา
  @Test
  public void testCanTestForEqualityWith_unionType_doesNotThrow() throws Throwable {
    JSType union = registry.createUnionType(numberType, booleanType);
    boolean canTest = voidType.canTestForEqualityWith(union);
    assertFalse(canTest);
  }

  // ล่าบั๊ก: ตัวแปร this เป็นอีกชนิดหนึ่ง (string) เทียบกับ union เดียวกัน ยังต้องได้ FALSE ไม่ใช่ NPE
  @Test
  public void testTestForEquality_unionWithStringAsThis_returnsFalseNotNull() throws Throwable {
    JSType union = registry.createUnionType(nullType, voidType);
    TernaryValue result = stringType.testForEquality(union);
    assertNotNull(result);
    assertEquals(TernaryValue.FALSE, result);
  }

}
