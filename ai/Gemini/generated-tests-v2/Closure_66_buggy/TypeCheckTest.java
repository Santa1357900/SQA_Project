package com.google.javascript.jscomp;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import org.junit.Test;

import static org.junit.Assert.*;

public class TypeCheckTest {

    @Test
    public void testTypeCheckConstructorsAndBasicMethods() throws Throwable {
        Compiler compiler = new Compiler();
        DefaultCodingConvention convention = new DefaultCodingConvention();
        JSTypeRegistry registry = compiler.getTypeRegistry();
        
        TypeCheck typeCheck1 = new TypeCheck(compiler, null, registry, CheckLevel.WARNING, CheckLevel.OFF);
        assertNotNull(typeCheck1);

        TypeCheck typeCheck2 = new TypeCheck(compiler, null, registry);
        assertNotNull(typeCheck2);

        TypeCheck chained = typeCheck2.reportMissingProperties(false);
        assertNotNull(chained);

        double percent = typeCheck2.getTypedPercent();
        assertEquals(0.0, percent, 0.001);
    }

    @Test
    public void testProcessWithNullScopeCreatorOrTopScope() throws Throwable {
        Compiler compiler = new Compiler();
        JSTypeRegistry registry = compiler.getTypeRegistry();
        TypeCheck typeCheck = new TypeCheck(compiler, null, registry, CheckLevel.OFF, CheckLevel.OFF);

        Node jsRoot = new Node(Token.SCRIPT);
        Node parent = new Node(Token.BLOCK);
        parent.addChildToBack(jsRoot);

        boolean exceptionThrown = false;
        try {
            typeCheck.process(null, jsRoot);
        } catch (NullPointerException e) {
            exceptionThrown = true;
        } catch (IllegalStateException e) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);
    }

    @Test
    public void testCheckMethodEdgeCases() throws Throwable {
        Compiler compiler = new Compiler();
        JSTypeRegistry registry = compiler.getTypeRegistry();
        TypeCheck typeCheck = new TypeCheck(compiler, null, registry, CheckLevel.WARNING, CheckLevel.OFF);

        Node scriptNode = new Node(Token.SCRIPT);
        
        boolean exceptionThrown = false;
        try {
            typeCheck.check(scriptNode, true);
        } catch (Throwable t) {
            exceptionThrown = true;
        }
        assertTrue(exceptionThrown);
    }
}