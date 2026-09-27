package com.google.javascript.jscomp;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import org.junit.Test;

import static org.junit.Assert.*;

public class DevirtualizePrototypeMethodsTest {

    @Test
    public void testClassInstantiation() throws Throwable {
        Compiler compiler = new Compiler();
        DevirtualizePrototypeMethods pass = new DevirtualizePrototypeMethods(compiler);
        assertNotNull(pass);
    }

    @Test
    public void testProcessWithEmptyNodes() throws Throwable {
        Compiler compiler = new Compiler();
        DevirtualizePrototypeMethods pass = new DevirtualizePrototypeMethods(compiler);
        Node externs = new Node(Token.BLOCK);
        Node root = new Node(Token.BLOCK);
        
        // Should execute without throwing unexpected exceptions
        pass.process(externs, root);
        assertTrue(true);
    }
}