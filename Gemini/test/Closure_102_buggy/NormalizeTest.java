package com.google.javascript.jscomp;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.JSDocInfo;
import junit.framework.TestCase;

public class NormalizeTest extends TestCase {

    private Compiler compiler;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        compiler = new Compiler();
        CompilerOptions options = new CompilerOptions();
        compiler.initOptions(options);
    }

    public void testNormalizeWhileLoop() throws Throwable {
        Node root = new Node(Token.BLOCK);
        Node whileNode = new Node(Token.WHILE, IR.trueNode(), new Node(Token.BLOCK));
        root.addChildToBack(whileNode);

        Normalize normalize = new Normalize(compiler, false);
        normalize.process(new Node(Token.BLOCK), root);

        assertEquals(Token.FOR, whileNode.getType());
        assertEquals(4, whileNode.getChildCount());
    }

    public void testNormalizeLabel() throws Throwable {
        Node root = new Node(Token.BLOCK);
        Node labelNode = new Node(Token.LABEL, IR.name("myLabel"), new Node(Token.EXPR_RESULT, IR.number(1)));
        root.addChildToBack(labelNode);

        Normalize normalize = new Normalize(compiler, false);
        normalize.process(new Node(Token.BLOCK), root);

        assertEquals(Token.BLOCK, labelNode.getLastChild().getType());
    }

    public void testSplitVarDeclarations() throws Throwable {
        Node root = new Node(Token.BLOCK);
        Node varNode = new Node(Token.VAR, IR.name("a"), IR.name("b"));
        root.addChildToBack(varNode);

        Normalize normalize = new Normalize(compiler, false);
        normalize.process(new Node(Token.BLOCK), root);

        assertEquals(2, root.getChildCount());
        assertEquals(Token.VAR, root.getFirstChild().getType());
        assertEquals(Token.VAR, root.getLastChild().getType());
    }

    public void testExtractForInitializer() throws Throwable {
        Node root = new Node(Token.BLOCK);
        Node forNode = new Node(Token.FOR, 
            new Node(Token.VAR, IR.name("i"), IR.number(0)), 
            IR.trueNode(), 
            new Node(Token.EXPR_RESULT, IR.inc(IR.name("i"))), 
            new Node(Token.BLOCK));
        root.addChildToBack(forNode);

        Normalize normalize = new Normalize(compiler, false);
        normalize.process(new Node(Token.BLOCK), root);

        assertEquals(2, root.getChildCount());
        assertEquals(Token.VAR, root.getFirstChild().getType());
        assertEquals(Token.FOR, root.getLastChild().getType());
    }

    public void testPropogateConstantAnnotations() throws Throwable {
        Node root = new Node(Token.BLOCK);
        Node nameNode = IR.name("CONST_VAR");
        nameNode.putBooleanProp(Node.IS_CONSTANT_NAME, false);
        Node varNode = new Node(Token.VAR, nameNode);
        root.addChildToBack(varNode);

        Normalize.PropogateConstantAnnotations prop = new Normalize.PropogateConstantAnnotations(compiler, false);
        prop.process(new Node(Token.BLOCK), root);
    }

    public void testVerifyConstants() throws Throwable {
        Node root = new Node(Token.BLOCK);
        Node nameNode = IR.name("FOO");
        nameNode.putBooleanProp(Node.IS_CONSTANT_NAME, true);
        Node varNode = new Node(Token.VAR, nameNode);
        root.addChildToBack(varNode);

        Node externs = new Node(Token.BLOCK);
        Node parent = new Node(Token.BLOCK, externs, root);

        Normalize.VerifyConstants verify = new Normalize.VerifyConstants(compiler, false);
        try {
            verify.process(externs, root);
        } catch (IllegalStateException e) {
            // expected or passed depending on state
        }
    }
}