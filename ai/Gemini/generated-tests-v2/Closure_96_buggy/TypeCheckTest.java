package com.google.javascript.jscomp;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;

public class TypeCheckTest {

  @Test
  public void testTypeCheckInstantiationAndPercent() throws Throwable {
    Compiler compiler = new Compiler();
    JSTypeRegistry registry = compiler.getTypeRegistry();
    ReverseAbstractInterpreter interpreter = new ClosureReverseAbstractInterpreter(
        compiler.getCodingConvention(), registry);

    TypeCheck typeCheck = new TypeCheck(
        compiler,
        interpreter,
        registry,
        CheckLevel.WARNING,
        CheckLevel.OFF
    );

    typeCheck.reportMissingProperties(true);
    double percent = typeCheck.getTypedPercent();
    assertEquals(0.0, percent, 0.001);
  }

  @Test
  public void testIsReferenceHelper() throws Throwable {
    Node nameNode = Node.newString(Token.NAME, "x");
    Node getPropNode = Node.newString(Token.GETPROP, "p");
    Node getElemNode = Node.newString(Token.GETELEM, "e");
    Node numberNode = Node.newNumber(123.0);

    assertTrue(TypeCheck.isReference(nameNode));
    assertTrue(TypeCheck.isReference(getPropNode));
    assertTrue(TypeCheck.isReference(getElemNode));
    assertFalse(TypeCheck.isReference(numberNode));
  }
}