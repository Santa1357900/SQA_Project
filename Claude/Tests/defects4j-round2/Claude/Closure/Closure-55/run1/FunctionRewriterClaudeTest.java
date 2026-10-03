package com.google.javascript.jscomp;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

public class FunctionRewriterClaudeTest {

  private Compiler compiler;

  @Before
  public void setUp() throws Throwable {
    compiler = new Compiler();
    compiler.initOptions(new CompilerOptions());
  }

  // covers FunctionRewriter(AbstractCompiler) constructor and that the instance is usable
  @Test
  public void testConstructor_createsRewriterUsableForParseHelperCode() throws Throwable {
    FunctionRewriter rewriter = new FunctionRewriter(compiler);
    TestReducer reducer = new TestReducer("function JSCompiler_marker(){return 42;}");
    Node helper = rewriter.parseHelperCode(reducer);
    assertNotNull(helper);
  }

  // covers process() main loop when no reductions are gathered (reductions.isEmpty() -> continue)
  @Test
  public void testProcess_emptyRoot_noChangesNoException() throws Throwable {
    Node externs = IR.block();
    Node root = IR.block();
    new FunctionRewriter(compiler).process(externs, root);
    assertNull(root.getFirstChild());
    assertNull(externs.getFirstChild());
  }

  // covers GetterReducer.reduce match branch and process() savings>threshold apply branch
  @Test
  public void testProcess_getterPattern_manyOccurrences_reducedToGetterCall() throws Throwable {
    StringBuilder src = new StringBuilder();
    for (int i = 0; i < 20; i++) {
      src.append("a.prototype.get").append(i)
         .append(" = function(p1,p2,p3,p4,p5,p6,p7,p8) { return this.prop")
         .append(i).append("_; };");
    }
    Node root = compiler.parseSyntheticCode("getterInput", src.toString());
    new FunctionRewriter(compiler).process(IR.block(), root);
    Node rhs = root.getFirstChild().getFirstChild().getLastChild();
    assertEquals(Token.CALL, rhs.getType());
    assertEquals("JSCompiler_get", rhs.getFirstChild().getString());
  }

  // covers SingleReturnStatementReducer.maybeGetSingleReturnRValue: body.hasOneChild() == false
  @Test
  public void testProcess_getterPattern_multiStatementBody_notReduced() throws Throwable {
    Node root = compiler.parseSyntheticCode("t4",
        "a.b = function() { var x = 1; return this.a_; };");
    new FunctionRewriter(compiler).process(IR.block(), root);
    Node rhs = root.getFirstChild().getFirstChild().getLastChild();
    assertEquals(Token.FUNCTION, rhs.getType());
  }

  // covers GetterReducer.getGetPropertyName: NodeUtil.isThis(...) == false
  @Test
  public void testProcess_getterPattern_nonThisProperty_notReduced() throws Throwable {
    Node root = compiler.parseSyntheticCode("t5",
        "a.b = function() { return foo.a_; };");
    new FunctionRewriter(compiler).process(IR.block(), root);
    Node rhs = root.getFirstChild().getFirstChild().getLastChild();
    assertEquals(Token.FUNCTION, rhs.getType());
  }

  // covers SetterReducer.reduce match branch and process() apply branch
  @Test
  public void testProcess_setterPattern_manyOccurrences_reducedToSetterCall() throws Throwable {
    StringBuilder src = new StringBuilder();
    for (int i = 0; i < 20; i++) {
      src.append("a.prototype.set").append(i)
         .append(" = function(v,p2,p3,p4,p5,p6,p7,p8) { this.prop")
         .append(i).append("_ = v; };");
    }
    Node root = compiler.parseSyntheticCode("setterInput", src.toString());
    new FunctionRewriter(compiler).process(IR.block(), root);
    Node rhs = root.getFirstChild().getFirstChild().getLastChild();
    assertEquals(Token.CALL, rhs.getType());
    assertEquals("JSCompiler_set", rhs.getFirstChild().getString());
  }

  // covers SetterReducer.getSetPropertyName: rhs name != paramNode name
  @Test
  public void testProcess_setterPattern_paramNameMismatch_notReduced() throws Throwable {
    Node root = compiler.parseSyntheticCode("t7",
        "a.b = function(v) { this.a_ = w; };");
    new FunctionRewriter(compiler).process(IR.block(), root);
    Node rhs = root.getFirstChild().getFirstChild().getLastChild();
    assertEquals(Token.FUNCTION, rhs.getType());
  }

  // covers SetterReducer.getSetPropertyName: paramNode == null branch
  @Test
  public void testProcess_setterPattern_zeroParams_notReduced() throws Throwable {
    Node root = compiler.parseSyntheticCode("t8",
        "a.b = function() { this.a_ = 5; };");
    new FunctionRewriter(compiler).process(IR.block(), root);
    Node rhs = root.getFirstChild().getFirstChild().getLastChild();
    assertEquals(Token.FUNCTION, rhs.getType());
  }

  // covers EmptyFunctionReducer negative (body not empty) and other reducers' non-match paths
  @Test
  public void testProcess_nonEmptyBodyPlainAssignment_notReduced() throws Throwable {
    Node root = compiler.parseSyntheticCode("t9",
        "a.b = function() { x = 1; };");
    new FunctionRewriter(compiler).process(IR.block(), root);
    Node rhs = root.getFirstChild().getFirstChild().getLastChild();
    assertEquals(Token.FUNCTION, rhs.getType());
  }

  // covers ReturnConstantReducer.reduce match (NUMBER immutable value) and process() apply branch
  @Test
  public void testProcess_returnConstantNumber_manyOccurrences_reducedToReturnArgCall() throws Throwable {
    StringBuilder src = new StringBuilder();
    for (int i = 0; i < 20; i++) {
      src.append("a.prototype.c").append(i)
         .append(" = function(p1,p2,p3,p4,p5,p6,p7,p8) { return ")
         .append(i).append("; };");
    }
    Node root = compiler.parseSyntheticCode("constInput", src.toString());
    new FunctionRewriter(compiler).process(IR.block(), root);
    Node rhs = root.getFirstChild().getFirstChild().getLastChild();
    assertEquals(Token.CALL, rhs.getType());
    assertEquals("JSCompiler_returnArg", rhs.getFirstChild().getString());
  }

  // covers ReturnConstantReducer.reduce match (STRING immutable value)
  @Test
  public void testProcess_returnConstantString_manyOccurrences_reducedToReturnArgCall() throws Throwable {
    StringBuilder src = new StringBuilder();
    for (int i = 0; i < 20; i++) {
      src.append("a.prototype.s").append(i)
         .append(" = function(p1,p2,p3,p4,p5,p6,p7,p8) { return 'v")
         .append(i).append("'; };");
    }
    Node root = compiler.parseSyntheticCode("constStrInput", src.toString());
    new FunctionRewriter(compiler).process(IR.block(), root);
    Node rhs = root.getFirstChild().getFirstChild().getLastChild();
    assertEquals(Token.CALL, rhs.getType());
    assertEquals("JSCompiler_returnArg", rhs.getFirstChild().getString());
  }

  // covers IdentityReducer.isIdentityFunction match branch and process() apply branch
  @Test
  public void testProcess_identityPattern_manyOccurrences_reducedToIdentityCall() throws Throwable {
    StringBuilder src = new StringBuilder();
    for (int i = 0; i < 20; i++) {
      src.append("a.prototype.id").append(i)
         .append(" = function(v,p2,p3,p4,p5,p6,p7,p8) { return v; };");
    }
    Node root = compiler.parseSyntheticCode("identityInput", src.toString());
    new FunctionRewriter(compiler).process(IR.block(), root);
    Node rhs = root.getFirstChild().getFirstChild().getLastChild();
    assertEquals(Token.CALL, rhs.getType());
    assertEquals("JSCompiler_identityFn", rhs.getFirstChild().getString());
  }

  // covers IdentityReducer.isIdentityFunction: paramNode == null branch
  @Test
  public void testProcess_identityPattern_zeroParams_notReduced() throws Throwable {
    Node root = compiler.parseSyntheticCode("t13",
        "a.b = function() { return x; };");
    new FunctionRewriter(compiler).process(IR.block(), root);
    Node rhs = root.getFirstChild().getFirstChild().getLastChild();
    assertEquals(Token.FUNCTION, rhs.getType());
  }

  // covers IdentityReducer.isIdentityFunction: returned name != first param name
  @Test
  public void testProcess_identityPattern_paramNameMismatch_notReduced() throws Throwable {
    Node root = compiler.parseSyntheticCode("t14",
        "a.b = function(a, b) { return b; };");
    new FunctionRewriter(compiler).process(IR.block(), root);
    Node rhs = root.getFirstChild().getFirstChild().getLastChild();
    assertEquals(Token.FUNCTION, rhs.getType());
  }

  // covers ReturnConstantReducer/GetterReducer negative: value is a free NAME, not immutable/getprop
  @Test
  public void testProcess_unmatchedFreeVariableReturn_notReduced() throws Throwable {
    Node root = compiler.parseSyntheticCode("t15",
        "a.b = function(z) { return someGlobal; };");
    new FunctionRewriter(compiler).process(        IR.block(), root);
    Node rhs = root.getFirstChild().getFirstChild().getLastChild();
    assertEquals(Token.FUNCTION, rhs.getType());
  }

  // covers process() branch: savings > (helperCodeCost + SAVINGS_THRESHOLD) == false
  @Test
  public void testProcess_singleGetterOccurrence_savingsBelowThreshold_notReduced() throws Throwable {
    Node root = compiler.parseSyntheticCode("t16",
        "a.b = function() { return this.a_; };");
    new FunctionRewriter(compiler).process(IR.block(), root);
    Node rhs = root.getFirstChild().getFirstChild().getLastChild();
    assertEquals(Token.FUNCTION, rhs.getType());
  }

  // covers parseHelperCode() success path: root != null -> removeFirstChild()
  @Test
  public void testParseHelperCode_validSource_returnsFunctionNode() throws Throwable {
    FunctionRewriter rewriter = new FunctionRewriter(compiler);
    TestReducer reducer = new TestReducer("function fooBar() { return 1; }");
    Node helper = rewriter.parseHelperCode(reducer);
    assertNotNull(helper);
    assertEquals(Token.FUNCTION, helper.getType());
    assertEquals("fooBar", helper.getFirstChild().getString());
  }

  // covers parseHelperCode() failure path per javadoc: "If parse fails, return null"
  @Test
  public void testParseHelperCode_invalidSyntax_returnsNull() throws Throwable {
    FunctionRewriter rewriter = new FunctionRewriter(compiler);
    TestReducer reducer = new TestReducer("function ( { ! invalid syntax !!!");
    Node helper = rewriter.parseHelperCode(reducer);
    assertNull(helper);
  }

  // covers Reducer.getHelperSource() contract used for helper code lookup
  @Test
  public void testGetHelperSource_returnsProvidedSource() throws Throwable {
    TestReducer reducer = new TestReducer("function z(){}");
    assertEquals("function z(){}", reducer.getHelperSource());
  }

  // covers buildCallNode branch where argumentNode != null
  @Test
  public void testBuildCallNode_withArgument_createsCallWithTwoChildren() throws Throwable {
    TestReducer reducer = new TestReducer("function f(){}");
    Node arg = IR.string("propName");
    Node call = reducer.callBuildCallNode("myMethod", arg, 5, 10);
    assertEquals(Token.CALL, call.getType());
    Node nameNode = call.getFirstChild();
    assertEquals("myMethod", nameNode.getString());
    Node argClone = nameNode.getNext();
    assertNotNull(argClone);
    assertEquals(Token.STRING, argClone.getType());
    assertEquals("propName", argClone.getString());
  }

  // covers buildCallNode branch where argumentNode == null
  @Test
  public void testBuildCallNode_withoutArgument_createsCallWithSingleChild() throws Throwable {
    TestReducer reducer = new TestReducer("function f(){}");
    Node call = reducer.callBuildCallNode("myMethod2", null, 1, 2);
    Node nameNode = call.getFirstChild();
    assertEquals("myMethod2", nameNode.getString());
    assertNull(nameNode.getNext());
  }

  // covers argumentNode.cloneTree() usage ensuring a copy (not the original) is attached
  @Test
  public void testBuildCallNode_clonesArgumentNode_notSameReference() throws Throwable {
    TestReducer reducer = new TestReducer("function f(){}");
    Node arg = IR.name("value");
    Node call = reducer.callBuildCallNode("m", arg, 0, 0);
    Node clone = call.getFirstChild().getNext();
    assertTrue(clone != arg);
    assertEquals(Token.NAME, clone.getType());
    assertEquals("value", clone.getString());
  }

  // covers lineno/charno assignment via the Node(Token.CALL, lineno, charno) constructor
  @Test
  public void testBuildCallNode_setsLineAndCharNumbers() throws Throwable {
    TestReducer reducer = new TestReducer("function f(){}");
    Node call = reducer.callBuildCallNode("m", null, 7, 3);
    assertEquals(7, call.getLineno());
    assertEquals(3, call.getCharno());
  }

  private static class TestReducer extends FunctionRewriter.Reducer {
    private final String helperSource;

    TestReducer(String helperSource) {
      this.helperSource = helperSource;
    }

    String getHelperSource() {
      return helperSource;
    }

    Node reduce(Node node) {
      return node;
    }

    Node callBuildCallNode(String methodName, Node argumentNode, int lineno, int charno) {
      return buildCallNode(methodName, argumentNode, lineno, charno);
    }
  }
}
