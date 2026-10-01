package com.google.javascript.rhino.jstype;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.IR;

import java.util.Set;

public class PrototypeObjectTypeClaudeTest {

  private JSTypeRegistry registry;

  @Before
  public void setUp() throws Throwable {
    registry = new JSTypeRegistry(null);
  }

  // 3-arg ctor, null prototype, nativeType=false -> defaults to OBJECT_TYPE
  @Test
  public void testConstructor_threeArgNullPrototype_defaultsToObjectType() throws Throwable {
    PrototypeObjectType type = new PrototypeObjectType(registry, "Foo", null);
    ObjectType expected = registry.getNativeObjectType(JSTypeNative.OBJECT_TYPE);
    assertSame(expected, type.getImplicitPrototype());
  }

  // 3-arg ctor, explicit non-null prototype -> uses given prototype as-is
  @Test
  public void testConstructor_threeArgExplicitPrototype_usesGivenPrototype() throws Throwable {
    PrototypeObjectType parent = new PrototypeObjectType(registry, "Parent", null);
    PrototypeObjectType child = new PrototypeObjectType(registry, "Child", parent);
    assertSame(parent, child.getImplicitPrototype());
  }

  // 4-arg ctor, nativeType=true, null prototype -> prototype stays null (branch: nativeType||proto!=null true)
  @Test
  public void testConstructor_fourArgNativeTrueNullPrototype_prototypeStaysNull() throws Throwable {
    PrototypeObjectType type = new PrototypeObjectType(registry, "Native", null, true);
    assertNull(type.getImplicitPrototype());
  }

  // isNativeObjectType reflects the constructor flag for true/false
  @Test
  public void testIsNativeObjectType_reflectsConstructorFlag() throws Throwable {
    PrototypeObjectType nativeObj = new PrototypeObjectType(registry, "N", null, true);
    PrototypeObjectType normalObj = new PrototypeObjectType(registry, "M", null, false);
    assertTrue(nativeObj.isNativeObjectType());
    assertFalse(normalObj.isNativeObjectType());
  }

  // getReferenceName/hasReferenceName with a className set
  @Test
  public void testGetReferenceName_withClassName_returnsClassName() throws Throwable {
    PrototypeObjectType type = new PrototypeObjectType(registry, "Foo", null);
    assertEquals("Foo", type.getReferenceName());
    assertTrue(type.hasReferenceName());
  }

  // getReferenceName/hasReferenceName without className and without owner function
  @Test
  public void testGetReferenceName_withoutClassNameNoOwner_returnsNull() throws Throwable {
    PrototypeObjectType type = new PrototypeObjectType(registry, null, null);
    assertNull(type.getReferenceName());
    assertFalse(type.hasReferenceName());
  }

  // getConstructor always returns null per implementation contract
  @Test
  public void testGetConstructor_alwaysReturnsNull() throws Throwable {
    PrototypeObjectType type = new PrototypeObjectType(registry, "Foo", null);
    assertNull(type.getConstructor());
  }

  // setImplicitPrototype updates value returned by getImplicitPrototype
  @Test
  public void testSetImplicitPrototype_updatesGetImplicitPrototype() throws Throwable {
    PrototypeObjectType type = new PrototypeObjectType(registry, "Foo", null);
    PrototypeObjectType newProto = new PrototypeObjectType(registry, "NewProto", null);
    type.setImplicitPrototype(newProto);
    assertSame(newProto, type.getImplicitPrototype());
  }

  // defineProperty on a fresh name returns true and property becomes observable
  @Test
  public void testDefineProperty_newProperty_returnsTrueAndObservable() throws Throwable {
    PrototypeObjectType type = new PrototypeObjectType(registry, null, null, true);
    JSType t = registry.getNativeObjectType(JSTypeNative.OBJECT_TYPE);
    boolean result = type.defineProperty("foo", t, false, null);
    assertTrue(result);
    assertTrue(type.hasOwnProperty("foo"));
  }

  // defineProperty twice with declared (non-inferred) property returns false the 2nd time
  @Test
  public void testDefineProperty_duplicateDeclaredProperty_returnsFalse() throws Throwable {
    PrototypeObjectType type = new PrototypeObjectType(registry, null, null, true);
    JSType t = registry.getNativeObjectType(JSTypeNative.OBJECT_TYPE);
    assertTrue(type.defineProperty("foo", t, false, null));
    assertFalse(type.defineProperty("foo", t, false, null));
  }

  // inferred property can be overwritten by a later declared definition of same name
  @Test
  public void testDefineProperty_inferredThenDeclaredSameName_overwritesAndReturnsTrue() throws Throwable {
    PrototypeObjectType type = new PrototypeObjectType(registry, null, null, true);
    JSType t = registry.getNativeObjectType(JSTypeNative.OBJECT_TYPE);
    assertTrue(type.defineProperty("foo", t, true, null));
    assertTrue(type.defineProperty("foo", t, false, null));
    assertFalse(type.isPropertyTypeInferred("foo"));
  }

  // removeProperty on existing property returns true and removes it
  @Test
  public void testRemoveProperty_existingProperty_returnsTrueAndRemoves() throws Throwable {
    PrototypeObjectType type = new PrototypeObjectType(registry, null, null, true);
    JSType t = registry.getNativeObjectType(JSTypeNative.OBJECT_TYPE);
    type.defineProperty("foo", t, false, null);
    assertTrue(type.removeProperty("foo"));
    assertFalse(type.hasOwnProperty("foo"));
  }

  // removeProperty on absent property returns false
  @Test
  public void testRemoveProperty_nonExistingProperty_returnsFalse() throws Throwable {
    PrototypeObjectType type = new PrototypeObjectType(registry, null, null, true);
    assertFalse(type.removeProperty("nope"));
  }

  // hasOwnProperty true for defined, false for undefined own property
  @Test
  public void testHasOwnProperty_trueForOwnFalseForOther() throws Throwable {
    PrototypeObjectType type = new PrototypeObjectType(registry, null, null, true);
    JSType t = registry.getNativeObjectType(JSTypeNative.OBJECT_TYPE);
    type.defineProperty("foo", t, false, null);
    assertTrue(type.hasOwnProperty("foo"));
    assertFalse(type.hasOwnProperty("bar"));
  }

  // getOwnPropertyNames reflects exactly the defined own properties
  @Test
  public void testGetOwnPropertyNames_reflectsDefinedProperties() throws Throwable {
    PrototypeObjectType type = new PrototypeObjectType(registry, null, null, true);
    JSType t = registry.getNativeObjectType(JSTypeNative.OBJECT_TYPE);
    type.defineProperty("x", t, false, null);
    type.defineProperty("y", t, false, null);
    Set<String> names = type.getOwnPropertyNames();
    assertTrue(names.contains("x"));
    assertTrue(names.contains("y"));
    assertEquals(2, names.size());
  }

  // hasProperty finds a property defined on the implicit prototype chain
  @Test
  public void testHasProperty_inheritedThroughPrototypeChain_true() throws Throwable {
    PrototypeObjectType parent = new PrototypeObjectType(registry, null, null, true);
    JSType t = registry.getNativeObjectType(JSTypeNative.OBJECT_TYPE);
    parent.defineProperty("shared", t, false, null);
    PrototypeObjectType child = new PrototypeObjectType(registry, null, parent);
    assertTrue(child.hasProperty("shared"));
  }

  // hasProperty returns false when property is absent on object and its isolated chain
  @Test
  public void testHasProperty_notPresentAnywhere_false() throws Throwable {
    PrototypeObjectType parent = new PrototypeObjectType(registry, null, null, true);
    PrototypeObjectType child = new PrototypeObjectType(registry, null, parent);
    assertFalse(child.hasProperty("absent"));
  }

  // getPropertiesCount with no implicit prototype counts only own properties
  @Test
  public void testGetPropertiesCount_noPrototype_countsOwnOnly() throws Throwable {
    PrototypeObjectType type = new PrototypeObjectType(registry, null, null, true);
    JSType t = registry.getNativeObjectType(JSTypeNative.OBJECT_TYPE);
    type.defineProperty("a", t, false, null);
    type.defineProperty("b", t, false, null);
    assertEquals(2, type.getPropertiesCount());
  }

  // getPropertiesCount sums parent and child counts when no name overlap
  @Test
  public void testGetPropertiesCount_withPrototypeChainNoShadow_sumsBoth() throws Throwable {
    PrototypeObjectType parent = new PrototypeObjectType(registry, null, null, true);
    JSType t = registry.getNativeObjectType(JSTypeNative.OBJECT_TYPE);
    parent.defineProperty("a", t, false, null);
    PrototypeObjectType child = new PrototypeObjectType(registry, null, parent);
    child.defineProperty("b", t, false, null);
    assertEquals(2, child.getPropertiesCount());
  }

  // getPropertiesCount does not double count a property shadowed on the child
  @Test
  public void testGetPropertiesCount_withShadowedProperty_doesNotDoubleCount() throws Throwable {
    PrototypeObjectType parent = new PrototypeObjectType(registry, null, null, true);
    JSType t = registry.getNativeObjectType(JSTypeNative.OBJECT_TYPE);
    parent.defineProperty("a", t, false, null);
    PrototypeObjectType child = new PrototypeObjectType(registry, null, parent);
    child.defineProperty("a", t, false, null);
    child.defineProperty("b", t, false, null);
    assertEquals(2, child.getPropertiesCount());
  }

  // isPropertyTypeDeclared/isPropertyTypeInferred correctly distinguish declared vs inferred
  @Test
  public void testIsPropertyTypeDeclaredAndInferred_distinguishesCorrectly() throws Throwable {
    PrototypeObjectType type = new PrototypeObjectType(registry, null, null, true);
    JSType t = registry.getNativeObjectType(JSTypeNative.OBJECT_TYPE);
    type.defineProperty("d", t, false, null);
    type.defineProperty("i", t, true, null);
    assertTrue(type.isPropertyTypeDeclared("d"));
    assertFalse(type.isPropertyTypeInferred("d"));
    assertTrue(type.isPropertyTypeInferred("i"));
    assertFalse(type.isPropertyTypeDeclared("i"));
  }

  // for an undefined property both isPropertyTypeDeclared and isPropertyTypeInferred are false
  @Test
  public void testIsPropertyTypeDeclaredAndInferred_undefinedProperty_bothFalse() throws Throwable {
    PrototypeObjectType type = new PrototypeObjectType(registry, null, null, true);
    assertFalse(type.isPropertyTypeDeclared("nope"));
    assertFalse(type.isPropertyTypeInferred("nope"));
  }

  // getPropertyType on a defined property returns exactly the assigned type
  @Test
  public void testGetPropertyType_definedProperty_returnsAssignedType() throws Throwable {
    PrototypeObjectType type = new PrototypeObjectType(registry, null, null, true);
    JSType t = registry.getNativeObjectType(JSTypeNative.OBJECT_TYPE);
    type.defineProperty("foo", t, false, null);
    assertSame(t, type.getPropertyType("foo"));
  }

  // getPropertyType on an undefined property falls back to UNKNOWN_TYPE
  @Test
  public void testGetPropertyType_undefinedProperty_returnsUnknownType() throws Throwable {
    PrototypeObjectType type = new PrototypeObjectType(registry, null, null, true);
    JSType unknown = registry.getNativeObjectType(JSTypeNative.UNKNOWN_TYPE);
    assertSame(unknown, type.getPropertyType("nope"));
  }

  // getPropertyNode on an own property returns exactly the node passed to defineProperty
  @Test
  public void testGetPropertyNode_ownProperty_returnsSameNode() throws Throwable {
    PrototypeObjectType type = new PrototypeObjectType(registry, null, null, true);
    JSType t = registry.getNativeObjectType(JSTypeNative.OBJECT_TYPE);
    Node node = IR.name("x");
    type.defineProperty("foo", t, false, node);
    assertSame(node, type.getPropertyNode("foo"));
  }

  // getPropertyNode falls back to prototype's node, and null when nowhere defined
  @Test
  public void testGetPropertyNode_fallbackAndNull() throws Throwable {
    PrototypeObjectType parent = new PrototypeObjectType(registry, null, null, true);
    JSType t = registry.getNativeObjectType(JSTypeNative.OBJECT_TYPE);
    Node node = IR.name("y");
    parent.defineProperty("bar", t, false, node);
    PrototypeObjectType child = new PrototypeObjectType(registry, null, parent);
    assertSame(node, child.getPropertyNode("bar"));
    assertNull(child.getPropertyNode("missing"));
  }

  // getOwnPropertyJSDocInfo returns null when no JSDoc was attached and for undefined properties
  @Test
  public void testGetOwnPropertyJSDocInfo_nullCases() throws Throwable {
    PrototypeObjectType type = new PrototypeObjectType(registry, null, null, true);
    JSType t = registry.getNativeObjectType(JSTypeNative.OBJECT_TYPE);
    type.defineProperty("foo", t, false, null);
    assertNull(type.getOwnPropertyJSDocInfo("foo"));
    assertNull(type.getOwnPropertyJSDocInfo("notThere"));
  }

  // setPropertyJSDocInfo with null info is a no-op, does not create the property
  @Test
  public void testSetPropertyJSDocInfo_nullInfo_noOpOnUndefinedProperty() throws Throwable {
    PrototypeObjectType type = new PrototypeObjectType(registry, null, null, true);
    type.setPropertyJSDocInfo("brandNew", null);
    assertFalse(type.hasOwnProperty("brandNew"));
  }

  // matchesObjectContext always returns true
  @Test
  public void testMatchesObjectContext_alwaysTrue() throws Throwable {
    PrototypeObjectType type = new PrototypeObjectType(registry, null, null, true);
    assertTrue(type.matchesObjectContext());
  }

  // canBeCalled is false for a plain (non-RegExp) object
  @Test
  public void testCanBeCalled_falseForPlainObject() throws Throwable {
    PrototypeObjectType type = new PrototypeObjectType(registry, null, null, true);
    assertFalse(type.canBeCalled());
  }

  // matchesNumberContext is false for an isolated native generic object
  @Test
  public void testMatchesNumberContext_falseForIsolatedNativeObject() throws Throwable {
    PrototypeObjectType type = new PrototypeObjectType(registry, null, null, true);
    assertFalse(type.matchesNumberContext());
  }

  // matchesStringContext is false for an isolated native generic object
  @Test
  public void testMatchesStringContext_falseForIsolatedNativeObject() throws Throwable {
    PrototypeObjectType type = new PrototypeObjectType(registry, null, null, true);
    assertFalse(type.matchesStringContext());
  }

  // setPrettyPrint/isPrettyPrint round trip, default false
  @Test
  public void testIsPrettyPrintSetPrettyPrint_roundTrip() throws Throwable {
    PrototypeObjectType type = new PrototypeObjectType(registry, null, null, true);
    assertFalse(type.isPrettyPrint());
    type.setPrettyPrint(true);
    assertTrue(type.isPrettyPrint());
  }

  // toStringHelper for anonymous, non pretty-print object, forAnnotations=false -> "{...}"
  @Test
  public void testToStringHelper_anonymousNoPrettyPrint_forAnnotationsFalse_returnsBraces() throws Throwable {
    PrototypeObjectType type = new PrototypeObjectType(registry, null, null, true);
    assertEquals("{...}", type.toStringHelper(false));
  }

  // toStringHelper for anonymous, non pretty-print object, forAnnotations=true -> "?"
  @Test
  public void testToStringHelper_anonymousNoPrettyPrint_forAnnotationsTrue_returnsQuestionMark() throws Throwable {
    PrototypeObjectType type = new PrototypeObjectType(registry, null, null, true);
    assertEquals("?", type.toStringHelper(true));
  }

  // named type returns its className regardless of forAnnotations value
  @Test
  public void testToStringHelper_namedType_returnsClassNameRegardlessOfAnnotations() throws Throwable {
    PrototypeObjectType named = new PrototypeObjectType(registry, "Foo", null, true);
    assertEquals("Foo", named.toStringHelper(false));
    assertEquals("Foo", named.toStringHelper(true));
  }

  // pretty print on anonymous non-native object with few properties lists property names
  @Test
  public void testToStringHelper_prettyPrintWithProperties_containsPropertyNames() throws Throwable {
    PrototypeObjectType type = new PrototypeObjectType(registry, null, null);
    JSType t = registry.getNativeObjectType(JSTypeNative.OBJECT_TYPE);
    type.defineProperty("a", t, false, null);
    type.defineProperty("b", t, false, null);
    type.setPrettyPrint(true);
    String s = type.toStringHelper(false);
    assertTrue(s.contains("a"));
    assertTrue(s.contains("b"));
    assertTrue(s.startsWith("{"));
  }

  // pretty print with more than MAX_PRETTY_PRINTED_PROPERTIES(4) properties truncates with "..."
  @Test
  public void testToStringHelper_prettyPrintExceedsMax_containsEllipsis() throws Throwable {
    PrototypeObjectType type = new PrototypeObjectType(registry, null, null);
    JSType t = registry.getNativeObjectType(JSTypeNative.OBJECT_TYPE);
    type.defineProperty("p1", t, false, null);
    type.defineProperty("p2", t, false, null);
    type.defineProperty("p3", t, false, null);
    type.defineProperty("p4", t, false, null);
    type.defineProperty("p5", t, false, null);
    type.setPrettyPrint(true);
    assertTrue(type.toStringHelper(false).contains("..."));
  }

  // a plain object that is not a function prototype has empty ctor implemented/extended interfaces
  @Test
  public void testGetCtorInterfaces_notFunctionPrototype_bothEmpty() throws Throwable {
    PrototypeObjectType type = new PrototypeObjectType(registry, "Foo", null);
    int implCount = 0;
    for (ObjectType o : type.getCtorImplementedInterfaces()) {
      implCount++;
    }
    int extCount = 0;
    for (ObjectType o : type.getCtorExtendedInterfaces()) {
      extCount++;
    }
    assertEquals(0, implCount);
    assertEquals(0, extCount);
  }

  // every type is a subtype of itself (reflexivity contract of subtyping)
  @Test
  public void testIsSubtype_reflexive_true() throws Throwable {
    PrototypeObjectType type = new PrototypeObjectType(registry, "Foo", null);
    assertTrue(type.isSubtype(type));
  }

  // any ordinary object (default prototype chain) is a subtype of the Object type
  @Test
  public void testIsSubtype_anyObjectIsSubtypeOfObjectType_true() throws Throwable {
    PrototypeObjectType type = new PrototypeObjectType(registry, "Bar", null);
    ObjectType objectType = registry.getNativeObjectType(JSTypeNative.OBJECT_TYPE);
    assertTrue(type.isSubtype(objectType));
  }

  // matchConstraint on a named (non-anonymous) type is a no-op, leaves properties unchanged
  @Test
  public void testMatchConstraint_namedType_noOpEarlyReturn() throws Throwable {
    PrototypeObjectType type = new PrototypeObjectType(registry, "Named", null);
    ObjectType constraint = registry.getNativeObjectType(JSTypeNative.OBJECT_TYPE);
    int before = type.getPropertiesCount();
    type.matchConstraint(constraint);
    assertEquals(before, type.getPropertiesCount());
  }

  // matchRecordTypeConstraint defines a missing property from constraint as inferred
  @Test
  public void testMatchRecordTypeConstraint_definesInferredPropertyWhenMissing() throws Throwable {
    PrototypeObjectType target = new PrototypeObjectType(registry, null, null);
    PrototypeObjectType constraintObj = new PrototypeObjectType(registry, null, null, true);
    JSType t = registry.getNativeObjectType(JSTypeNative.OBJECT_TYPE);
    constraintObj.defineProperty("foo", t, false, null);
    target.matchRecordTypeConstraint(constraintObj);
    assertTrue(target.hasOwnProperty("foo"));
    assertTrue(target.isPropertyTypeInferred("foo"));
  }
}
