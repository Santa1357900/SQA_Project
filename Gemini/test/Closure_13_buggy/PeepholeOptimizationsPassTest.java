package com.google.javascript.jscomp;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import junit.framework.TestCase;

public class PeepholeOptimizationsPassTest extends TestCase {

  private Compiler compiler;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    compiler.initOptions(options);
  }

  private static class DummyOptimization extends AbstractPeepholeOptimization {
    private boolean beginCalled = false;
    private boolean endCalled = false;
    private int optimizeCount = 0;
    private Node replacementNode = null;

    @Override
    Node optimizeSubtree(Node subtree) {
      optimizeCount++;
      if (replacementNode != null) {
        Node ret = replacementNode;
        replacementNode = null;
        return ret;
      }
      return subtree;
    }

    @Override
    void beginTraversal(AbstractCompiler compiler) {
      beginCalled = true;
    }

    @Override
    void endTraversal(AbstractCompiler compiler) {
      endCalled = true;
    }
  }

  public void testConstructorAndGetCompiler() throws Throwable {
    DummyOptimization opt = new DummyOptimization();
    PeepholeOptimizationsPass pass = new PeepholeOptimizationsPass(compiler, opt);
    assertSame(compiler, pass.getCompiler());
  }

  public void testProcessBasicTraversal() throws Throwable {
    DummyOptimization opt = new DummyOptimization();
    PeepholeOptimizationsPass pass = new PeepholeOptimizationsPass(compiler, opt);

    Node root = new Node(Token.SCRIPT);
    Node externs = new Node(Token.SCRIPT);

    pass.process(externs, root);

    assertTrue(opt.beginCalled);
    assertTrue(opt.endCalled);
    assertTrue(opt.optimizeCount > 0);
  }

  public void testVisitWithNodeReplacement() throws Throwable {
    Node original = new Node(Token.NUMBER, 1.0);
    Node replacement = new Node(Token.NUMBER, 2.0);

    DummyOptimization opt = new DummyOptimization();
    opt.replacementNode = replacement;

    PeepholeOptimizationsPass pass = new PeepholeOptimizationsPass(compiler, opt);
    pass.visit(original);

    assertTrue(opt.optimizeCount >= 2);
  }

  public void testVisitReturnsNullWhenNodeBecomesNull() throws Throwable {
    DummyOptimization opt = new DummyOptimization() {
      @Override
      Node optimizeSubtree(Node subtree) {
        return null;
      }
    };

    PeepholeOptimizationsPass pass = new PeepholeOptimizationsPass(compiler, opt);
    Node node = new Node(Token.NUMBER, 1.0);
    pass.visit(node);
  }

  public void testRetraverseOnFunctionOrScriptChange() throws Throwable {
    final Node replacement = new Node(Token.SCRIPT);
    DummyOptimization opt = new DummyOptimization() {
      private boolean changedOnce = false;
      @Override
      Node optimizeSubtree(Node subtree) {
        if (!changedOnce && subtree.isScript()) {
          changedOnce = true;
          // Report change through change handler
          compiler.reportCodeChange();
        }
        return subtree;
      }
    };

    PeepholeOptimizationsPass pass = new PeepholeOptimizationsPass(compiler, opt);
    Node root = new Node(Token.SCRIPT);
    Node externs = new Node(Token.SCRIPT);

    pass.process(externs, root);
    assertTrue(opt.optimizeCount > 1);
  }

  public void testIterationLimitExceeded() throws Throwable {
    DummyOptimization opt = new DummyOptimization() {
      @Override
      Node optimizeSubtree(Node subtree) {
        compiler.reportCodeChange();
        return subtree;
      }
    };

    PeepholeOptimizationsPass pass = new PeepholeOptimizationsPass(compiler, opt);
    Node root = new Node(Token.SCRIPT);
    Node externs = new Node(Token.SCRIPT);

    try {
      pass.process(externs, root);
      fail("Expected IllegalStateException due to too many iterations");
    } catch (IllegalStateException e) {
      assertTrue(e.getMessage().contains("too many interations"));
    }
  }

  public void testNestedFunctionTraversal() throws Throwable {
    DummyOptimization opt = new DummyOptimization();
    PeepholeOptimizationsPass pass = new PeepholeOptimizationsPass(compiler, opt);

    Node script = new Node(Token.SCRIPT);
    Node func = new Node(Token.FUNCTION);
    script.addChildToBack(func);

    Node externs = new Node(Token.SCRIPT);
    pass.process(externs, script);

    assertTrue(opt.beginCalled);
    assertTrue(opt.endCalled);
  }
}