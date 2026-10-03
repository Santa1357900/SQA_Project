package com.google.javascript.jscomp;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import junit.framework.TestCase;

public class ScopedAliasesTest extends TestCase {

    private Compiler compiler;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        compiler = new Compiler();
    }

    public void testScopedAliasesConstruction() throws Throwable {
        ScopedAliases pass = new ScopedAliases(compiler, null, new CompilerOptions().getAliasTransformationHandler());
        assertNotNull(pass);
    }

    public void testProcessBasicScope() throws Throwable {
        Node script = IR.script();
        Node name = IR.name("goog");
        Node scopeCall = IR.call(IR.getprop(name, "scope"), IR.function(IR.name(""), IR.paramList(), IR.block()));
        script.addChildToBack(IR.exprResult(scopeCall));

        ScopedAliases pass = new ScopedAliases(compiler, null, new CompilerOptions().getAliasTransformationHandler());
        pass.process(null, script);
        assertEquals(0, compiler.getErrorCount());
    }

    public void testHotSwapScript() throws Throwable {
        Node script = IR.script();
        Node name = IR.name("goog");
        Node scopeCall = IR.call(IR.getprop(name, "scope"), IR.function(IR.name(""), IR.paramList(), IR.block()));
        script.addChildToBack(IR.exprResult(scopeCall));

        ScopedAliases pass = new ScopedAliases(compiler, null, new CompilerOptions().getAliasTransformationHandler());
        pass.hotSwapScript(script, null);
        assertEquals(0, compiler.getErrorCount());
    }

    public void testScopeUsedImproperly() throws Throwable {
        Node script = IR.script();
        Node name = IR.name("goog");
        Node scopeCall = IR.call(IR.getprop(name, "scope"), IR.function(IR.name(""), IR.paramList(), IR.block()));
        // Not wrapped in an expression result, should trigger GOOG_SCOPE_USED_IMPROPERLY
        script.addChildToBack(scopeCall);

        ScopedAliases pass = new ScopedAliases(compiler, null, new CompilerOptions().getAliasTransformationHandler());
        pass.process(null, script);
        assertTrue(compiler.getErrorCount() > 0);
    }

    public void testScopeHasBadParametersMissingFn() throws Throwable {
        Node script = IR.script();
        Node name = IR.name("goog");
        Node scopeCall = IR.call(IR.getprop(name, "scope"), IR.number(1.0));
        script.addChildToBack(IR.exprResult(scopeCall));

        ScopedAliases pass = new ScopedAliases(compiler, null, new CompilerOptions().getAliasTransformationHandler());
        pass.process(null, script);
        assertTrue(compiler.getErrorCount() > 0);
    }
}