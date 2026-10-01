package com.google.javascript.rhino.jstype;

import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;

import com.google.javascript.rhino.ErrorReporter;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;

import java.util.HashSet;
import java.util.Set;

public class PrototypeObjectTypeClaudeTest {

  private JSTypeRegistry registry;

  @Before
  public void setUp() throws Throwable {
    ErrorReporter reporter = new ErrorReporter() {
      public void warning(String message, String sourceName, int line, int lineOffset) {}
      public void error(String message, String sourceName, int line, int lineOffset) {}
    };
    registry = new JSTypeRegistry(reporter);
  }

  // Covers: implicitPrototype == null branch -> defaults to native OBJECT_TYPE
  @Test
  public void testConstructor_nullImplicitPrototype_defaultsToNativeObjectType() throws Throwable {
    PrototypeObjectType obj = new PrototypeObjectType(registry, "Foo", null);
    ObjectType nativeObject = registry.getNativeObjectType(JSTypeNative.OBJECT_TYPE);
    assertSame(nativeObject, obj.getImplicitPrototype());
  }

  // Covers: explicit implicitPrototype argument is preserved as-is
  @Test
  public void testConstructor_explicitImplicitPrototype_isUsed() throws Throwable {
    PrototypeObjectType parent = new PrototypeObjectType(registry, "Parent", null);
    PrototypeObjectType child = new PrototypeObjectType(registry, "Child", parent);
    assertSame(parent, child.getImplicitPrototype());
  }

  // Covers: nativeType=true with null implicitPrototype keeps prototype null (no default)
  @Test
  public void testConstructor_nativeTypeTrueWithNullPrototype_prototypeIsNull() throws Throwable {
    PrototypeObjectType obj = new PrototypeObjectType(registry, "Base", null, true);
    assertNull(obj.getImplicitPrototype());
  }

  // Covers: isNativeObjectType reflects the constructor flag
  @Test
  public void testIsNativeObjectType_trueForNativeFalseForNonNative() throws Throwable {
    PrototypeObjectType nativeObj = new PrototypeObjectType(registry, "N", null, true);
    PrototypeObjectType nonNative = new PrototypeObjectType(registry, "NN", null, false);
    assertTrue(nativeObj.isNativeObjectType());
    assertFalse(nonNative.isNativeObjectType());
  }

  // Covers: getSlot finds own property before checking prototype
  @Test
  public void testGetSlot_ownProperty_found() throws Throwable {
    PrototypeObjectType obj = new PrototypeObjectType(registry, "Base", null, true);
    JSType type = obj.getNativeType(JSTypeNative.NUMBER_TYPE);
    obj.defineProperty("x", type, false, null);
    Property p = obj.getSlot("x");
    assertNotNull(p);
    assertSame(type, p.getType());
  }

  // Covers: getSlot falls back to implicit prototype chain when not found locally
  @Test
  public void testGetSlot_inheritedProperty_foundViaPrototype() throws Throwable {
    PrototypeObjectType parent = new PrototypeObjectType(registry, "Parent", null, true);
    JSType type = parent.getNativeType(JSTypeNative.NUMBER_TYPE);
    parent.defineProperty("x", type, false, null);
    PrototypeObjectType child = new PrototypeObjectType(registry, "Child", parent);
    Property p = child.getSlot("x");
    assertNotNull(p);
    assertSame(type, p.getType());
  }

  // Covers: getSlot returns null when property absent anywhere in the chain
  @Test
  public void testGetSlot_missingProperty_returnsNull() throws Throwable {
    PrototypeObjectType obj = new PrototypeObjectType(registry, "Base", null, true);
    assertNull(obj.getSlot("missing"));
  }

  // Covers: getPropertiesCount with null implicit prototype returns own size
  @Test
  public void testGetPropertiesCount_noImplicitPrototype_returnsOwnSize() throws Throwable {
    PrototypeObjectType obj = new PrototypeObjectType(registry, "Base", null, true);
    JSType type = obj.getNativeType(JSTypeNative.NUMBER_TYPE);
    obj.defineProperty("a", type, false, null);
    obj.defineProperty("b", type, false, null);
    assertEquals(2, obj.getPropertiesCount());
  }

  // Covers: getPropertiesCount sums parent and child counts when no overlap
  @Test
  public void testGetPropertiesCount_withNonOverlappingPrototype_sumsCounts() throws Throwable {
    PrototypeObjectType parent = new PrototypeObjectType(registry, "Parent", null, true);
    JSType type = parent.getNativeType(JSTypeNative.NUMBER_TYPE);
    parent.defineProperty("a", type, false, null);
    PrototypeObjectType child = new PrototypeObjectType(registry, "Child", parent);
    child.defineProperty("b", type, false, null);
    assertEquals(2, child.getPropertiesCount());
  }

  // Covers: getPropertiesCount does not double count a property overridden in child
  @Test
  public void testGetPropertiesCount_withOverlappingProperty_doesNotDoubleCount() throws Throwable {
    PrototypeObjectType parent = new PrototypeObjectType(registry, "Parent", null, true);
    JSType type = parent.getNativeType(JSTypeNative.NUMBER_TYPE);
    parent.defineProperty("a", type, false, null);
    PrototypeObjectType child = new PrototypeObjectType(registry, "Child", parent);
    child.defineProperty("a", type, false, null);
    child.defineProperty("b", type, false, null);
    assertEquals(2, child.getPropertiesCount());
  }

  // Covers: hasProperty true for own property
  @Test
  public void testHasProperty_ownProperty_true() throws Throwable {
    PrototypeObjectType obj = new PrototypeObjectType(registry, "Base", null, true);
    JSType type = obj.getNativeType(JSTypeNative.NUMBER_TYPE);
    obj.defineProperty("a", type, false, null);
    assertTrue(obj.hasProperty("a"));
  }

  // Covers: hasProperty true for property inherited via the prototype chain
  @Test
  public void testHasProperty_inheritedProperty_true() throws Throwable {
    PrototypeObjectType parent = new PrototypeObjectType(registry, "Parent", null, true);
    JSType type = parent.getNativeType(JSTypeNative.NUMBER_TYPE);
    parent.defineProperty("a", type, false, null);
    PrototypeObjectType child = new PrototypeObjectType(registry, "Child", parent);
    assertTrue(child.hasProperty("a"));
  }

  // Covers: hasProperty false when the property does not exist anywhere
  @Test
  public void testHasProperty_missingProperty_false() throws Throwable {
    PrototypeObjectType obj = new PrototypeObjectType(registry, "Base", null, true);
    assertFalse(obj.hasProperty("missing"));
  }

  // Covers: hasOwnProperty distinguishes own property from inherited property
  @Test
  public void testHasOwnProperty_ownTrue_inheritedFalse() throws Throwable {
    PrototypeObjectType parent = new PrototypeObjectType(registry, "Parent", null, true);
    JSType type = parent.getNativeType(JSTypeNative.NUMBER_TYPE);
    parent.defineProperty("a", type, false, null);
    PrototypeObjectType child = new PrototypeObjectType(registry, "Child", parent);
    assertTrue(parent.hasOwnProperty("a"));
    assertFalse(child.hasOwnProperty("a"));
  }

  // Covers: getOwnPropertyNames returns only local names, not inherited ones
  @Test
  public void testGetOwnPropertyNames_onlyOwnNames() throws Throwable {
    PrototypeObjectType parent = new PrototypeObjectType(registry, "Parent", null, true);
    JSType type = parent.getNativeType(JSTypeNative.NUMBER_TYPE);
    parent.defineProperty("a", type, false, null);
    PrototypeObjectType child = new PrototypeObjectType(registry, "Child", parent);
    child.defineProperty("b", type, false, null);
    Set<String> names = child.getOwnPropertyNames();
    assertTrue(names.contains("b"));
    assertFalse(names.contains("a"));
  }

  // Covers: isPropertyTypeDeclared true when property declared (not inferred)
  @Test
  public void testIsPropertyTypeDeclared_declaredTrue() throws Throwable {
    PrototypeObjectType obj = new PrototypeObjectType(registry, "Base", null, true);
    JSType type = obj.getNativeType(JSTypeNative.NUMBER_TYPE);
    obj.defineProperty("a", type, false, null);
    assertTrue(obj.isPropertyTypeDeclared("a"));
  }

  // Covers: isPropertyTypeDeclared false when the property is inferred
  @Test
  public void testIsPropertyTypeDeclared_inferredFalse() throws Throwable {
    PrototypeObjectType obj = new PrototypeObjectType(registry, "Base", null, true);
    JSType type = obj.getNativeType(JSTypeNative.NUMBER_TYPE);
    obj.defineProperty("a", type, true, null);
    assertFalse(obj.isPropertyTypeDeclared("a"));
  }

  // Covers: isPropertyTypeDeclared false when slot is null (property missing)
  @Test
  public void testIsPropertyTypeDeclared_missingFalse() throws Throwable {
    PrototypeObjectType obj = new PrototypeObjectType(registry, "Base", null, true);
    assertFalse(obj.isPropertyTypeDeclared("missing"));
  }

  // Covers: isPropertyTypeInferred mirrors isTypeInferred of the slot
  @Test
  public void testIsPropertyTypeInferred_inferredTrue_declaredFalse() throws Throwable {
    PrototypeObjectType obj = new PrototypeObjectType(registry, "Base", null, true);
    JSType type = obj.getNativeType(JSTypeNative.NUMBER_TYPE);
    obj.defineProperty("inf", type, true, null);
    obj.defineProperty("dec", type, false, null);
    assertTrue(obj.isPropertyTypeInferred("inf"));
    assertFalse(obj.isPropertyTypeInferred("dec"));
  }

  // Covers: collectPropertyNames gathers own and inherited property names
  @Test
  public void testCollectPropertyNames_includesInherited() throws Throwable {
    PrototypeObjectType parent = new PrototypeObjectType(registry, "Parent", null, true);
    JSType type = parent.getNativeType(JSTypeNative.NUMBER_TYPE);
    parent.defineProperty("a", type, false, null);
    PrototypeObjectType child = new PrototypeObjectType(registry, "Child", parent);
    child.defineProperty("b", type, false, null);
    Set<String> props = new HashSet<String>();
    child.collectPropertyNames(props);
    assertTrue(props.contains("a"));
    assertTrue(props.contains("b"));
  }

  // Covers: getPropertyType returns declared type when property exists
  @Test
  public void testGetPropertyType_defined_returnsType() throws Throwable {
    PrototypeObjectType obj = new PrototypeObjectType(registry, "Base", null, true);
    JSType type = obj.getNativeType(JSTypeNative.NUMBER_TYPE);
    obj.defineProperty("a", type, false, null);
    assertSame(type, obj.getPropertyType("a"));
  }

  // Covers: getPropertyType returns UNKNOWN_TYPE when property undefined
  @Test
  public void testGetPropertyType_undefined_returnsUnknownType() throws Throwable {
    PrototypeObjectType obj = new PrototypeObjectType(registry, "Base", null, true);
    JSType unknown = obj.getNativeType(JSTypeNative.UNKNOWN_TYPE);
    assertSame(unknown, obj.getPropertyType("missing"));
  }

  // Covers: defineProperty returns true and stores a new property
  @Test
  public void testDefineProperty_newProperty_returnsTrueAndStored() throws Throwable {
    PrototypeObjectType obj = new PrototypeObjectType(registry, "Base", null, true);
    JSType type = obj.getNativeType(JSTypeNative.NUMBER_TYPE);
    boolean result = obj.defineProperty("a", type, false, null);
    assertTrue(result);
    assertTrue(obj.hasOwnProperty("a"));
  }

  // Covers: defineProperty returns false when already declared (not inferred)
  @Test
  public void testDefineProperty_alreadyDeclared_returnsFalse() throws Throwable {
    PrototypeObjectType obj = new PrototypeObjectType(registry, "Base", null, true);
    JSType type = obj.getNativeType(JSTypeNative.NUMBER_TYPE);
    obj.defineProperty("a", type, false, null);
    boolean result = obj.defineProperty("a", type, false, null);
    assertFalse(result);
  }

  // Covers: defineProperty allows redeclaring over a previously inferred property
  @Test
  public void testDefineProperty_overwriteInferredWithDeclared_returnsTrue() throws Throwable {
    PrototypeObjectType obj = new PrototypeObjectType(registry, "Base", null, true);
    JSType type = obj.getNativeType(JSTypeNative.NUMBER_TYPE);
    obj.defineProperty("a", type, true, null);
    boolean result = obj.defineProperty("a", type, false, null);
    assertTrue(result);
    assertFalse(obj.isPropertyTypeInferred("a"));
  }

  // Covers: removeProperty returns true and removes an existing property
  @Test
  public void testRemoveProperty_existing_trueAndGone() throws Throwable {
    PrototypeObjectType obj = new PrototypeObjectType(registry, "Base", null, true);
    JSType type = obj.getNativeType(JSTypeNative.NUMBER_TYPE);
    obj.defineProperty("a", type, false, null);
    assertTrue(obj.removeProperty("a"));
    assertFalse(obj.hasOwnProperty("a"));
  }

  // Covers: removeProperty returns false when the property does not exist
  @Test
  public void testRemoveProperty_missing_false() throws Throwable {
    PrototypeObjectType obj = new PrototypeObjectType(registry, "Base", null, true);
    assertFalse(obj.removeProperty("missing"));
  }

  // Covers: getPropertyNode returns node for an own declared property
  @Test
  public void testGetPropertyNode_own_returnsNode() throws Throwable {
    PrototypeObjectType obj = new PrototypeObjectType(registry, "Base", null, true);
    JSType type = obj.getNativeType(JSTypeNative.NUMBER_TYPE);
    Node node = IR.name("a");
    obj.defineProperty("a", type, false, node);
    assertSame(node, obj.getPropertyNode("a"));
  }

  // Covers: getPropertyNode falls back to implicit prototype when missing locally
  @Test
  public void testGetPropertyNode_inherited_returnsFromPrototype() throws Throwable {
    PrototypeObjectType parent = new PrototypeObjectType(registry, "Parent", null, true);
    JSType type = parent.getNativeType(JSTypeNative.NUMBER_TYPE);
    Node node = IR.name("a");
    parent.defineProperty("a", type, false, node);
    PrototypeObjectType child = new PrototypeObjectType(registry, "Child", parent);
    assertSame(node, child.getPropertyNode("a"));
  }

  // Covers: getPropertyNode returns null when property not found anywhere
  @Test
  public void testGetPropertyNode_missing_returnsNull() throws Throwable {
    PrototypeObjectType obj = new PrototypeObjectType(registry, "Base", null, true);
    assertNull(obj.getPropertyNode("missing"));
  }

  // Covers: getOwnPropertyJSDocInfo returns null when property has no info
  @Test
  public void testGetOwnPropertyJSDocInfo_notSet_returnsNull() throws Throwable {
    PrototypeObjectType obj = new PrototypeObjectType(registry, "Base", null, true);
    JSType type = obj.getNativeType(JSTypeNative.NUMBER_TYPE);
    obj.defineProperty("a", type, false, null);
    assertNull(obj.getOwnPropertyJSDocInfo("a"));
  }

  // Covers: setPropertyJSDocInfo with null info is a no-op (info != null branch false)
  @Test
  public void testSetPropertyJSDocInfo_nullInfo_noEffect() throws Throwable {
    PrototypeObjectType obj = new PrototypeObjectType(registry, "Base", null, true);
    obj.setPropertyJSDocInfo("a", null);
    assertFalse(obj.hasOwnProperty("a"));
  }

  // Covers: matchesObjectContext always returns true
  @Test
  public void testMatchesObjectContext_alwaysTrue() throws Throwable {
    PrototypeObjectType obj = new PrototypeObjectType(registry, "Base", null, true);
    assertTrue(obj.matchesObjectContext());
  }

  // Covers: toStringHelper returns reference name when hasReferenceName is true
  @Test
  public void testToStringHelper_withClassName_returnsClassName() throws Throwable {
    PrototypeObjectType obj = new PrototypeObjectType(registry, "MyClass", null, true);
    assertEquals("MyClass", obj.toStringHelper(false));
  }

  // Covers: toStringHelper returns "{...}" when no reference name and prettyPrint off
  @Test
  public void testToStringHelper_noNamePrettyPrintOff_returnsEllipsis() throws Throwable {
    PrototypeObjectType obj = new PrototypeObjectType(registry, null, null, true);
    assertFalse(obj.isPrettyPrint());
    assertEquals("{...}", obj.toStringHelper(false));
  }

  // Covers: toStringHelper pretty-print branch with zero properties yields empty braces
  @Test
  public void testToStringHelper_noNamePrettyPrintOnNoProps_returnsEmptyBraces() throws Throwable {
    PrototypeObjectType obj = new PrototypeObjectType(registry, null, null, true);
    obj.setPrettyPrint(true);
    assertEquals("{}", obj.toStringHelper(false));
  }

  // Covers: setPrettyPrint/isPrettyPrint getter-setter pair
  @Test
  public void testSetAndIsPrettyPrint() throws Throwable {
    PrototypeObjectType obj = new PrototypeObjectType(registry, "Base", null, true);
    assertFalse(obj.isPrettyPrint());
    obj.setPrettyPrint(true);
    assertTrue(obj.isPrettyPrint());
  }

  // Covers: getConstructor always returns null for PrototypeObjectType
  @Test
  public void testGetConstructor_alwaysNull() throws Throwable {
    PrototypeObjectType obj = new PrototypeObjectType(registry, "Base", null, true);
    assertNull(obj.getConstructor());
  }

  // Covers: setImplicitPrototype updates the value returned by getImplicitPrototype
  @Test
  public void testGetAndSetImplicitPrototype() throws Throwable {
    PrototypeObjectType obj = new PrototypeObjectType(registry, "Base", null, true);
    PrototypeObjectType newProto = new PrototypeObjectType(registry, "NewProto", null, true);
    obj.setImplicitPrototype(newProto);
    assertSame(newProto, obj.getImplicitPrototype());
  }

  // Covers: getReferenceName returns className when present
  @Test
  public void testGetReferenceName_withClassName() throws Throwable {
    PrototypeObjectType obj = new PrototypeObjectType(registry, "Foo", null, true);
    assertEquals("Foo", obj.getReferenceName());
  }

  // Covers: getReferenceName returns null when no className and no ownerFunction
  @Test
  public void testGetReferenceName_withoutClassNameOrOwner_null() throws Throwable {
    PrototypeObjectType obj = new PrototypeObjectType(registry, null, null, true);
    assertNull(obj.getReferenceName());
  }

  // Covers: isSubtype reflexive case - a type is always a subtype of itself
  @Test
  public void testIsSubtype_reflexive_true() throws Throwable {
    PrototypeObjectType obj = new PrototypeObjectType(registry, "Base", null, true);
    assertTrue(obj.isSubtype(obj));
  }

  // Covers: isSubtype true when the other type is an ancestor in the implicit prototype chain
  @Test
  public void testIsSubtype_childIsSubtypeOfPrototypeParent_true() throws Throwable {
    PrototypeObjectType parent = new PrototypeObjectType(registry, "Parent", null, true);
    PrototypeObjectType child = new PrototypeObjectType(registry, "Child", parent);
    assertTrue(child.isSubtype(parent));
  }

  // Covers: isSubtype false when relation direction is reversed (asymmetry of subtyping)
  @Test
  public void testIsSubtype_parentNotSubtypeOfChild_false() throws Throwable {
    PrototypeObjectType parent = new PrototypeObjectType(registry, "Parent", null, true);
    PrototypeObjectType child = new PrototypeObjectType(registry, "Child", parent);
    assertFalse(parent.isSubtype(child));
  }

  // Covers: getCtorImplementedInterfaces defaults to empty for a non function-prototype object
  @Test
  public void testGetCtorImplementedInterfaces_defaultEmpty() throws Throwable {
    PrototypeObjectType obj = new PrototypeObjectType(registry, "Base", null, true);
    Iterable<ObjectType> result = obj.getCtorImplementedInterfaces();
    assertFalse(result.iterator().hasNext());
  }

  // Covers: defineProperty preserves old JSDocInfo when redefining an existing property
  @Test
  public void testDefineProperty_redefineKeepsPreviousJSDocInfoReference() throws Throwable {
    PrototypeObjectType obj = new PrototypeObjectType(registry, "Base", null, true);
    JSType type = obj.getNativeType(JSTypeNative.NUMBER_TYPE);
    obj.defineProperty("a", type, false, null);
    obj.defineProperty("a", type, true, null);
    assertNull(obj.getOwnPropertyJSDocInfo("a"));
  }

}
