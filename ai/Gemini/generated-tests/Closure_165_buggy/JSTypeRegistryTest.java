package com.google.javascript.rhino.jstype;

import static org.junit.Assert.*;

import com.google.javascript.rhino.ErrorReporter;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.JSTypeRegistry.ResolveMode;
import com.google.javascript.rhino.jstype.RecordTypeBuilder.RecordProperty;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JSTypeRegistryTest {

  private static class DummyErrorReporter implements ErrorReporter {
    @Override
    public void warning(String message, String sourceName, int line, int charIndex) {}

    @Override
    public void error(String message, String sourceName, int line, int charIndex) {}
  }

  private static class DummyStaticScope implements StaticScope<JSType> {
    private final StaticScope<JSType> parent;

    public DummyStaticScope(StaticScope<JSType> parent) {
      this.parent = parent;
    }

    @Override
    public StaticSymbolTable<JSType, ? extends StaticSlot<JSType>> getSymbolTable() {
      return null;
    }

    @Override
    public String getRootName() {
      return "global";
    }

    @Override
    public StaticScope<JSType> getParentScope() {
      return parent;
    }

    @Override
    public JSType slotTypeOf(String name) {
      return null;
    }
  }

  @Test
  public void testConstructorsAndGetters() throws Throwable {
    ErrorReporter reporter = new DummyErrorReporter();
    JSTypeRegistry registry = new JSTypeRegistry(reporter);
    registry.setTolerateUndefinedValues(false);

    assertNotNull(registry.getErrorReporter());
    assertFalse(registry.shouldTolerateUndefinedValues());

    registry.setResolveMode(ResolveMode.IMMEDIATE);
    assertEquals(ResolveMode.IMMEDIATE, registry.getResolveMode());

    JSType boolType = registry.getNativeType(JSTypeNative.BOOLEAN_TYPE);
    assertNotNull(boolType);

    ObjectType objType = registry.getNativeObjectType(JSTypeNative.OBJECT_TYPE);
    assertNotNull(objType);

    FunctionType funcType = registry.getNativeFunctionType(JSTypeNative.OBJECT_FUNCTION_TYPE);
    assertNotNull(funcType);
  }

  @Test
  public void testTolerateUndefinedValues() throws Throwable {
    ErrorReporter reporter = new DummyErrorReporter();
    JSTypeRegistry registry = new JSTypeRegistry(reporter, true);
    assertTrue(registry.shouldTolerateUndefinedValues());
    JSType numType = registry.getNativeType(JSTypeNative.NUMBER_TYPE);
    JSType union = registry.createDefaultObjectUnion(numType);
    assertNotNull(union);
  }

  @Test
  public void testTypeDeclarationAndLookup() throws Throwable {
    ErrorReporter reporter = new DummyErrorReporter();
    JSTypeRegistry registry = new JSTypeRegistry(reporter);

    JSType numType = registry.getNativeType(JSTypeNative.NUMBER_TYPE);
    boolean declared = registry.declareType("MyNumber", numType);
    assertTrue(declared);

    // Duplicate declaration should fail
    boolean declaredAgain = registry.declareType("MyNumber", numType);
    assertFalse(declaredAgain);

    JSType found = registry.getType("MyNumber");
    assertEquals(numType, found);

    // Test overwrite
    JSType strType = registry.getNativeType(JSTypeNative.STRING_TYPE);
    registry.overwriteDeclaredType("MyNumber", strType);
    assertEquals(strType, registry.getType("MyNumber"));
  }

  @Test
  public void testNamespacesAndForwardDeclaration() throws Throwable {
    ErrorReporter reporter = new DummyErrorReporter();
    JSTypeRegistry registry = new JSTypeRegistry(reporter);

    JSType numType = registry.getNativeType(JSTypeNative.NUMBER_TYPE);
    registry.declareType("goog.string.Utf8", numType);

    assertTrue(registry.hasNamespace("goog"));
    assertTrue(registry.hasNamespace("goog.string"));
    assertFalse(registry.hasNamespace("nonexistent"));

    registry.forwardDeclareType("ForwardType");
    assertTrue(registry.isForwardDeclaredType("ForwardType"));
    assertFalse(registry.isForwardDeclaredType("OtherType"));
  }

  @Test
  public void testPropertyRegistrationAndQueries() throws Throwable {
    ErrorReporter reporter = new DummyErrorReporter();
    JSTypeRegistry registry = new JSTypeRegistry(reporter);

    ObjectType objType = registry.getNativeObjectType(JSTypeNative.OBJECT_TYPE);
    registry.registerPropertyOnType("customProp", objType);

    assertTrue(registry.canPropertyBeDefined(objType, "customProp"));
    assertFalse(registry.canPropertyBeDefined(objType, "nonExistentProp"));

    Iterable<JSType> typesWithProp = registry.getTypesWithProperty("customProp");
    assertNotNull(typesWithProp);

    Iterable<ObjectType> refTypes = registry.getEachReferenceTypeWithProperty("customProp");
    assertNotNull(refTypes);

    JSType greatestSub = registry.getGreatestSubtypeWithProperty(objType, "customProp");
    assertNotNull(greatestSub);

    JSType greatestSubEmpty = registry.getGreatestSubtypeWithProperty(objType, "nonExistentProp");
    assertNotNull(greatestSubEmpty);

    registry.unregisterPropertyOnType("customProp", objType);
  }

  @Test
  public void testGenerationsAndScope() throws Throwable {
    ErrorReporter reporter = new DummyErrorReporter();
    JSTypeRegistry registry = new JSTypeRegistry(reporter);

    assertTrue(registry.isLastGeneration());
    registry.setLastGeneration(false);
    assertFalse(registry.isLastGeneration());

    registry.incrementGeneration();

    StaticScope<JSType> scope = new DummyStaticScope(null);
    registry.resolveTypesInScope(scope);
    registry.clearNamedTypes();
  }

  @Test
  public void testCreateTypeHelpers() throws Throwable {
    ErrorReporter reporter = new DummyErrorReporter();
    JSTypeRegistry registry = new JSTypeRegistry(reporter);

    JSType numType = registry.getNativeType(JSTypeNative.NUMBER_TYPE);
    JSType strType = registry.getNativeType(JSTypeNative.STRING_TYPE);

    JSType optType = registry.createOptionalType(numType);
    assertNotNull(optType);

    JSType nullableType = registry.createNullableType(numType);
    assertNotNull(nullableType);

    JSType optNullableType = registry.createOptionalNullableType(numType);
    assertNotNull(optNullableType);

    JSType unionType = registry.createUnionType(numType, strType);
    assertNotNull(unionType);

    JSType unionTypeNative = registry.createUnionType(JSTypeNative.NUMBER_TYPE, JSTypeNative.STRING_TYPE);
    assertNotNull(unionTypeNative);

    EnumType enumType = registry.createEnumType("MyEnum", null, numType);
    assertNotNull(enumType);

    ObjectType proto = registry.getNativeObjectType(JSTypeNative.OBJECT_TYPE);
    ObjectType customObj = registry.createObjectType(proto);
    assertNotNull(customObj);

    ObjectType customNamedObj = registry.createObjectType("CustomName", null, proto);
    assertNotNull(customNamedObj);

    ObjectType anonObj = registry.createAnonymousObjectType();
    assertNotNull(anonObj);

    assertTrue(registry.resetImplicitPrototype(customObj, proto));
    assertFalse(registry.resetImplicitPrototype(numType, proto));

    Map<String, RecordProperty> props = new HashMap<String, RecordProperty>();
    Node fieldNode = new Node(Token.STRING, "field");
    props.put("field", new RecordProperty(numType, fieldNode));
    RecordType recordType = registry.createRecordType(props);
    assertNotNull(recordType);

    ParameterizedType paramType = registry.createParameterizedType(proto, numType);
    assertNotNull(paramType);

    JSType namedType = registry.createNamedType("SomeType", "source.js", 1, 0);
    assertNotNull(namedType);
  }

  @Test
  public void testFunctionAndParameterCreation() throws Throwable {
    ErrorReporter reporter = new DummyErrorReporter();
    JSTypeRegistry registry = new JSTypeRegistry(reporter);

    JSType numType = registry.getNativeType(JSTypeNative.NUMBER_TYPE);
    JSType strType = registry.getNativeType(JSTypeNative.STRING_TYPE);

    List<JSType> paramList = new ArrayList<JSType>();
    paramList.add(numType);

    Node paramsNode = registry.createParameters(paramList);
    assertNotNull(paramsNode);

    Node varArgsNode = registry.createParametersWithVarArgs(paramList);
    assertNotNull(varArgsNode);

    Node optParamsNode = registry.createOptionalParameters(numType);
    assertNotNull(optParamsNode);

    FunctionType func1 = registry.createFunctionType(numType, numType, strType);
    assertNotNull(func1);

    FunctionType func2 = registry.createFunctionTypeWithVarArgs(numType, paramList);
    assertNotNull(func2);

    FunctionType func3 = registry.createFunctionType(numType, paramList);
    assertNotNull(func3);

    FunctionType func4 = registry.createFunctionTypeWithVarArgs(numType, numType, strType);
    assertNotNull(func4);

    FunctionType ctor1 = registry.createConstructorType(numType, numType, strType);
    assertNotNull(ctor1);

    FunctionType ctor2 = registry.createConstructorTypeWithVarArgs(numType, numType, strType);
    assertNotNull(ctor2);

    ObjectType thisType = registry.getNativeObjectType(JSTypeNative.OBJECT_TYPE);
    JSType funcThis1 = registry.createFunctionType(thisType, numType, paramList);
    assertNotNull(funcThis1);

    JSType funcThis2 = registry.createFunctionTypeWithVarArgs(thisType, numType, paramList);
    assertNotNull(funcThis2);

    FunctionType funcNewRet = registry.createFunctionTypeWithNewReturnType(func1, strType);
    assertNotNull(funcNewRet);

    FunctionType funcNewThis = registry.createFunctionTypeWithNewThisType(func1, thisType);
    assertNotNull(funcNewThis);

    FunctionType funcNode = registry.createFunctionType(numType, paramsNode);
    assertNotNull(funcNode);

    FunctionType ctorGen = registry.createConstructorType(numType, false, numType);
    assertNotNull(ctorGen);

    FunctionType ctorVarArgsGen = registry.createConstructorType(numType, true, numType);
    assertNotNull(ctorVarArgsGen);

    FunctionType funcGen = registry.createFunctionType(numType, false, numType);
    assertNotNull(funcGen);
  }

  @Test
  public void testTemplateTypeHandling() throws Throwable {
    ErrorReporter reporter = new DummyErrorReporter();
    JSTypeRegistry registry = new JSTypeRegistry(reporter);

    registry.setTemplateTypeName("T");
    assertEquals(registry.getNativeType(JSTypeNative.UNKNOWN_TYPE), registry.getType("SomeOther"));
    registry.clearTemplateTypeName();
  }

  @Test
  public void testCreateFromTypeNodesVariants() throws Throwable {
    ErrorReporter reporter = new DummyErrorReporter();
    JSTypeRegistry registry = new JSTypeRegistry(reporter);
    StaticScope<JSType> scope = new DummyStaticScope(null);

    // Test Lazy Expressions mode with names
    registry.setResolveMode(ResolveMode.LAZY_EXPRESSIONS);
    Node stringNode = Node.newString("Object");
    JSType lazyType = registry.createFromTypeNodes(stringNode, "test.js", scope);
    assertNotNull(lazyType);

    // Test STAR token (*)
    registry.setResolveMode(ResolveMode.IMMEDIATE);
    Node starNode = new Node(Token.STAR);
    JSType allType = registry.createFromTypeNodes(starNode, "test.js", scope);
    assertNotNull(allType);

    // Test LB token (Array type)
    Node lbNode = new Node(Token.LB);
    JSType arrayToken = registry.createFromTypeNodes(lbNode, "test.js", scope);
    assertNotNull(arrayToken);

    // Test EMPTY token
    Node emptyNode = new Node(Token.EMPTY);
    JSType emptyType = registry.createFromTypeNodes(emptyNode, "test.js", scope);
    assertNotNull(emptyType);

    // Test VOID token
    Node voidNode = new Node(Token.VOID);
    JSType voidType = registry.createFromTypeNodes(voidNode, "test.js", scope);
    assertNotNull(voidType);

    // Test QMARK token without child (unknown)
    Node qmarkNode = new Node(Token.QMARK);
    JSType unknownType = registry.createFromTypeNodes(qmarkNode, "test.js", scope);
    assertNotNull(unknownType);

    // Test QMARK token with child (nullable)
    Node qmarkChildNode = new Node(Token.QMARK, Node.newString("Number"));
    JSType nullableFromNode = registry.createFromTypeNodes(qmarkChildNode, "test.js", scope);
    assertNotNull(nullableFromNode);

    // Test BANG token (not nullable)
    Node bangNode = new Node(Token.BANG, Node.newString("Object"));
    JSType notNullableType = registry.createFromTypeNodes(bangNode, "test.js", scope);
    assertNotNull(notNullableType);

    // Test EQUALS token (optional)
    Node equalsNode = new Node(Token.EQUALS, Node.newString("Number"));
    JSType optionalTypeFromNode = registry.createFromTypeNodes(equalsNode, "test.js", scope);
    assertNotNull(optionalTypeFromNode);

    // Test ELLIPSIS token (var args)
    Node ellipsisNode = new Node(Token.ELLIPSIS, Node.newString("Number"));
    JSType varArgsTypeFromNode = registry.createFromTypeNodes(ellipsisNode, "test.js", scope);
    assertNotNull(varArgsTypeFromNode);

    // Test PIPE token (union)
    Node pipeNode = new Node(Token.PIPE, Node.newString("Number"), Node.newString("String"));
    JSType unionFromNode = registry.createFromTypeNodes(pipeNode, "test.js", scope);
    assertNotNull(unionFromNode);

    // Test LC token (Record type)
    Node recordField = new Node(Token.COLON, Node.newString("a"), Node.newString("Number"));
    Node lcNode = new Node(Token.LC, recordField);
    JSType recordFromNode = registry.createFromTypeNodes(lcNode, "test.js", scope);
    assertNotNull(recordFromNode);

    // Test Function type node
    Node paramList = new Node(Token.PARAM_LIST, Node.newString("Number"));
    Node funcNode = new Node(Token.FUNCTION, Node.newString("Number"), paramList);
    JSType funcFromNode = registry.createFromTypeNodes(funcNode, "test.js", scope);
    assertNotNull(funcFromNode);

    // Test Function type node with THIS
    Node thisContext = new Node(Token.THIS, Node.newString("Object"));
    Node funcThisNode = new Node(Token.FUNCTION, thisContext, new Node(Token.PARAM_LIST), Node.newString("Number"));
    JSType funcThisFromNode = registry.createFromTypeNodes(funcThisNode, "test.js", scope);
    assertNotNull(funcThisFromNode);

    // Test Function type node with NEW (constructor)
    Node newContext = new Node(Token.NEW, Node.newString("Object"));
    Node funcNewNode = new Node(Token.FUNCTION, newContext, new Node(Token.PARAM_LIST), Node.newString("Object"));
    JSType funcNewFromNode = registry.createFromTypeNodes(funcNewNode, "test.js", scope);
    assertNotNull(funcNewFromNode);
  }

  @Test(expected = IllegalStateException.class)
  public void testUnexpectedNodeThrowsException() throws Throwable {
    ErrorReporter reporter = new DummyErrorReporter();
    JSTypeRegistry registry = new JSTypeRegistry(reporter);
    StaticScope<JSType> scope = new DummyStaticScope(null);

    Node invalidNode = new Node(Token.BLOCK);
    registry.createFromTypeNodes(invalidNode, "test.js", scope);
  }

  @Test
  public void testInterfaceImplementors() throws Throwable {
    ErrorReporter reporter = new DummyErrorReporter();
    JSTypeRegistry registry = new JSTypeRegistry(reporter);

    FunctionType interfaceType = registry.createInterfaceType("MyInterface", null);
    FunctionType implementor = registry.createFunctionType(registry.getNativeType(JSTypeNative.NUMBER_TYPE));

    registry.registerTypeImplementingInterface(implementor, interfaceType.getInstanceType());
    Collection<FunctionType> implementors = registry.getDirectImplementors(interfaceType.getInstanceType());
    assertNotNull(implementors);
    assertTrue(implementors.contains(implementor));
  }

  @Test
  public void testIdentifyNonNullableName() throws Throwable {
    ErrorReporter reporter = new DummyErrorReporter();
    JSTypeRegistry registry = new JSTypeRegistry(reporter);

    registry.identifyNonNullableName("MyNonNullableEnum");
  }

  @Test(expected = NullPointerException.class)
  public void testIdentifyNonNullableNameNull() throws Throwable {
    ErrorReporter reporter = new DummyErrorReporter();
    JSTypeRegistry registry = new JSTypeRegistry(reporter);

    registry.identifyNonNullableName(null);
  }
}