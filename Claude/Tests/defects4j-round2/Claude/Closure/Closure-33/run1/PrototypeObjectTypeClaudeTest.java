package com.google.javascript.rhino.jstype;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.google.common.collect.Sets;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;

import java.util.Set;

public class PrototypeObjectTypeClaudeTest {

  private JSTypeRegistry registry;
  private JSType numberType;
  private JSType stringType;
  private ObjectType objectType;

  @Before
  public void setUp() throws Throwable {
    registry = new JSTypeRegistry(null);
    PrototypeObjectType anchor = new PrototypeObjectType(registry, "Anchor", null);
    numberType = anchor.getNativeType(JSTypeNative.NUMBER_TYPE);
    stringType = anchor.getNativeType(JSTypeNative.STRING_TYPE);
    objectType = registry.getNativeObjectType(JSTypeNative.OBJECT_TYPE);
  }

  @Test
  // Covers: implicitPrototype == null && nativeType == false -> defaults to OBJECT_TYPE
  public void testConstructor_nullPrototype_defaultsToObjectType() throws Throwable {
    PrototypeObjectType type = new PrototypeObjectType(registry, "Foo", null);
    assertSame(objectType, type.getImplicitPrototype());
  }

  @Test
  // Covers: implicitPrototype != null branch -> explicit prototype preserved
  public void testConstructor_explicitPrototype_setsPrototype() throws Throwable {
    PrototypeObjectType proto = new PrototypeObjectType(registry, "Proto", null);
    PrototypeObjectType type = new PrototypeObjectType(registry, "Foo", proto);
    assertSame(proto, type.getImplicitPrototype());
  }

  @Test
  // Covers: nativeType == true with null implicitPrototype -> prototype stays null
  public void testConstructor_nativeTrueNullPrototype_allowsNullPrototype() throws Throwable {
    PrototypeObjectType type = new PrototypeObjectType(registry, "Native", null, true);
    assertNull(type.getImplicitPrototype());
    assertTrue(type.isNativeObjectType());
  }

  @Test
  // Covers: properties.containsKey(name) true -> returns own Property
  public void testGetSlot_ownProperty_returnsProperty() throws Throwable {
    PrototypeObjectType type = new PrototypeObjectType(registry, "Foo", null);
    Node node = IR.name("x");
    type.defineProperty("x", numberType, false, node);
    Property p = type.getSlot("x");
    assertNotNull(p);
    assertSame(numberType, p.getType());
  }

  @Test
  // Covers: own property missing but implicitPrototype.getSlot(name) found
  public void testGetSlot_inheritedProperty_returnsFromPrototype() throws Throwable {
    PrototypeObjectType parent = new PrototypeObjectType(registry, "Parent", null, true);
    Node node = IR.name("y");
    parent.defineProperty("y", numberType, false, node);
    PrototypeObjectType child = new PrototypeObjectType(registry, "Child", parent);
    Property p = child.getSlot("y");
    assertNotNull(p);
    assertSame(numberType, p.getType());
  }

  @Test
  // Covers: property not found anywhere -> null
  public void testGetSlot_missingProperty_returnsNull() throws Throwable {
    PrototypeObjectType type = new PrototypeObjectType(registry, "Foo", null, true);
    assertNull(type.getSlot("missing"));
  }

  @Test
  // Covers: getSlot(null) throws NullPointerException (TreeMap-backed property map rejects null keys)
  public void testGetSlot_nullPropertyName_throwsNullPointerException() throws Throwable {
    PrototypeObjectType type = new PrototypeObjectType(registry, "Foo", null);
    try {
      type.getSlot(null);
      fail("expected NullPointerException");
    } catch (NullPointerException expected) {
    }
  }

  @Test
  // Covers: implicitPrototype == null -> return properties.size()
  public void testGetPropertiesCount_noImplicitPrototype_returnsOwnSize() throws Throwable {
    PrototypeObjectType type = new PrototypeObjectType(registry, "Empty", null, true);
    assertEquals(0, type.getPropertiesCount());
    type.defineProperty("a", numberType, false, IR.name("a"));
    assertEquals(1, type.getPropertiesCount());
  }

  @Test
  // Covers: implicitPrototype != null, own property not present on prototype -> counted
  public void testGetPropertiesCount_withNonOverlappingInherited_sumsCounts() throws Throwable {
    PrototypeObjectType parent = new PrototypeObjectType(registry, "Parent", null, true);
    parent.defineProperty("a", numberType, false, IR.name("a"));
    PrototypeObjectType child = new PrototypeObjectType(registry, "Child", parent);
    child.defineProperty("b", numberType, false, IR.name("b"));
    assertEquals(2, child.getPropertiesCount());
  }

  @Test
  // Covers: implicitPrototype != null, own property overlaps prototype's -> not double counted
  public void testGetPropertiesCount_withOverlappingProperty_notDoubleCounted() throws Throwable {
    PrototypeObjectType parent = new PrototypeObjectType(registry, "Parent", null, true);
    parent.defineProperty("a", numberType, false, IR.name("a"));
    PrototypeObjectType child = new PrototypeObjectType(registry, "Child", parent);
    child.defineProperty("a", stringType, false, IR.name("a"));
    assertEquals(1, child.getPropertiesCount());
  }

  @Test
  // Covers: hasProperty true for own, false for missing (non-unknown type path)
  public void testHasProperty_ownAndMissing() throws Throwable {
    PrototypeObjectType type = new PrototypeObjectType(registry, "Foo", null, true);
    type.defineProperty("a", numberType, false, IR.name("a"));
    assertTrue(type.hasProperty("a"));
    assertFalse(type.hasProperty("b"));
  }

  @Test
  // Covers: hasOwnProperty true for own, false for inherited-only property
  public void testHasOwnProperty_trueForOwnFalseForInherited() throws Throwable {
    PrototypeObjectType parent = new PrototypeObjectType(registry, "Parent", null, true);
    parent.defineProperty("a", numberType, false, IR.name("a"));
    PrototypeObjectType child = new PrototypeObjectType(registry, "Child", parent);
    assertFalse(child.hasOwnProperty("a"));
    child.defineProperty("b", numberType, false, IR.name("b"));
    assertTrue(child.hasOwnProperty("b"));
  }

  @Test
  // Covers: getOwnPropertyNames returns only locally defined names, not inherited ones
  public void testGetOwnPropertyNames_returnsOwnOnly() throws Throwable {
    PrototypeObjectType parent = new PrototypeObjectType(registry, "Parent", null, true);
    parent.defineProperty("a", numberType, false, IR.name("a"));
    PrototypeObjectType child = new PrototypeObjectType(registry, "Child", parent);
    child.defineProperty("b", numberType, false, IR.name("b"));
    Set<String> names = child.getOwnPropertyNames();
    assertEquals(1, names.size());
    assertTrue(names.contains("b"));
  }

  @Test
  // Covers: isPropertyTypeDeclared/isPropertyTypeInferred for declared, inferred, and missing properties
  public void testIsPropertyTypeDeclaredAndInferred_variousStates() throws Throwable {
    PrototypeObjectType type = new PrototypeObjectType(registry, "Foo", null, true);
    type.defineProperty("declared", numberType, false, IR.name("declared"));
    type.defineProperty("inferred", numberType, true, IR.name("inferred"));
    assertTrue(type.isPropertyTypeDeclared("declared"));
    assertFalse(type.isPropertyTypeInferred("declared"));
    assertFalse(type.isPropertyTypeDeclared("inferred"));
    assertTrue(type.isPropertyTypeInferred("inferred"));
    assertFalse(type.isPropertyTypeDeclared("missing"));
    assertFalse(type.isPropertyTypeInferred("missing"));
  }

  @Test
  // Covers: collectPropertyNames adds own names and delegates to implicit prototype
  public void testCollectPropertyNames_includesOwnAndInherited() throws Throwable {
    PrototypeObjectType parent = new PrototypeObjectType(registry, "Parent", null, true);
    parent.defineProperty("a", numberType, false, IR.name("a"));
    PrototypeObjectType child = new PrototypeObjectType(registry, "Child", parent);
    child.defineProperty("b", numberType, false, IR.name("b"));
    Set<String> names = Sets.newHashSet();
    child.collectPropertyNames(names);
    assertTrue(names.contains("a"));
    assertTrue(names.contains("b"));
  }

  @Test
  // Covers: getPropertyType missing -> UNKNOWN_TYPE, existing -> defined type
  public void testGetPropertyType_missingReturnsUnknown_existingReturnsDefined() throws Throwable {
    PrototypeObjectType type = new PrototypeObjectType(registry, "Foo", null, true);
    assertTrue(type.getPropertyType("missing").isUnknownType());
    type.defineProperty("a", numberType, false, IR.name("a"));
    assertSame(numberType, type.getPropertyType("a"));
  }

  @Test
  // Covers: isPropertyInExterns with no own property and no implicit prototype -> false
  public void testIsPropertyInExterns_noPropertyNoPrototype_false() throws Throwable {
    PrototypeObjectType type = new PrototypeObjectType(registry, "Foo", null, true);
    assertFalse(type.isPropertyInExterns("missing"));
  }

  @Test
  // Covers: defineProperty on a brand-new name -> true and stored
  public void testDefineProperty_newProperty_true() throws Throwable {
    PrototypeObjectType type = new PrototypeObjectType(registry, "Foo", null, true);
    boolean result = type.defineProperty("a", numberType, false, IR.name("a"));
    assertTrue(result);
    assertTrue(type.hasOwnProperty("a"));
  }

  @Test
  // Covers: defineProperty on an already-declared property -> false
  public void testDefineProperty_duplicateDeclared_false() throws Throwable {
    PrototypeObjectType type = new PrototypeObjectType(registry, "Foo", null, true);
    type.defineProperty("a", numberType, false, IR.name("a"));
    boolean result = type.defineProperty("a", stringType, false, IR.name("a2"));
    assertFalse(result);
  }

  @Test
  // Covers: defineProperty redefining a previously-inferred property succeeds
  public void testDefineProperty_redefineAfterInferred_true() throws Throwable {
    PrototypeObjectType type = new PrototypeObjectType(registry, "Foo", null, true);
    type.defineProperty("a", numberType, true, IR.name("a"));
    boolean result = type.defineProperty("a", stringType, false, IR.name("a2"));
    assertTrue(result);
    assertFalse(type.isPropertyTypeInferred("a"));
    assertSame(stringType, type.getPropertyType("a"));
  }

  @Test
  // Covers: removeProperty on existing property -> true and removed
  public void testRemoveProperty_existing_trueAndGone() throws Throwable {
    PrototypeObjectType type = new PrototypeObjectType(registry, "Foo", null, true);
    type.defineProperty("a", numberType, false, IR.name("a"));
    assertTrue(type.removeProperty("a"));
    assertFalse(type.hasOwnProperty("a"));
  }

  @Test
  // Covers: removeProperty on missing property -> false
  public void testRemoveProperty_missing_false() throws Throwable {
    PrototypeObjectType type = new PrototypeObjectType(registry, "Foo", null, true);
    assertFalse(type.removeProperty("missing"));
  }

  @Test
  // Covers: getPropertyNode returns own property's node
  public void testGetPropertyNode_own_returnsNode() throws Throwable {
    PrototypeObjectType type = new PrototypeObjectType(registry, "Foo", null, true);
    Node node = IR.name("a");
    type.defineProperty("a", numberType, false, node);
    assertSame(node, type.getPropertyNode("a"));
  }

  @Test
  // Covers: getPropertyNode delegates to prototype when missing locally, null when no prototype
  public void testGetPropertyNode_inheritedOrMissing() throws Throwable {
    PrototypeObjectType parent = new PrototypeObjectType(registry, "Parent", null, true);
    Node node = IR.name("a");
    parent.defineProperty("a", numberType, false, node);
    PrototypeObjectType child = new PrototypeObjectType(registry, "Child", parent);
    assertSame(node, child.getPropertyNode("a"));
    assertNull(parent.getPropertyNode("missing"));
  }

  @Test
  // Covers: getOwnPropertyJSDocInfo with no info set -> null
  public void testGetOwnPropertyJSDocInfo_none_null() throws Throwable {
    PrototypeObjectType type = new PrototypeObjectType(registry, "Foo", null, true);
    type.defineProperty("a", numberType, false, IR.name("a"));
    assertNull(type.getOwnPropertyJSDocInfo("a"));
  }

  @Test
  // Covers: setPropertyJSDocInfo with null info -> no-op, property still undefined
  public void testSetPropertyJSDocInfo_nullInfo_noop() throws Throwable {
    PrototypeObjectType type = new PrototypeObjectType(registry, "Foo", null, true);
    type.setPropertyJSDocInfo("missing", null);
    assertFalse(type.hasOwnProperty("missing"));
  }

  @Test
  // Covers: plain object without overrides is neither number-context nor string-context
  public void testMatchesNumberAndStringContext_plainObject_false() throws Throwable {
    PrototypeObjectType type = new PrototypeObjectType(registry, "Plain", null);
    assertFalse(type.matchesNumberContext());
    assertFalse(type.matchesStringContext());
  }

  @Test
  // Covers: overriding valueOf with a different type makes matchesNumberContext true
  public void testMatchesNumberContext_overriddenValueOf_true() throws Throwable {
    PrototypeObjectType type = new PrototypeObjectType(registry, "Plain", null);
    type.defineProperty("valueOf", numberType, false, IR.name("valueOf"));
    assertTrue(type.matchesNumberContext());
  }

  @Test
  // Covers: overriding toString with a different type makes matchesStringContext true
  public void testMatchesStringContext_overriddenToString_true() throws Throwable {
    PrototypeObjectType type = new PrototypeObjectType(registry, "Plain", null);
    type.defineProperty("toString", stringType, false, IR.name("toString"));
    assertTrue(type.matchesStringContext());
  }

  @Test
  // Covers: matchesObjectContext always returns true
  public void testMatchesObjectContext_alwaysTrue() throws Throwable {
    PrototypeObjectType type = new PrototypeObjectType(registry, "Plain", null);
    assertTrue(type.matchesObjectContext());
  }

  @Test
  // Covers: canBeCalled is false for non-regexp plain object
  public void testCanBeCalled_nonRegexp_false() throws Throwable {
    PrototypeObjectType type = new PrototypeObjectType(registry, "Plain", null);
    assertFalse(type.canBeCalled());
  }

  @Test
  // Covers: toStringHelper returns reference name when className is set, for both forAnnotations values
  public void testToStringHelper_withReferenceName() throws Throwable {
    PrototypeObjectType type = new PrototypeObjectType(registry, "Foo", null);
    assertEquals("Foo", type.toStringHelper(false));
    assertEquals("Foo", type.toStringHelper(true));
  }

  @Test
  // Covers: anonymous, not pretty-printing -> "{...}" or "?" depending on forAnnotations
  public void testToStringHelper_anonymousNotPrettyPrint() throws Throwable {
    PrototypeObjectType type = new PrototypeObjectType(registry, null, null);
    assertEquals("{...}", type.toStringHelper(false));
    assertEquals("?", type.toStringHelper(true));
  }

  @Test
  // Covers: anonymous + prettyPrint true + no properties -> "{}"
  public void testToStringHelper_anonymousPrettyPrintNoProperties() throws Throwable {
    PrototypeObjectType type = new PrototypeObjectType(registry, null, null);
    type.setPrettyPrint(true);
    assertEquals("{}", type.toStringHelper(false));
  }

  @Test
  // Covers: prettyPrint with own properties lists property names sorted alphabetically
  public void testToStringHelper_prettyPrintWithPropertiesSorted() throws Throwable {
    PrototypeObjectType type = new PrototypeObjectType(registry, null, null);
    type.defineProperty("b", numberType, false, IR.name("b"));
    type.defineProperty("a", numberType, false, IR.name("a"));
    type.setPrettyPrint(true);
    String s = type.toStringHelper(false);
    assertTrue(s.indexOf("a: ") < s.indexOf("b: "));
  }

  @Test
  // Covers: setPrettyPrint/isPrettyPrint round trip
  public void testSetIsPrettyPrint_roundTrip() throws Throwable {
    PrototypeObjectType type = new PrototypeObjectType(registry, "Foo", null);
    assertFalse(type.isPrettyPrint());
    type.setPrettyPrint(true);
    assertTrue(type.isPrettyPrint());
  }

  @Test
  // Covers: getConstructor always returns null
  public void testGetConstructor_alwaysNull() throws Throwable {
    PrototypeObjectType type = new PrototypeObjectType(registry, "Foo", null);
    assertNull(type.getConstructor());
  }

  @Test
  // Covers: getImplicitPrototype/setImplicitPrototype round trip
  public void testGetImplicitPrototype_setImplicitPrototype() throws Throwable {
    PrototypeObjectType type = new PrototypeObjectType(registry, "Foo", null);
    PrototypeObjectType newProto = new PrototypeObjectType(registry, "NewProto", null);
    type.setImplicitPrototype(newProto);
    assertSame(newProto, type.getImplicitPrototype());
  }

  @Test
  // Covers: getReferenceName/hasReferenceName for named and anonymous classes
  public void testGetReferenceName_hasReferenceName_namedAndAnonymous() throws Throwable {
    PrototypeObjectType named = new PrototypeObjectType(registry, "Foo", null);
    assertEquals("Foo", named.getReferenceName());
    assertTrue(named.hasReferenceName());
    PrototypeObjectType anon = new PrototypeObjectType(registry, null, null);
    assertNull(anon.getReferenceName());
    assertFalse(anon.hasReferenceName());
  }

  @Test
  // Covers: isSubtype is reflexive
  public void testIsSubtype_reflexive_true() throws Throwable {
    PrototypeObjectType type = new PrototypeObjectType(registry, "T", null);
    assertTrue(type.isSubtype(type));
  }

  @Test
  // Covers: isSubtype true when the other type is in this type's implicit prototype chain
  public void testIsSubtype_prototypeChain_true() throws Throwable {
    PrototypeObjectType parent = new PrototypeObjectType(registry, "Parent", null);
    PrototypeObjectType child = new PrototypeObjectType(registry, "Child", parent);
    assertTrue(child.isSubtype(parent));
  }

  @Test
  // Covers: isSubtype false for unrelated sibling types
  public void testIsSubtype_unrelated_false() throws Throwable {
    PrototypeObjectType a = new PrototypeObjectType(registry, "A", null);
    PrototypeObjectType b = new PrototypeObjectType(registry, "B", null);
    assertFalse(a.isSubtype(b));
  }

  @Test
  // Covers: isNativeObjectType reflects the constructor's nativeType flag
  public void testIsNativeObjectType_trueFalse() throws Throwable {
    PrototypeObjectType nativeType = new PrototypeObjectType(registry, "N", null, true);
    assertTrue(nativeType.isNativeObjectType());
    PrototypeObjectType normalType = new PrototypeObjectType(registry, "M", null, false);
    assertFalse(normalType.isNativeObjectType());
  }

  @Test
  // Covers: getCtorImplementedInterfaces/getCtorExtendedInterfaces empty and getOwnerFunction null when no owner
  public void testGetCtorInterfacesAndOwnerFunction_noOwner_emptyAndNull() throws Throwable {
    PrototypeObjectType type = new PrototypeObjectType(registry, "T", null);
    assertFalse(type.getCtorImplementedInterfaces().iterator().hasNext());
    assertFalse(type.getCtorExtendedInterfaces().iterator().hasNext());
    assertNull(type.getOwnerFunction());
  }

  @Test
  // Covers: matchConstraint is a no-op when the constraint object is not a record type
  public void testMatchConstraint_nonRecordType_noop() throws Throwable {
    PrototypeObjectType target = new PrototypeObjectType(registry, "Target", null, true);
    PrototypeObjectType constraint = new PrototypeObjectType(registry, "Constraint", null, true);
    int before = target.getPropertiesCount();
    target.matchConstraint(constraint);
    assertEquals(before, target.getPropertiesCount());
  }
}
