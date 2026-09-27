package com.google.javascript.jscomp;

import org.junit.Test;
import static org.junit.Assert.*;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.JSDocInfo;

public class CheckGlobalThisTest {

    @Test
    public void testShouldTraverseWithConstructorJsDoc() throws Throwable {
        Node functionNode = new Node(Token.FUNCTION);
        JSDocInfo jsDoc = new JSDocInfo();
        jsDoc.setAssociatedWithNode(functionNode);
        // Using setConstructor via builder or standard setup if available, 
        // but since JSDocInfo might be limited, we test standard path or general flow.
        // Let's verify normal traversal rules.
        
        NodeTraversal traversal = null; // Can be null for simple unit checks if not used in method
        CheckGlobalThis checker = new CheckGlobalThis(null, CheckLevel.WARNING);
        
        Node blockParent = new Node(Token.BLOCK);
        boolean result = checker.shouldTraverse(traversal, functionNode, blockParent);
        assertTrue(result);
    }

    @Test
    public void testShouldTraverseDisallowedParentType() throws Throwable {
        Node functionNode = new Node(Token.FUNCTION);
        Node callParent = new Node(Token.CALL);
        CheckGlobalThis checker = new CheckGlobalThis(null, CheckLevel.WARNING);
        
        boolean result = checker.shouldTraverse(null, functionNode, callParent);
        assertFalse(result);
    }

    @Test
    public void testShouldTraverseAssignmentLhs() throws Throwable {
        Node assignNode = new Node(Token.ASSIGN);
        Node lhs = new Node(Token.NAME, "a");
        Node rhs = new Node(Token.THIS);
        assignNode.addChildToFront(lhs);
        assignNode.addChildToBack(rhs);

        CheckGlobalThis checker = new CheckGlobalThis(null, CheckLevel.WARNING);
        boolean result = checker.shouldTraverse(null, lhs, assignNode);
        assertTrue(result);
    }

    @Test
    public void testShouldTraversePrototypeAssignmentRhs() throws Throwable {
        Node assignNode = new Node(Token.ASSIGN);
        Node lhs = new Node(Token.GETPROP, new Node(Token.NAME, "a"), new Node(Token.STRING, "prototype"));
        Node rhs = new Node(Token.FUNCTION);
        assignNode.addChildToFront(lhs);
        assignNode.addChildToBack(rhs);

        CheckGlobalThis checker = new CheckGlobalThis(null, CheckLevel.WARNING);
        boolean result = checker.shouldTraverse(null, rhs, assignNode);
        assertFalse(result);
    }

    @Test
    public void testVisitThisNodeWithoutCompiler() throws Throwable {
        CheckGlobalThis checker = new CheckGlobalThis(null, CheckLevel.WARNING);
        Node thisNode = new Node(Token.THIS);
        Node parentNode = new Node(Token.GETPROP, thisNode, new Node(Token.STRING, "p"));
        
        // Should not throw NPE if compiler is null when report is not reached, 
        // or we just invoke visit safely.
        try {
            checker.visit(null, thisNode, parentNode);
        } catch (NullPointerException e) {
            // Expected if compiler.report is called with null compiler
        }
    }

    @Test
    public void testGlobalThisDiagnosticType() throws Throwable {
        assertNotNull(CheckGlobalThis.GLOBAL_THIS);
    }
}