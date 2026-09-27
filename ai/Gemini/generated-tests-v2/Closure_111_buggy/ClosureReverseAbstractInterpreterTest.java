package com.google.javascript.jscomp.type;

import com.google.javascript.jscomp.CodingConvention;
import com.google.javascript.jscomp.GoogleCodingConvention;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.SimpleErrorReporter;

import org.junit.Test;
import static org.junit.Assert.*;

public class ClosureReverseAbstractInterpreterTest {

  @Test
  public void testInitializationAndConditionOutcome() throws Throwable {
    CodingConvention convention = new GoogleCodingConvention();
    JSTypeRegistry registry = new JSTypeRegistry(new SimpleErrorReporter());
    ClosureReverseAbstractInterpreter interpreter = 
        new ClosureReverseAbstractInterpreter(convention, registry);

    assertNotNull(interpreter);

    Node condition = IR.number(1.0);
    FlowScope blindScope = null;
    
    try {
      interpreter.getPreciserScopeKnowingConditionOutcome(condition, blindScope, true);
      fail("Expected NullPointerException for null blindScope with non-call condition");
    } catch (NullPointerException e) {
      // Expected
    }
  }

  @Test
  public void testCallConditionNotGoog() throws Throwable {
    CodingConvention convention = new GoogleCodingConvention();
    JSTypeRegistry registry = new JSTypeRegistry(new SimpleErrorReporter());
    ClosureReverseAbstractInterpreter interpreter = 
        new ClosureReverseAbstractInterpreter(convention, registry);

    Node callNode = IR.call(IR.getProp(IR.name("notGoog"), "isDef"), IR.name("x"));
    
    FlowScope result = interpreter.getPreciserScopeKnowingConditionOutcome(callNode, null, true);
    assertNull(result);
  }

  @Test
  public void testCallConditionUnknownRestricter() throws Throwable {
    CodingConvention convention = new GoogleCodingConvention();
    JSTypeRegistry registry = new JSTypeRegistry(new SimpleErrorReporter());
    ClosureReverseAbstractInterpreter interpreter = 
        new ClosureReverseAbstractInterpreter(convention, registry);

    Node callNode = IR.call(IR.getProp(IR.name("goog"), "unknownRestricter"), IR.name("x"));
    
    FlowScope result = interpreter.getPreciserScopeKnowingConditionOutcome(callNode, null, true);
    assertNull(result);
  }
}