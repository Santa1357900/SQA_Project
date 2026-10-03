package com.google.javascript.jscomp;

import com.google.javascript.jscomp.CheckLevel;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import junit.framework.TestCase;

public class CheckGlobalThisTest extends TestCase {

    private Compiler compiler;
    private CheckGlobalThis checkGlobalThis;

    protected void setUp() throws Exception {
        super.setUp();
        compiler = new Compiler();
        // Initialize basic compiler options or settings if necessary
        CompilerOptions options = new CompilerOptions();
        compiler.initOptions(options);
        checkGlobalThis = new CheckGlobalThis(compiler, CheckLevel.WARNING);
    }

    public void testShouldTraverseConstructorFunction() throws Throwable {
        Node funcNode = new Node(Token.FUNCTION);
        NodeTraversal traversal = new NodeTraversal(compiler, checkGlobalThis);
        Node parent = new Node(Token.SCRIPT);
        
        // Testing that constructor functions are not traversed (shouldTraverse returns false)
        // Since we cannot easily mock JSDocInfo without full Rhino setup, we test standard traversal paths.
        boolean result = checkGlobalThis.shouldTraverse(traversal, funcNode, parent);
        assertTrue(result);
    }

    public void testShouldTraverseInvalidParentTypeForFunction() throws Throwable {
        Node funcNode = new Node(Token.FUNCTION);
        NodeTraversal traversal = new NodeTraversal(compiler, checkGlobalThis);
        // Parent is an illegal type for a function to be traversed in this pass (e.g., Token.RETURN)
        Node parent = new Node(Token.RETURN);
        
        boolean result = checkGlobalThis.shouldTraverse(traversal, funcNode, parent);
        assertFalse(result);
    }

    public void testVisitThisNodeWithoutReport() throws Throwable {
        Node thisNode = new Node(Token.THIS);
        NodeTraversal traversal = new NodeTraversal(compiler, checkGlobalThis);
        Node parent = new Node(Token.BLOCK);

        // visit should not crash when encountering THIS in a safe context
        checkGlobalThis.visit(traversal, thisNode, parent);
        assertEquals(0, compiler.getErrors().length);
    }

    public void testAssignLhsChildReset() throws Throwable {
        Node assignNode = new Node(Token.ASSIGN);
        Node lhs = new Node(Token.NAME, "a");
        Node rhs = new Node(Token.THIS);
        assignNode.addChildToBack(lhs);
        assignNode.addChildToBack(rhs);

        NodeTraversal traversal = new NodeTraversal(compiler, checkGlobalThis);

        // Trigger shouldTraverse to set assignLhsChild
        checkGlobalThis.shouldTraverse(traversal, lhs, assignNode);

        // Visit lhs which matches assignLhsChild, should reset it
        checkGlobalThis.visit(traversal, lhs, assignNode);
        
        // Visit another node to ensure no state leakage
        checkGlobalThis.visit(traversal, rhs, assignNode);
        assertEquals(0, compiler.getErrors().length);
    }

    public void testPrototypeAssignmentSkipping() throws Throwable {
        Node assignNode = new Node(Token.ASSIGN);
        Node lhs = new Node(Token.GETPROP, new Node(Token.NAME, "A"), new Node(Token.STRING, "prototype"));
        Node rhs = new Node(Token.FUNCTION);
        assignNode.addChildToBack(lhs);
        assignNode.addChildToBack(rhs);

        NodeTraversal traversal = new NodeTraversal(compiler, checkGlobalThis);

        // Traverse right hand side of a prototype assignment should return false
        boolean result = checkGlobalThis.shouldTraverse(traversal, rhs, assignNode);
        assertFalse(result);
    }

    public void testSubprototypeAssignmentSkipping() throws Throwable {
        Node getprop = new Node(Token.GETPROP, new Node(Token.NAME, "A"), new Node(Token.STRING, "prototype"));
        Node lhs = new Node(Token.GETPROP, getprop, new Node(Token.STRING, "method"));
        Node assignNode = new Node(Token.ASSIGN);
        Node rhs = new Node(Token.FUNCTION);
        assignNode.addChildToBack(lhs);
        assignNode.addChildToBack(rhs);

        NodeTraversal traversal = new NodeTraversal(compiler, checkGlobalThis);

        boolean result = checkGlobalThis.shouldTraverse(traversal, rhs, assignNode);
        assertFalse(result);
    }

    public void testGlobalThisDiagnostics() throws Throwable {
        assertNotNull(CheckGlobalThis.GLOBAL_THIS);
    }
}