package com.google.javascript.jscomp;

import com.google.common.base.Supplier;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import junit.framework.TestCase;

public class InlineObjectLiteralsTest extends TestCase {

    private Compiler compiler;
    private Supplier<String> safeNameIdSupplier;

    @Override
    protected void setUp() throws Exception {
        super.setUp();
        compiler = new Compiler();
        compiler.initOptions(new CompilerOptions());
        safeNameIdSupplier = new Supplier<String>() {
            private int id = 0;
            public String get() {
                return String.valueOf(id++);
            }
        };
    }

    public void testConstructorAndProcess() throws Throwable {
        InlineObjectLiterals pass = new InlineObjectLiterals(compiler, safeNameIdSupplier);
        Node externs = new Node(Token.BLOCK);
        Node root = new Node(Token.BLOCK);
        pass.process(externs, root);
        assertNotNull(compiler);
    }

    public void testVarPrefixConstant() throws Throwable {
        assertEquals("JSCompiler_object_inline_", InlineObjectLiterals.VAR_PREFIX);
    }

    public void testProcessWithSimpleScope() throws Throwable {
        InlineObjectLiterals pass = new InlineObjectLiterals(compiler, safeNameIdSupplier);
        Node externs = new Node(Token.BLOCK);
        Node script = new Node(Token.SCRIPT);
        Node root = new Node(Token.BLOCK, script);
        
        ScopeCreator scopeCreator = new SyntacticScopeCreator(compiler);
        Scope scope = scopeCreator.createScope(root, null);
        
        pass.process(externs, root);
        assertTrue(scope.isGlobal());
    }
}