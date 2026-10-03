package com.google.javascript.rhino.jstype;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import com.google.javascript.jscomp.Compiler;
import com.google.javascript.rhino.ErrorReporter;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;

public class JSTypeRegistryClaudeTest {

  private JSTypeRegistry registry;
  private ErrorReporter reporter;

  @Before
  public void setUp() throws Throwable {
    reporter = new Compiler();
    registry = new JSTypeRegistry(reporter);
  }

  // Covers two-arg constructor default (false) and explicit true for tolerateUndefinedValues
  @Test
  public void testConstructor_tolerateUndefinedValues_defaultFalseAndExplicitTrue() throws Throwable {
    assertFalse(registry.shouldTolerateUndefinedValues());
    JSTypeRegistry explicit = new JSTypeRegistry(reporter, true);
    assertTrue(explicit.shouldTolerateUndefinedValues());
  }

  // Covers getErrorReporter returning the exact reporter passed to the constructor
  @Test
  public void testGetErrorReporter_returnsProvidedReporter() throws Throwable {
    assertSame(reporter, registry.getErrorReporter());
  }

  // Covers getResolveMode default value and setResolveMode mutating it
  @Test
  public void testGetResolveMode_defaultLazyNames_andSetResolveModeChangesIt() throws Throwable {
    assertEquals(JSTypeRegistry.ResolveMode.LAZY_NAMES, registry.getResolveMode());
    registry.setResolveMode(JSTypeRegistry.ResolveMode.IMMEDIATE);
    assertEquals(JSTypeRegistry.ResolveMode.IMMEDIATE, registry.getResolveMode());
  }

  // Covers isLastGeneration default true and setLastGeneration(false) updating state
  @Test
  public void testIsLastGeneration_defaultTrue_andSetFalseUpdatesState() throws Throwable {
    assertTrue(registry.isLastGeneration());
    registry.setLastGeneration(false);
    assertFalse(registry.isLastGeneration());
  }

  // Covers getNativeType for several JSTypeNative ids returning correct concrete implementation classes
  @Test
  public void testGetNativeType_returnsConcreteTypeInstances() throws Throwable {
    assertTrue(registry.getNativeType(JSTypeNative.BOOLEAN_TYPE) instanceof BooleanType);
    assertTrue(registry.getNativeType(JSTypeNative.NUMBER_TYPE) instanceof NumberType);
    assertTrue(registry.getNativeType(JSTypeNative.STRING_TYPE) instanceof StringType);
    assertTrue(registry.getNativeType(JSTypeNative.VOID_TYPE) instanceof VoidType);
    assertTrue(registry.getNativeType(JSTypeNative.NULL_TYPE) instanceof NullType);
    assertTrue(registry.getNativeType(JSTypeNative.UNKNOWN_TYPE) instanceof UnknownType);
  }

  // Covers register() namespace accumulation: only parent segments become namespaces, not full dotted name
  @Test
  public void testHasNamespace_dottedDeclaration_onlyParentIsNamespace() throws Throwable {
    registry.declareType("ns1.ns2.MyType", registry.getNativeType(JSTypeNative.NUMBER_TYPE));
    assertTrue(registry.hasNamespace("ns1.ns2"));
    assertTrue(registry.hasNamespace("ns1"));
    assertFalse(registry.hasNamespace("ns1.ns2.MyType"));
  }

  // Covers declareType: true for a brand new name, false when name already exists
  @Test
  public void testDeclareType_newName_thenExistingName() throws Throwable {
    JSType numberType = registry.getNativeType(JSTypeNative.NUMBER_TYPE);
    assertTrue(registry.declareType("Custom.TypeA", numberType));
    assertFalse(registry.declareType("Custom.TypeA", numberType));
    assertSame(numberType, registry.getType("Custom.TypeA"));
  }

  // Covers overwriteDeclaredType success path replacing a previously declared type
  @Test
  public void testOverwriteDeclaredType_existingName_updates() throws Throwable {
    JSType numberType = registry.getNativeType(JSTypeNative.NUMBER_TYPE);
    JSType stringType = registry.getNativeType(JSTypeNative.STRING_TYPE);
    registry.declareType("Custom.TypeB", numberType);
    registry.overwriteDeclaredType("Custom.TypeB", stringType);
    assertSame(stringType, registry.getType("Custom.TypeB"));
  }

  // Covers Preconditions.checkState failure path when overwriting an undeclared name
  @Test
  public void testOverwriteDeclaredType_undeclaredName_throwsIllegalStateException() throws Throwable {
    try {
      registry.overwriteDeclaredType("Never.Declared", registry.getNativeType(JSTypeNative.NUMBER_TYPE));
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }

  // Covers forwardDeclareType/isForwardDeclaredType round trip
  @Test
  public void testForwardDeclareType_marksAsForwardDeclared() throws Throwable {
    assertFalse(registry.isForwardDeclaredType("Fwd.Type"));
    registry.forwardDeclareType("Fwd.Type");
    assertTrue(registry.isForwardDeclaredType("Fwd.Type"));
  }

  // Covers getType(String) built-in aliases and unknown-name null path
  @Test
  public void testGetType_builtInAliases_andUnknownNameReturnsNull() throws Throwable {
    assertSame(registry.getNativeType(JSTypeNative.VOID_TYPE), registry.getType("Undefined"));
    assertSame(registry.getNativeType(JSTypeNative.VOID_TYPE), registry.getType("void"));
    assertSame(registry.getNativeType(JSTypeNative.NULL_TYPE), registry.getType("Null"));
    assertNull(registry.getType("DoesNotExist123"));
  }

  // Covers getType(scope,...) short-circuit returning an already-known native type
  @Test
  public void testGetTypeWithScope_knownName_returnsNativeInstance() throws Throwable {
    JSType result = registry.getType((StaticScope<JSType>) null, "Array", "test.js", 1, 0);
    assertSame(registry.getNativeType(JSTypeNative.ARRAY_TYPE), result);
  }

  // Covers getType(scope,...) creating a NamedType proxy for an unresolved name
  @Test
  public void testGetTypeWithScope_unknownName_returnsNamedType() throws Throwable {
    JSType result = registry.getType((StaticScope<JSType>) null, "Totally.Unknown", "test.js", 1, 0);
    assertTrue(result instanceof NamedType);
  }

  // Covers registerTypeImplementingInterface storing by interface reference name, retrievable via getDirectImplementors
  @Test
  public void testRegisterTypeImplementingInterface_getDirectImplementors() throws Throwable {
    FunctionType impl = registry.getNativeFunctionType(JSTypeNative.ARRAY_FUNCTION_TYPE);
    ObjectType iface = registry.getNativeObjectType(JSTypeNative.OBJECT_TYPE);
    registry.registerTypeImplementingInterface(impl, iface);
    Collection<FunctionType> implementors = registry.getDirectImplementors(iface);
    assertTrue(implementors.contains(impl));
  }

  // Covers registerPropertyOnType indexing and getTypesWithProperty retrieval
  @Test
  public void testRegisterPropertyOnType_getTypesWithProperty() throws Throwable {
    ObjectType objType = registry.getNativeObjectType(JSTypeNative.OBJECT_TYPE);
    registry.registerPropertyOnType("myProp1", objType);
    Iterable<JSType> types = registry.getTypesWithProperty("myProp1");
    boolean found = false;
    for (JSType t : types) {
      if (t == objType) { found = true; }
    }
    assertTrue(found);
  }

  // Covers canPropertyBeDefined true for registered property, false for unregistered
  @Test
  public void testCanPropertyBeDefined_registeredAndUnregistered() throws Throwable {
    ObjectType objType = registry.getNativeObjectType(JSTypeNative.OBJECT_TYPE);
    registry.registerPropertyOnType("myProp2", objType);
    assertTrue(registry.canPropertyBeDefined(objType, "myProp2"));
    assertFalse(registry.canPropertyBeDefined(objType, "neverRegisteredProp"));
  }

  // Covers getGreatestSubtypeWithProperty fallback to NO_TYPE when property never registered
  @Test
  public void testGetGreatestSubtypeWithProperty_unregistered_returnsNoType() throws Throwable {
    ObjectType objType = registry.getNativeObjectType(JSTypeNative.OBJECT_TYPE);
    JSType result = registry.getGreatestSubtypeWithProperty(objType, "neverRegisteredProp2");
    assertSame(registry.getNativeType(JSTypeNative.NO_TYPE), result);
  }

  // Covers getGreatestSubtypeWithProperty building the union and returning a non-empty subtype
  @Test
  public void testGetGreatestSubtypeWithProperty_registered_returnsNonEmpty() throws Throwable {
    ObjectType objType = registry.getNativeObjectType(JSTypeNative.OBJECT_TYPE);
    registry.registerPropertyOnType("myProp3", objType);
    JSType result = registry.getGreatestSubtypeWithProperty(objType, "myProp3");
    assertFalse(result.isEmptyType());
  }

  // Covers getEachReferenceTypeWithProperty for a registered ObjectType and an unregistered property
  @Test
  public void testGetEachReferenceTypeWithProperty_registeredAndUnregistered() throws Throwable {
    ObjectType objType = registry.getNativeObjectType(JSTypeNative.OBJECT_TYPE);
    registry.registerPropertyOnType("myProp4", objType);
    Iterable<ObjectType> found = registry.getEachReferenceTypeWithProperty("myProp4");
    assertTrue(found.iterator().hasNext());
    Iterable<ObjectType> notFound = registry.getEachReferenceTypeWithProperty("neverRegisteredProp4");
    assertFalse(notFound.iterator().hasNext());
  }

  // Covers unregisterPropertyOnType removing the entry from the reference-type-by-property index
  @Test
  public void testUnregisterPropertyOnType_removesFromIndex() throws Throwable {
    ObjectType objType = registry.getNativeObjectType(JSTypeNative.OBJECT_TYPE);
    registry.registerPropertyOnType("myProp5", objType);
    registry.unregisterPropertyOnType("myProp5", objType);
    Iterable<ObjectType> result = registry.getEachReferenceTypeWithProperty("myProp5");
    assertFalse(result.iterator().hasNext());
  }

  // Covers findCommonSuperObject when both arguments are the same type: loop fully matches and returns it
  @Test
  public void testFindCommonSuperObject_sameType() throws Throwable {
    ObjectType objType = registry.getNativeObjectType(JSTypeNative.OBJECT_TYPE);
    ObjectType result = registry.findCommonSuperObject(objType, objType);
    assertTrue(result.isEquivalentTo(objType));
  }

  // Covers createOptionalType special-cases for UnknownType and AllType: returned unchanged
  @Test
  public void testCreateOptionalType_unknownAndAll_sameInstance() throws Throwable {
    JSType unknown = registry.getNativeType(JSTypeNative.UNKNOWN_TYPE);
    JSType all = registry.getNativeType(JSTypeNative.ALL_TYPE);
    assertSame(unknown, registry.createOptionalType(unknown));
    assertSame(all, registry.createOptionalType(all));
  }

  // Covers createOptionalType general case: union of the type with VOID_TYPE
  @Test
  public void testCreateOptionalType_number_unionWithVoid() throws Throwable {
    JSType number = registry.getNativeType(JSTypeNative.NUMBER_TYPE);
    JSType result = registry.createOptionalType(number);
    assertTrue(result.isUnionType());
  }

  // Covers createNullableType: union of the type with NULL_TYPE
  @Test
  public void testCreateNullableType_number_unionWithNull() throws Throwable {
    JSType number = registry.getNativeType(JSTypeNative.NUMBER_TYPE);
    JSType result = registry.createNullableType(number);
    assertTrue(result.isUnionType());
  }

  // Covers createOptionalNullableType: union of type, void and null -> three alternates
  @Test
  public void testCreateOptionalNullableType_number_threeAlternates() throws Throwable {
    JSType number = registry.getNativeType(JSTypeNative.NUMBER_TYPE);
    JSType result = registry.createOptionalNullableType(number);
    int count = 0;
    for (JSType alt : result.toMaybeUnionType().getAlternates()) { count++; }
    assertEquals(3, count);
  }

  // Covers createDefaultObjectUnion branching on shouldTolerateUndefinedValues()
  @Test
  public void testCreateDefaultObjectUnion_tolerateFalseAndTrue() throws Throwable {
    JSType number = registry.getNativeType(JSTypeNative.NUMBER_TYPE);
    JSType resultFalse = registry.createDefaultObjectUnion(number);
    int countFalse = 0;
    for (JSType alt : resultFalse.toMaybeUnionType().getAlternates()) { countFalse++; }
    JSTypeRegistry tolerant = new JSTypeRegistry(reporter, true);
    JSType resultTrue = tolerant.createDefaultObjectUnion(tolerant.getNativeType(JSTypeNative.NUMBER_TYPE));
    int countTrue = 0;
    for (JSType alt : resultTrue.toMaybeUnionType().getAlternates()) { countTrue++; }
    assertEquals(2, countFalse);
    assertEquals(3, countTrue);
  }

  // Covers createUnionType(JSType...) collapsing duplicate alternates to a single non-union type
  @Test
  public void testCreateUnionType_duplicates_collapse() throws Throwable {
    JSType number = registry.getNativeType(JSTypeNative.NUMBER_TYPE);
    JSType result = registry.createUnionType(number, number);
    assertFalse(result.isUnionType());
    assertEquals(number.toString(), result.toString());
  }

  // Covers createUnionType(JSType...) building a real union for two distinct alternates
  @Test
  public void testCreateUnionType_distinct_twoAlternates() throws Throwable {
    JSType number = registry.getNativeType(JSTypeNative.NUMBER_TYPE);
    JSType string = registry.getNativeType(JSTypeNative.STRING_TYPE);
    JSType result = registry.createUnionType(number, string);
    assertTrue(result.isUnionType());
  }

  // Covers createUnionType(JSTypeNative...) matching the predefined NUMBER_STRING native union
  @Test
  public void testCreateUnionTypeNative_matchesPredefined() throws Throwable {
    JSType result = registry.createUnionType(JSTypeNative.NUMBER_TYPE, JSTypeNative.STRING_TYPE);
    JSType predefined = registry.getNativeType(JSTypeNative.NUMBER_STRING);
    assertEquals(predefined.toString(), result.toString());
  }

  // Covers createEnumType returning a usable non-null EnumType
  @Test
  public void testCreateEnumType_nonNull() throws Throwable {
    EnumType et = registry.createEnumType("MyEnumX", IR.block(), registry.getNativeType(JSTypeNative.NUMBER_TYPE));
    assertNotNull(et);
  }

  // Covers createFunctionType(JSType, JSType...) and the lastVarArgs true/false branches
  @Test
  public void testCreateFunctionType_setsReturnType_andLastVarArgsBranches() throws Throwable {
    JSType numberType = registry.getNativeType(JSTypeNative.NUMBER_TYPE);
    JSType stringType = registry.getNativeType(JSTypeNative.STRING_TYPE);
    FunctionType ft1 = registry.createFunctionType(numberType, stringType);
    assertSame(numberType, ft1.getInternalArrowType().returnType);
    FunctionType ft2 = registry.createFunctionType(numberType, false, stringType);
    assertSame(numberType, ft2.getInternalArrowType().returnType);
    FunctionType ft3 = registry.createFunctionType(numberType, true, stringType);
    assertSame(numberType, ft3.getInternalArrowType().returnType);
  }

  // Covers createFunctionTypeWithNewReturnType copying an existing function with a new return type
  @Test
  public void testCreateFunctionTypeWithNewReturnType_updates() throws Throwable {
    FunctionType original = registry.getNativeFunctionType(JSTypeNative.ARRAY_FUNCTION_TYPE);
    JSType newReturn = registry.getNativeType(JSTypeNative.STRING_TYPE);
    FunctionType updated = registry.createFunctionTypeWithNewReturnType(original, newReturn);
    assertSame(newReturn, updated.getInternalArrowType().returnType);
  }

  // Covers createObjectType(ObjectType) setting implicit prototype and createAnonymousObjectType default null prototype
  @Test
  public void testCreateObjectType_setsImplicitPrototype_andAnonymousHasNullPrototype() throws Throwable {
    ObjectType proto = registry.getNativeObjectType(JSTypeNative.OBJECT_PROTOTYPE);
    ObjectType obj = registry.createObjectType(proto);
    assertSame(proto, obj.getImplicitPrototype());
    ObjectType anon = registry.createAnonymousObjectType();
    assertNull(anon.getImplicitPrototype());
  }

  // Covers resetImplicitPrototype true path for PrototypeObjectType and false path for a non-PrototypeObjectType
  @Test
  public void testResetImplicitPrototype_trueAndFalse() throws Throwable {
    ObjectType obj = registry.createAnonymousObjectType();
    ObjectType newProto = registry.getNativeObjectType(JSTypeNative.OBJECT_PROTOTYPE);
    assertTrue(registry.resetImplicitPrototype(obj, newProto));
    assertSame(newProto, obj.getImplicitPrototype());
    JSType number = registry.getNativeType(JSTypeNative.NUMBER_TYPE);
    assertFalse(registry.resetImplicitPrototype(number, newProto));
  }

  // Covers createParameterizedType returning a non-null ParameterizedType
  @Test
  public void testCreateParameterizedType_nonNull() throws Throwable {
    ObjectType arrayType = registry.getNativeObjectType(JSTypeNative.ARRAY_TYPE);
    JSType stringType = registry.getNativeType(JSTypeNative.STRING_TYPE);
    ParameterizedType pt = registry.createParameterizedType(arrayType, stringType);
    assertNotNull(pt);
  }

  // Covers createNamedType returning a NamedType instance for a given reference
  @Test
  public void testCreateNamedType_instance() throws Throwable {
    JSType nt = registry.createNamedType("foo.Bar", "test.js", 1, 0);
    assertTrue(nt instanceof NamedType);
  }

  // Covers identifyNonNullableName's Preconditions.checkNotNull guard
  @Test
  public void testIdentifyNonNullableName_null_throwsNPE() throws Throwable {
    try {
      registry.identifyNonNullableName(null);
      fail("expected NullPointerException");
    } catch (NullPointerException expected) {
    }
  }

  // Covers setTemplateTypeName making getType resolve it, and clearTemplateTypeName reverting to null
  @Test
  public void testSetAndClearTemplateTypeName() throws Throwable {
    registry.setTemplateTypeName("T");
    JSType t = registry.getType("T");
    assertTrue(t instanceof TemplateType);
    registry.clearTemplateTypeName();
    assertNull(registry.getType("T"));
  }

  // Covers createRecordType with an empty property map returning a usable RecordType
  @Test
  public void testCreateRecordType_emptyMap() throws Throwable {
    Map<String, RecordTypeBuilder.RecordProperty> props = new HashMap<String, RecordTypeBuilder.RecordProperty>();
    RecordType rt = registry.createRecordType(props);
    assertNotNull(rt);
  }

  // Covers createInterfaceType delegating to FunctionType.forInterface and returning a non-null type
  @Test
  public void testCreateInterfaceType_nonNull() throws Throwable {
    FunctionType ft = registry.createInterfaceType("MyIface", IR.block());
    assertNotNull(ft);
  }

  // Covers createFromTypeNodes Token.STRING branch for a primitive value type name (not ObjectType)
  @Test
  public void testCreateFromTypeNodes_primitiveType() throws Throwable {
    Node typeNode = IR.string("number");
    JSType result = registry.createFromTypeNodes(typeNode, "test.js", null);
    assertSame(registry.getNativeType(JSTypeNative.NUMBER_TYPE), result);
  }

  // Covers createFromTypeNodes Token.STRING branch for an ObjectType name wrapped in nullable default union
  @Test
  public void testCreateFromTypeNodes_objectType_nullableUnion() throws Throwable {
    Node typeNode = IR.string("Object");
    JSType result = registry.createFromTypeNodes(typeNode, "test.js", null);
    assertTrue(result.isUnionType());
  }

  // Covers createFromTypeNodes Token.STRING branch creating an unresolved NamedType wrapped in nullable union
  @Test
  public void testCreateFromTypeNodes_unknownType_nullableNamedTypeUnion() throws Throwable {
    Node typeNode = IR.string("some.Unknown.Type");
    JSType result = registry.createFromTypeNodes(typeNode, "test.js", null);
    assertTrue(result.isUnionType());
  }

  // Covers createFromTypeNodes skipping the nullable wrap for a name registered via identifyNonNullableName
  @Test
  public void testCreateFromTypeNodes_nonNullableIdentifiedName() throws Throwable {
    registry.identifyNonNullableName("MyEnumName");
    Node typeNode = IR.string("MyEnumName");
    JSType result = registry.createFromTypeNodes(typeNode, "test.js", null);
    assertFalse(result.isUnionType());
  }

  // Covers LAZY_EXPRESSIONS resolve mode producing UnresolvedTypeExpression, and the unsupported-token throw path
  @Test
  public void testCreateFromTypeNodes_lazyExpressions_andUnsupportedTokenThrows() throws Throwable {
    registry.setResolveMode(JSTypeRegistry.ResolveMode.LAZY_EXPRESSIONS);
    Node typeNode = IR.string("number");
    JSType result = registry.createFromTypeNodes(typeNode, "test.js", null);
    assertTrue(result instanceof UnresolvedTypeExpression);
    try {
      registry.createFromTypeNodes(IR.name("x"), "test.js", null);
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }
}
