package com.google.javascript.rhino.jstype;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.HashMap;
import java.util.Map;

import com.google.javascript.rhino.jstype.RecordTypeBuilder.RecordProperty;

public class RecordTypeClaudeTest {

  private JSTypeRegistry registry;

  @Before
  public void setUp() throws Throwable {
    registry = new JSTypeRegistry(null);
  }

  private JSType numberType() {
    return registry.getNativeType(JSTypeNative.NUMBER_TYPE);
  }

  private JSType stringType() {
    return registry.getNativeType(JSTypeNative.STRING_TYPE);
  }

  private JSType unknownType() {
    return registry.getNativeType(JSTypeNative.UNKNOWN_TYPE);
  }

  private RecordType oneProp(String name, JSType type) {
    RecordTypeBuilder builder = new RecordTypeBuilder(registry);
    builder.addProperty(name, type, null);
    JSType result = builder.build();
    return result.toMaybeRecordType();
  }

  private RecordType twoProps(String n1, JSType t1, String n2, JSType t2) {
    RecordTypeBuilder builder = new RecordTypeBuilder(registry);
    builder.addProperty(n1, t1, null);
    builder.addProperty(n2, t2, null);
    JSType result = builder.build();
    return result.toMaybeRecordType();
  }

  // Constructor branch: empty properties map -> loop runs 0 times, no exception.
  @Test
  public void testConstructor_emptyProperties_noPropertiesDefined() throws Throwable {
    Map<String, RecordProperty> props = new HashMap<String, RecordProperty>();
    RecordType record = new RecordType(registry, props);
    assertFalse(record.hasProperty("a"));
  }

  // Constructor branch: null RecordProperty value must throw IllegalStateException.
  @Test
  public void testConstructor_nullRecordProperty_throwsIllegalStateException() throws Throwable {
    Map<String, RecordProperty> props = new HashMap<String, RecordProperty>();
    props.put("a", null);
    try {
      new RecordType(registry, props);
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }

  // Constructor branch: loop runs multiple times, all properties defined.
  @Test
  public void testConstructor_multipleProperties_allDefined() throws Throwable {
    RecordType record = twoProps("a", numberType(), "b", stringType());
    assertTrue(record.hasProperty("a"));
    assertTrue(record.hasProperty("b"));
  }

  // isEquivalentTo: other is not a record type -> false.
  @Test
  public void testIsEquivalentTo_otherNotRecordType_returnsFalse() throws Throwable {
    RecordType record = oneProp("a", numberType());
    assertFalse(record.isEquivalentTo(numberType()));
  }

  // isEquivalentTo: same instance short-circuit -> true.
  @Test
  public void testIsEquivalentTo_sameInstance_returnsTrue() throws Throwable {
    RecordType record = oneProp("a", numberType());
    assertTrue(record.isEquivalentTo(record));
  }

  // isEquivalentTo: different key sets -> false.
  @Test
  public void testIsEquivalentTo_differentKeySets_returnsFalse() throws Throwable {
    RecordType r1 = oneProp("a", numberType());
    RecordType r2 = oneProp("b", numberType());
    assertFalse(r1.isEquivalentTo(r2));
  }

  // isEquivalentTo: same keys but different property types -> false.
  @Test
  public void testIsEquivalentTo_sameKeysDifferentTypes_returnsFalse() throws Throwable {
    RecordType r1 = oneProp("a", numberType());
    RecordType r2 = oneProp("a", stringType());
    assertFalse(r1.isEquivalentTo(r2));
  }

  // isEquivalentTo: same keys, same property types -> true.
  @Test
  public void testIsEquivalentTo_sameKeysSameTypes_returnsTrue() throws Throwable {
    RecordType r1 = oneProp("a", numberType());
    RecordType r2 = oneProp("a", numberType());
    assertTrue(r1.isEquivalentTo(r2));
  }

  // getImplicitPrototype always returns the native OBJECT_TYPE.
  @Test
  public void testGetImplicitPrototype_returnsObjectType() throws Throwable {
    RecordType record = oneProp("a", numberType());
    ObjectType proto = record.getImplicitPrototype();
    assertSame(registry.getNativeObjectType(JSTypeNative.OBJECT_TYPE), proto);
  }

  // defineProperty: after construction record is frozen -> returns false, property not added.
  @Test
  public void testDefineProperty_afterConstruction_returnsFalseAndDoesNotAdd() throws Throwable {
    RecordType record = oneProp("a", numberType());
    boolean result = record.defineProperty("b", stringType(), false, null);
    assertFalse(result);
    assertFalse(record.hasProperty("b"));
  }

  // getLeastSupertype: that is not a record type -> delegates, result must be supertype of both.
  @Test
  public void testGetLeastSupertype_nonRecordType_resultIsSupertypeOfBoth() throws Throwable {
    RecordType record = oneProp("a", numberType());
    JSType that = numberType();
    JSType result = record.getLeastSupertype(that);
    assertTrue(record.isSubtype(result));
    assertTrue(that.isSubtype(result));
  }

  // getLeastSupertype: common property with equivalent type is kept in the result.
  @Test
  public void testGetLeastSupertype_commonPropertyEquivalentType_keepsCommonProperty() throws Throwable {
    RecordType small = oneProp("a", numberType());
    RecordType big = twoProps("a", numberType(), "b", stringType());
    JSType result = small.getLeastSupertype(big);
    RecordType resultRecord = result.toMaybeRecordType();
    assertTrue(resultRecord.isEquivalentTo(small));
  }

  // getLeastSupertype: conflicting property type for same name -> property dropped from result.
  @Test
  public void testGetLeastSupertype_conflictingPropertyType_commonPropertyDropped() throws Throwable {
    RecordType r1 = oneProp("a", numberType());
    RecordType r2 = oneProp("a", stringType());
    JSType result = r1.getLeastSupertype(r2);
    RecordType resultRecord = result.toMaybeRecordType();
    assertFalse(resultRecord.hasProperty("a"));
  }

  // getLeastSupertype: joining a record with itself keeps all of its properties.
  @Test
  public void testGetLeastSupertype_withItself_returnsEquivalentRecord() throws Throwable {
    RecordType record = oneProp("a", numberType());
    JSType result = record.getLeastSupertype(record);
    assertTrue(result.toMaybeRecordType().isEquivalentTo(record));
  }

  // getGreatestSubtypeHelper: disjoint property names -> result unions both properties.
  @Test
  public void testGetGreatestSubtypeHelper_disjointProperties_unionsProperties() throws Throwable {
    RecordType r1 = oneProp("a", numberType());
    RecordType r2 = oneProp("b", stringType());
    JSType result = r1.getGreatestSubtypeHelper(r2);
    RecordType resultRecord = result.toMaybeRecordType();
    assertTrue(resultRecord.hasProperty("a"));
    assertTrue(resultRecord.hasProperty("b"));
  }

  // getGreatestSubtypeHelper: overlapping property with equivalent type is included once.
  @Test
  public void testGetGreatestSubtypeHelper_overlappingEquivalentProperty_includesProperty() throws Throwable {
    RecordType r1 = twoProps("a", numberType(), "b", stringType());
    RecordType r2 = oneProp("a", numberType());
    JSType result = r1.getGreatestSubtypeHelper(r2);
    RecordType resultRecord = result.toMaybeRecordType();
    assertTrue(resultRecord.hasProperty("a"));
    assertTrue(resultRecord.hasProperty("b"));
  }

  // getGreatestSubtypeHelper: conflicting property type between two record types has no common
  // subtype; since both operands are object-like record types, the result must be the bottom
  // object type (NO_OBJECT_TYPE), matching the NO_OBJECT_TYPE bottom used elsewhere in this
  // same method for the non-record branch.
  @Test
  public void testGetGreatestSubtypeHelper_conflictingPropertyType_returnsNoObjectType() throws Throwable {
    RecordType r1 = oneProp("a", numberType());
    RecordType r2 = oneProp("a", stringType());
    JSType result = r1.getGreatestSubtypeHelper(r2);
    assertSame(registry.getNativeObjectType(JSTypeNative.NO_OBJECT_TYPE), result);
  }

  // getGreatestSubtypeHelper: non-record "that" with no registered reference type sharing the
  // property name -> result stays at the initial NO_OBJECT_TYPE value.
  @Test
  public void testGetGreatestSubtypeHelper_nonRecordType_returnsNoObjectTypeDefault() throws Throwable {
    RecordType record = oneProp("a", numberType());
    JSType result = record.getGreatestSubtypeHelper(numberType());
    assertSame(registry.getNativeType(JSTypeNative.NO_OBJECT_TYPE), result);
  }

  // toMaybeRecordType always returns the same instance.
  @Test
  public void testToMaybeRecordType_returnsSelf() throws Throwable {
    RecordType record = oneProp("a", numberType());
    assertSame(record, record.toMaybeRecordType());
  }

  // isSubtype: reflexivity, a record is a subtype of itself.
  @Test
  public void testIsSubtype_sameInstance_returnsTrue() throws Throwable {
    RecordType record = oneProp("a", numberType());
    assertTrue(record.isSubtype(record));
  }

  // isSubtype: OBJECT_TYPE is the top of record types -> any record is its subtype.
  @Test
  public void testIsSubtype_objectTypeIsSuperTypeOfAnyRecord_returnsTrue() throws Throwable {
    RecordType record = oneProp("a", numberType());
    JSType objectType = registry.getNativeObjectType(JSTypeNative.OBJECT_TYPE);
    assertTrue(record.isSubtype(objectType));
  }

  // isSubtype: "that" is not a record type and OBJECT_TYPE is not its subtype -> false.
  @Test
  public void testIsSubtype_nonRecordNonSuperOfObject_returnsFalse() throws Throwable {
    RecordType record = oneProp("a", numberType());
    assertFalse(record.isSubtype(numberType()));
  }

  // isSubtype (static helper via instance): a record with more properties is a subtype of a
  // record with fewer, compatible properties (per class javadoc example).
  @Test
  public void testIsSubtype_moreSpecificRecordIsSubtypeOfLessSpecific_returnsTrue() throws Throwable {
    RecordType big = twoProps("a", numberType(), "b", stringType());
    RecordType small = oneProp("a", numberType());
    assertTrue(big.isSubtype(small));
  }

  // isSubtype (static helper): typeA missing a property declared on typeB -> false.
  @Test
  public void testIsSubtype_missingPropertyMakesNotSubtype_returnsFalse() throws Throwable {
    RecordType small = oneProp("a", numberType());
    RecordType big = twoProps("a", numberType(), "c", stringType());
    assertFalse(small.isSubtype(big));
  }

  // isSubtype (static helper): declared property type differs from required type -> false.
  @Test
  public void testIsSubtype_declaredPropertyTypeMismatch_returnsFalse() throws Throwable {
    RecordType r1 = oneProp("a", stringType());
    RecordType r2 = oneProp("a", numberType());
    assertFalse(r1.isSubtype(r2));
  }

  // isSubtype (static helper): unknown property type on either side skips the type comparison.
  @Test
  public void testIsSubtype_unknownPropertyTypeSkipsCheck_returnsTrue() throws Throwable {
    RecordType r1 = oneProp("a", unknownType());
    RecordType r2 = oneProp("a", stringType());
    assertTrue(r1.isSubtype(r2));
  }
}
