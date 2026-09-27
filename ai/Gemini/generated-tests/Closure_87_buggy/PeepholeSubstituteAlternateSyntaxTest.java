package com.google.javascript.jscomp;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import junit.framework.TestCase;

public class PeepholeSubstituteAlternateSyntaxTest extends TestCase {

    private PeepholeSubstituteAlternateSyntax optimization;
    private AbstractCompiler compiler;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        optimization = new PeepholeSubstituteAlternateSyntax();
        compiler = new Compiler();
        optimization.beginTraversal(null);
    }

    public void testOptimizeSubtreeReturn() throws Throwable {
        Node returnNode = new Node(Token.RETURN);
        Node script = new Node(Token.SCRIPT, returnNode);
        Node result = optimization.optimizeSubtree(returnNode);
        assertNotNull(result);
    }

    public void testOptimizeSubtreeNot() throws Throwable {
        Node eqNode = new Node(Token.EQ, IR.name("a"), IR.name("b"));
        Node notNode = new Node(Token.NOT, eqNode);
        Node script = new Node(Token.SCRIPT, notNode);
        Node result = optimization.optimizeSubtree(notNode);
        assertNotNull(result);
    }

    public void testOptimizeSubtreeIf() throws Throwable {
        Node cond = IR.name("x");
        Node thenBranch = new Node(Token.BLOCK, new Node(Token.EXPR_RESULT, IR.name("foo")));
        Node ifNode = new Node(Token.IF, cond, thenBranch);
        Node script = new Node(Token.SCRIPT, ifNode);
        Node result = optimization.optimizeSubtree(ifNode);
        assertNotNull(result);
    }

    public void testOptimizeSubtreeExprResult() throws Throwable {
        Node expr = new Node(Token.EXPR_RESULT, IR.name("a"));
        Node result = optimization.optimizeSubtree(expr);
        assertEquals(expr, result);
    }

    public void testOptimizeSubtreeHook() throws Throwable {
        Node hook = new Node(Token.HOOK, IR.name("x"), IR.name("y"), IR.name("z"));
        Node result = optimization.optimizeSubtree(hook);
        assertEquals(hook, result);
    }

    public void testOptimizeSubtreeWhile() throws Throwable {
        Node cond = IR.name("x");
        Node body = new Node(Token.BLOCK);
        Node whileNode = new Node(Token.WHILE, cond, body);
        Node result = optimization.optimizeSubtree(whileNode);
        assertEquals(whileNode, result);
    }

    public void testOptimizeSubtreeDo() throws Throwable {
        Node body = new Node(Token.BLOCK);
        Node cond = IR.name("x");
        Node doNode = new Node(Token.DO, body, cond);
        Node result = optimization.optimizeSubtree(doNode);
        assertEquals(doNode, result);
    }

    public void testOptimizeSubtreeFor() throws Throwable {
        Node init = new Node(Token.EXPR_RESULT, IR.name("i"));
        Node cond = IR.name("x");
        Node incr = new Node(Token.EXPR_RESULT, IR.name("i"));
        Node body = new Node(Token.BLOCK);
        Node forNode = new Node(Token.FOR, init, cond, incr, body);
        Node result = optimization.optimizeSubtree(forNode);
        assertEquals(forNode, result);
    }

    public void testOptimizeSubtreeNew() throws Throwable {
        Node newObj = new Node(Token.NEW, IR.name("Object"));
        Node result = optimization.optimizeSubtree(newObj);
        assertNotNull(result);
    }

    public void testOptimizeSubtreeCall() throws Throwable {
        Node callObj = new Node(Token.CALL, IR.name("Array"), IR.number(0));
        Node script = new Node(Token.SCRIPT, callObj);
        compiler.setNormalized();
        Node result = optimization.optimizeSubtree(callObj);
        assertNotNull(result);
    }

    public void testOptimizeSubtreeDefault() throws Throwable {
        Node literal = IR.number(42);
        Node result = optimization.optimizeSubtree(literal);
        assertEquals(literal, result);
    }

    public void testContainsUnicodeEscape() throws Throwable {
        boolean hasEscape = PeepholeSubstituteAlternateSyntax.containsUnicodeEscape("\\u0041");
        assertTrue(hasEscape);

        boolean noEscape = PeepholeSubstituteAlternateSyntax.containsUnicodeEscape("A");
        assertFalse(noEscape);
    }

    public void testTryMinimizeNotVariants() throws Throwable {
        Node neNode = new Node(Token.NE, IR.name("a"), IR.name("b"));
        Node notNode = new Node(Token.NOT, neNode);
        Node script = new Node(Token.SCRIPT, notNode);
        Node result = optimization.optimizeSubtree(notNode);
        assertNotNull(result);

        Node sheqNode = new Node(Token.SHEQ, IR.name("a"), IR.name("b"));
        Node notNode2 = new Node(Token.NOT, sheqNode);
        Node script2 = new Node(Token.SCRIPT, notNode2);
        Node result2 = optimization.optimizeSubtree(notNode2);
        assertNotNull(result2);

        Node shneNode = new Node(Token.SHNE, IR.name("a"), IR.name("b"));
        Node notNode3 = new Node(Token.NOT, shneNode);
        Node script3 = new Node(Token.SCRIPT, notNode3);
        Node result3 = optimization.optimizeSubtree(notNode3);
        assertNotNull(result3);
    }

    public void testTryFoldRegularExpressionConstructor() throws Throwable {
        Node pattern = Node.newString("abc");
        Node flags = Node.newString("i");
        Node regExpCall = new Node(Token.CALL, IR.name("RegExp"), pattern, flags);
        Node script = new Node(Token.SCRIPT, regExpCall);
        compiler.setNormalized();
        Node result = optimization.optimizeSubtree(regExpCall);
        assertNotNull(result);
    }

    public void testTryFoldRegularExpressionConstructorEmptyFlags() throws Throwable {
        Node pattern = Node.newString("abc");
        Node flags = Node.newString("");
        Node regExpCall = new Node(Token.CALL, IR.name("RegExp"), pattern, flags);
        Node script = new Node(Token.SCRIPT, regExpCall);
        compiler.setNormalized();
        Node result = optimization.optimizeSubtree(regExpCall);
        assertNotNull(result);
    }

    public void testTryFoldRegularExpressionConstructorInvalidFlags() throws Throwable {
        Node pattern = Node.newString("abc");
        Node flags = Node.newString("z");
        Node regExpCall = new Node(Token.CALL, IR.name("RegExp"), pattern, flags);
        Node script = new Node(Token.SCRIPT, regExpCall);
        compiler.setNormalized();
        Node result = optimization.optimizeSubtree(regExpCall);
        assertNotNull(result);
    }

    public void testTryFoldRegularExpressionConstructorUnsafeFlags() throws Throwable {
        Node pattern = Node.newString("abc");
        Node flags = Node.newString("g");
        Node regExpCall = new Node(Token.CALL, IR.name("RegExp"), pattern, flags);
        Node script = new Node(Token.SCRIPT, regExpCall);
        compiler.setNormalized();
        Node result = optimization.optimizeSubtree(regExpCall);
        assertNotNull(result);
    }

    public void testTryFoldLiteralConstructorArrayWithArgs() throws Throwable {
        Node arrayCall = new Node(Token.CALL, IR.name("Array"), IR.string("item"));
        Node script = new Node(Token.SCRIPT, arrayCall);
        compiler.setNormalized();
        Node result = optimization.optimizeSubtree(arrayCall);
        assertNotNull(result);
    }

    public void testTryFoldLiteralConstructorArrayWithArrayLitArg() throws Throwable {
        Node innerArray = new Node(Token.ARRAYLIT);
        Node arrayCall = new Node(Token.CALL, IR.name("Array"), innerArray);
        Node script = new Node(Token.SCRIPT, arrayCall);
        compiler.setNormalized();
        Node result = optimization.optimizeSubtree(arrayCall);
        assertNotNull(result);
    }

    public void testTryFoldLiteralConstructorArrayWithStringArg() throws Throwable {
        Node arrayCall = new Node(Token.CALL, IR.name("Array"), Node.newString("test"));
        Node script = new Node(Token.SCRIPT, arrayCall);
        compiler.setNormalized();
        Node result = optimization.optimizeSubtree(arrayCall);
        assertNotNull(result);
    }

    public void testTryMinimizeConditionNotNot() throws Throwable {
        Node innerNot = new Node(Token.NOT, IR.name("x"));
        Node outerNot = new Node(Token.NOT, innerNot);
        Node exprResult = new Node(Token.EXPR_RESULT, outerNot);
        Node script = new Node(Token.SCRIPT, exprResult);
        Node result = optimization.optimizeSubtree(exprResult);
        assertNotNull(result);
    }

    public void testTryMinimizeConditionAndOr() throws Throwable {
        Node leftNot = new Node(Token.NOT, IR.name("x"));
        Node rightNot = new Node(Token.NOT, IR.name("y"));
        Node andNode = new Node(Token.AND, leftNot, rightNot);
        Node notNode = new Node(Token.NOT, andNode);
        Node exprResult = new Node(Token.EXPR_RESULT, notNode);
        Node script = new Node(Token.SCRIPT, exprResult);
        Node result = optimization.optimizeSubtree(exprResult);
        assertNotNull(result);
    }

    public void testTryMinimizeConditionBooleanLogic() throws Throwable {
        Node andNode = new Node(Token.AND, IR.name("x"), IR.trueNode());
        Node exprResult = new Node(Token.EXPR_RESULT, andNode);
        Node script = new Node(Token.SCRIPT, exprResult);
        Node result = optimization.optimizeSubtree(exprResult);
        assertNotNull(result);

        Node orNode = new Node(Token.OR, IR.name("x"), IR.falseNode());
        Node exprResult2 = new Node(Token.EXPR_RESULT, orNode);
        Node script2 = new Node(Token.SCRIPT, exprResult2);
        Node result2 = optimization.optimizeSubtree(exprResult2);
        assertNotNull(result2);
    }

    public void testTryMinimizeConditionHookLogic() throws Throwable {
        Node hookTrueFalse = new Node(Token.HOOK, IR.name("x"), IR.trueNode(), IR.falseNode());
        Node exprResult1 = new Node(Token.EXPR_RESULT, hookTrueFalse);
        Node script1 = new Node(Token.SCRIPT, exprResult1);
        Node result1 = optimization.optimizeSubtree(exprResult1);
        assertNotNull(result1);

        Node hookFalseTrue = new Node(Token.HOOK, IR.name("x"), IR.falseNode(), IR.trueNode());
        Node exprResult2 = new Node(Token.EXPR_RESULT, hookFalseTrue);
        Node script2 = new Node(Token.SCRIPT, exprResult2);
        Node result2 = optimization.optimizeSubtree(exprResult2);
        assertNotNull(result2);

        Node hookTrueY = new Node(Token.HOOK, IR.name("x"), IR.trueNode(), IR.name("y"));
        Node exprResult3 = new Node(Token.EXPR_RESULT, hookTrueY);
        Node script3 = new Node(Token.SCRIPT, exprResult3);
        Node result3 = optimization.optimizeSubtree(exprResult3);
        assertNotNull(result3);

        Node hookYFalse = new Node(Token.HOOK, IR.name("x"), IR.name("y"), IR.falseNode());
        Node exprResult4 = new Node(Token.EXPR_RESULT, hookYFalse);
        Node script4 = new Node(Token.SCRIPT, exprResult4);
        Node result4 = optimization.optimizeSubtree(exprResult4);
        assertNotNull(result4);
    }

    public void testTryMinimizeIfWithNot() throws Throwable {
        Node cond = new Node(Token.NOT, IR.name("x"));
        Node thenBranch = new Node(Token.BLOCK, new Node(Token.EXPR_RESULT, IR.name("bar")));
        Node ifNode = new Node(Token.IF, cond, thenBranch);
        Node script = new Node(Token.SCRIPT, ifNode);
        Node result = optimization.optimizeSubtree(ifNode);
        assertNotNull(result);
    }

    public void testTryMinimizeIfReturnBlocks() throws Throwable {
        Node cond = IR.name("x");
        Node thenBranch = new Node(Token.BLOCK, new Node(Token.RETURN, IR.number(1)));
        Node elseBranch = new Node(Token.BLOCK, new Node(Token.RETURN, IR.number(2)));
        Node ifNode = new Node(Token.IF, cond, thenBranch, elseBranch);
        Node script = new Node(Token.SCRIPT, ifNode);
        Node result = optimization.optimizeSubtree(ifNode);
        assertNotNull(result);
    }

    public void testTryMinimizeIfAssignmentBlocks() throws Throwable {
        Node cond = IR.name("x");
        Node thenAssign = new Node(Token.ASSIGN, IR.name("a"), IR.number(1));
        Node elseAssign = new Node(Token.ASSIGN, IR.name("a"), IR.number(2));
        Node thenBranch = new Node(Token.BLOCK, new Node(Token.EXPR_RESULT, thenAssign));
        Node elseBranch = new Node(Token.BLOCK, new Node(Token.EXPR_RESULT, elseAssign));
        Node ifNode = new Node(Token.IF, cond, thenBranch, elseBranch);
        Node script = new Node(Token.SCRIPT, ifNode);
        Node result = optimization.optimizeSubtree(ifNode);
        assertNotNull(result);
    }

    public void testTryMinimizeIfCallBlocks() throws Throwable {
        Node cond = IR.name("x");
        Node thenCall = new Node(Token.CALL, IR.name("foo"));
        Node elseCall = new Node(Token.CALL, IR.name("bar"));
        Node thenBranch = new Node(Token.BLOCK, new Node(Token.EXPR_RESULT, thenCall));
        Node elseBranch = new Node(Token.BLOCK, new Node(Token.EXPR_RESULT, elseCall));
        Node ifNode = new Node(Token.IF, cond, thenBranch, elseBranch);
        Node script = new Node(Token.SCRIPT, ifNode);
        Node result = optimization.optimizeSubtree(ifNode);
        assertNotNull(result);
    }

    public void testTryMinimizeIfVarBlocks() throws Throwable {
        Node cond = IR.name("x");
        Node varNode = new Node(Token.VAR, IR.name("y"));
        varNode.getFirstChild().addChildToBack(IR.number(1));
        Node elseAssign = new Node(Token.ASSIGN, IR.name("y"), IR.number(2));
        Node thenBranch = new Node(Token.BLOCK, varNode);
        Node elseBranch = new Node(Token.BLOCK, new Node(Token.EXPR_RESULT, elseAssign));
        Node ifNode = new Node(Token.IF, cond, thenBranch, elseBranch);
        Node script = new Node(Token.SCRIPT, ifNode);
        Node result = optimization.optimizeSubtree(ifNode);
        assertNotNull(result);
    }

    public void testTryMinimizeIfVarBlocksInverse() throws Throwable {
        Node cond = IR.name("x");
        Node thenAssign = new Node(Token.ASSIGN, IR.name("y"), IR.number(1));
        Node varNode = new Node(Token.VAR, IR.name("y"));
        varNode.getFirstChild().addChildToBack(IR.number(2));
        Node thenBranch = new Node(Token.BLOCK, new Node(Token.EXPR_RESULT, thenAssign));
        Node elseBranch = new Node(Token.BLOCK, varNode);
        Node ifNode = new Node(Token.IF, cond, thenBranch, elseBranch);
        Node script = new Node(Token.SCRIPT, ifNode);
        Node result = optimization.optimizeSubtree(ifNode);
        assertNotNull(result);
    }

    public void testTryReduceReturnUndefined() throws Throwable {
        Node returnNode = new Node(Token.RETURN, IR.name("undefined"));
        Node script = new Node(Token.SCRIPT, returnNode);
        Node result = optimization.optimizeSubtree(returnNode);
        assertNotNull(result);
    }

    public void testTryReduceReturnVoid() throws Throwable {
        Node voidNode = new Node(Token.VOID, IR.number(0));
        Node returnNode = new Node(Token.RETURN, voidNode);
        Node script = new Node(Token.SCRIPT, returnNode);
        Node result = optimization.optimizeSubtree(returnNode);
        assertNotNull(result);
    }
}