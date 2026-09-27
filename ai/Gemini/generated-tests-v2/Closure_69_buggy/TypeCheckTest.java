package com.google.javascript.jscomp;

import static com.google.javascript.rhino.jstype.JSTypeNative.UNKNOWN_TYPE;

import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.JSTypeRegistry;

import org.junit.Test;
import static org.junit.Assert.*;

public class TypeCheckTest {

  @Test
  public void testTypeCheckConstructionAndPercent() throws Throwable {
    Compiler compiler = new Compiler();
    JSTypeRegistry registry = compiler.getTypeRegistry();
    ClosureCodingConvention convention = new ClosureCodingConvention();
    TypedScopeCreator scopeCreator = new TypedScopeCreator(compiler);
    Scope topScope = scopeCreator.createScope(IR.block(), null);

    TypeCheck typeCheck = new TypeCheck(
        compiler,
        new ControlFlowAnalysis.AstControlFlowGraph.AbstractFlowComparator(),
        registry,
        topScope,
        scopeCreator,
        CheckLevel.WARNING,
        CheckLevel.OFF
    );

    typeCheck.reportMissingProperties(false);
    assertEquals(0.0, typeCheck.getTypedPercent(), 0.001);
  }

  @Test
  public void testTypeCheckAlternativeConstructors() throws Throwable {
    Compiler compiler = new Compiler();
    JSTypeRegistry registry = compiler.getTypeRegistry();

    TypeCheck tc1 = new TypeCheck(
        compiler,
        null,
        registry,
        CheckLevel.WARNING,
        CheckLevel.OFF
    );
    assertNotNull(tc1);

    TypeCheck tc2 = new TypeCheck(
        compiler,
        null,
        registry
    );
    assertNotNull(tc2);
  }

  @Test
  public void testIsReferenceHelper() throws Throwable {
    Node nameNode = IR.name("testVar");
    assertTrue(TypeCheck.isReference(nameNode));

    Node getPropNode = IR.getprop(IR.name("a"), IR.string("b"));
    assertTrue(TypeCheck.isReference(getPropNode));

    Node numberNode = IR.number(123.0);
    assertFalse(TypeCheck.isReference(numberNode));
  }
}