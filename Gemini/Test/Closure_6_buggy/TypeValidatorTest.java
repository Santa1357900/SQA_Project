package com.google.javascript.jscomp;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;
import org.junit.Before;
import org.junit.Test;

import java.util.Iterator;

import static org.junit.Assert.*;

public class TypeValidatorTest {

  private Compiler compiler;
  private TypeValidator typeValidator;
  private JSTypeRegistry typeRegistry;

  @Before
  public void setUp() throws Throwable {
    compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    compiler.initOptions(options);
    typeValidator = new TypeValidator(compiler);
    typeRegistry = compiler.getTypeRegistry();
  }

  @Test
  public void testGetMismatchesAndReportingFlag() throws Throwable {
    typeValidator.setShouldReport(false);
    assertNotNull(typeValidator.getMismatches());
    
    typeValidator.setShouldReport(true);
    NodeTraversal t = new NodeTraversal(compiler, new NodeTraversal.Callback() {
      public boolean shouldTraverse(NodeTraversal nodeTraversal, Node n) {
        return true;
      }
      public void visit(NodeTraversal nodeTraversal, Node n) {}
    });
    Node node = IR.name("test");
    typeValidator.expectValidTypeofName(t, node, "unknown_type");
    
    Iterable<TypeValidator.TypeMismatch> mismatches = typeValidator.getMismatches();
    assertNotNull(mismatches);
  }

  @Test
  public void testExpectObjectAndActualObject() throws Throwable {
    NodeTraversal t = new NodeTraversal(compiler, null);
    Node node = IR.name("obj");
    JSType unknownType = typeRegistry.getNativeType(com.google.javascript.rhino.jstype.JSTypeNative.UNKNOWN_TYPE);
    JSType objectType = typeRegistry.getNativeType(com.google.javascript.rhino.jstype.JSTypeNative.OBJECT_TYPE);

    boolean res = typeValidator.expectObject(t, node, objectType, "msg");
    assertTrue(res);

    typeValidator.expectActualObject(t, node, objectType, "msg");
  }

  @Test
  public void testExpectAnyObject() throws Throwable {
    NodeTraversal t = new NodeTraversal(compiler, null);
    Node node = IR.name("obj");
    JSType objectType = typeRegistry.getNativeType(com.google.javascript.rhino.jstype.JSTypeNative.OBJECT_TYPE);
    
    typeValidator.expectAnyObject(t, node, objectType, "msg");
  }

  @Test
  public void testExpectStringAndNumber() throws Throwable {
    NodeTraversal t = new NodeTraversal(compiler, null);
    Node node = IR.name("val");
    JSType stringType = typeRegistry.getNativeType(com.google.javascript.rhino.jstype.JSTypeNative.STRING_TYPE);
    JSType numberType = typeRegistry.getNativeType(com.google.javascript.rhino.jstype.JSTypeNative.NUMBER_TYPE);

    typeValidator.expectString(t, node, stringType, "msg");
    typeValidator.expectNumber(t, node, numberType, "msg");
    typeValidator.expectBitwiseable(t, node, numberType, "msg");
    typeValidator.expectStringOrNumber(t, node, stringType, "msg");
  }

  @Test
  public void testExpectNotNullOrUndefined() throws Throwable {
    NodeTraversal t = new NodeTraversal(compiler, null);
    Node node = IR.name("val");
    JSType stringType = typeRegistry.getNativeType(com.google.javascript.rhino.jstype.JSTypeNative.STRING_TYPE);
    JSType nullType = typeRegistry.getNativeType(com.google.javascript.rhino.jstype.JSTypeNative.NULL_TYPE);

    boolean res = typeValidator.expectNotNullOrUndefined(t, node, stringType, "msg", nullType);
    assertTrue(res);
  }

  @Test
  public void testExpectSwitchMatchesCase() throws Throwable {
    NodeTraversal t = new NodeTraversal(compiler, null);
    Node switchNode = IR.switchNode(IR.name("s"), IR.caseNode(IR.number(1), IR.block()));
    JSType numberType = typeRegistry.getNativeType(com.google.javascript.rhino.jstype.JSTypeNative.NUMBER_TYPE);

    typeValidator.expectSwitchMatchesCase(t, switchNode, numberType, numberType);
  }

  @Test
  public void testExpectIndexMatch() throws Throwable {
    NodeTraversal t = new NodeTraversal(compiler, null);
    Node getElem = IR.getelem(IR.name("arr"), IR.number(0));
    JSType unknownType = typeRegistry.getNativeType(com.google.javascript.rhino.jstype.JSTypeNative.UNKNOWN_TYPE);
    JSType numberType = typeRegistry.getNativeType(com.google.javascript.rhino.jstype.JSTypeNative.NUMBER_TYPE);

    typeValidator.expectIndexMatch(t, getElem, unknownType, numberType);
  }

  @Test
  public void testExpectCanAssignTo() throws Throwable {
    NodeTraversal t = new NodeTraversal(compiler, null);
    Node node = IR.name("x");
    JSType stringType = typeRegistry.getNativeType(com.google.javascript.rhino.jstype.JSTypeNative.STRING_TYPE);
    JSType numberType = typeRegistry.getNativeType(com.google.javascript.rhino.jstype.JSTypeNative.NUMBER_TYPE);

    boolean res = typeValidator.expectCanAssignTo(t, node, stringType, stringType, "msg");
    assertTrue(res);

    boolean resMismatch = typeValidator.expectCanAssignTo(t, node, stringType, numberType, "msg");
    assertFalse(resMismatch);
  }

  @Test
  public void testExpectArgumentMatchesParameter() throws Throwable {
    NodeTraversal t = new NodeTraversal(compiler, null);
    Node argNode = IR.name("arg");
    Node callNode = IR.call(IR.name("func"), argNode);
    JSType stringType = typeRegistry.getNativeType(com.google.javascript.rhino.jstype.JSTypeNative.STRING_TYPE);

    typeValidator.expectArgumentMatchesParameter(t, argNode, stringType, stringType, callNode, 0);
  }

  @Test
  public void testExpectCanCast() throws Throwable {
    NodeTraversal t = new NodeTraversal(compiler, null);
    Node node = IR.name("x");
    JSType stringType = typeRegistry.getNativeType(com.google.javascript.rhino.jstype.JSTypeNative.STRING_TYPE);

    typeValidator.expectCanCast(t, node, stringType, stringType);
  }

  @Test
  public void testGetReadableJSTypeName() throws Throwable {
    Node node = IR.name("x");
    String name = typeValidator.getReadableJSTypeName(node, false);
    assertNotNull(name);

    Node getProp = IR.getprop(IR.name("obj"), IR.string("prop"));
    String propName = typeValidator.getReadableJSTypeName(getProp, true);
    assertNotNull(propName);
  }

  @Test
  public void testTypeMismatchEqualsAndHashCode() throws Throwable {
    JSType stringType = typeRegistry.getNativeType(com.google.javascript.rhino.jstype.JSTypeNative.STRING_TYPE);
    JSType numberType = typeRegistry.getNativeType(com.google.javascript.rhino.jstype.JSTypeNative.NUMBER_TYPE);

    TypeValidator.TypeMismatch mismatch1 = new TypeValidator.TypeMismatch(stringType, numberType, null);
    TypeValidator.TypeMismatch mismatch2 = new TypeValidator.TypeMismatch(stringType, numberType, null);
    TypeValidator.TypeMismatch mismatch3 = new TypeValidator.TypeMismatch(numberType, stringType, null);
    Object otherObj = new Object();

    assertEquals(mismatch1, mismatch2);
    assertEquals(mismatch1, mismatch3);
    assertFalse(mismatch1.equals(null));
    assertFalse(mismatch1.equals(otherObj));
    assertEquals(mismatch1.hashCode(), mismatch2.hashCode());
    assertNotNull(mismatch1.toString());
  }
}