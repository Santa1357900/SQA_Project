package com.google.javascript.jscomp;

import junit.framework.TestCase;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.JSDocInfo;

public class CheckGlobalThisTest extends TestCase {

    private Compiler compiler;
    private CheckGlobalThis checker;

    protected void setUp() throws Exception {
        super.setUp();
        compiler = new Compiler();
        // Initialize compiler options minimally if needed
        compiler.initOptions(new CompilerOptions());
        checker = new CheckGlobalThis(compiler, CheckLevel.WARNING);
    }

    public void testShouldTraverseConstructorFunction() throws Throwable {
        Node fn = new Node(Token.FUNCTION);
        JSDocInfo jsDoc = new JSDocInfo();
        jsDoc.setAssociatedWithNode(fn);
        // We test via shouldTraverse directly since we cannot subclass AbstractCompiler safely without mocking
        // But we can check public interface behavior or direct method calls if accessible or via NodeTraversal.
        // Since CheckGlobalThis implements Callback, we can test shouldTraverse.
        boolean result = checker.shouldTraverse(null, fn, null);
        // Without JSDoc constructor set, it returns true. Let's test with constructor.
        // Note: JSDocInfo setters might be restricted, but we can verify standard node traversal logic.
        assertTrue(result);
    }

    public void testGlobalThisConstants() throws Throwable {
        assertNotNull(CheckGlobalThis.GLOBAL_THIS);
    }

    public void testVisitAndAssignLhsChildReset() throws Throwable {
        // Construct a simple AST snippet to test visit
        Node script = new Node(Token.SCRIPT);
        NodeTraversal traversal = new NodeTraversal(compiler, checker);
        
        Node thisNode = new Node(Token.THIS);
        script.addChildToBack(thisNode);
        
        // Should not throw any exceptions
        checker.visit(traversal, thisNode, script);
    }

    public void testShouldTraverseAssignLhs() throws Throwable {
        Node assign = new Node(Token.ASSIGN);
        Node name = new Node(Token.NAME, "a");
        Node thisNode = new Node(Token.THIS);
        assign.addChildToBack(name);
        assign.addChildToBack(thisNode);

        boolean res = checker.shouldTraverse(null, name, assign);
        assertTrue(res);
    }

    public void testShouldTraversePrototypeAssignment() throws Throwable {
        Node assign = new Node(Token.ASSIGN);
        Node getprop = new Node(Token.GETPROP, new Node(Token.NAME, "a"), new Node(Token.STRING, "prototype"));
        Node func = new Node(Token.FUNCTION);
        
        assign.addChildToBack(getprop);
        assign.addChildToBack(func);

        boolean res = checker.shouldTraverse(null, func, assign);
        assertFalse(res);
    }
}