package com.google.javascript.rhino.jstype;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

public class FunctionTypeTest {

  @Test
  public void testForInterfaceCreation() throws Throwable {
    JSTypeRegistry registry = new JSTypeRegistry(null);
    Node source = new Node(Token.FUNCTION);
    FunctionType interfaceType = FunctionType.forInterface(registry, "MyInterface", source);

    assertNotNull(interfaceType);
    assertTrue(interfaceType.isInterface());
    assertFalse(interfaceType.isConstructor());
    assertFalse(interfaceType.isOrdinaryFunction());
    assertTrue(interfaceType.isFunctionType());
    assertTrue(interfaceType.canBeCalled());
    assertEquals("MyInterface", interfaceType.getReferenceName());
    assertEquals(source, interfaceType.getSource());
    assertTrue(interfaceType.hasInstanceType());
  }

  @Test
  public void testConstructorFunctionType() throws Throwable {
    JSTypeRegistry registry = new JSTypeRegistry(null);
    Node source = new Node(Token.FUNCTION);
    Node params = new Node(Token.LP);
    ArrowType arrowType = new ArrowType(registry, params, registry.getNativeType(JSTypeNative.NO_TYPE));
    
    FunctionType ctorType = new FunctionType(
        registry, "MyCtor", source, arrowType, null, "T", true, false
    );

    assertNotNull(ctorType);
    assertTrue(ctorType.isConstructor());
    assertFalse(ctorType.isInterface());
    assertFalse(ctorType.isOrdinaryFunction());
    assertTrue(ctorType.hasInstanceType());
    assertEquals("T", ctorType.getTemplateTypeName());
    assertEquals(0, ctorType.getMinArguments());
    assertEquals(0, ctorType.getMaxArguments());
  }

  @Test
  public void testOrdinaryFunctionType() throws Throwable {
    JSTypeRegistry registry = new JSTypeRegistry(null);
    Node source = new Node(Token.FUNCTION);
    Node params = new Node(Token.LP);
    ArrowType arrowType = new ArrowType(registry, params, registry.getNativeType(JSTypeNative.NO_TYPE));
    
    FunctionType ordinaryType = new FunctionType(
        registry, "MyFunc", source, arrowType, null, null, false, false
    );

    assertNotNull(ordinaryType);
    assertFalse(ordinaryType.isConstructor());
    assertFalse(ordinaryType.isInterface());
    assertTrue(ordinaryType.isOrdinaryFunction());
    assertFalse(ordinaryType.hasInstanceType());
    assertNull(ordinaryType.getTemplateTypeName());
  }

  @Test
  public void testGetParametersAndArgumentsCount() throws Throwable {
    JSTypeRegistry registry = new JSTypeRegistry(null);
    Node params = new Node(Token.LP);
    Node reqArg = Node.newString(Token.NAME, "a");
    Node optArg = Node.newString(Token.NAME, "b");
    optArg.setOptionalArg(true);
    
    params.addChildToBack(reqArg);
    params.addChildToBack(optArg);

    ArrowType arrowType = new ArrowType(registry, params, registry.getNativeType(JSTypeNative.NO_TYPE));
    FunctionType func = new FunctionType(
        registry, "Func", null, arrowType, null, null, false, false
    );

    assertEquals(1, func.getMinArguments());
    assertEquals(2, func.getMaxArguments());
    
    Iterable<Node> paramIterable = func.getParameters();
    assertNotNull(paramIterable);
  }

  @Test
  public void testGetMaxArgumentsVarArgs() throws Throwable {
    JSTypeRegistry registry = new JSTypeRegistry(null);
    Node params = new Node(Token.LP);
    Node varArg = Node.newString(Token.NAME, "args");
    varArg.setVarArgs(true);
    params.addChildToBack(varArg);

    ArrowType arrowType = new ArrowType(registry, params, registry.getNativeType(JSTypeNative.NO_TYPE));
    FunctionType func = new FunctionType(
        registry, "FuncVarArgs", null, arrowType, null, null, false, false
    );

    assertEquals(Integer.MAX_VALUE, func.getMaxArguments());
  }

  @Test
  public void testPrototypeLazyInitialization() throws Throwable {
    JSTypeRegistry registry = new JSTypeRegistry(null);
    Node source = new Node(Token.FUNCTION);
    Node params = new Node(Token.LP);
    ArrowType arrowType = new ArrowType(registry, params, registry.getNativeType(JSTypeNative.NO_TYPE));
    
    FunctionType func = new FunctionType(
        registry, "FuncProto", source, arrowType, null, null, true, false
    );

    FunctionPrototypeType proto1 = func.getPrototype();
    assertNotNull(proto1);
    
    FunctionPrototypeType proto2 = func.getPrototype();
    assertEquals(proto1, proto2);
  }

  @Test
  public void testSetImplementedInterfaces() throws Throwable {
    JSTypeRegistry registry = new JSTypeRegistry(null);
    Node source = new Node(Token.FUNCTION);
    Node params = new Node(Token.LP);
    ArrowType arrowType = new ArrowType(registry, params, registry.getNativeType(JSTypeNative.NO_TYPE));
    
    FunctionType func = new FunctionType(
        registry, "FuncIface", source, arrowType, null, null, true, false
    );

    List<ObjectType> ifaces = new ArrayList<ObjectType>();
    FunctionType ifaceType = FunctionType.forInterface(registry, "DummyIface", source);
    ifaces.add(ifaceType);

    func.setImplementedInterfaces(ifaces);
    Iterable<ObjectType> retrievedIfaces = func.getImplementedInterfaces();
    assertNotNull(retrievedIfaces);
  }

  @Test
  public void testHasPropertyAndOwnProperty() throws Throwable {
    JSTypeRegistry registry = new JSTypeRegistry(null);
    Node source = new Node(Token.FUNCTION);
    Node params = new Node(Token.LP);
    ArrowType arrowType = new ArrowType(registry, params, registry.getNativeType(JSTypeNative.NO_TYPE));
    
    FunctionType func = new FunctionType(
        registry, "FuncProp", source, arrowType, null, null, false, false
    );

    assertTrue(func.hasProperty("prototype"));
    assertTrue(func.hasOwnProperty("prototype"));
    assertFalse(func.hasProperty("nonExistentProperty"));
  }

  @Test
  public void testGetPropertyTypeSpecial() throws Throwable {
    JSTypeRegistry registry = new JSTypeRegistry(null);
    Node source = new Node(Token.FUNCTION);
    Node params = new Node(Token.LP);
    ArrowType arrowType = new ArrowType(registry, params, registry.getNativeType(JSTypeNative.NO_TYPE));
    
    FunctionType func = new FunctionType(
        registry, "FuncPropType", source, arrowType, null, null, false, false
    );

    JSType protoType = func.getPropertyType("prototype");
    assertNotNull(protoType);

    JSType callPropType = func.getPropertyType("call");
    assertNotNull(callPropType);

    JSType applyPropType = func.getPropertyType("apply");
    assertNotNull(applyPropType);
  }

  @Test
  public void testIsPropertyTypeInferred() throws Throwable {
    JSTypeRegistry registry = new JSTypeRegistry(null);
    Node source = new Node(Token.FUNCTION);
    Node params = new Node(Token.LP);
    ArrowType arrowType = new ArrowType(registry, params, registry.getNativeType(JSTypeNative.NO_TYPE));
    
    FunctionType func = new FunctionType(
        registry, "FuncInferred", source, arrowType, null, null, false, false
    );

    assertTrue(func.isPropertyTypeInferred("prototype"));
  }

  @Test
  public void testToStringRepresentation() throws Throwable {
    JSTypeRegistry registry = new JSTypeRegistry(null);
    Node source = new Node(Token.FUNCTION);
    Node params = new Node(Token.LP);
    ArrowType arrowType = new ArrowType(registry, params, registry.getNativeType(JSTypeNative.NO_TYPE));
    
    FunctionType func = new FunctionType(
        registry, "FuncStr", source, arrowType, null, null, false, false
    );

    String str = func.toString();
    assertNotNull(str);
    assertTrue(str.startsWith("function ("));
  }

  @Test
  public void testVisit() throws Throwable {
    JSTypeRegistry registry = new JSTypeRegistry(null);
    Node source = new Node(Token.FUNCTION);
    Node params = new Node(Token.LP);
    ArrowType arrowType = new ArrowType(registry, params, registry.getNativeType(JSTypeNative.NO_TYPE));
    
    FunctionType func = new FunctionType(
        registry, "FuncVisit", source, arrowType, null, null, false, false
    );

    JSTypeVisitor visitor = new JSTypeVisitor() {
      public Boolean caseBooleanType() { return false; }
      public Boolean caseFunctionType(FunctionType t) { return true; }
      public Boolean caseObjectType(ObjectType t) { return false; }
      public Boolean caseUnknownType() { return false; }
      public Boolean caseNoType() { return false; }
      public Boolean caseNullType() { return false; }
      public Boolean caseVoidType() { return false; }
    };

    Boolean result = func.visit(visitor);
    assertNotNull(result);
    assertTrue(result.booleanValue());
  }

  @Test
  public void testSourceNodeGetSet() throws Throwable {
    JSTypeRegistry registry = new JSTypeRegistry(null);
    Node source1 = new Node(Token.FUNCTION);
    Node source2 = new Node(Token.FUNCTION);
    Node params = new Node(Token.LP);
    ArrowType arrowType = new ArrowType(registry, params, registry.getNativeType(JSTypeNative.NO_TYPE));
    
    FunctionType func = new FunctionType(
        registry, "FuncSource", source1, arrowType, null, null, false, false
    );

    assertEquals(source1, func.getSource());
    func.setSource(source2);
    assertEquals(source2, func.getSource());
  }

  @Test
  public void testHasCachedValues() throws Throwable {
    JSTypeRegistry registry = new JSTypeRegistry(null);
    Node source = new Node(Token.FUNCTION);
    Node params = new Node(Token.LP);
    ArrowType arrowType = new ArrowType(registry, params, registry.getNativeType(JSTypeNative.NO_TYPE));
    
    FunctionType func = new FunctionType(
        registry, "FuncCache", source, arrowType, null, null, false, false
    );

    boolean cached = func.hasCachedValues();
    assertFalse(cached);
    
    func.getPrototype();
    assertTrue(func.hasCachedValues());
  }

  @Test
  public void testHashCode() throws Throwable {
    JSTypeRegistry registry = new JSTypeRegistry(null);
    Node source = new Node(Token.FUNCTION);
    FunctionType interfaceType = FunctionType.forInterface(registry, "InterfaceHash", source);
    
    int hash = interfaceType.hashCode();
    assertTrue(hash != 0);
  }

}