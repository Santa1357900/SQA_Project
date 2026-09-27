package com.google.javascript.jscomp;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.ObjectType;

import junit.framework.TestCase;

import java.util.Collection;
import java.util.List;

public class ClosureCodingConventionTest extends TestCase {

  private ClosureCodingConvention convention;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    convention = new ClosureCodingConvention();
  }

  public void testIsSuperClassReference() throws Throwable {
    assertTrue(convention.isSuperClassReference("superClass_"));
    assertFalse(convention.isSuperClassReference("prototype"));
    assertFalse(convention.isSuperClassReference(null));
  }

  public void testExtractClassNameIfProvide() throws Throwable {
    Node provideNode = Node.newString(Token.NAME, "goog.provide");
    Node argNode = Node.newString(Token.STRING, "my.class");
    provideNode.addChildToBack(argNode);

    Node exprResult = new Node(Token.EXPR_RESULT, provideNode);

    String className = convention.extractClassNameIfProvide(provideNode, exprResult);
    assertEquals("my.class", className);

    // Test invalid parent or structure
    assertNull(convention.extractClassNameIfProvide(provideNode, new Node(Token.BLOCK)));
    
    Node nonGetPropNode = Node.newString(Token.NAME, "notGetProp");
    Node exprResult2 = new Node(Token.EXPR_RESULT, nonGetPropNode);
    assertNull(convention.extractClassNameIfProvide(nonGetPropNode, exprResult2));
  }

  public void testExtractClassNameIfRequire() throws Throwable {
    Node requireNode = Node.newString(Token.NAME, "goog.require");
    Node argNode = Node.newString(Token.STRING, "my.required");
    requireNode.addChildToBack(argNode);

    Node exprResult = new Node(Token.EXPR_RESULT, requireNode);

    String className = convention.extractClassNameIfRequire(requireNode, exprResult);
    assertEquals("my.required", className);

    assertNull(convention.extractClassNameIfRequire(requireNode, new Node(Token.BLOCK)));
  }

  public void testGetExportPropertyFunction() throws Throwable {
    assertEquals("goog.exportProperty", convention.getExportPropertyFunction());
  }

  public void testGetExportSymbolFunction() throws Throwable {
    assertEquals("goog.exportSymbol", convention.getExportSymbolFunction());
  }

  public void testGetAbstractMethodName() throws Throwable {
    assertEquals("goog.abstractMethod", convention.getAbstractMethodName());
  }

  public void testGetGlobalObject() throws Throwable {
    assertEquals("goog.global", convention.getGlobalObject());
  }

  public void testIsOptionalParameter() throws Throwable {
    Node node = Node.newNumber(1.0);
    assertFalse(convention.isOptionalParameter(node));
    assertFalse(convention.isOptionalParameter(null));
  }

  public void testIsVarArgsParameter() throws Throwable {
    Node node = Node.newNumber(1.0);
    assertFalse(convention.isVarArgsParameter(node));
    assertFalse(convention.isVarArgsParameter(null));
  }

  public void testIsPrivate() throws Throwable {
    assertFalse(convention.isPrivate("anyName"));
    assertFalse(convention.isPrivate("_privateName"));
    assertFalse(convention.isPrivate(null));
  }

  public void testGetAssertionFunctions() throws Throwable {
    Collection<AssertionFunctionSpec> specs = convention.getAssertionFunctions();
    assertNotNull(specs);
    assertFalse(specs.isEmpty());
  }

  public void testIdentifyTypeDeclarationCall() throws Throwable {
    Node callName = Node.newString(Token.NAME, "goog.addDependency");
    Node arg1 = Node.newString(Token.STRING, "file.js");
    Node arg2 = Node.newString(Token.STRING, "provides");
    Node arrayLit = new Node(Token.ARRAYLIT);
    Node elem1 = Node.newString(Token.STRING, "dep1");
    arrayLit.addChildToBack(elem1);

    callName.addChildToBack(arg1);
    callName.addChildToBack(arg2);
    callName.addChildToBack(arrayLit);

    Node callNode = new Node(Token.CALL, callName);

    List<String> types = convention.identifyTypeDeclarationCall(callNode);
    assertNotNull(types);
    assertEquals(1, types.size());
    assertEquals("dep1", types.get(0));

    // Test not enough children or wrong name
    Node smallCall = new Node(Token.CALL, Node.newString(Token.NAME, "wrong.name"));
    assertNull(convention.identifyTypeDeclarationCall(smallCall));
  }

  public void testGetSingletonGetterClassName() throws Throwable {
    Node callName = Node.newString(Token.NAME, "goog.addSingletonGetter");
    Node arg = Node.newString(Token.NAME, "MyClass");
    callName.addChildToBack(arg);

    Node callNode = new Node(Token.CALL, callName);
    // Child count must be 2 (callName and 1 arg)
    // Wait, getChildCount() for callNode including callName:
    // callNode has children: callName, arg. Total = 2 children.
    // Let's verify implementation: callNode.getChildCount() != 2 returns null.
    // Actually callNode.getFirstChild() is callName, callArg.getNext() is arg.
    
    // Let's test invalid child count
    Node invalidCallNode = new Node(Token.CALL);
    assertNull(convention.getSingletonGetterClassName(invalidCallNode));

    // Test valid addSingletonGetter with correct structure
    Node validCall = new Node(Token.CALL);
    Node target = Node.newString(Token.NAME, "goog.addSingletonGetter");
    Node param = Node.newString(Token.NAME, "TargetClass");
    validCall.addChildToBack(target);
    validCall.addChildToBack(param);

    assertEquals("TargetClass", convention.getSingletonGetterClassName(validCall));
    
    // Test post-CollapseProperties name
    Node validCall2 = new Node(Token.CALL);
    Node target2 = Node.newString(Token.NAME, "goog$addSingletonGetter");
    Node param2 = Node.newString(Token.NAME, "TargetClass2");
    validCall2.addChildToBack(target2);
    validCall2.addChildToBack(param2);

    assertEquals("TargetClass2", convention.getSingletonGetterClassName(validCall2));
  }

  public void testIsPropertyTestFunction() throws Throwable {
    Node callName = Node.newString(Token.NAME, "goog.isDef");
    Node callNode = new Node(Token.CALL, callName);
    assertTrue(convention.isPropertyTestFunction(callNode));

    Node falseCallName = Node.newString(Token.NAME, "goog.notAPropertyTest");
    Node falseCallNode = new Node(Token.CALL, falseCallName);
    assertFalse(convention.isPropertyTestFunction(falseCallNode));
  }

  public void testDescribeFunctionBind() throws Throwable {
    // Test goog.bind
    Node callName = Node.newString(Token.NAME, "goog.bind");
    Node fn = Node.newString(Token.NAME, "myFunc");
    Node self = Node.newString(Token.NAME, "mySelf");
    callName.addChildToBack(fn);
    fn.setNext(self);

    Node callNode = new Node(Token.CALL, callName);
    Bind bind = convention.describeFunctionBind(callNode);
    assertNotNull(bind);

    // Test goog.partial
    Node callNamePartial = Node.newString(Token.NAME, "goog.partial");
    Node fnPartial = Node.newString(Token.NAME, "myFuncPartial");
    callNamePartial.addChildToBack(fnPartial);

    Node callNodePartial = new Node(Token.CALL, callNamePartial);
    Bind bindPartial = convention.describeFunctionBind(callNodePartial);
    assertNotNull(bindPartial);

    // Test non-call or unknown
    Node nonCall = Node.newString(Token.NAME, "notACall");
    assertNull(convention.describeFunctionBind(nonCall));
  }
}