package com.google.javascript.jscomp;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class TypeCheckTest {

  @Test
  public void testGetTypedPercentEmpty() throws Throwable {
    Compiler compiler = new Compiler();
    JSTypeRegistry registry = compiler.getTypeRegistry();
    TypeCheck typeCheck = new TypeCheck(compiler, null, registry);
    double percent = typeCheck.getTypedPercent();
    assertEquals(Double.valueOf(0.0), Double.valueOf(percent));
  }

  @Test
  public void testReportMissingProperties() throws Throwable {
    Compiler compiler = new Compiler();
    JSTypeRegistry registry = compiler.getTypeRegistry();
    TypeCheck typeCheck = new TypeCheck(compiler, null, registry);
    TypeCheck chained = typeCheck.reportMissingProperties(false);
    assertNotNull(chained);
  }

  @Test
  public void testProcessNullNodesSafe() throws Throwable {
    Compiler compiler = new Compiler();
    JSTypeRegistry registry = compiler.getTypeRegistry();
    TypeCheck typeCheck = new TypeCheck(compiler, null, registry);
    
    boolean exceptionThrown = false;
    try {
      typeCheck.process(null, null);
    } catch (Exception e) {
      exceptionThrown = true;
    }
    assertTrue(exceptionThrown);
  }
}