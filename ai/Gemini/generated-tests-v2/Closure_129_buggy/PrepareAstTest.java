package com.google.javascript.jscomp;

import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import junit.framework.TestCase;

public class PrepareAstTest extends TestCase {

    private Compiler compiler;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        compiler = new Compiler();
    }

    public void testPrepareAstConstructorAndProcess() throws Throwable {
        PrepareAst prepareAst = new PrepareAst(compiler, false);
        Node externs = IR.block();
        Node root = IR.block();
        prepareAst.process(externs, root);
        assertNotNull(root);
    }

    public void testPrepareAstCheckOnlyTrue() throws Throwable {
        PrepareAst prepareAst = new PrepareAst(compiler, true);
        Node root = IR.block();
        try {
            prepareAst.process(null, root);
            fail("Expected IllegalStateException due to checkOnly constraint");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("normalizeNodeType constraints violated"));
        }
    }

    public void testPrepareAstWithNullExterns() throws Throwable {
        PrepareAst prepareAst = new PrepareAst(compiler, false);
        Node root = IR.block();
        prepareAst.process(null, root);
        assertNotNull(root);
    }

    public void testPrepareAnnotationsCallbackShouldTraverse() throws Throwable {
        PrepareAst.PrepareAnnotations annotations = new PrepareAst.PrepareAnnotations();
        Node objLit = IR.objectLit();
        NodeTraversal traversal = new NodeTraversal(compiler, annotations);
        
        boolean result = annotations.shouldTraverse(traversal, objLit, null);
        assertTrue(result);
    }

    public void testPrepareAnnotationsCallbackVisitCall() throws Throwable {
        PrepareAst.PrepareAnnotations annotations = new PrepareAst.PrepareAnnotations();
        Node callNode = IR.call(IR.name("print"));
        NodeTraversal traversal = new NodeTraversal(compiler, annotations);
        
        annotations.visit(traversal, callNode, null);
        assertTrue(callNode.getBooleanProp(Node.FREE_CALL));
    }

    public void testPrepareAnnotationsCallbackVisitDirectEval() throws Throwable {
        PrepareAst.PrepareAnnotations annotations = new PrepareAst.PrepareAnnotations();
        Node evalCall = IR.call(IR.name("eval"));
        NodeTraversal traversal = new NodeTraversal(compiler, annotations);
        
        annotations.visit(traversal, evalCall, null);
        assertTrue(evalCall.getFirstChild().getBooleanProp(Node.DIRECT_EVAL));
    }

    public void testPrepareAnnotationsCallbackVisitFunction() throws Throwable {
        PrepareAst.PrepareAnnotations annotations = new PrepareAst.PrepareAnnotations();
        Node fnNode = IR.function(IR.name("f"), IR.paramList(), IR.block());
        Node parent = IR.exprResult(fnNode);
        NodeTraversal traversal = new NodeTraversal(compiler, annotations);
        
        annotations.visit(traversal, fnNode, parent);
        assertFalse(fnNode.getBooleanProp(Node.IS_DISPATCHER));
    }

    public void testNormalizeBlocksWithIfWithoutBlock() throws Throwable {
        PrepareAst prepareAst = new PrepareAst(compiler, true);
        Node ifNode = IR.ifNode(IR.trueNode(), IR.exprResult(IR.number(1)));
        Node root = IR.block(ifNode);
        
        try {
            prepareAst.process(null, root);
            fail("Expected exception due to reportChange in checkOnly mode");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("normalizeNodeType constraints violated"));
        }
    }
}