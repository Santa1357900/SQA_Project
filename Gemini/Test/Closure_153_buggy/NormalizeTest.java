package com.google.javascript.jscomp;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.JSDocInfo;
import com.google.javascript.rhino.jstype.JSType;

import org.junit.Test;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.List;

public class NormalizeTest {

  private static class DummyCompiler extends AbstractCompiler {
    private boolean normalized = false;
    private int codeChanges = 0;
    private List<JSError> errors = new ArrayList<JSError>();

    @Override
    public Node parseSyntheticCode(String js) {
      return new Node(Token.SCRIPT);
    }

    @Override
    public Node parseTestCode(String js) {
      return new Node(Token.SCRIPT);
    }

    @Override
    public void reportCodeChange() {
      codeChanges++;
    }

    @Override
    public boolean getLifeCycleStageNormalized() {
      return normalized;
    }

    @Override
    public void setNormalized() {
      this.normalized = true;
    }

    @Override
    public CodingConvention getCodingConvention() {
      return new DefaultCodingConvention();
    }

    @Override
    public void report(JSError error) {
      errors.add(error);
    }

    @Override
    public CompilerOptions getOptions() {
      return new CompilerOptions();
    }
  }

  @Test
  public void testNormalizeInstantiationAndProcess() throws Throwable {
    DummyCompiler compiler = new DummyCompiler();
    Normalize normalize = new Normalize(compiler, false);
    Node externs = new Node(Token.SCRIPT);
    Node root = new Node(Token.SCRIPT);
    Node block = new Node(Token.BLOCK);
    root.addChildToBack(block);
    
    normalize.process(externs, root);
    assertTrue(compiler.getLifeCycleStageNormalized());
  }

  @Test(expected = IllegalStateException.class)
  public void testNormalizeAssertOnChangeViolation() throws Throwable {
    DummyCompiler compiler = new DummyCompiler();
    Normalize normalize = new Normalize(compiler, true);
    Node externs = new Node(Token.SCRIPT);
    Node root = new Node(Token.SCRIPT);
    Node whileNode = new Node(Token.WHILE, IR.trueNode(), new Node(Token.BLOCK));
    root.addChildToBack(whileNode);

    normalize.process(externs, root);
  }

  @Test
  public void testParseAndNormalizeSyntheticCode() throws Throwable {
    DummyCompiler compiler = new DummyCompiler();
    Node result = Normalize.parseAndNormalizeSyntheticCode(compiler, "var a = 0;", "test");
    assertNotNull(result);
  }

  @Test
  public void testParseAndNormalizeTestCode() throws Throwable {
    DummyCompiler compiler = new DummyCompiler();
    Node result = Normalize.parseAndNormalizeTestCode(compiler, "var a = 0;", "test");
    assertNotNull(result);
  }

  @Test
  public void testVerifyConstantsPass() throws Throwable {
    DummyCompiler compiler = new DummyCompiler();
    Node externs = new Node(Token.SCRIPT);
    Node root = new Node(Token.SCRIPT);
    Node name = IR.name("FOO");
    name.putBooleanProp(Node.IS_CONSTANT_NAME, true);
    Node var = new Node(Token.VAR, name);
    root.addChildToBack(var);
    externs.addChildToBack(new Node(Token.SCRIPT));

    Normalize.VerifyConstants verify = new Normalize.VerifyConstants(compiler, false);
    verify.process(externs, root);
  }

  @Test(expected = IllegalStateException.class)
  public void testVerifyConstantsFailCheck() throws Throwable {
    DummyCompiler compiler = new DummyCompiler();
    Node externs = new Node(Token.SCRIPT);
    Node root = new Node(Token.SCRIPT);
    Node name = IR.name("foo");
    name.putBooleanProp(Node.IS_CONSTANT_NAME, true);
    Node var = new Node(Token.VAR, name);
    root.addChildToBack(var);
    externs.addChildToBack(new Node(Token.SCRIPT));

    Normalize.VerifyConstants verify = new Normalize.VerifyConstants(compiler, true);
    verify.process(externs, root);
  }

  @Test
  public void testPropagateConstantAnnotationsOverVars() throws Throwable {
    DummyCompiler compiler = new DummyCompiler();
    Node externs = new Node(Token.SCRIPT);
    Node root = new Node(Token.SCRIPT);
    Node name = IR.name("FOO");
    Node var = new Node(Token.VAR, name);
    root.addChildToBack(var);

    Normalize.PropagateConstantAnnotationsOverVars prop = 
        new Normalize.PropagateConstantAnnotationsOverVars(compiler, false);
    prop.process(externs, root);
  }

  @Test(expected = IllegalStateException.class)
  public void testPropagateConstantAnnotationsAssertOnChange() throws Throwable {
    DummyCompiler compiler = new DummyCompiler();
    Node externs = new Node(Token.SCRIPT);
    Node root = new Node(Token.SCRIPT);
    Node name = IR.name("FOO");
    Node expr = IR.exprResult(name);
    root.addChildToBack(expr);

    Normalize.PropagateConstantAnnotationsOverVars prop = 
        new Normalize.PropagateConstantAnnotationsOverVars(compiler, true);
    prop.process(externs, root);
  }

  @Test
  public void testNormalizeStatementsWhileConversion() throws Throwable {
    DummyCompiler compiler = new DummyCompiler();
    Normalize normalize = new Normalize(compiler, false);
    Node externs = new Node(Token.SCRIPT);
    Node root = new Node(Token.SCRIPT);
    Node whileNode = new Node(Token.WHILE, IR.trueNode(), new Node(Token.BLOCK));
    root.addChildToBack(whileNode);

    normalize.process(externs, root);
    assertEquals(Token.FOR, whileNode.getType());
  }

  @Test
  public void testNormalizeStatementsLabel() throws Throwable {
    DummyCompiler compiler = new DummyCompiler();
    Normalize normalize = new Normalize(compiler, false);
    Node externs = new Node(Token.SCRIPT);
    Node root = new Node(Token.SCRIPT);
    Node label = new Node(Token.LABEL, IR.name("L"), IR.exprResult(IR.number(1)));
    root.addChildToBack(label);

    normalize.process(externs, root);
    assertEquals(Token.BLOCK, label.getLastChild().getType());
  }

  @Test
  public void testNormalizeStatementsForInVar() throws Throwable {
    DummyCompiler compiler = new DummyCompiler();
    Normalize normalize = new Normalize(compiler, false);
    Node externs = new Node(Token.SCRIPT);
    Node root = new Node(Token.SCRIPT);
    Node block = new Node(Token.BLOCK);
    Node var = new Node(Token.VAR, IR.name("a"));
    Node forIn = new Node(Token.FOR, var, IR.name("b"), new Node(Token.BLOCK));
    forIn.putBooleanProp(Node.IS_FOR_IN, true);
    block.addChildToBack(forIn);
    root.addChildToBack(block);

    normalize.process(externs, root);
  }

  @Test
  public void testNormalizeStatementsForInitializer() throws Throwable {
    DummyCompiler compiler = new DummyCompiler();
    Normalize normalize = new Normalize(compiler, false);
    Node externs = new Node(Token.SCRIPT);
    Node root = new Node(Token.SCRIPT);
    Node block = new Node(Token.BLOCK);
    Node init = IR.number(1);
    Node forNode = new Node(Token.FOR, init, IR.trueNode(), IR.empty(), new Node(Token.BLOCK));
    block.addChildToBack(forNode);
    root.addChildToBack(block);

    normalize.process(externs, root);
  }

  @Test
  public void testNormalizeStatementsSplitVar() throws Throwable {
    DummyCompiler compiler = new DummyCompiler();
    Normalize normalize = new Normalize(compiler, false);
    Node externs = new Node(Token.SCRIPT);
    Node root = new Node(Token.SCRIPT);
    Node block = new Node(Token.BLOCK);
    Node var = new Node(Token.VAR, IR.name("a"), IR.name("b"));
    block.addChildToBack(var);
    root.addChildToBack(block);

    normalize.process(externs, root);
  }

  @Test
  public void testNormalizeStatementsMoveNamedFunctions() throws Throwable {
    DummyCompiler compiler = new DummyCompiler();
    Normalize normalize = new Normalize(compiler, false);
    Node externs = new Node(Token.SCRIPT);
    Node root = new Node(Token.SCRIPT);
    Node block = new Node(Token.BLOCK);
    Node expr = IR.exprResult(IR.number(1));
    Node func = new Node(Token.FUNCTION, IR.name("f"), new Node(Token.LP), new Node(Token.BLOCK));
    block.addChildToBack(expr);
    block.addChildToBack(func);
    Node fn = new Node(Token.FUNCTION, IR.name("outer"), new Node(Token.LP), block);
    root.addChildToBack(fn);

    normalize.process(externs, root);
  }
}