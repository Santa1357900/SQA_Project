package com.google.javascript.rhino.jstype;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.google.javascript.rhino.ErrorReporter;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class FunctionTypeClaudeTest {

  private JSTypeRegistry registry;
  private ObjectType objectType;
  private JSType voidType;

  @Before
  public void setUp() throws Throwable {
    registry = new JSTypeRegistry(new ErrorReporter() {
      public void warning(String message, String sourceName, int line, int lineOffset) {}
      public void error(String message, String sourceName, int line, int lineOffset) {}
    });
    objectType = registry.getNativeObjectType(JSTypeNative.OBJECT_TYPE);
    voidType = registry.getNativeType(JSTypeNative.VOID_TYPE);
  }

  private Node emptyParams() {
    return new Node(Token.LP);
  }

  private Node oneParam(boolean optional, JSType type) {
    Node params = new Node(Token.LP);
    Node p = Node.newString(Token.NAME, "a");
    p.setJSType(type);
    if (optional) {
      p.setOptionalArg(true);
    }
    params.addChildToFront(p);
    return params;
  }

  private FunctionType ordinaryFunction(String name, Node params, JSType returnType) {
    return new FunctionType(registry, name, null,
        new ArrowType(registry, params, returnType), null, null, false, false);
  }

  private FunctionType constructorFunction(String name, Node params, JSType returnType) {
    return new FunctionType(registry, name, null,
        new ArrowType(registry, params, returnType), null, null, true, false);
  }

  // Covers: Preconditions.checkArgument(source == null || Token.FUNCTION == source.getType())
  @Test
  public void testConstructor_sourceNotFunctionToken_throwsIllegalArgumentException() throws Throwable {
    Node badSource = Node.newString(Token.NAME, "x");
    try {
      new FunctionType(registry, "f", badSource,
          new ArrowType(registry, emptyParams(), objectType), null, null, false, false);
      fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
    }
  }

  // Covers: Preconditions.checkNotNull(arrowType)
  @Test
  public void testConstructor_nullArrowType_throwsNullPointerException() throws Throwable {
    try {
      new FunctionType(registry, "f", null, null, null, null, false, false);
      fail("expected NullPointerException");
    } catch (NullPointerException expected) {
    }
  }

  // Covers: forInterface's Preconditions.checkArgument(name != null)
  @Test
  public void testForInterface_nullName_throwsIllegalArgumentException() throws Throwable {
    try {
      FunctionType.forInterface(registry, null, null);
      fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
    }
  }

  // Covers: Kind.CONSTRUCTOR branch of isConstructor/isInterface/isOrdinaryFunction
  @Test
  public void testIsConstructor_trueForConstructorKind() throws Throwable {
    FunctionType f = constructorFunction("C", emptyParams(), objectType);
    assertTrue(f.isConstructor());
    assertFalse(f.isInterface());
    assertFalse(f.isOrdinaryFunction());
  }

  // Covers: Kind.INTERFACE branch
  @Test
  public void testIsInterface_trueForInterfaceKind() throws Throwable {
    FunctionType f = FunctionType.forInterface(registry, "I", null);
    assertTrue(f.isInterface());
    assertFalse(f.isConstructor());
    assertFalse(f.isOrdinaryFunction());
  }

  // Covers: Kind.ORDINARY branch plus isFunctionType()/canBeCalled() constants
  @Test
  public void testIsOrdinaryFunction_trueForOrdinaryKind() throws Throwable {
    FunctionType f = ordinaryFunction("f", emptyParams(), objectType);
    assertTrue(f.isOrdinaryFunction());
    assertFalse(f.isConstructor());
    assertFalse(f.isInterface());
    assertTrue(f.isFunctionType());
    assertTrue(f.canBeCalled());
  }

  // Covers: getParameters() when getParametersNode() == null -> Collections.emptySet()
  @Test
  public void testGetParameters_nullParametersNode_returnsEmpty() throws Throwable {
    FunctionType f = ordinaryFunction("f", null, objectType);
    Iterator<Node> it = f.getParameters().iterator();
    assertFalse(it.hasNext());
  }

  // Covers: getParameters() when params node has children -> n.children()
  @Test
  public void testGetParameters_withChildren_returnsChildren() throws Throwable {
    Node params = oneParam(false, objectType);
    FunctionType f = ordinaryFunction("f", params, objectType);
    Iterator<Node> it = f.getParameters().iterator();
    assertTrue(it.hasNext());
    Node p = it.next();
    assertFalse(it.hasNext());
    assertSame(params.getFirstChild(), p);
  }

  // Covers: getParametersNode() returns call.parameters
  @Test
  public void testGetParametersNode_returnsSameNodeAsConstructed() throws Throwable {
    Node params = emptyParams();
    FunctionType f = ordinaryFunction("f", params, objectType);
    assertSame(params, f.getParametersNode());
  }

  // Covers: getMinArguments() loop with zero parameters
  @Test
  public void testGetMinArguments_noParams_returnsZero() throws Throwable {
    FunctionType f = ordinaryFunction("f", emptyParams(), objectType);
    assertEquals(0, f.getMinArguments());
  }

  // Covers: getMinArguments() with a required parameter -> min updated
  @Test
  public void testGetMinArguments_oneRequiredParam_returnsOne() throws Throwable {
    FunctionType f = ordinaryFunction("f", oneParam(false, objectType), objectType);
    assertEquals(1, f.getMinArguments());
  }

  // Covers: getMinArguments() with optional param -> min not updated
  @Test
  public void testGetMinArguments_oneOptionalParam_returnsZero() throws Throwable {
    FunctionType f = ordinaryFunction("f", oneParam(true, objectType), objectType);
    assertEquals(0, f.getMinArguments());
  }

  // Covers: getMaxArguments() when params node is null -> Integer.MAX_VALUE
  @Test
  public void testGetMaxArguments_nullParamsNode_returnsMaxValue() throws Throwable {
    FunctionType f = ordinaryFunction("f", null, objectType);
    assertEquals(Integer.MAX_VALUE, f.getMaxArguments());
  }

  // Covers: getMaxArguments() with non-varargs last param -> childCount
  @Test
  public void testGetMaxArguments_withParams_returnsChildCount() throws Throwable {
    FunctionType f = ordinaryFunction("f", oneParam(false, objectType), objectType);
    assertEquals(1, f.getMaxArguments());
  }

  // Covers: getReturnType() returns call.returnType
  @Test
  public void testGetReturnType_returnsConfiguredType() throws Throwable {
    FunctionType f = ordinaryFunction("f", emptyParams(), objectType);
    assertSame(objectType, f.getReturnType());
  }

  // Covers: isReturnTypeInferred() true/false via 4-arg ArrowType constructor
  @Test
  public void testIsReturnTypeInferred_trueAndFalse() throws Throwable {
    FunctionType inferred = new FunctionType(registry, "f", null,
        new ArrowType(registry, emptyParams(), objectType, true), null, null, false, false);
    FunctionType notInferred = ordinaryFunction("f2", emptyParams(), objectType);
    assertTrue(inferred.isReturnTypeInferred());
    assertFalse(notInferred.isReturnTypeInferred());
  }

  // Covers: getPrototype() lazy init on first call and caching on second call
  @Test
  public void testGetPrototype_lazyInitAndCached() throws Throwable {
    FunctionType f = ordinaryFunction("f", emptyParams(), objectType);
    FunctionPrototypeType p1 = f.getPrototype();
    assertNotNull(p1);
    FunctionPrototypeType p2 = f.getPrototype();
    assertSame(p1, p2);
  }

  // Covers: setPrototype(null) -> false
  @Test
  public void testSetPrototype_nullArgument_returnsFalse() throws Throwable {
    FunctionType f = ordinaryFunction("f", emptyParams(), objectType);
    assertFalse(f.setPrototype(null));
  }

  // Covers: isConstructor() && prototype == getInstanceType() -> false branch
  @Test
  public void testSetPrototype_equalsInstanceType_returnsFalse() throws Throwable {
    FunctionType ctor = constructorFunction("C", emptyParams(), objectType);
    FunctionPrototypeType proto = ctor.getPrototype();
    ctor.setInstanceType(proto);
    assertFalse(ctor.setPrototype(proto));
  }

  // Covers: setPrototype() success path for ordinary function, updates prototype field
  @Test
  public void testSetPrototype_validPrototype_returnsTrueAndUpdates() throws Throwable {
    FunctionType f = ordinaryFunction("f", emptyParams(), objectType);
    FunctionType other = ordinaryFunction("other", emptyParams(), objectType);
    FunctionPrototypeType otherProto = other.getPrototype();
    assertTrue(f.setPrototype(otherProto));
    assertSame(otherProto, f.getPrototype());
  }

  // Covers: setPrototypeBasedOn() when prototype == null -> creates new prototype linked to base
  @Test
  public void testSetPrototypeBasedOn_setsImplicitPrototype() throws Throwable {
    FunctionType ctor = constructorFunction("C", emptyParams(), objectType);
    ctor.setPrototypeBasedOn(objectType);
    assertSame(objectType, ctor.getPrototype().getImplicitPrototype());
  }

  // Covers: getImplementedInterfaces() direct list and getAllImplementedInterfaces() traversal
  @Test
  public void testImplementedInterfaces_setAndGetAll() throws Throwable {
    FunctionType iface = FunctionType.forInterface(registry, "Iface", null);
    FunctionType ctor = constructorFunction("Ctor", emptyParams(), objectType);
    List<ObjectType> ifaces = new ArrayList<ObjectType>();
    ifaces.add(iface.getInstanceType());
    ctor.setImplementedInterfaces(ifaces);

    Iterator<ObjectType> direct = ctor.getImplementedInterfaces().iterator();
    assertTrue(direct.hasNext());
    assertSame(iface.getInstanceType(), direct.next());
    assertFalse(direct.hasNext());

    Iterator<ObjectType> all = ctor.getAllImplementedInterfaces().iterator();
    assertTrue(all.hasNext());
    assertSame(iface.getInstanceType(), all.next());
    assertFalse(all.hasNext());
  }

  // Covers: hasProperty/hasOwnProperty "prototype" shortcut true, unset name false
  @Test
  public void testHasProperty_and_HasOwnProperty_prototypeAlwaysTrue() throws Throwable {
    FunctionType f = ordinaryFunction("f", emptyParams(), objectType);
    assertTrue(f.hasProperty("prototype"));
    assertTrue(f.hasOwnProperty("prototype"));
    assertFalse(f.hasProperty("notDefinedProp"));
  }

  // Covers: getPropertyType("prototype") -> getPrototype()
  @Test
  public void testGetPropertyType_prototype_returnsPrototypeObject() throws Throwable {
    FunctionType f = ordinaryFunction("f", emptyParams(), objectType);
    JSType protoType = f.getPropertyType("prototype");
    assertSame(f.getPrototype(), protoType);
  }

  // Covers: getPropertyType("call") lazy define for null params and non-null params
  @Test
  public void testGetPropertyType_call_definesLazilyAndCaches() throws Throwable {
    FunctionType fNullParams = ordinaryFunction("f1", null, objectType);
    JSType callType1 = fNullParams.getPropertyType("call");
    assertNotNull(callType1);
    assertTrue(callType1.isFunctionType());

    FunctionType fWithParams = ordinaryFunction("f2", emptyParams(), objectType);
    JSType callType2 = fWithParams.getPropertyType("call");
    assertNotNull(callType2);
    assertTrue(callType2.isFunctionType());
    assertTrue(fWithParams.hasOwnProperty("call"));
  }

  // Covers: getPropertyType("apply") lazy define branch
  @Test
  public void testGetPropertyType_apply_definesLazily() throws Throwable {
    FunctionType f = ordinaryFunction("f", emptyParams(), objectType);
    JSType applyType = f.getPropertyType("apply");
    assertNotNull(applyType);
    assertTrue(applyType.isFunctionType());
  }

  // Covers: defineProperty("prototype", type, ...) where type.toObjectType() == null -> false
  @Test
  public void testDefineProperty_prototypeWithNonObjectType_returnsFalse() throws Throwable {
    FunctionType f = ordinaryFunction("f", emptyParams(), objectType);
    assertFalse(f.defineProperty("prototype", voidType, false, false));
  }

  // Covers: isPropertyTypeInferred("prototype") -> always true regardless of super
  @Test
  public void testIsPropertyTypeInferred_prototypeAlwaysTrue() throws Throwable {
    FunctionType f = ordinaryFunction("f", emptyParams(), objectType);
    assertTrue(f.isPropertyTypeInferred("prototype"));
  }

  // Covers: supAndInfHelper isEquivalentTo(that) shortcut -> returns this
  @Test
  public void testGetLeastSupertype_sameInstance_returnsSelf() throws Throwable {
    FunctionType f = ordinaryFunction("f", emptyParams(), objectType);
    JSType result = f.getLeastSupertype(f);
    assertSame(f, result);
  }

  // Covers: supAndInfHelper functionInstance.isEquivalentTo(that) branch -> returns that
  @Test
  public void testGetLeastSupertype_withFunctionInstanceType_returnsFunctionInstance() throws Throwable {
    ObjectType functionInstanceType = registry.getNativeObjectType(JSTypeNative.FUNCTION_INSTANCE_TYPE);
    FunctionType f = ordinaryFunction("f", emptyParams(), objectType);
    JSType result = f.getLeastSupertype(functionInstanceType);
    assertSame(functionInstanceType, result);
  }

  // Covers: supAndInfHelper merge/branch path for two ordinary zero-arg functions
  @Test
  public void testSupAndInfHelper_mergeOrdinaryFunctions_returnsFunctionTypeWithSameArity() throws Throwable {
    Node sharedParams = emptyParams();
    FunctionType f1 = ordinaryFunction("f1", sharedParams, objectType);
    FunctionType f2 = ordinaryFunction("f2", sharedParams, voidType);
    JSType result = f1.getLeastSupertype(f2);
    assertTrue(result.isFunctionType());
    assertEquals(0, ((FunctionType) result).getMaxArguments());
  }

  // Covers: hasEqualCallType() reflexive comparison
  @Test
  public void testHasEqualCallType_sameArrowType_returnsTrue() throws Throwable {
    FunctionType f = ordinaryFunction("f", emptyParams(), objectType);
    assertTrue(f.hasEqualCallType(f));
  }

  // Covers: toString() with no params, no known this-type
  @Test
  public void testToString_noParamsNoThis() throws Throwable {
    FunctionType f = ordinaryFunction("f", emptyParams(), objectType);
    String expected = "function (): " + objectType.toString();
    assertEquals(expected, f.toString());
  }

  // Covers: toString() with known this-type and one param -> comma joining
  @Test
  public void testToString_withParamAndKnownThis() throws Throwable {
    Node params = oneParam(false, objectType);
    FunctionType f = new FunctionType(registry, "f", null,
        new ArrowType(registry, params, objectType), objectType, null, false, false);
    String expected = "function (this:" + objectType.toString() + ", "
        + objectType.toString() + "): " + objectType.toString();
    assertEquals(expected, f.toString());
  }

  // Covers: isSubtype() when target is an interface -> always true
  @Test
  public void testIsSubtype_toInterfaceType_alwaysTrue() throws Throwable {
    FunctionType ordinary = ordinaryFunction("f", emptyParams(), objectType);
    FunctionType iface = FunctionType.forInterface(registry, "I", null);
    assertTrue(ordinary.isSubtype(iface));
  }

  // Covers: isSubtype() when this is interface and target is not -> false
  @Test
  public void testIsSubtype_interfaceToOrdinary_returnsFalse() throws Throwable {
    FunctionType ordinary = ordinaryFunction("f", emptyParams(), objectType);
    FunctionType iface = FunctionType.forInterface(registry, "I", null);
    assertFalse(iface.isSubtype(ordinary));
  }

  // Covers: getInstanceType() for constructor -> returns typeOfThis
  @Test
  public void testGetInstanceType_constructor_returnsTypeOfThis() throws Throwable {
    FunctionType f = constructorFunction("C", emptyParams(), objectType);
    ObjectType instance = f.getInstanceType();
    assertNotNull(instance);
  }

  // Covers: getInstanceType() Preconditions.checkState failure for ordinary function
  @Test
  public void testGetInstanceType_ordinaryFunction_throwsIllegalStateException() throws Throwable {
    FunctionType f = ordinaryFunction("f", emptyParams(), objectType);
    try {
      f.getInstanceType();
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }

  // Covers: setInstanceType() updates typeOfThis field directly
  @Test
  public void testSetInstanceType_updatesInstanceType() throws Throwable {
    FunctionType f = constructorFunction("C", emptyParams(), objectType);
    f.setInstanceType(objectType);
    assertSame(objectType, f.getInstanceType());
  }

  // Covers: hasInstanceType() true for constructor/interface, false for ordinary
  @Test
  public void testHasInstanceType_variousKinds() throws Throwable {
    FunctionType ctor = constructorFunction("C", emptyParams(), objectType);
    FunctionType iface = FunctionType.forInterface(registry, "I", null);
    FunctionType ordinary = ordinaryFunction("f", emptyParams(), objectType);
    assertTrue(ctor.hasInstanceType());
    assertTrue(iface.hasInstanceType());
    assertFalse(ordinary.hasInstanceType());
  }

  // Covers: getTypeOfThis() non-NoObjectType branch returns typeOfThis unchanged
  @Test
  public void testGetTypeOfThis_returnsConfiguredType() throws Throwable {
    FunctionType f = new FunctionType(registry, "f", null,
        new ArrowType(registry, emptyParams(), objectType), objectType, null, false, false);
    assertSame(objectType, f.getTypeOfThis());
  }

  // Covers: getSource()/setSource() round trip, initial null value
  @Test
  public void testGetSource_setSource_roundTrip() throws Throwable {
    FunctionType f = ordinaryFunction("f", emptyParams(), objectType);
    assertNull(f.getSource());
    Node srcNode = new Node(Token.FUNCTION);
    f.setSource(srcNode);
    assertSame(srcNode, f.getSource());
  }

  // Covers: addSubType() triggered via setPrototype()/setPrototypeBasedOn() linking
  @Test
  public void testGetSubTypes_populatedAfterPrototypeLink() throws Throwable {
    FunctionType sup = constructorFunction("S", emptyParams(), objectType);
    FunctionType sub = constructorFunction("C", emptyParams(), objectType);
    assertNull(sup.getSubTypes());
    sub.setPrototypeBasedOn(sup.getInstanceType());
    List<FunctionType> subs = sup.getSubTypes();
    assertNotNull(subs);
    assertTrue(subs.contains(sub));
  }

  // Covers: hasCachedValues() false before prototype access, true after
  @Test
  public void testHasCachedValues_falseThenTrueAfterPrototypeAccess() throws Throwable {
    FunctionType f = ordinaryFunction("f", emptyParams(), objectType);
    assertFalse(f.hasCachedValues());
    f.getPrototype();
    assertTrue(f.hasCachedValues());
  }

  // Covers: getTemplateTypeName() returns configured value or null
  @Test
  public void testGetTemplateTypeName_returnsConfiguredValue() throws Throwable {
    FunctionType f = new FunctionType(registry, "f", null,
        new ArrowType(registry, emptyParams(), objectType), null, "T", false, false);
    FunctionType f2 = ordinaryFunction("f2", emptyParams(), objectType);
    assertEquals("T", f.getTemplateTypeName());
    assertNull(f2.getTemplateTypeName());
  }
}
