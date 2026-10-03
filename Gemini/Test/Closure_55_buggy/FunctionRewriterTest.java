package com.google.javascript.jscomp;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.SourceFile;

import junit.framework.TestCase;

public class FunctionRewriterTest extends TestCase {

  private Compiler compiler;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    compiler.init(
        new ArrayList<SourceFile>(),
        new ArrayList<SourceFile>(),
        options);
  }

  public void testEmptyFunctionReducer() throws Throwable {
    FunctionRewriter rewriter = new FunctionRewriter(compiler);
    
    Node fn = new Node(Token.FUNCTION);
    Node name = new Node(Token.NAME, "");
    Node args = new Node(Token.PARAM_LIST);
    Node body = new Node(Token.BLOCK);
    fn.addChildToBack(name);
    fn.addChildToBack(args);
    fn.addChildToBack(body);

    Node root = new Node(Token.BLOCK, fn);
    rewriter.process(null, root);
    
    assertFalse(NodeUtil.isEmptyFunctionExpression(fn));
  }

  public void testIdentityReducer() throws Throwable {
    FunctionRewriter rewriter = new FunctionRewriter(compiler);

    Node fn = new Node(Token.FUNCTION);
    Node name = new Node(Token.NAME, "");
    Node args = new Node(Token.PARAM_LIST);
    args.addChildToBack(new Node(Token.NAME, "x"));
    
    Node body = new Node(Token.BLOCK);
    Node ret = new Node(Token.RETURN, new Node(Token.NAME, "x"));
    body.addChildToBack(ret);

    fn.addChildToBack(name);
    fn.addChildToBack(args);
    fn.addChildToBack(body);

    Node root = new Node(Token.BLOCK, fn);
    rewriter.process(null, root);
    
    assertEquals(Token.CALL, root.getFirstChild().getType());
  }

  public void testReturnConstantReducer() throws Throwable {
    FunctionRewriter rewriter = new FunctionRewriter(compiler);

    Node fn = new Node(Token.FUNCTION);
    Node name = new Node(Token.NAME, "");
    Node args = new Node(Token.PARAM_LIST);
    
    Node body = new Node(Token.BLOCK);
    Node ret = new Node(Token.RETURN, Node.newNumber(10.0));
    body.addChildToBack(ret);

    fn.addChildToBack(name);
    fn.addChildToBack(args);
    fn.addChildToBack(body);

    Node root = new Node(Token.BLOCK, fn);
    rewriter.process(null, root);
    
    assertEquals(Token.CALL, root.getFirstChild().getType());
  }

  public void testGetterReducer() throws Throwable {
    FunctionRewriter rewriter = new FunctionRewriter(compiler);

    Node fn = new Node(Token.FUNCTION);
    Node name = new Node(Token.NAME, "");
    Node args = new Node(Token.PARAM_LIST);
    
    Node body = new Node(Token.BLOCK);
    Node getProp = new Node(Token.GETPROP, new Node(Token.THIS), Node.newString(Token.STRING, "b_"));
    Node ret = new Node(Token.RETURN, getProp);
    body.addChildToBack(ret);

    fn.addChildToBack(name);
    fn.addChildToBack(args);
    fn.addChildToBack(body);

    Node root = new Node(Token.BLOCK, fn);
    rewriter.process(null, root);
    
    assertEquals(Token.CALL, root.getFirstChild().getType());
  }

  public void testSetterReducer() throws Throwable {
    FunctionRewriter rewriter = new FunctionRewriter(compiler);

    Node fn = new Node(Token.FUNCTION);
    Node name = new Node(Token.NAME, "");
    Node args = new Node(Token.PARAM_LIST);
    args.addChildToBack(new Node(Token.NAME, "val"));
    
    Node body = new Node(Token.BLOCK);
    Node getProp = new Node(Token.GETPROP, new Node(Token.THIS), Node.newString(Token.STRING, "b_"));
    Node assign = new Node(Token.ASSIGN, getProp, new Node(Token.NAME, "val"));
    Node expr = new Node(Token.EXPR_RESULT, assign);
    body.addChildToBack(expr);

    fn.addChildToBack(name);
    fn.addChildToBack(args);
    fn.addChildToBack(body);

    Node root = new Node(Token.BLOCK, fn);
    rewriter.process(null, root);
    
    assertEquals(Token.CALL, root.getFirstChild().getType());
  }

  public void testParseHelperCodeValid() throws Throwable {
    FunctionRewriter rewriter = new FunctionRewriter(compiler);
    FunctionRewriter.Reducer reducer = new FunctionRewriter.EmptyFunctionReducer();
    Node helper = rewriter.parseHelperCode(reducer);
    assertNotNull(helper);
  }

  public void testNonReduceableFunction() throws Throwable {
    FunctionRewriter rewriter = new FunctionRewriter(compiler);

    Node fn = new Node(Token.FUNCTION);
    Node name = new Node(Token.NAME, "myFunc");
    Node args = new Node(Token.PARAM_LIST);
    Node body = new Node(Token.BLOCK);
    fn.addChildToBack(name);
    fn.addChildToBack(args);
    fn.addChildToBack(body);

    Node root = new Node(Token.BLOCK, fn);
    rewriter.process(null, root);
    
    assertEquals(Token.FUNCTION, root.getFirstChild().getType());
  }

  public void testGetterReducerInvalidProp() throws Throwable {
    FunctionRewriter rewriter = new FunctionRewriter(compiler);

    Node fn = new Node(Token.FUNCTION);
    Node name = new Node(Token.NAME, "");
    Node args = new Node(Token.PARAM_LIST);
    
    Node body = new Node(Token.BLOCK);
    Node getProp = new Node(Token.GETPROP, new Node(Token.THIS), Node.newNumber(1.0));
    Node ret = new Node(Token.RETURN, getProp);
    body.addChildToBack(ret);

    fn.addChildToBack(name);
    fn.addChildToBack(args);
    fn.addChildToBack(body);

    Node root = new Node(Token.BLOCK, fn);
    try {
      rewriter.process(null, root);
      fail("Expected IllegalStateException");
    } catch (IllegalStateException e) {
      assertTrue(e.getMessage().contains("Expected STRING"));
    }
  }

  public void testSetterReducerInvalidProp() throws Throwable {
    FunctionRewriter rewriter = new FunctionRewriter(compiler);

    Node fn = new Node(Token.FUNCTION);
    Node name = new Node(Token.NAME, "");
    Node args = new Node(Token.PARAM_LIST);
    args.addChildToBack(new Node(Token.NAME, "val"));
    
    Node body = new Node(Token.BLOCK);
    Node getProp = new Node(Token.GETPROP, new Node(Token.THIS), Node.newNumber(1.0));
    Node assign = new Node(Token.ASSIGN, getProp, new Node(Token.NAME, "val"));
    Node expr = new Node(Token.EXPR_RESULT, assign);
    body.addChildToBack(expr);

    fn.addChildToBack(name);
    fn.addChildToBack(args);
    fn.addChildToBack(body);

    Node root = new Node(Token.BLOCK, fn);
    // SetterReducer's getSetPropertyName returns null if propertyName is not a string, causing no reduction.
    rewriter.process(null, root);
    assertEquals(Token.FUNCTION, root.getFirstChild().getType());
  }
}