package com.google.javascript.jscomp;

import org.junit.Test;
import static org.junit.Assert.*;

import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;

public class PrepareAstClaudeTest {

    private static Node findCall(Node n) {
        if (n.isCall()) {
            return n;
        }
        for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
            Node found = findCall(c);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static Node findObjectLit(Node n) {
        if (n.isObjectLit()) {
            return n;
        }
        for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
            Node found = findObjectLit(c);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private static Node findFunction(Node n) {
        if (n.isFunction()) {
            return n;
        }
        for (Node c = n.getFirstChild(); c != null; c = c.getNext()) {
            Node found = findFunction(c);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private Compiler compileInput(String code) {
        Compiler compiler = new Compiler();
        CompilerOptions options = new CompilerOptions();
        SourceFile extern = SourceFile.fromCode("externs.js", "");
        SourceFile input = SourceFile.fromCode("input.js", code);
        compiler.compile(extern, input, options);
        return compiler;
    }

    private Compiler compileExterns(String code) {
        Compiler compiler = new Compiler();
        CompilerOptions options = new CompilerOptions();
        SourceFile extern = SourceFile.fromCode("externs.js", code);
        SourceFile input = SourceFile.fromCode("input.js", "");
        compiler.compile(extern, input, options);
        return compiler;
    }

    // process(): checkOnly=true, root is an empty BLOCK (not a control structure) -> zero-iteration loop, no wrap.
    @Test
    public void testProcess_checkOnlyTrue_emptyBlockRoot_remainsChildless() throws Throwable {
        Compiler compiler = new Compiler();
        PrepareAst pass = new PrepareAst(compiler, true);
        Node block = IR.block();
        pass.process(null, block);
        assertNull(block.getFirstChild());
    }

    // process(): checkOnly=true, root BLOCK with one NAME child -> loop runs once, parent pointer check passes.
    @Test
    public void testProcess_checkOnlyTrue_singleChildBlock_childParentUnchanged() throws Throwable {
        Compiler compiler = new Compiler();
        PrepareAst pass = new PrepareAst(compiler, true);
        Node block = IR.block();
        Node name = IR.name("a");
        block.addChildrenToFront(name);
        pass.process(null, block);
        assertSame(block, name.getParent());
    }

    // process(): checkOnly=true, root BLOCK with two children -> loop runs multiple times, both keep parent.
    @Test
    public void testProcess_checkOnlyTrue_twoChildBlock_bothChildrenKeepParent() throws Throwable {
        Compiler compiler = new Compiler();
        PrepareAst pass = new PrepareAst(compiler, true);
        Node block = IR.block();
        Node first = IR.name("a");
        block.addChildrenToFront(first);
        Node second = IR.name("b");
        block.addChildrenToFront(second);
        pass.process(null, block);
        assertSame(block, first.getParent());
        assertSame(block, second.getParent());
    }

    // process(): checkOnly=true -> externs parameter is entirely ignored, only root is normalized.
    @Test
    public void testProcess_checkOnlyTrue_externsParamIgnored_remainsUnmodified() throws Throwable {
        Compiler compiler = new Compiler();
        PrepareAst pass = new PrepareAst(compiler, true);
        Node externsMarker = IR.name("marker");
        Node root = IR.block();
        pass.process(externsMarker, root);
        assertEquals("marker", externsMarker.getString());
    }

    // normalizeBlocks via process(checkOnly=true): IF with non-block body is unnormalized -> throws.
    @Test
    public void testProcess_checkOnlyTrue_ifWithoutBlock_throwsIllegalStateException() throws Throwable {
        Compiler compiler = compileInput("var x = 1;\nif (x) foo();");
        Node root = compiler.getRoot();
        Node externsRoot = root.getFirstChild();
        Node jsRoot = externsRoot.getNext();
        try {
            new PrepareAst(compiler, true).process(externsRoot, jsRoot);
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
        }
    }

    // normalizeBlocks via process(checkOnly=true): IF body already a BLOCK -> no wrap needed, no throw.
    @Test
    public void testProcess_checkOnlyTrue_ifWithBlock_noExceptionCallFound() throws Throwable {
        Compiler compiler = compileInput("var x = 1;\nif (x) { foo(); }");
        Node root = compiler.getRoot();
        Node externsRoot = root.getFirstChild();
        Node jsRoot = externsRoot.getNext();
        new PrepareAst(compiler, true).process(externsRoot, jsRoot);
        assertNotNull(findCall(jsRoot));
    }

    // normalizeBlocks via process(checkOnly=true): LABEL is a control structure but explicitly excluded -> no throw.
    @Test
    public void testProcess_checkOnlyTrue_labelStatement_noExceptionCallFound() throws Throwable {
        Compiler compiler = compileInput("lbl: foo();");
        Node root = compiler.getRoot();
        Node externsRoot = root.getFirstChild();
        Node jsRoot = externsRoot.getNext();
        new PrepareAst(compiler, true).process(externsRoot, jsRoot);
        assertNotNull(findCall(jsRoot));
    }

    // normalizeBlocks via process(checkOnly=true): SWITCH is a control structure but explicitly excluded -> no throw.
    @Test
    public void testProcess_checkOnlyTrue_switchStatement_noExceptionCallFound() throws Throwable {
        Compiler compiler = compileInput("var x = 1;\nswitch (x) { case 1: foo(); break; }");
        Node root = compiler.getRoot();
        Node externsRoot = root.getFirstChild();
        Node jsRoot = externsRoot.getNext();
        new PrepareAst(compiler, true).process(externsRoot, jsRoot);
        assertNotNull(findCall(jsRoot));
    }

    // normalizeBlocks via process(checkOnly=true): WHILE with non-block body is unnormalized -> throws.
    @Test
    public void testProcess_checkOnlyTrue_whileWithoutBlock_throwsIllegalStateException() throws Throwable {
        Compiler compiler = compileInput("var x = 1;\nwhile (x) foo();");
        Node root = compiler.getRoot();
        Node externsRoot = root.getFirstChild();
        Node jsRoot = externsRoot.getNext();
        try {
            new PrepareAst(compiler, true).process(externsRoot, jsRoot);
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
        }
    }

    // normalizeBlocks via process(checkOnly=true): WHILE body already a BLOCK -> no throw.
    @Test
    public void testProcess_checkOnlyTrue_whileWithBlock_noExceptionCallFound() throws Throwable {
        Compiler compiler = compileInput("var x = 1;\nwhile (x) { foo(); }");
        Node root = compiler.getRoot();
        Node externsRoot = root.getFirstChild();
        Node jsRoot = externsRoot.getNext();
        new PrepareAst(compiler, true).process(externsRoot, jsRoot);
        assertNotNull(findCall(jsRoot));
    }

    // normalizeBlocks via process(checkOnly=true): DO body with non-block is unnormalized -> throws.
    @Test
    public void testProcess_checkOnlyTrue_doWhileWithoutBlock_throwsIllegalStateException() throws Throwable {
        Compiler compiler = compileInput("var x = 1;\ndo foo(); while (x);");
        Node root = compiler.getRoot();
        Node externsRoot = root.getFirstChild();
        Node jsRoot = externsRoot.getNext();
        try {
            new PrepareAst(compiler, true).process(externsRoot, jsRoot);
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
        }
    }

    // normalizeBlocks via process(checkOnly=true): DO body already a BLOCK -> no throw.
    @Test
    public void testProcess_checkOnlyTrue_doWhileWithBlock_noExceptionCallFound() throws Throwable {
        Compiler compiler = compileInput("var x = 1;\ndo { foo(); } while (x);");
        Node root = compiler.getRoot();
        Node externsRoot = root.getFirstChild();
        Node jsRoot = externsRoot.getNext();
        new PrepareAst(compiler, true).process(externsRoot, jsRoot);
        assertNotNull(findCall(jsRoot));
    }

    // process(): checkOnly=false never calls normalizeNodeTypes -> an unnormalized IF body stays unwrapped.
    @Test
    public void testProcess_checkOnlyFalse_doesNotWrapIfBody() throws Throwable {
        Compiler compiler = compileInput("var x = 1;\nif (x) foo();");
        Node root = compiler.getRoot();
        Node externsRoot = root.getFirstChild();
        Node jsRoot = externsRoot.getNext();
        Node ifBody = findCall(jsRoot).getParent().getParent();
        new PrepareAst(compiler, false).process(externsRoot, jsRoot);
        assertFalse(ifBody.isBlock());
    }

    // process(): checkOnly=false, externs null, root provided -> root is still traversed and annotated.
    @Test
    public void testProcess_externsNullRootProvided_rootStillTraversed() throws Throwable {
        Compiler compiler = compileInput("foo();");
        Node root = compiler.getRoot();
        Node externsRoot = root.getFirstChild();
        Node jsRoot = externsRoot.getNext();
        Node call = findCall(jsRoot);
        new PrepareAst(compiler, false).process(null, jsRoot);
        assertTrue(call.getBooleanProp(Node.FREE_CALL));
    }

    // process(): checkOnly=false, root null, externs provided -> externs is still traversed and annotated.
    @Test
    public void testProcess_rootNullExternsProvided_externsStillTraversed() throws Throwable {
        Compiler compiler = compileExterns("foo();");
        Node root = compiler.getRoot();
        Node externsRoot = root.getFirstChild();
        Node call = findCall(externsRoot);
        new PrepareAst(compiler, false).process(externsRoot, null);
        assertTrue(call.getBooleanProp(Node.FREE_CALL));
    }

    // PrepareAnnotations.shouldTraverse: non-object-literal node -> always returns true, no side effect.
    @Test
    public void testShouldTraverse_nonObjectLit_returnsTrue() throws Throwable {
        PrepareAst.PrepareAnnotations pa = new PrepareAst.PrepareAnnotations();
        Node name = IR.name("x");
        boolean result = pa.shouldTraverse(null, name, null);
        assertTrue(result);
    }

    // shouldTraverse on OBJECTLIT copies key JSDoc to a FUNCTION value (normalizeObjectLiteralKeyAnnotations).
    @Test
    public void testProcess_objectLiteralKeyJSDoc_copiedToFunctionValue() throws Throwable {
        Compiler compiler = compileInput("var x = {\n/** @type {Function} */\na: function(){}\n};");
        Node root = compiler.getRoot();
        Node externsRoot = root.getFirstChild();
        Node jsRoot = externsRoot.getNext();
        Node objlit = findObjectLit(jsRoot);
        Node value = objlit.getFirstChild().getFirstChild();
        new PrepareAst(compiler, false).process(externsRoot, jsRoot);
        assertNotNull(value.getJSDocInfo());
    }

    // visit(): NAME node matches no switch case -> default no-op, FREE_CALL stays unset.
    @Test
    public void testVisit_nameNode_defaultCase_noException() throws Throwable {
        PrepareAst.PrepareAnnotations pa = new PrepareAst.PrepareAnnotations();
        Node name = IR.name("y");
        pa.visit(null, name, null);
        assertFalse(name.getBooleanProp(Node.FREE_CALL));
    }

    // visit(): BLOCK node matches no switch case -> default no-op, IS_DISPATCHER stays unset.
    @Test
    public void testVisit_blockNode_defaultCase_noException() throws Throwable {
        PrepareAst.PrepareAnnotations pa = new PrepareAst.PrepareAnnotations();
        Node block = IR.block();
        pa.visit(null, block, null);
        assertFalse(block.getBooleanProp(Node.IS_DISPATCHER));
    }

    // annotateCalls: callee is a plain NAME (not a GET) -> FREE_CALL true, and name != "eval" -> DIRECT_EVAL false.
    @Test
    public void testAnnotateCalls_nameCallee_freeCallTrueDirectEvalFalse() throws Throwable {
        Compiler compiler = compileInput("foo();");
        Node root = compiler.getRoot();
        Node externsRoot = root.getFirstChild();
        Node jsRoot = externsRoot.getNext();
        Node call = findCall(jsRoot);
        new PrepareAst(compiler, false).process(externsRoot, jsRoot);
        assertTrue(call.getBooleanProp(Node.FREE_CALL));
        assertFalse(call.getFirstChild().getBooleanProp(Node.DIRECT_EVAL));
    }

    // annotateCalls: callee is a GET expression (obj.method) -> FREE_CALL must NOT be set.
    @Test
    public void testAnnotateCalls_getCallee_notFreeCall() throws Throwable {
        Compiler compiler = compileInput("var obj = {method: function(){}};\nobj.method();");
        Node root = compiler.getRoot();
        Node externsRoot = root.getFirstChild();
        Node jsRoot = externsRoot.getNext();
        Node call = findCall(jsRoot);
        new PrepareAst(compiler, false).process(externsRoot, jsRoot);
        assertFalse(call.getBooleanProp(Node.FREE_CALL));
    }

    // annotateCalls: callee NAME "eval" -> DIRECT_EVAL true on the callee.
    @Test
    public void testAnnotateCalls_evalCallee_directEvalTrue() throws Throwable {
        Compiler compiler = compileInput("eval();");
        Node root = compiler.getRoot();
        Node externsRoot = root.getFirstChild();
        Node jsRoot = externsRoot.getNext();
        Node call = findCall(jsRoot);
        new PrepareAst(compiler, false).process(externsRoot, jsRoot);
        assertTrue(call.getFirstChild().getBooleanProp(Node.DIRECT_EVAL));
    }

    // Bug-catching: comment says "ignore cast nodes" but code does not; a cast-wrapped GET callee
    // must still be recognized as a GET call (per the stated contract), so FREE_CALL must be false.
    @Test
    public void testAnnotateCalls_castWrappedGetCallee_notFreeCall_bug() throws Throwable {
        Compiler compiler = compileInput(
            "var obj = {method: function(){}};\n(/** @type {Function} */ (obj.method))();");
        Node root = compiler.getRoot();
        Node externsRoot = root.getFirstChild();
        Node jsRoot = externsRoot.getNext();
        Node call = findCall(jsRoot);
        new PrepareAst(compiler, false).process(externsRoot, jsRoot);
        assertFalse(call.getBooleanProp(Node.FREE_CALL));
    }

    // annotateDispatchers: parent has no JSDocInfo -> short-circuits, IS_DISPATCHER stays false.
    @Test
    public void testAnnotateDispatchers_noJSDoc_isDispatcherFalse() throws Throwable {
        Compiler compiler = compileInput("var x = {a: function(){}};");
        Node root = compiler.getRoot();
        Node externsRoot = root.getFirstChild();
        Node jsRoot = externsRoot.getNext();
        Node fn = findFunction(jsRoot);        new PrepareAst(compiler, false).process(externsRoot, jsRoot);
        assertFalse(fn.getBooleanProp(Node.IS_DISPATCHER));
    }
}
