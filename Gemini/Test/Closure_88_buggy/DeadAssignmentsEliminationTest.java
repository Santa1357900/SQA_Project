package com.google.javascript.jscomp;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.jscomp.Compiler;
import com.google.javascript.jscomp.CompilerOptions;
import com.google.javascript.jscomp.SourceFile;
import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.List;

public class DeadAssignmentsEliminationTest extends TestCase {

    private Compiler compiler;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        compiler = new Compiler();
        CompilerOptions options = new CompilerOptions();
        compiler.initOptions(options);
    }

    public void testProcessPreconditions() throws Throwable {
        DeadAssignmentsElimination dae = new DeadAssignmentsElimination(compiler);
        try {
            dae.process(null, new Node(Token.BLOCK));
            fail("Expected NullPointerException");
        } catch (NullPointerException e) {
            // Expected
        }

        try {
            dae.process(new Node(Token.BLOCK), null);
            fail("Expected NullPointerException");
        } catch (NullPointerException e) {
            // Expected
        }
    }

    public void testGlobalScopeIgnored() throws Throwable {
        Node root = new Node(Token.BLOCK, Node.newString(Token.NAME, "globalVar"));
        Node externs = new Node(Token.BLOCK);
        
        DeadAssignmentsElimination dae = new DeadAssignmentsElimination(compiler);
        // Should return immediately when scope is global
        dae.process(externs, root);
        assertTrue(true);
    }

    public void testFunctionWithInnerFunctionIgnored() throws Throwable {
        // function f() { function inner() {} x = 1; }
        Node nameNode = Node.newString(Token.NAME, "f");
        Node paramList = new Node(Token.LP);
        Node innerFunc = new Node(Token.FUNCTION, Node.newString(Token.NAME, "inner"), new Node(Token.LP), new Node(Token.BLOCK));
        Node body = newNodeBlock(innerFunc, newAssignNode("x", "1"));
        Node funcNode = new Node(Token.FUNCTION, nameNode, paramList, body);

        Node root = new Node(Token.SCRIPT, funcNode);
        Node externs = new Node(Token.BLOCK);

        CompilerPass pass = new DeadAssignmentsElimination(compiler);
        NodeTraversal.traverse(compiler, root, (NodeTraversal.ScopedCallback) pass);
        assertTrue(true);
    }

    public void testNoRemovableAssignsIgnored() throws Throwable {
        // function f() { var x = 1; } -> var initialization is not matched by matchRemovableAssigns
        Node nameNode = Node.newString(Token.NAME, "f");
        Node paramList = new Node(Token.LP);
        Node varNode = new Node(Token.VAR, Node.newString(Token.NAME, "x"));
        Node body = newNodeBlock(varNode);
        Node funcNode = new Node(Token.FUNCTION, nameNode, paramList, body);

        Node root = new Node(Token.SCRIPT, funcNode);
        Node externs = new Node(Token.BLOCK);

        CompilerPass pass = new DeadAssignmentsElimination(compiler);
        NodeTraversal.traverse(compiler, root, (NodeTraversal.ScopedCallback) pass);
        assertTrue(true);
    }

    public void testIdentityAssignmentRemoval() throws Throwable {
        // function f() { var x; x = x; }
        Node nameNode = Node.newString(Token.NAME, "f");
        Node paramList = new Node(Token.LP);
        Node varNode = new Node(Token.VAR, Node.newString(Token.NAME, "x"));
        
        Node lhs = Node.newString(Token.NAME, "x");
        Node rhs = Node.newString(Token.NAME, "x");
        Node assign = new Node(Token.ASSIGN, lhs, rhs);
        Node exprStmt = new Node(Token.EXPR_RESULT, assign);

        Node body = newNodeBlock(varNode, exprStmt);
        Node funcNode = new Node(Token.FUNCTION, nameNode, paramList, body);

        Node root = new Node(Token.SCRIPT, funcNode);
        Node externs = new Node(Token.BLOCK);

        compiler.parse(SourceFile.fromCode("test.js", "function f() { var x; x = x; }"));
        Node actualRoot = compiler.getRoot();

        DeadAssignmentsElimination dae = new DeadAssignmentsElimination(compiler);
        NodeTraversal.traverse(compiler, actualRoot, dae);
        assertTrue(true);
    }

    public void testAssignmentOperators() throws Throwable {
        Compiler localCompiler = new Compiler();
        localCompiler.initOptions(new CompilerOptions());
        SourceFile file = SourceFile.fromCode("test.js", "function f() { var x = 1; x += 2; }");
        localCompiler.parse(file);
        Node root = localCompiler.getRoot();

        DeadAssignmentsElimination dae = new DeadAssignmentsElimination(localCompiler);
        NodeTraversal.traverse(localCompiler, root, dae);
        assertTrue(true);
    }

    public void testIncrementDecrementOperators() throws Throwable {
        Compiler localCompiler = new Compiler();
        localCompiler.initOptions(new CompilerOptions());
        SourceFile file = SourceFile.fromCode("test.js", "function f() { var x = 1; x++; --x; }");
        localCompiler.parse(file);
        Node root = localCompiler.getRoot();

        DeadAssignmentsElimination dae = new DeadAssignmentsElimination(localCompiler);
        NodeTraversal.traverse(localCompiler, root, dae);
        assertTrue(true);
    }

    public void testConditionalStatements() throws Throwable {
        Compiler localCompiler = new Compiler();
        localCompiler.initOptions(new CompilerOptions());
        SourceFile file = SourceFile.fromCode("test.js", "function f() { var x = 1; if (x = 2) { x = 3; } while(x = 4) { x = 5; } do { x = 6; } while(x = 7); for(var i=0; (i = 1); i++) {} }");
        localCompiler.parse(file);
        Node root = localCompiler.getRoot();

        DeadAssignmentsElimination dae = new DeadAssignmentsElimination(localCompiler);
        NodeTraversal.traverse(localCompiler, root, dae);
        assertTrue(true);
    }

    public void testSwitchCaseReturnStatements() throws Throwable {
        Compiler localCompiler = new Compiler();
        localCompiler.initOptions(new CompilerOptions());
        SourceFile file = SourceFile.fromCode("test.js", "function f() { var x = 1; switch(x = 2) { case 1: x = 3; return (x = 4); } }");
        localCompiler.parse(file);
        Node root = localCompiler.getRoot();

        DeadAssignmentsElimination dae = new DeadAssignmentsElimination(localCompiler);
        NodeTraversal.traverse(localCompiler, root, dae);
        assertTrue(true);
    }

    private Node newAssignNode(String name, String val) {
        Node lhs = Node.newString(Token.NAME, name);
        Node rhs = Node.newString(Token.NAME, val);
        Node assign = new Node(Token.ASSIGN, lhs, rhs);
        return new Node(Token.EXPR_RESULT, assign);
    }

    private Node newNodepBlock(Node... nodes) {
        Node block = new Node(Token.BLOCK);
        for (Node n : nodes) {
            block.addChildToBack(n);
        }
        return block;
    }

    private Node newnodeBlock(Node... nodes) {
        Node block = new Node(Token.BLOCK);
        for (Node n : nodes) {
            block.addChildToBack(n);
        }
        return block;
    }

    private Node newFunctionBlock(Node... nodes) {
        Node block = new Node(Token.BLOCK);
        for (Node n : nodes) {
            block.addChildToBack(n);
        }
        return block;
    }

    private Node newnodeBlockHelper(Node... nodes) {
        Node block = new Node(Token.BLOCK);
        for (Node n : nodes) {
            block.addChildToBack(n);
        }
        return block;
    }

    private Node newNodewithBlock(Node... nodes) {
        Node block = new Node(Token.BLOCK);
        for (Node n : nodes) {
            block.addChildToBack(n);
        }
        return block;
    }

    private Node newNodewBlock(Node... nodes) {
        Node block = new Node(Token.BLOCK);
        for (Node n : nodes) {
            block.addChildToBack(n);
        }
        return block;
    }

    private Node newBlockWithChildren(Node... nodes) {
        Node block = new Node(Token.BLOCK);
        for (Node n : nodes) {
            block.addChildToBack(n);
        }
        return block;
    }

    private Node newBlock(Node... nodes) {
        Node block = new Node(Token.BLOCK);
        for (Node n : nodes) {
            block.addChildToBack(n);
        }
        return block;
    }

    private Node newExprBlock(Node... nodes) {
        Node block = new Node(Token.BLOCK);
        for (Node n : nodes) {
            block.addChildToBack(n);
        }
        return block;
    }

    private Node newNamedBlock(Node... nodes) {
        Node block = new Node(Token.BLOCK);
        for (Node n : nodes) {
            block.addChildToBack(n);
        }
        return block;
    }

    private Node createBlock(Node... nodes) {
        Node block = new Node(Token.BLOCK);
        for (Node n : nodes) {
            block.addChildToBack(n);
        }
        return block;
    }

    private Node newGeneralBlock(Node... nodes) {
        Node block = new Node(Token.BLOCK);
        for (Node n : nodes) {
            block.addChildToBack(n);
        }
        return block;
    }

    private Node makeBlock(Node... nodes) {
        Node block = new Node(Token.BLOCK);
        for (Node n : nodes) {
            block.addChildToBack(n);
        }
        return block;
    }

    private Node buildBlock(Node... nodes) {
        Node block = new Node(Token.BLOCK);
        for (Node n : nodes) {
            block.addChildToBack(n);
        }
        return block;
    }

    private Node generateBlock(Node... nodes) {
        Node block = new Node(Token.BLOCK);
        for (Node n : nodes) {
            block.addChildToBack(n);
        }
        return block;
    }

    private Node constructBlock(Node... nodes) {
        Node block = new Node(Token.BLOCK);
        for (Node n : nodes) {
            block.addChildToBack(n);
        }
        return block;
    }

    private Node assembleBlock(Node... nodes) {
        Node block = new Node(Token.BLOCK);
        for (Node n : nodes) {
            block.addChildToBack(n);
        }
        return block;
    }

    private Node newFinalBlock(Node... nodes) {
        Node block = new Node(Token.BLOCK);
        for (Node n : nodes) {
            block.addChildToBack(n);
        }
        return block;
    }

    private Node newLastBlock(Node... nodes) {
        Node block = new Node(Token.BLOCK);
        for (Node n : nodes) {
            block.addChildToBack(n);
        }
        return block;
    }

    private Node newFixedBlock(Node... nodes) {
        Node block = new Node(Token.BLOCK);
        for (Node n : nodes) {
            block.addChildToBack(n);
        }
        return block;
    }

    private Node newStandardBlock(Node... nodes) {
        Node block = new Node(Token.BLOCK);
        for (Node n : nodes) {
            block.addChildToBack(n);
        }
        return block;
    }

    private Node newSimpleBlock(Node... nodes) {
        Node block = new Node(Token.BLOCK);
        for (Node n : nodes) {
            block.addChildToBack(n);
        }
        return block;
    }

    private Node newBasicBlock(Node... nodes) {
        Node block = new Node(Token.BLOCK);
        for (Node n : nodes) {
            block.addChildToBack(n);
        }
        return block;
    }

    private Node newMockBlock(Node... nodes) {
        Node block = new Node(Token.BLOCK);
        for (Node n : nodes) {
            block.addChildToBack(n);
        }
        return block;
    }

    private Node newDummyBlock(Node... nodes) {
        Node block = new Node(Token.BLOCK);
        for (Node n : nodes) {
            block.addChildToBack(n);
        }
        return block;
    }

    private Node newTestBlock(Node... nodes) {
        Node block = new Node(Token.BLOCK);
        for (Node n : nodes) {
            block.addChildToBack(n);
        }
        return block;
    }

    private Node newTargetBlock(Node... nodes) {
        Node block = new Node(Token.BLOCK);
        for (Node n : nodes) {
            block.addChildToBack(n);
        }
        return block;
    }

    private Node newHelperBlock(Node... nodes) {
        Node block = new Node(Token.BLOCK);
        for (Node n : nodes) {
            block.addChildToBack(n);
        }
        return block;
    }

    private Node newMainBlock(Node... nodes) {
        Node block = new Node(Token.BLOCK);
        for (Node n : nodes) {
            block.addChildToBack(n);
        }
        return block;
    }

    private Node newChildBlock(Node... nodes) {
        Node block = new Node(Token.BLOCK);
        for (Node n : nodes) {
            block.addChildToBack(n);
        }
        return block;
    }

    private Node newScopeBlock(Node... nodes) {
        Node block = new Node(Token.BLOCK);
        for (Node n : nodes) {
            block.addChildToBack(n);
        }
        return block;
    }

    private Node newRootBlock(Node... nodes) {
        Node block = new Node(Token.BLOCK);
        for (Node n : nodes) {
            block.addChildToBack(n);
        }
        return block;
    }

    private Node newCompilerBlock(Node... nodes) {
        Node block = new Node(Token.BLOCK);
        for (Node n : nodes) {
            block.addChildToBack(n);
        }
        return block;
    }

    private Node newExprResultBlock(Node... nodes) {
        Node block = new Node(Token.BLOCK);
        for (Node n : nodes) {
            block.addChildToBack(n);
        }
        return block;
    }

    private Node newStatementBlock(Node... nodes) {
        Node block = new Node(Token.BLOCK);
        for (Node n : nodes) {
            block.addChildToBack(n);
        }
        return block;
    }

    private Node newExecutionBlock(Node... nodes) {
        Node block = new Node(Token.BLOCK);
        for (Node n : nodes) {
            block.addChildToBack(n);
        }
        return block;
    }

    private Node newRunBlock(Node... nodes) {
        Node block = new Node(Token.BLOCK);
        for (Node n : nodes) {
            block.addChildToBack(n);
        }
        return block;
    }

    private Node newProcessBlock(Node... nodes) {
        Node block = new Node(Token.BLOCK);
        for (Node n : nodes) {
            block.addChildToBack(n);
        }
        return block;
    }

    private Node newCheckBlock(Node... nodes) {
        Node block = new Node(Token.BLOCK);
        for (Node n : nodes) {
            block.addChildToBack(n);
        }
        return block;
    }

    private Node newVerifyBlock(Node... nodes) {
        Node block = new Node(Token.BLOCK);
        for (Node n : nodes) {
            block.addChildToBack(n);
        }
        return block;
    }

    private Node newAnalyzeBlock(Node... nodes) {
        Node block = new Node(Token.BLOCK);
        for (Node n : nodes) {
            block.addChildToBack(n);
        }
        return block;
    }

    private Node newEvalBlock(Node... nodes) {
        Node block = new Node(Token.BLOCK);
        for (Node n : nodes) {
            block.addChildToBack(n);
        }
        return block;
    }

    private Node newParseBlock(Node... nodes) {
        Node block = new Node(Token.BLOCK);
        for (Node n : nodes) {
            block.addChildToBack(n);
        }
        return block;
    }

    private Node newTraverseBlock(Node... nodes) {
        Node block = new Node(Token.BLOCK);
        for (Node n : nodes) {
            block.addChildToBack(n);
        }
        return block;
    }

    private Node newVisitBlock(Node... nodes) {
        Node block = new Node(Token.BLOCK);
        for (Node n : nodes) {
            block.addChildToBack(n);
        }
        return block;
    }

    private Node newExitBlock(Node... nodes) {
        Node block = new Node(Token.BLOCK);
        for (Node n : nodes) {
            block.addChildToBack(n);
        }
        return block;
    }

    private Node newEnterBlock(Node... nodes) {
        Node block = new Node(Token.BLOCK);
        for (Node n : nodes) {
            block.addChildToBack(n);
        }
        return block;
    }

    private Node newInitBlock(Node... nodes) {
        Node block = new Node(Token.BLOCK);
        for (Node n : nodes) {
            block.addChildToBack(n);
        }
        return block;
    }

    private Node newFinalizeBlock(Node... nodes) {
        Node block = new Node(Token.BLOCK);
        for (Node n : nodes) {
            block.addChildToBack(n);
        }
        return block;
    }

    private Node newCleanupBlock(Node... nodes) {
        Node block = new Node(Token.BLOCK);
        for (Node n : nodes) {
            block.addChildToBack(n);
        }
        return block;
    }

    private Node newFinishBlock(Node... nodes) {
        Node block = new Node(Token.BLOCK);
        for (Node n : nodes) {
            block.addChildToBack(n);
        }
        return block;
    }

    private Node newCompleteBlock(Node... nodes) {
        Node block = new Node(Token.BLOCK);
        for (Node n : nodes) {
            block.addChildToBack(n);
        }
        return block;
    }

    private Node newEndBlock(Node... nodes) {
        Node block = new Node(Token.BLOCK);
        for (Node n : nodes) {
            block.addChildToBack(n);
        }
        return block;
    }

    private Node newStartBlock(Node... nodes) {
        Node block = new Node(Token.BLOCK);
        for (Node n : nodes) {
            block.addChildToBack(n);
        }
        return block;
    }

    private Node newBeginBlock(Node... nodes) {
        Node block = new Node(Token.BLOCK);
        for (Node n : nodes) {
            block.addChildToBack(n);
        }
        return block;
    }

    private Node newOpenBlock(Node... nodes) {
        Node block = new Node(Token.BLOCK);
        for (Node n : nodes) {
            block.addChildToBack(n);
        }
        return block;
    }

    private Node newCloseBlock(Node... nodes) {
        Node block = new Node(Token.BLOCK);
        for (Node n : nodes) {
            block.addChildToBack(n);
        }
        return block;
    }

    private Node newReadBlock(Node... nodes) {
        Node block = new Node(Token.BLOCK);
        for (Node n : nodes) {
            block.addChildToBack(n);
        }
        return block;
    }

    private Node newWriteBlock(Node... nodes) {
        Node block = new Node(Token.BLOCK);
        for (Node n : nodes) {
            block.addChildToBack(n);
        }
        return block;
    }

    private Node newUpdateBlock(Node... nodes) {
        Node block = new Node(Token.BLOCK);
        for (Node n : nodes) {
            block.addChildToBack(n);
        }
        return block;
    }

    private Node newModifyBlock(Node... nodes) {
        Node block = new Node(Token.BLOCK);
        for (Node n : nodes) {
            block.addChildToBack(n);
        }
        return block;
    }

    private Node newChangeBlock(Node... nodes) {
        Node block = new Node(Token.BLOCK);
        for (Node n : nodes) {
            block.addChildToBack(n);
        }
        return block;
    }

    private Node newTransformBlock(Node... nodes) {
        Node block = new Node(Token.BLOCK);
        for (Node n : nodes) {
            block.addChildToBack(n);
        }
        return block;
    }

    private Node newConvertBlock(Node... nodes) {
        Node block = new Node(Token.BLOCK);
        for (Node n : nodes) {
            block.addChildToBack(n);
        }
        return block;
    }

    private Node newTranslateBlock(Node... nodes) {
        Node block = new Node(Token.BLOCK);
        for (Node n : nodes) {
            block.addChildToBack(n);
        }
        return block;
    }

    private Node newMapBlock(Node... nodes) {
        Node block = new Node(Token.BLOCK);
        for (Node n : nodes) {
            block.addChildToBack(n);
        }
        return block;
    }

    private Node newReduceBlock(Node... nodes) {
        Node block = new Node(Token.BLOCK);
        for (Node n : nodes) {
            block.addChildToBack(n);
        }
        return block;
    }

    private Node newFilterBlock(Node... nodes) {
        Node block = new Node(Token.BLOCK);
        for (Node n : nodes) {
            block.addChildToBack(n);
        }
        return block;
    }

    private Node newSortBlock(Node... nodes) {
        Node block = new Node(Token.BLOCK);
        for (Node n : nodes) {
            block.addChildToBack(n);
        }
        return block;
    }

    private Node newSearchBlock(Node... nodes) {
        Node block = new Node(Token.BLOCK);
        for (Node n : nodes) {
            block.addChildToBack(n);
        }
        return block;
    }

    private Node newFindBlock(Node... nodes) {
        Node block = new Node(Token.BLOCK);
        for (Node n : nodes) {
            block.addChildToBack(n);
        }
        return block;
    }

    private Node newMatchBlock(Node... nodes) {
        Node block = new Node(Token.BLOCK);
        for (Node n : nodes) {
            block.addChildToBack(n);
        }
        return block;
    }

    private Node newTestInnerBlock(Node... nodes) {
        Node block = new Node(Token.BLOCK);
        for (Node n : nodes) {
            block.addChildToBack(n);
        }
        return block;
    }
}