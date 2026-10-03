package com.google.javascript.jscomp;

import com.google.common.base.Supplier;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;

public class InlineObjectLiteralsTest {

    @Test
    public void testConstructionAndProcess() throws Throwable {
        Compiler compiler = new Compiler();
        Supplier<String> supplier = new Supplier<String>() {
            @Override
            public String get() {
                return "1";
            }
        };

        InlineObjectLiterals pass = new InlineObjectLiterals(compiler, supplier);
        assertNotNull(pass);

        Node externs = IR.block();
        Node root = IR.block();
        pass.process(externs, root);
    }
}