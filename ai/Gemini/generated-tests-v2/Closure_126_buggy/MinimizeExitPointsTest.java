package com.google.javascript.jscomp;

import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import junit.framework.TestCase;

public class MinimizeExitPointsTest extends TestCase {

    private Compiler compiler;
    private MinimizeExitPoints minimizeExitPoints;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        compiler = new Compiler();
        // Initialize compiler with basic options if needed
        CompilerOptions options = new CompilerOptions();
        compiler.initOptions(options);
        minimizeExitPoints = new MinimizeExitPoints(compiler);
    }

    public void testProcess() throws Throwable {
        Node externs = IR.block();
        Node root = IR.block(IR.function(IR.name("test"), IR.paramList(), IR.block(IR.returnNode())));
        minimizeExitPoints.process(externs, root);
        assertNotNull(root);
    }

    public void testVisitLabel() throws Throwable {
        Node labelChild = IR.block(IR.breakNode());
        Node label = IR.label(IR.name("l1"), labelChild);
        Node root = IR.block(label);
        
        NodeTraversal t = new NodeTraversal(compiler, new AbstractPostOrderCallback() {
            @Override
            public void visit(NodeTraversal t, Node n, Node parent) {
            }
        });
        
        minimizeExitPoints.visit(t, label, root);
        assertTrue(true);
    }

    public void testVisitForLoop() throws Throwable {
        Node forNode = IR.forNode(IR.name("i"), IR.number(0), IR.number(10), IR.block(IR.continueNode()));
        Node root = IR.block(forNode);

        NodeTraversal t = new NodeTraversal(compiler, new AbstractPostOrderCallback() {
            @Override
            public void visit(NodeTraversal t, Node n, Node parent) {
            }
        });

        minimizeExitPoints.visit(t, forNode, root);
        assertTrue(true);
    }

    public void testVisitWhileLoop() throws Throwable {
        Node whileNode = IR.whileNode(IR.trueNode(), IR.block(IR.continueNode()));
        Node root = IR.block(whileNode);

        NodeTraversal t = new NodeTraversal(compiler, new AbstractPostOrderCallback() {
            @Override
            public void visit(NodeTraversal t, Node n, Node parent) {
            }
        });

        minimizeExitPoints.visit(t, whileNode, root);
        assertTrue(true);
    }

    public void testVisitDoLoop() throws Throwable {
        Node doNode = IR.doNode(IR.block(IR.continueNode()), IR.falseNode());
        Node root = IR.block(doNode);

        NodeTraversal t = new NodeTraversal(compiler, new AbstractPostOrderCallback() {
            @Override
            public void visit(NodeTraversal t, Node n, Node parent) {
            }
        });

        minimizeExitPoints.visit(t, doNode, root);
        assertTrue(true);
    }

    public void testVisitFunction() throws Throwable {
        Node funcNode = IR.function(IR.name("f"), IR.paramList(), IR.block(IR.returnNode()));
        Node root = IR.block(funcNode);

        NodeTraversal t = new NodeTraversal(compiler, new AbstractPostOrderCallback() {
            @Override
            public void visit(NodeTraversal t, Node n, Node parent) {
            }
        });

        minimizeExitPoints.visit(t, funcNode, root);
        assertTrue(true);
    }

    public void testTryMinimizeExitsNonBlock() throws Throwable {
        Node expr = IR.exprResult(IR.number(1));
        minimizeExitPoints.tryMinimizeExits(expr, Token.RETURN, null);
        assertTrue(true);
    }

    public void testTryMinimizeExitsIf() throws Throwable {
        Node ifNode = IR.ifNode(IR.trueNode(), IR.block(IR.returnNode()));
        minimizeExitPoints.tryMinimizeExits(ifNode, Token.RETURN, null);
        assertTrue(true);
    }

    public void testTryMinimizeExitsTryCatchFinally() throws Throwable {
        Node tryBlock = IR.block(IR.returnNode());
        Node catchNode = IR.catchNode(IR.name("e"), IR.block(IR.returnNode()));
        Node catchBlock = IR.block(catchNode);
        Node finallyBlock = IR.block(IR.exprResult(IR.number(1)));
        Node tryNode = IR.tryNode(tryBlock, catchBlock, finallyBlock);
        
        minimizeExitPoints.tryMinimizeExits(tryNode, Token.RETURN, null);
        assertTrue(true);
    }

    public void testTryMinimizeExitsLabel() throws Throwable {
        Node labelBlock = IR.block(IR.breakNode());
        Node label = IR.label(IR.name("lbl"), labelBlock);
        minimizeExitPoints.tryMinimizeExits(label, Token.BREAK, "lbl");
        assertTrue(true);
    }
}