package com.google.javascript.jscomp.type;

import org.junit.Test;
import static org.junit.Assert.*;

import com.google.javascript.jscomp.CodingConvention;
import com.google.javascript.jscomp.SimpleCodingConvention;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSType;

public class SemanticReverseAbstractInterpreterTest {

  @Test
  public void testConstructor() throws Throwable {
    CodingConvention convention = new SimpleCodingConvention();
    JSTypeRegistry registry = new JSTypeRegistry(null, true);
    SemanticReverseAbstractInterpreter interpreter = 
        new SemanticReverseAbstractInterpreter(convention, registry);
    assertNotNull(interpreter);
  }

  @Test
  public void testGetPreciserScopeWithNullCondition() throws Throwable {
    CodingConvention convention = new SimpleCodingConvention();
    JSTypeRegistry registry = new JSTypeRegistry(null, true);
    SemanticReverseAbstractInterpreter interpreter = 
        new SemanticReverseAbstractInterpreter(convention, registry);
    
    FlowScope blindScope = interpreter.createInitialFlowScope();
    Node condition = null;
    
    try {
      interpreter.getPreciserScopeKnowingConditionOutcome(condition, blindScope, true);
      fail("Expected NullPointerException for null condition");
    } catch (NullPointerException e) {
      // Expected
    }
  }

  @Test
  public void testGetPreciserScopeUnknownToken() throws Throwable {
    CodingConvention convention = new SimpleCodingConvention();
    JSTypeRegistry registry = new JSTypeRegistry(null, true);
    SemanticReverseAbstractInterpreter interpreter = 
        new SemanticReverseAbstractInterpreter(convention, registry);
    
    FlowScope blindScope = interpreter.createInitialFlowScope();
    Node condition = new Node(Token.BLOCK);
    
    FlowScope result = interpreter.getPreciserScopeKnowingConditionOutcome(condition, blindScope, true);
    assertNotNull(result);
  }

  @Test
  public void testNotCondition() throws Throwable {
    CodingConvention convention = new SimpleCodingConvention();
    JSTypeRegistry registry = new JSTypeRegistry(null, true);
    SemanticReverseAbstractInterpreter interpreter = 
        new SemanticReverseAbstractInterpreter(convention, registry);
    
    FlowScope blindScope = interpreter.createInitialFlowScope();
    Node innerNode = Node.newString(Token.NAME, "x");
    Node condition = new Node(Token.NOT, innerNode);
    
    FlowScope result = interpreter.getPreciserScopeKnowingConditionOutcome(condition, blindScope, true);
    assertNotNull(result);
  }
}