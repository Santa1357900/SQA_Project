package com.google.javascript.jscomp;

import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import org.junit.Test;
import static org.junit.Assert.*;

public class PeepholeSubstituteAlternateSyntaxTest {

    @Test
    public void testEarlyLateCreation() throws Throwable {
        PeepholeSubstituteAlternateSyntax optEarly = new PeepholeSubstituteAlternateSyntax(false);
        PeepholeSubstituteAlternateSyntax optLate = new PeepholeSubstituteAlternateSyntax(true);
        assertNotNull(optEarly);
        assertNotNull(optLate);
    }

    @Test
    public void testOptimizeReturnRedundant() throws Throwable {
        PeepholeSubstituteAlternateSyntax opt = new PeepholeSubstituteAlternateSyntax(false);
        Node nameNode = IR.name("undefined");
        Node returnNode = IR.returnNode(nameNode);
        Node block = IR.block(returnNode);
        Node script = IR.script(block);
        
        Node result = opt.optimizeSubtree(returnNode);
        assertNotNull(result);
    }

    @Test
    public void testOptimizeTrueFalse() throws Throwable {
        PeepholeSubstituteAlternateSyntax optLate = new PeepholeSubstituteAlternateSyntax(true);
        Node trueNode = IR.trueNode();
        Node expr = IR.exprResult(trueNode);
        
        Node result = optLate.optimizeSubtree(trueNode);
        assertNotNull(result);
    }

    @Test
    public void testOptimizeNot() throws Throwable {
        PeepholeSubstituteAlternateSyntax opt = new PeepholeSubstituteAlternateSyntax(false);
        Node name1 = IR.name("a");
        Node name2 = IR.name("b");
        Node eq = new Node(Token.EQ, name1, name2);
        Node notNode = IR.not(eq);
        Node expr = IR.exprResult(notNode);

        Node result = opt.optimizeSubtree(notNode);
        assertNotNull(result);
    }

    @Test
    public void testOptimizeNewObject() throws Throwable {
        PeepholeSubstituteAlternateSyntax opt = new PeepholeSubstituteAlternateSyntax(false);
        Node newObj = new Node(Token.NEW, IR.name("Object"));
        Node expr = IR.exprResult(newObj);

        Node result = opt.optimizeSubtree(newObj);
        assertNotNull(result);
    }

    @Test
    public void testOptimizeArrayLit() throws Throwable {
        PeepholeSubstituteAlternateSyntax opt = new PeepholeSubstituteAlternateSyntax(true);
        Node str1 = IR.string("a");
        Node str2 = IR.string("b");
        Node arrLit = IR.arraylit(str1, str2);
        Node expr = IR.exprResult(arrLit);

        Node result = opt.optimizeSubtree(arrLit);
        assertNotNull(result);
    }

    @Test
    public void testContainsUnicodeEscape() throws Throwable {
        boolean hasUnicode = PeepholeSubstituteAlternateSyntax.containsUnicodeEscape("abc");
        assertFalse(hasUnicode);
    }
}