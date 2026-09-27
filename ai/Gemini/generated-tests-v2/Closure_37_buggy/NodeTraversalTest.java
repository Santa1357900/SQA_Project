package com.google.javascript.jscomp;

import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;

import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class NodeTraversalTest extends TestCase {

  private Compiler compiler;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    compiler.initOptions(options);
  }

  public void testBasicTraversal() throws Throwable {
    Node root = IR.script();
    root.addChildToBack(IR.var(IR.name("x"), IR.number(1)));

    final List<Node> visited = new ArrayList<Node>();
    NodeTraversal.Callback cb = new NodeTraversal.AbstractPostOrderCallback() {
      @Override
      public void visit(NodeTraversal t, Node n, Node parent) {
        visited.add(n);
      }
    };

    NodeTraversal traversal = new NodeTraversal(compiler, cb);
    traversal.traverse(root);

    assertTrue(visited.size() > 0);
    assertEquals(root, traversal.getCurrentNode());
    assertNotNull(traversal.getCompiler());
  }

  public void testStaticTraverseMethods() throws Throwable {
    Node root = IR.script();
    final List<Node> visited = new ArrayList<Node>();
    NodeTraversal.Callback cb = new NodeTraversal.AbstractPostOrderCallback() {
      @Override
      public void visit(NodeTraversal t, Node n, Node parent) {
        visited.add(n);
      }
    };

    NodeTraversal.traverse(compiler, root, cb);
    assertTrue(visited.contains(root));

    List<Node> roots = new ArrayList<Node>();
    roots.add(root);
    NodeTraversal.traverseRoots(compiler, roots, cb);
    NodeTraversal.traverseRoots(compiler, cb, root);
  }

  public void testScopedCallback() throws Throwable {
    Node root = IR.script();
    Node fn = IR.function(IR.name("f"), IR.paramList(), IR.block());
    root.addChildToBack(fn);

    final List<String> events = new ArrayList<String>();
    NodeTraversal.ScopedCallback cb = new NodeTraversal.AbstractScopedCallback() {
      @Override
      public void visit(NodeTraversal t, Node n, Node parent) {
        events.add("visit:" + n.getType());
      }

      @Override
      public void enterScope(NodeTraversal t) {
        events.add("enterScope");
      }

      @Override
      public void exitScope(NodeTraversal t) {
        events.add("exitScope");
      }
    };

    NodeTraversal traversal = new NodeTraversal(compiler, cb);
    traversal.traverse(root);

    assertTrue(events.contains("enterScope"));
    assertTrue(events.contains("exitScope"));
  }

  public void testShallowCallback() throws Throwable {
    Node root = IR.script();
    Node fn = IR.function(IR.name("f"), IR.paramList(), IR.block(IR.returnNode(IR.number(1))));
    root.addChildToBack(fn);

    final List<Node> visited = new ArrayList<Node>();
    NodeTraversal.Callback cb = new NodeTraversal.AbstractShallowCallback() {
      @Override
      public void visit(NodeTraversal t, Node n, Node parent) {
        visited.add(n);
      }
    };

    NodeTraversal.traverse(compiler, root, cb);
    assertTrue(visited.size() > 0);
  }

  public void testShallowStatementCallback() throws Throwable {
    Node root = IR.script();
    Node block = IR.block(IR.exprResult(IR.number(1)));
    root.addChildToBack(block);

    final List<Node> visited = new ArrayList<Node>();
    NodeTraversal.Callback cb = new NodeTraversal.AbstractShallowStatementCallback() {
      @Override
      public void visit(NodeTraversal t, Node n, Node parent) {
        visited.add(n);
      }
    };

    NodeTraversal.traverse(compiler, root, cb);
    assertTrue(visited.size() > 0);
  }

  public void testNodeTypePruningCallback() throws Throwable {
    Set<Integer> types = new HashSet<Integer>();
    types.add(Token.NUMBER);

    NodeTraversal.Callback cbInclude = new NodeTraversal.AbstractNodeTypePruningCallback(types, true) {
      @Override
      public void visit(NodeTraversal t, Node n, Node parent) {}
    };

    NodeTraversal.Callback cbExclude = new NodeTraversal.AbstractNodeTypePruningCallback(types, false) {
      @Override
      public void visit(NodeTraversal t, Node n, Node parent) {}
    };

    Node root = IR.script();
    root.addChildToBack(IR.number(1));

    NodeTraversal.traverse(compiler, root, cbInclude);
    NodeTraversal.traverse(compiler, root, cbExclude);
  }

  public void testGetLineNumberAndSource() throws Throwable {
    Node root = IR.script();
    Node num = IR.number(1);
    num.setLineno(10);
    num.setCharno(5);
    root.addChildToBack(num);

    final List<Integer> lines = new ArrayList<Integer>();
    final List<String> sources = new ArrayList<String>();
    NodeTraversal.Callback cb = new NodeTraversal.AbstractPostOrderCallback() {
      @Override
      public void visit(NodeTraversal t, Node n, Node parent) {
        lines.add(t.getLineNumber());
        sources.add(t.getSourceName());
      }
    };

    NodeTraversal.traverse(compiler, root, cb);
    assertTrue(lines.size() > 0);
    assertNotNull(sources.get(0));
  }

  public void testMakeErrorAndReport() throws Throwable {
    Node root = IR.script();
    NodeTraversal traversal = new NodeTraversal(compiler, new NodeTraversal.AbstractPostOrderCallback() {
      @Override
      public void visit(NodeTraversal t, Node n, Node parent) {}
    });

    JSError err1 = traversal.makeError(root, NodeTraversal.NODE_TRAVERSAL_ERROR, "test");
    assertNotNull(err1);

    JSError err2 = traversal.makeError(root, CheckLevel.ERROR, NodeTraversal.NODE_TRAVERSAL_ERROR, "test");
    assertNotNull(err2);

    traversal.report(root, NodeTraversal.NODE_TRAVERSAL_ERROR, "test");
  }

  public void testTraverseRootsEmpty() throws Throwable {
    NodeTraversal traversal = new NodeTraversal(compiler, new NodeTraversal.AbstractPostOrderCallback() {
      @Override
      public void visit(NodeTraversal t, Node n, Node parent) {}
    });
    traversal.traverseRoots(new ArrayList<Node>());
  }

  public void testEnclosingFunctionAndScopeMethods() throws Throwable {
    Node root = IR.script();
    NodeTraversal traversal = new NodeTraversal(compiler, new NodeTraversal.AbstractPostOrderCallback() {
      @Override
      public void visit(NodeTraversal t, Node n, Node parent) {
        assertNotNull(t.getCurrentNode());
        assertFalse(t.hasScope());
      }
    });
    traversal.traverse(root);
  }
}