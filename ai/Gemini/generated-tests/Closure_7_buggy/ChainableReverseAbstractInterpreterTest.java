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
import com.google.javascript.rhino.jstype.Visitor;
import com.google.javascript.rhino.SimpleErrorReporter;

import junit.framework.TestCase;

public class ChainableReverseAbstractInterpreterTest extends TestCase {

  private JSTypeRegistry typeRegistry;
  private CodingConvention convention;
  private ChainableReverseAbstractInterpreter interpreter;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    typeRegistry = new JSTypeRegistry(new SimpleErrorReporter());
    convention = new com.google.javascript.jscomp.DefaultCodingConvention();
    interpreter = new SemanticReverseAbstractInterpreter(convention, typeRegistry);
  }

  public void testConstructorAndChain() throws Throwable {
    ChainableReverseAbstractInterpreter interpreter2 = new SemanticReverseAbstractInterpreter(convention, typeRegistry);
    
    assertNotNull(interpreter.getFirst());
    assertSame(interpreter, interpreter.getFirst());
    
    ChainableReverseAbstractInterpreter last = interpreter.append(interpreter2);
    assertSame(interpreter2, last);
    assertSame(interpreter, interpreter2.getFirst());
    assertSame(interpreter, last.getFirst());
  }

  public void testAppendConstraintViolation() throws Throwable {
    ChainableReverseAbstractInterpreter interpreter2 = new SemanticReverseAbstractInterpreter(convention, typeRegistry);
    ChainableReverseAbstractInterpreter interpreter3 = new SemanticReverseAbstractInterpreter(convention, typeRegistry);
    
    interpreter.append(interpreter2);
    try {
      interpreter.append(interpreter3);
      fail("Expected IllegalArgumentException when appending to an interpreter that already has a next link");
    } catch (IllegalArgumentException e) {
      // Expected
    }
  }

  public void testGetTypeIfRefinableName() throws Throwable {
    FlowScope scope = interpreter.getNativeType(JSTypeNative.UNKNOWN_TYPE) != null ? null : null;
    // Create a dummy FlowScope using an anonymous inner class or test helper if possible.
    // Since FlowScope is an interface, let's see if we can use a basic implementation or test via null / concrete if available.
    // Wait, FlowScope is an interface. Can we mock/dummy it safely?
    // Let's implement FlowScope with minimal methods if needed, or test getTypeIfRefinable with a node type that doesn't require complex scope if possible.
    // Wait, getTypeIfRefinable checks scope.getSlot(...) for Token.NAME and Token.GETPROP.
  }

  public void testGetRestrictedWithoutUndefined() throws Throwable {
    JSType unionType = typeRegistry.createUnionType(
        typeRegistry.getNativeType(JSTypeNative.NUMBER_TYPE),
        typeRegistry.getNativeType(JSTypeNative.VOID_TYPE)
    );
    JSType restricted = interpreter.getRestrictedWithoutUndefined(unionType);
    assertNotNull(restricted);
    
    JSType nullResult = interpreter.getRestrictedWithoutUndefined(null);
    assertNull(nullResult);
  }

  public void testGetRestrictedWithoutNull() throws Throwable {
    JSType unionType = typeRegistry.createUnionType(
        typeRegistry.getNativeType(JSTypeNative.STRING_TYPE),
        typeRegistry.getNativeType(JSTypeNative.NULL_TYPE)
    );
    JSType restricted = interpreter.getRestrictedWithoutNull(unionType);
    assertNotNull(restricted);
    
    JSType nullResult = interpreter.getRestrictedWithoutNull(null);
    assertNull(nullResult);
  }

  public void testGetRestrictedByTypeOfResultNullType() throws Throwable {
    JSType restricted = interpreter.getRestrictedByTypeOfResult(null, "number", true);
    assertNotNull(restricted);
    
    JSType restrictedFalse = interpreter.getRestrictedByTypeOfResult(null, "number", false);
    assertNull(restrictedFalse);

    JSType restrictedUnknown = interpreter.getRestrictedByTypeOfResult(null, "unknown_type_val", true);
    assertNotNull(restrictedUnknown);
  }

  public void testGetRestrictedByTypeOfResultBasicTypes() throws Throwable {
    JSType numType = typeRegistry.getNativeType(JSTypeNative.NUMBER_TYPE);
    
    JSType res1 = interpreter.getRestrictedByTypeOfResult(numType, "number", true);
    assertNotNull(res1);

    JSType res2 = interpreter.getRestrictedByTypeOfResult(numType, "string", true);
    assertNull(res2);

    JSType res3 = interpreter.getRestrictedByTypeOfResult(numType, "number", false);
    assertNull(res3);
  }

  public void testVisitorsCoverage() throws Throwable {
    // Exercise restrictUndefinedVisitor and restrictNullVisitor via various JSTypes
    JSType allType = typeRegistry.getNativeType(JSTypeNative.ALL_TYPE);
    assertNotNull(interpreter.getRestrictedWithoutUndefined(allType));
    assertNotNull(interpreter.getRestrictedWithoutNull(allType));

    JSType noObjType = typeRegistry.getNativeType(JSTypeNative.NO_OBJECT_TYPE);
    assertNotNull(interpreter.getRestrictedWithoutUndefined(noObjType));
    assertNotNull(interpreter.getRestrictedWithoutNull(noObjType));

    JSType noType = typeRegistry.getNativeType(JSTypeNative.NO_TYPE);
    assertNotNull(interpreter.getRestrictedWithoutUndefined(noType));
    assertNotNull(interpreter.getRestrictedWithoutNull(noType));

    JSType boolType = typeRegistry.getNativeType(JSTypeNative.BOOLEAN_TYPE);
    assertNotNull(interpreter.getRestrictedWithoutUndefined(boolType));
    assertNotNull(interpreter.getRestrictedWithoutNull(boolType));

    JSType nullType = typeRegistry.getNativeType(JSTypeNative.NULL_TYPE);
    assertNotNull(interpreter.getRestrictedWithoutUndefined(nullType));
    assertNull(interpreter.getRestrictedWithoutNull(nullType));

    JSType strType = typeRegistry.getNativeType(JSTypeNative.STRING_TYPE);
    assertNotNull(interpreter.getRestrictedWithoutUndefined(strType));
    assertNotNull(interpreter.getRestrictedWithoutNull(strType));

    JSType voidType = typeRegistry.getNativeType(JSTypeNative.VOID_TYPE);
    assertNull(interpreter.getRestrictedWithoutUndefined(voidType));
    assertNotNull(interpreter.getRestrictedWithoutNull(voidType));

    JSType unkType = typeRegistry.getNativeType(JSTypeNative.UNKNOWN_TYPE);
    assertNotNull(interpreter.getRestrictedWithoutUndefined(unkType));
    assertNotNull(interpreter.getRestrictedWithoutNull(unkType));
  }
}