package com.google.javascript.jscomp.type;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.google.javascript.jscomp.ClosureCodingConvention;
import com.google.javascript.jscomp.CodingConvention;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;

public class ChainableReverseAbstractInterpreterClaudeTest {

  private static class TestInterpreter extends ChainableReverseAbstractInterpreter {
    TestInterpreter(CodingConvention convention, JSTypeRegistry typeRegistry) {
      super(convention, typeRegistry);
    }

    public FlowScope getPreciserScopeKnowingConditionOutcome(
        Node condition, FlowScope blindScope, boolean outcome) {
      return blindScope;
    }
  }

  private JSTypeRegistry registry;
  private ChainableReverseAbstractInterpreter interpreter;

  @Before
  public void setUp() throws Throwable {
    Compiler compiler = new Compiler();
    registry = compiler.getTypeRegistry();
    interpreter = new TestInterpreter(new ClosureCodingConvention(), registry);
  }

  // covers Preconditions.checkNotNull(convention) in constructor
  @Test
  public void testConstructor_nullConvention_throwsNullPointerException() throws Throwable {
    try {
      new TestInterpreter(null, registry);
      fail("expected NullPointerException");
    } catch (NullPointerException expected) {
    }
  }

  // covers firstLink = this; in constructor
  @Test
  public void testConstructor_validArgs_getFirstReturnsSelf() throws Throwable {
    assertSame(interpreter, interpreter.getFirst());
  }

  // covers append() normal path setting nextLink and propagating firstLink
  @Test
  public void testAppend_returnsLastLinkAndPropagatesFirstLink() throws Throwable {
    ChainableReverseAbstractInterpreter b = new TestInterpreter(new ClosureCodingConvention(), registry);
    ChainableReverseAbstractInterpreter result = interpreter.append(b);
    assertSame(b, result);
    assertSame(interpreter, b.getFirst());
  }

  // covers Preconditions.checkArgument(lastLink.nextLink == null) throwing
  @Test
  public void testAppend_lastLinkAlreadyHasNext_throwsIllegalArgumentException() throws Throwable {
    ChainableReverseAbstractInterpreter b = new TestInterpreter(new ClosureCodingConvention(), registry);
    ChainableReverseAbstractInterpreter d = new TestInterpreter(new ClosureCodingConvention(), registry);
    b.append(d);
    try {
      interpreter.append(b);
      fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
    }
  }

  // covers nextLink == null branch returning blindScope unchanged
  @Test
  public void testNextPreciserScope_noNextLink_returnsBlindScope() throws Throwable {
    Node cond = IR.name("x");
    FlowScope result = interpreter.nextPreciserScopeKnowingConditionOutcome(cond, null, true);
    assertNull(result);
  }

  // covers nextLink != null branch delegating to next link
  @Test
  public void testNextPreciserScope_withNextLink_delegatesToNextLink() throws Throwable {
    ChainableReverseAbstractInterpreter b = new TestInterpreter(new ClosureCodingConvention(), registry);
    interpreter.append(b);
    Node cond = IR.name("x");
    FlowScope result = interpreter.nextPreciserScopeKnowingConditionOutcome(cond, null, true);
    assertNull(result);
  }

  // covers firstLink.getPreciserScopeKnowingConditionOutcome delegation
  @Test
  public void testFirstPreciserScope_delegatesToFirstLink() throws Throwable {
    ChainableReverseAbstractInterpreter b = new TestInterpreter(new ClosureCodingConvention(), registry);
    interpreter.append(b);
    Node cond = IR.name("x");
    FlowScope result = b.firstPreciserScopeKnowingConditionOutcome(cond, null, true);
    assertNull(result);
  }

  // covers getTypeIfRefinable default (no case matched) for STRING node
  @Test
  public void testGetTypeIfRefinable_stringNode_returnsNull() throws Throwable {
    Node node = IR.string("foo");
    JSType result = interpreter.getTypeIfRefinable(node, null);
    assertNull(result);
  }

  // covers getTypeIfRefinable default (no case matched) for NUMBER node
  @Test
  public void testGetTypeIfRefinable_numberNode_returnsNull() throws Throwable {
    Node node = IR.number(5);
    JSType result = interpreter.getTypeIfRefinable(node, null);
    assertNull(result);
  }

  // covers getTypeIfRefinable default (no case matched) for BLOCK node
  @Test
  public void testGetTypeIfRefinable_blockNode_returnsNull() throws Throwable {
    Node node = IR.block();
    JSType result = interpreter.getTypeIfRefinable(node, null);
    assertNull(result);
  }

  // covers declareNameInScope default branch throwing IllegalArgumentException
  @Test
  public void testDeclareNameInScope_stringNode_throwsIllegalArgumentException() throws Throwable {
    Node node = IR.string("foo");
    try {
      interpreter.declareNameInScope(null, node, null);
      fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
      assertTrue(expected.getMessage().contains("cannot be refined"));
    }
  }

  // covers declareNameInScope default branch for a different non-refinable node type
  @Test
  public void testDeclareNameInScope_numberNode_throwsIllegalArgumentException() throws Throwable {
    Node node = IR.number(3);
    try {
      interpreter.declareNameInScope(null, node, null);
      fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
      assertTrue(expected.getMessage().contains("cannot be refined"));
    }
  }

  // covers getRestrictedWithoutUndefined null shortcut
  @Test
  public void testGetRestrictedWithoutUndefined_nullInput_returnsNull() throws Throwable {
    assertNull(interpreter.getRestrictedWithoutUndefined(null));
  }

  // covers caseVoidType returning null (undefined removed entirely)
  @Test
  public void testGetRestrictedWithoutUndefined_voidType_returnsNull() throws Throwable {
    JSType voidType = interpreter.getNativeType(JSTypeNative.VOID_TYPE);
    assertNull(interpreter.getRestrictedWithoutUndefined(voidType));
  }

  // covers caseNumberType leaving number type unchanged
  @Test
  public void testGetRestrictedWithoutUndefined_numberType_returnsNumberType() throws Throwable {
    JSType numberType = interpreter.getNativeType(JSTypeNative.NUMBER_TYPE);
    JSType result = interpreter.getRestrictedWithoutUndefined(numberType);
    assertTrue(numberType.isEquivalentTo(result));
  }

  // covers caseStringType leaving string type unchanged
  @Test
  public void testGetRestrictedWithoutUndefined_stringType_returnsStringType() throws Throwable {
    JSType stringType = interpreter.getNativeType(JSTypeNative.STRING_TYPE);
    JSType result = interpreter.getRestrictedWithoutUndefined(stringType);
    assertTrue(stringType.isEquivalentTo(result));
  }

  // covers caseBooleanType leaving boolean type unchanged
  @Test
  public void testGetRestrictedWithoutUndefined_booleanType_returnsBooleanType() throws Throwable {
    JSType booleanType = interpreter.getNativeType(JSTypeNative.BOOLEAN_TYPE);
    JSType result = interpreter.getRestrictedWithoutUndefined(booleanType);
    assertTrue(booleanType.isEquivalentTo(result));
  }

  // covers caseNullType leaving null type unchanged (only void is stripped)
  @Test
  public void testGetRestrictedWithoutUndefined_nullTypeConstant_returnsNullType() throws Throwable {
    JSType nullType = interpreter.getNativeType(JSTypeNative.NULL_TYPE);
    JSType result = interpreter.getRestrictedWithoutUndefined(nullType);
    assertTrue(nullType.isEquivalentTo(result));
  }

  // covers caseNoType
  @Test
  public void testGetRestrictedWithoutUndefined_noType_returnsNoType() throws Throwable {
    JSType noType = interpreter.getNativeType(JSTypeNative.NO_TYPE);
    JSType result = interpreter.getRestrictedWithoutUndefined(noType);
    assertTrue(interpreter.getNativeType(JSTypeNative.NO_TYPE).isEquivalentTo(result));
  }

  // covers caseUnknownType
  @Test
  public void testGetRestrictedWithoutUndefined_unknownType_returnsUnknownType() throws Throwable {
    JSType unknownType = interpreter.getNativeType(JSTypeNative.UNKNOWN_TYPE);
    JSType result = interpreter.getRestrictedWithoutUndefined(unknownType);
    assertTrue(interpreter.getNativeType(JSTypeNative.UNKNOWN_TYPE).isEquivalentTo(result));
  }

  // covers caseAllType building union without void
  @Test
  public void testGetRestrictedWithoutUndefined_allType_returnsUnionWithoutVoid() throws Throwable {
    JSType allType = interpreter.getNativeType(JSTypeNative.ALL_TYPE);
    JSType expected = registry.createUnionType(JSTypeNative.OBJECT_TYPE, JSTypeNative.NUMBER_TYPE,
        JSTypeNative.STRING_TYPE, JSTypeNative.BOOLEAN_TYPE, JSTypeNative.NULL_TYPE);
    JSType result = interpreter.getRestrictedWithoutUndefined(allType);
    assertTrue(expected.isEquivalentTo(result));
  }

  // covers caseUnionType removing void from a union
  @Test
  public void testGetRestrictedWithoutUndefined_unionWithVoid_removesVoid() throws Throwable {
    JSType union = registry.createUnionType(JSTypeNative.NUMBER_TYPE, JSTypeNative.VOID_TYPE);
    JSType result = interpreter.getRestrictedWithoutUndefined(union);
    assertTrue(interpreter.getNativeType(JSTypeNative.NUMBER_TYPE).isEquivalentTo(result));
  }

  // covers getRestrictedWithoutNull null shortcut
  @Test
  public void testGetRestrictedWithoutNull_nullInput_returnsNull() throws Throwable {
    assertNull(interpreter.getRestrictedWithoutNull(null));
  }

  // covers caseNullType returning null (null removed entirely)
  @Test
  public void testGetRestrictedWithoutNull_nullTypeConstant_returnsNull() throws Throwable {
    JSType nullType = interpreter.getNativeType(JSTypeNative.NULL_TYPE);
    assertNull(interpreter.getRestrictedWithoutNull(nullType));
  }

  // covers caseVoidType leaving void type unchanged (only null is stripped)
  @Test
  public void testGetRestrictedWithoutNull_voidType_returnsVoidType() throws Throwable {
    JSType voidType = interpreter.getNativeType(JSTypeNative.VOID_TYPE);
    JSType result = interpreter.getRestrictedWithoutNull(voidType);
    assertTrue(voidType.isEquivalentTo(result));
  }

  // covers caseNumberType leaving number type unchanged
  @Test
  public void testGetRestrictedWithoutNull_numberType_returnsNumberType() throws Throwable {
    JSType numberType = interpreter.getNativeType(JSTypeNative.NUMBER_TYPE);
    JSType result = interpreter.getRestrictedWithoutNull(numberType);
    assertTrue(numberType.isEquivalentTo(result));
  }

  // covers caseAllType building union without null
  @Test
  public void testGetRestrictedWithoutNull_allType_returnsUnionWithoutNull() throws Throwable {
    JSType allType = interpreter.getNativeType(JSTypeNative.ALL_TYPE);
    JSType expected = registry.createUnionType(JSTypeNative.OBJECT_TYPE, JSTypeNative.NUMBER_TYPE,
        JSTypeNative.STRING_TYPE, JSTypeNative.BOOLEAN_TYPE, JSTypeNative.VOID_TYPE);
    JSType result = interpreter.getRestrictedWithoutNull(allType);
    assertTrue(expected.isEquivalentTo(result));
  }

  // covers caseUnionType removing null from a union
  @Test
  public void testGetRestrictedWithoutNull_unionWithNull_removesNull() throws Throwable {
    JSType union = registry.createUnionType(JSTypeNative.NUMBER_TYPE, JSTypeNative.NULL_TYPE);
    JSType result = interpreter.getRestrictedWithoutNull(union);
    assertTrue(interpreter.getNativeType(JSTypeNative.NUMBER_TYPE).isEquivalentTo(result));
  }

  // covers type==null && resultEqualsValue branch returning a known native type
  @Test
  public void testGetRestrictedByTypeOfResult_nullType_knownValue_returnsNativeType() throws Throwable {
    JSType result = interpreter.getRestrictedByTypeOfResult(null, "number", true);
    assertTrue(interpreter.getNativeType(JSTypeNative.NUMBER_TYPE).isEquivalentTo(result));
  }

  // covers type==null && resultEqualsValue with unrecognized value falling back to CHECKED_UNKNOWN_TYPE
  @Test
  public void testGetRestrictedByTypeOfResult_nullType_unknownValue_returnsCheckedUnknown() throws Throwable {
    JSType result = interpreter.getRestrictedByTypeOfResult(null, "symbol", true);
    assertTrue(interpreter.getNativeType(JSTypeNative.CHECKED_UNKNOWN_TYPE).isEquivalentTo(result));
  }

  // covers type==null && !resultEqualsValue returning null
  @Test
  public void testGetRestrictedByTypeOfResult_nullType_resultNotEqualsValue_returnsNull() throws Throwable {
    JSType result = interpreter.getRestrictedByTypeOfResult(null, "number", false);
    assertNull(result);
  }

  // covers caseNumberType matching value with resultEqualsValue true
  @Test
  public void testGetRestrictedByTypeOfResult_numberType_matching_resultEquals_returnsType() throws Throwable {
    JSType numberType = interpreter.getNativeType(JSTypeNative.NUMBER_TYPE);
    JSType result = interpreter.getRestrictedByTypeOfResult(numberType, "number", true);
    assertTrue(numberType.isEquivalentTo(result));
  }

  // covers caseNumberType matching value with resultEqualsValue false (contradiction -> null)
  @Test
  public void testGetRestrictedByTypeOfResult_numberType_matching_resultNotEquals_returnsNull() throws Throwable {
    JSType numberType = interpreter.getNativeType(JSTypeNative.NUMBER_TYPE);
    JSType result = interpreter.getRestrictedByTypeOfResult(numberType, "number", false);
    assertNull(result);
  }

  // covers caseNumberType non-matching value with resultEqualsValue false (consistent -> keep type)
  @Test
  public void testGetRestrictedByTypeOfResult_numberType_nonMatching_resultNotEquals_returnsType() throws Throwable {
    JSType numberType = interpreter.getNativeType(JSTypeNative.NUMBER_TYPE);
    JSType result = interpreter.getRestrictedByTypeOfResult(numberType, "string", false);
    assertTrue(numberType.isEquivalentTo(result));
  }

  // covers caseNullType: typeof null === "object" per JS spec
  @Test
  public void testGetRestrictedByTypeOfResult_nullTypeConstant_typeofObject_returnsNullType() throws Throwable {
    JSType nullType = interpreter.getNativeType(JSTypeNative.NULL_TYPE);
    JSType result = interpreter.getRestrictedByTypeOfResult(nullType, "object", true);
    assertTrue(nullType.isEquivalentTo(result));
  }

  // covers caseObjectType function branch, resultEqualsValue true: generic Object can be narrowed to Function
  @Test
  public void testGetRestrictedByTypeOfResult_objectType_typeofFunction_resultEquals_returnsNonNull() throws Throwable {
    JSType objectType = interpreter.getNativeType(JSTypeNative.OBJECT_TYPE);
    JSType result = interpreter.getRestrictedByTypeOfResult(objectType, "function", true);
    assertNotNull(result);
  }

  // covers caseObjectType function branch, resultEqualsValue false: a plain Object that is not a
  // function is a perfectly valid, non-contradictory type (bug: buggy code always returns null here)
  @Test
  public void testGetRestrictedByTypeOfResult_objectType_typeofFunction_resultNotEquals_returnsNonNull() throws Throwable {
    JSType objectType = interpreter.getNativeType(JSTypeNative.OBJECT_TYPE);
    JSType result = interpreter.getRestrictedByTypeOfResult(objectType, "function", false);
    assertNotNull(result);
  }

  // covers caseFunctionType matching "function" with resultEqualsValue true
  @Test
  public void testGetRestrictedByTypeOfResult_functionType_typeofFunction_resultEquals_returnsSameType() throws Throwable {
    JSType functionType = interpreter.getNativeType(JSTypeNative.U2U_CONSTRUCTOR_TYPE);
    JSType result = interpreter.getRestrictedByTypeOfResult(functionType, "function", true);
    assertTrue(functionType.isEquivalentTo(result));
  }

  // covers caseVoidType matching "undefined"
  @Test
  public void testGetRestrictedByTypeOfResult_voidType_typeofUndefined_returnsVoidType() throws Throwable {
    JSType voidType = interpreter.getNativeType(JSTypeNative.VOID_TYPE);
    JSType result = interpreter.getRestrictedByTypeOfResult(voidType, "undefined", true);
    assertTrue(voidType.isEquivalentTo(result));
  }

  // covers caseUnionType (base class) merging only alternates that satisfy the restriction
  @Test
  public void testGetRestrictedByTypeOfResult_unionType_matchingNumber_returnsNumberType() throws Throwable {
    JSType union = registry.createUnionType(JSTypeNative.NUMBER_TYPE, JSTypeNative.STRING_TYPE);
    JSType result = interpreter.getRestrictedByTypeOfResult(union, "number", true);
    assertTrue(interpreter.getNativeType(JSTypeNative.NUMBER_TYPE).isEquivalentTo(result));
  }

  // covers caseNoObjectType matching "object" with resultEqualsValue true
  @Test
  public void testGetRestrictedByTypeOfResult_noObjectType_typeofObject_returnsNoObjectType() throws Throwable {
    JSType noObjectType = interpreter.getNativeType(JSTypeNative.NO_OBJECT_TYPE);
    JSType result = interpreter.getRestrictedByTypeOfResult(noObjectType, "object", true);
    assertTrue(interpreter.getNativeType(JSTypeNative.NO_OBJECT_TYPE).isEquivalentTo(result));
  }

  // covers getNativeType simple delegation to typeRegistry.getNativeType
  @Test
  public void testGetNativeType_returnsRegistryNativeType() throws Throwable {
    JSType fromInterpreter = interpreter.getNativeType(JSTypeNative.STRING_TYPE);
    JSType fromRegistry = registry.getNativeType(JSTypeNative.STRING_TYPE);
    assertTrue(fromRegistry.isEquivalentTo(fromInterpreter));
  }
}
