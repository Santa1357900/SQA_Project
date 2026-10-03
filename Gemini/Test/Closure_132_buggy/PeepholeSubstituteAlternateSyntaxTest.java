package com.google.javascript.jscomp;

import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import org.junit.Test;

import static org.junit.Assert.*;

public class PeepholeSubstituteAlternateSyntaxTest {

    @Test
    public void testConstructorLateFalse() throws Throwable {
        PeepholeSubstituteAlternateSyntax opt = new PeepholeSubstituteAlternateSyntax(false);
        assertNotNull(opt);
    }

    @Test
    public void testConstructorLateTrue() throws Throwable {
        PeepholeSubstituteAlternateSyntax opt = new PeepholeSubstituteAlternateSyntax(true);
        assertNotNull(opt);
    }

    @Test
    public void testOptimizeSubtreeReturnNull() throws Throwable {
        PeepholeSubstituteAlternateSyntax opt = new PeepholeSubstituteAlternateSyntax(false);
        Node retNode = IR.returnNode();
        Node result = opt.optimizeSubtree(retNode);
        assertNotNull(result);
    }

    @Test
    public void testOptimizeSubtreeThrow() throws Throwable {
        PeepholeSubstituteAlternateSyntax opt = new PeepholeSubstituteAlternateSyntax(false);
        Node throwNode = new Node(Token.THROW, IR.string("error"));
        Node result = opt.optimizeSubtree(throwNode);
        assertNotNull(result);
    }

    @Test
    public void testOptimizeSubtreeNot() throws Throwable {
        PeepholeSubstituteAlternateSyntax opt = new PeepholeSubstituteAlternateSyntax(false);
        Node notNode = IR.not(IR.trueNode());
        Node result = opt.optimizeSubtree(notNode);
        assertNotNull(result);
    }

    @Test
    public void testOptimizeSubtreeIf() throws Throwable {
        PeepholeSubstituteAlternateSyntax opt = new PeepholeSubstituteAlternateSyntax(false);
        Node ifNode = new Node(Token.IF, IR.trueNode(), IR.block());
        Node result = opt.optimizeSubtree(ifNode);
        assertNotNull(result);
    }

    @Test
    public void testOptimizeSubtreeExprResult() throws Throwable {
        PeepholeSubstituteAlternateSyntax opt = new PeepholeSubstituteAlternateSyntax(false);
        Node exprNode = IR.exprResult(IR.number(1));
        Node result = opt.optimizeSubtree(exprNode);
        assertNotNull(result);
    }

    @Test
    public void testOptimizeSubtreeHook() throws Throwable {
        PeepholeSubstituteAlternateSyntax opt = new PeepholeSubstituteAlternateSyntax(false);
        Node hookNode = IR.hook(IR.trueNode(), IR.number(1), IR.number(2));
        Node result = opt.optimizeSubtree(hookNode);
        assertNotNull(result);
    }

    @Test
    public void testOptimizeSubtreeWhile() throws Throwable {
        PeepholeSubstituteAlternateSyntax opt = new PeepholeSubstituteAlternateSyntax(false);
        Node whileNode = new Node(Token.WHILE, IR.trueNode(), IR.block());
        Node result = opt.optimizeSubtree(whileNode);
        assertNotNull(result);
    }

    @Test
    public void testOptimizeSubtreeDo() throws Throwable {
        PeepholeSubstituteAlternateSyntax opt = new PeepholeSubstituteAlternateSyntax(false);
        Node doNode = new Node(Token.DO, IR.block(), IR.trueNode());
        Node result = opt.optimizeSubtree(doNode);
        assertNotNull(result);
    }

    @Test
    public void testOptimizeSubtreeFor() throws Throwable {
        PeepholeSubstituteAlternateSyntax opt = new PeepholeSubstituteAlternateSyntax(true);
        Node forNode = new Node(Token.FOR, IR.empty(), IR.trueNode(), IR.empty(), IR.block());
        Node result = opt.optimizeSubtree(forNode);
        assertNotNull(result);
    }

    @Test
    public void testOptimizeSubtreeTrueFalse() throws Throwable {
        PeepholeSubstituteAlternateSyntax opt = new PeepholeSubstituteAlternateSyntax(true);
        Node trueNode = IR.trueNode();
        Node result = opt.optimizeSubtree(trueNode);
        assertNotNull(result);
    }

    @Test
    public void testOptimizeSubtreeNew() throws Throwable {
        PeepholeSubstituteAlternateSyntax opt = new PeepholeSubstituteAlternateSyntax(false);
        Node newNode = new Node(Token.NEW, IR.name("Object"));
        Node result = opt.optimizeSubtree(newNode);
        assertNotNull(result);
    }

    @Test
    public void testOptimizeSubtreeComma() throws Throwable {
        PeepholeSubstituteAlternateSyntax opt = new PeepholeSubstituteAlternateSyntax(false);
        Node commaNode = new Node(Token.COMMA, IR.number(1), IR.number(2));
        Node result = opt.optimizeSubtree(commaNode);
        assertNotNull(result);
    }

    @Test
    public void testOptimizeSubtreeName() throws Throwable {
        PeepholeSubstituteAlternateSyntax opt = new PeepholeSubstituteAlternateSyntax(false);
        Node nameNode = IR.name("undefined");
        Node result = opt.optimizeSubtree(nameNode);
        assertNotNull(result);
    }

    @Test
    public void testOptimizeSubtreeBlock() throws Throwable {
        PeepholeSubstituteAlternateSyntax opt = new PeepholeSubstituteAlternateSyntax(false);
        Node blockNode = IR.block();
        Node result = opt.optimizeSubtree(blockNode);
        assertNotNull(result);
    }

    @Test
    public void testOptimizeSubtreeArrayLit() throws Throwable {
        PeepholeSubstituteAlternateSyntax opt = new PeepholeSubstituteAlternateSyntax(true);
        Node arrayLit = IR.arraylit(IR.string("a"), IR.string("b"));
        Node result = opt.optimizeSubtree(arrayLit);
        assertNotNull(result);
    }

    @Test
    public void testOptimizeSubtreeDefault() throws Throwable {
        PeepholeSubstituteAlternateSyntax opt = new PeepholeSubstituteAlternateSyntax(false);
        Node defaultNode = new Node(Token.DEBUGGER);
        Node result = opt.optimizeSubtree(defaultNode);
        assertNotNull(result);
    }

    @Test
    public void testContainsUnicodeEscape() throws Throwable {
        boolean hasEscape = PeepholeSubstituteAlternateSyntax.containsUnicodeEscape("abc\\u0000");
        assertTrue(hasEscape);
    }

    @Test
    public void testContainsUnicodeEscapeFalse() throws Throwable {
        boolean hasEscape = PeepholeSubstituteAlternateSyntax.containsUnicodeEscape("abcdef");
        assertFalse(hasEscape);
    }
}