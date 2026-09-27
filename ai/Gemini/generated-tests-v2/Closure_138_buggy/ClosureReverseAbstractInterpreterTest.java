package com.google.javascript.jscomp;

import static com.google.javascript.rhino.Token.CALL;
import static com.google.javascript.rhino.Token.GETPROP;
import static com.google.javascript.rhino.Token.NAME;
import static com.google.javascript.rhino.Token.STRING;

import com.google.common.base.Function;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.ObjectType;
import com.google.javascript.rhino.jstype.FunctionType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.IR;

import junit.framework.TestCase;

public class ClosureReverseAbstractInterpreterTest extends TestCase {

  private Compiler compiler;
  private JSTypeRegistry typeRegistry;
  private CodingConvention convention;
  private ClosureReverseAbstractInterpreter interpreter;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    compiler = new Compiler();
    typeRegistry = compiler.getTypeRegistry();
    convention = new DefaultCodingConvention();
    interpreter = new ClosureReverseAbstractInterpreter(convention, typeRegistry);
  }

  public void testIsDefOutcomeTrue() throws Throwable {
    Node condition = IR.call(
        IR.getprop(IR.name("goog"), IR.string("isDef")),
        IR.name("x")
    );
    FlowScope blindScope = new FlowScope();
    
    // Test condition outcome with valid structure
    FlowScope result = interpreter.getPreciserScopeKnowingConditionOutcome(condition, blindScope, true);
    assertNotNull(result);
  }

  public void testIsDefOutcomeFalse() throws Throwable {
    Node condition = IR.call(
        IR.getprop(IR.name("goog"), IR.string("isDef")),
        IR.name("x")
    );
    FlowScope blindScope = new FlowScope();
    
    FlowScope result = interpreter.getPreciserScopeKnowingConditionOutcome(condition, blindScope, false);
    assertNotNull(result);
  }

  public void testIsNull() throws Throwable {
    Node condition = IR.call(
        IR.getprop(IR.name("goog"), IR.string("isNull")),
        IR.name("x")
    );
    FlowScope blindScope = new FlowScope();
    
    FlowScope resultTrue = interpreter.getPreciserScopeKnowingConditionOutcome(condition, blindScope, true);
    assertNotNull(resultTrue);

    FlowScope resultFalse = interpreter.getPreciserScopeKnowingConditionOutcome(condition, blindScope, false);
    assertNotNull(resultFalse);
  }

  public void testIsDefAndNotNull() throws Throwable {
    Node condition = IR.call(
        IR.getprop(IR.name("goog"), IR.string("isDefAndNotNull")),
        IR.name("x")
    );
    FlowScope blindScope = new FlowScope();
    
    FlowScope result = interpreter.getPreciserScopeKnowingConditionOutcome(condition, blindScope, true);
    assertNotNull(result);
  }

  public void testIsString() throws Throwable {
    Node condition = IR.call(
        IR.getprop(IR.name("goog"), IR.string("isString")),
        IR.name("x")
    );
    FlowScope blindScope = new FlowScope();
    
    FlowScope result = interpreter.getPreciserScopeKnowingConditionOutcome(condition, blindScope, true);
    assertNotNull(result);
  }

  public void testIsBoolean() throws Throwable {
    Node condition = IR.call(
        IR.getprop(IR.name("goog"), IR.string("isBoolean")),
        IR.name("x")
    );
    FlowScope blindScope = new FlowScope();
    
    FlowScope result = interpreter.getPreciserScopeKnowingConditionOutcome(condition, blindScope, true);
    assertNotNull(result);
  }

  public void testIsNumber() throws Throwable {
    Node condition = IR.call(
        IR.getprop(IR.name("goog"), IR.string("isNumber")),
        IR.name("x")
    );
    FlowScope blindScope = new FlowScope();
    
    FlowScope result = interpreter.getPreciserScopeKnowingConditionOutcome(condition, blindScope, true);
    assertNotNull(result);
  }

  public void testIsFunction() throws Throwable {
    Node condition = IR.call(
        IR.getprop(IR.name("goog"), IR.string("isFunction")),
        IR.name("x")
    );
    FlowScope blindScope = new FlowScope();
    
    FlowScope result = interpreter.getPreciserScopeKnowingConditionOutcome(condition, blindScope, true);
    assertNotNull(result);
  }

  public void testIsArray() throws Throwable {
    Node condition = IR.call(
        IR.getprop(IR.name("goog"), IR.string("isArray")),
        IR.name("x")
    );
    FlowScope blindScope = new FlowScope();
    
    FlowScope resultTrue = interpreter.getPreciserScopeKnowingConditionOutcome(condition, blindScope, true);
    assertNotNull(resultTrue);

    FlowScope resultFalse = interpreter.getPreciserScopeKnowingConditionOutcome(condition, blindScope, false);
    assertNotNull(resultFalse);
  }

  public void testIsObject() throws Throwable {
    Node condition = IR.call(
        IR.getprop(IR.name("goog"), IR.string("isObject")),
        IR.name("x")
    );
    FlowScope blindScope = new FlowScope();
    
    FlowScope resultTrue = interpreter.getPreciserScopeKnowingConditionOutcome(condition, blindScope, true);
    assertNotNull(resultTrue);

    FlowScope resultFalse = interpreter.getPreciserScopeKnowingConditionOutcome(condition, blindScope, false);
    assertNotNull(resultFalse);
  }

  public void testInvalidConditionNotACall() throws Throwable {
    Node condition = IR.name("x");
    FlowScope blindScope = new FlowScope();
    FlowScope result = interpreter.getPreciserScopeKnowingConditionOutcome(condition, blindScope, true);
    assertEquals(blindScope, result);
  }

  public void testInvalidConditionWrongCallee() throws Throwable {
    Node condition = IR.call(
        IR.getprop(IR.name("notgoog"), IR.string("isDef")),
        IR.name("x")
    );
    FlowScope blindScope = new FlowScope();
    FlowScope result = interpreter.getPreciserScopeKnowingConditionOutcome(condition, blindScope, true);
    assertEquals(blindScope, result);
  }

  public void testInvalidConditionUnknownRestricter() throws Throwable {
    Node condition = IR.call(
        IR.getprop(IR.name("goog"), IR.string("unknownCheck")),
        IR.name("x")
    );
    FlowScope blindScope = new FlowScope();
    FlowScope result = interpreter.getPreciserScopeKnowingConditionOutcome(condition, blindScope, true);
    assertEquals(blindScope, result);
  }
}