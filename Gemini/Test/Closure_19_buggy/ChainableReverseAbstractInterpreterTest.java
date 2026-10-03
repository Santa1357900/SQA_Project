package com.google.javascript.jscomp.type;

import com.google.javascript.jscomp.CodingConvention;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.EnumElementType;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.jstype.ParameterizedType;
import com.google.javascript.rhino.jstype.StaticSlot;
import com.google.javascript.rhino.jstype.TemplateType;
import com.google.javascript.rhino.jstype.UnionType;
import com.google.javascript.rhino.SimpleErrorReporter;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class ChainableReverseAbstractInterpreterTest {

  private JSTypeRegistry typeRegistry;
  private CodingConvention convention;
  private ConcreteChainableReverseAbstractInterpreter interpreter;

  private static class ConcreteChainableReverseAbstractInterpreter extends ChainableReverseAbstractInterpreter {
    public ConcreteChainableReverseAbstractInterpreter(CodingConvention convention, JSTypeRegistry typeRegistry) {
      super(convention, typeRegistry);
    }

    @Override
    public FlowScope getPreciserScopeKnowingConditionOutcome(Node condition, FlowScope blindScope, boolean outcome) {
      return blindScope;
    }
  }

  private static class DummyCodingConvention implements CodingConvention {
    @Override public boolean isOptionalParameter(Node parameter) { return false; }
    @Override public boolean isVarArgsParameter(Node parameter) { return false; }
    @Override public boolean isExported(String name) { return false; }
    @Override public boolean isExported(String name, boolean local) { return false; }
    @Override public boolean isIdentifier(String identifier) { return true; }
    @Override public String getAbstractMethodName() { return "goog.abstractMethod"; }
    @Override public String getGlobalObject() { return "window"; }
    @Override public boolean isSingletonGetter(Node callNode, String functionName, String className) { return false; }
    @Override public void applySingletonGetterDefinition(FunctionType functionType, String className) {}
    @Override public String getSkinnablePrefix() { return ""; }
    @Override public boolean isPropertyTestFunction(Node call) { return false; }
    @Override public boolean isSuperDispatcher(String methodName) { return false; }
    @Override public String getFullNameForSuperDispatcher(String className) { return null; }
    @Override public SubclassType getSubclassType(Node callNode) { return null; }
    @Override public void applySubclassRelationship(FunctionType parentClass, FunctionType childClass, SubclassType type) {}
    @Override public void applyDeclarativeFunctionDefinition(FunctionType function) {}
    @Override public CollectionType getCollectionTypeFromValue(Node value) { return null; }
    @Override public boolean isValidEnumKey(String key) { return true; }
    @Override public String getProductName() { return ""; }
    @Override public boolean applyDelegateRelationship(FunctionType delegatesTo, FunctionType delegator, FunctionType delegateProxy, FunctionType superclass, FunctionType superProto) { return false; }
    @Override public void applyObjectLiteralCast(FlowScope scope, Node callNode, String leftName, JSType rightType, String ownerName, JSType ownerType) {}
    @Override public String getDelegateSuperclass() { return null; }
    @Override public boolean isObjectLiteralCast(Node callNode) { return false; }
    @Override public boolean isAssert(Node callNode) { return false; }
    @Override public void checkAllFunctionsDefined() {}
    @Override public boolean isImplicitlyNamedFunction(Node n) { return false; }
    @Override public boolean isToStringMethod(String methodName) { return "toString".equals(methodName); }
    @Override public boolean isClassMethod(String methodName) { return false; }
    @Override public boolean isInstanceMethod(String methodName) { return false; }
  }

  private static class DummyStaticSlot implements StaticSlot<JSType> {
    private final JSType type;
    public DummyStaticSlot(JSType type) {
      this.type = type;
    }
    @Override public String getName() { return "dummy"; }
    @Override public JSType getType() { return type; }
    @Override public boolean isTypeInferred() { return false; }
  }

  private static class DummyFlowScope implements FlowScope {
    private final StaticSlot<JSType> slot;
    public DummyFlowScope(StaticSlot<JSType> slot) {
      this.slot = slot;
    }
    @Override public StaticSlot<JSType> getSlot(String name) { return slot; }
    @Override public StaticSlot<JSType> getOwnSlot(String name) { return slot; }
    @Override public void inferSlotType(String symbol, JSType type) {}
    @Override public void inferQualifiedSlot(Node n, String symbol, JSType blindType, JSType narrowedType) {}
    @Override public void baslineShape() {}
    @Override public FlowScope optimize() { return this; }
    @Override public FlowScope createChildFlowScope() { return this; }
    @Override public void grunt() {}
  }

  @Before
  public void setUp() throws Throwable {
    typeRegistry = new JSTypeRegistry(new SimpleErrorReporter());
    convention = new DummyCodingConvention();
    interpreter = new ConcreteChainableReverseAbstractInterpreter(convention, typeRegistry);
  }

  @Test
  public void testAppendAndGetFirst() throws Throwable {
    ConcreteChainableReverseAbstractInterpreter second = 
        new ConcreteChainableReverseAbstractInterpreter(convention, typeRegistry);
    
    assertSame(interpreter, interpreter.getFirst());
    assertSame(interpreter, second.getFirst());

    interpreter.append(second);
    assertSame(interpreter, second.getFirst());
    assertSame(second, interpreter.append(new ConcreteChainableReverseAbstractInterpreter(convention, typeRegistry)).getFirst());
  }

  @Test(expected = IllegalArgumentException.class)
  public void testAppendInvalidArgument() throws Throwable {
    ConcreteChainableReverseAbstractInterpreter second = 
        new ConcreteChainableReverseAbstractInterpreter(convention, typeRegistry);
    ConcreteChainableReverseAbstractInterpreter third = 
        new ConcreteChainableReverseAbstractInterpreter(convention, typeRegistry);
    
    interpreter.append(second);
    // second already has nextLink set if we try to append it elsewhere or if second already has a link, 
    // but the check is lastLink.nextLink == null. Let's make second have a nextLink:
    second.append(third);
    
    // Trying to append 'second' (which now has nextLink != null) should throw IllegalArgumentException
    interpreter.append(second);
  }

  @Test
  public void testGetTypeIfRefinableNameToken() throws Throwable {
    Node nameNode = Node.newString(Token.NAME, "x");
    JSType stringType = typeRegistry.getNativeType(JSTypeNative.STRING_TYPE);
    DummyStaticSlot slot = new DummyStaticSlot(stringType);
    FlowScope scope = new DummyFlowScope(slot);

    JSType result = interpreter.getTypeIfRefinable(nameNode, scope);
    assertEquals(stringType, result);
  }

  @Test
  public void testGetTypeIfRefinableNameTokenNullSlotType() throws Throwable {
    Node nameNode = Node.newString(Token.NAME, "x");
    nameNode.setJSType(typeRegistry.getNativeType(JSTypeNative.NUMBER_TYPE));
    DummyStaticSlot slot = new DummyStaticSlot(null);
    FlowScope scope = new DummyFlowScope(slot);

    JSType result = interpreter.getTypeIfRefinable(nameNode, scope);
    assertEquals(typeRegistry.getNativeType(JSTypeNative.NUMBER_TYPE), result);
  }

  @Test
  public void testGetTypeIfRefinableNameTokenNoSlot() throws Throwable {
    Node nameNode = Node.newString(Token.NAME, "x");
    FlowScope scope = new DummyFlowScope(null);

    JSType result = interpreter.getTypeIfRefinable(nameNode, scope);
    assertNull(result);
  }

  @Test
  public void testGetTypeIfRefinableGetPropToken() throws Throwable {
    Node propNode = Node.newString(Token.GETPROP, "a.b");
    JSType boolType = typeRegistry.getNativeType(JSTypeNative.BOOLEAN_TYPE);
    DummyStaticSlot slot = new DummyStaticSlot(boolType);
    FlowScope scope = new DummyFlowScope(slot);

    JSType result = interpreter.getTypeIfRefinable(propNode, scope);
    assertEquals(boolType, result);
  }

  @Test
  public void testGetTypeIfRefinableGetPropTokenNullQualifiedName() throws Throwable {
    Node propNode = new Node(Token.GETPROP); // No children, qualified name is null
    FlowScope scope = new DummyFlowScope(null);

    JSType result = interpreter.getTypeIfRefinable(propNode, scope);
    assertNull(result);
  }

  @Test
  public void testGetTypeIfRefinableUnsupportedToken() throws Throwable {
    Node numberNode = Node.newNumber(123.0);
    FlowScope scope = new DummyFlowScope(null);

    JSType result = interpreter.getTypeIfRefinable(numberNode, scope);
    assertNull(result);
  }

  @Test
  public void testDeclareNameInScopeName() throws Throwable {
    Node nameNode = Node.newString(Token.NAME, "x");
    JSType type = typeRegistry.getNativeType(JSTypeNative.STRING_TYPE);
    FlowScope scope = new DummyFlowScope(null);

    // Should not throw
    interpreter.declareNameInScope(scope, nameNode, type);
  }

  @Test
  public void testDeclareNameInScopeGetProp() throws Throwable {
    Node propNode = Node.newString(Token.GETPROP, "a.b");
    JSType type = typeRegistry.getNativeType(JSTypeNative.STRING_TYPE);
    FlowScope scope = new DummyFlowScope(null);

    // Should not throw
    interpreter.declareNameInScope(scope, propNode, type);
  }

  @Test(expected = IllegalArgumentException.class)
  public void testDeclareNameInScopeInvalidNode() throws Throwable {
    Node numberNode = Node.newNumber(123.0);
    JSType type = typeRegistry.getNativeType(JSTypeNative.NUMBER_TYPE);
    FlowScope scope = new DummyFlowScope(null);

    interpreter.declareNameInScope(scope, numberNode, type);
  }

  @Test
  public void testGetRestrictedWithoutUndefined() throws Throwable {
    assertNull(interpreter.getRestrictedWithoutUndefined(null));
    JSType allType = typeRegistry.getNativeType(JSTypeNative.ALL_TYPE);
    JSType restricted = interpreter.getRestrictedWithoutUndefined(allType);
    assertNotNull(restricted);
  }

  @Test
  public void testGetRestrictedWithoutNull() throws Throwable {
    assertNull(interpreter.getRestrictedWithoutNull(null));
    JSType allType = typeRegistry.getNativeType(JSTypeNative.ALL_TYPE);
    JSType restricted = interpreter.getRestrictedWithoutNull(allType);
    assertNotNull(restricted);
  }

  @Test
  public void testGetRestrictedByTypeOfResultNullType() throws Throwable {
    JSType resTrue = interpreter.getRestrictedByTypeOfResult(null, "number", true);
    assertNotNull(resTrue);

    JSType resFalse = interpreter.getRestrictedByTypeOfResult(null, "number", false);
    assertNull(resFalse);
  }

  @Test
  public void testGetRestrictedByTypeOfResultWithOptions() throws Throwable {
    JSType numType = typeRegistry.getNativeType(JSTypeNative.NUMBER_TYPE);
    
    JSType restrictedMatch = interpreter.getRestrictedByTypeOfResult(numType, "number", true);
    assertNotNull(restrictedMatch);

    JSType restrictedNoMatch = interpreter.getRestrictedByTypeOfResult(numType, "string", true);
    assertNull(restrictedNoMatch);

    JSType restrictedNotEqual = interpreter.getRestrictedByTypeOfResult(numType, "string", false);
    assertNotNull(restrictedNotEqual);
  }

  @Test
  public void testNextPreciserScopeKnowingConditionOutcome() throws Throwable {
    Node cond = Node.newString(Token.NAME, "x");
    FlowScope blind = new DummyFlowScope(null);

    // Without next link, should return blind scope
    FlowScope result = interpreter.nextPreciserScopeKnowingConditionOutcome(cond, blind, true);
    assertEquals(blind, result);

    // With next link
    ConcreteChainableReverseAbstractInterpreter second = 
        new ConcreteChainableReverseAbstractInterpreter(convention, typeRegistry);
    interpreter.append(second);

    FlowScope resultWithNext = interpreter.nextPreciserScopeKnowingConditionOutcome(cond, blind, true);
    assertEquals(blind, resultWithNext);
  }
}