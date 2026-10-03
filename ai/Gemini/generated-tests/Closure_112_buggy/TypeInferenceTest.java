package com.google.javascript.jscomp;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeNative;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class TypeInferenceTest {

  @Test
  public void testBooleanOutcomes() throws Throwable {
    com.google.javascript.rhino.jstype.BooleanLiteralSet left = com.google.javascript.rhino.jstype.BooleanLiteralSet.TRUE;
    com.google.javascript.rhino.jstype.BooleanLiteralSet right = com.google.javascript.rhino.jstype.BooleanLiteralSet.FALSE;
    
    com.google.javascript.rhino.jstype.BooleanLiteralSet resultTrue = TypeInference.getBooleanOutcomes(left, right, true);
    assertNotNull(resultTrue);

    com.google.javascript.rhino.jstype.BooleanLiteralSet resultFalse = TypeInference.getBooleanOutcomes(left, right, false);
    assertNotNull(resultFalse);
  }

  @Test
  public void testCreateInitialEstimateLattice() throws Throwable {
    Compiler compiler = new Compiler();
    AbstractCompiler abstractCompiler = compiler;
    JSTypeRegistry registry = abstractCompiler.getTypeRegistry();
    Scope syntacticScope = Scope.createLatticeBottom(new Node(Token.BLOCK));
    ControlFlowGraph<Node> cfg = ControlFlowGraph.create(new Node(Token.BLOCK), false, false);
    ReverseAbstractInterpreter reverseInterpreter = new ClosureCodingConvention().getReverseAbstractInterpreter(registry, false);
    Map<String, CodingConvention.AssertionFunctionSpec> assertionMap = new HashMap<String, CodingConvention.AssertionFunctionSpec>();

    TypeInference inference = new TypeInference(
        abstractCompiler,
        cfg,
        reverseInterpreter,
        syntacticScope,
        assertionMap
    );

    FlowScope initial = inference.createInitialEstimateLattice();
    assertNotNull(initial);

    FlowScope entry = inference.createEntryLattice();
    assertNotNull(entry);
  }

  @Test
  public void testFlowThroughBottom() throws Throwable {
    Compiler compiler = new Compiler();
    AbstractCompiler abstractCompiler = compiler;
    JSTypeRegistry registry = abstractCompiler.getTypeRegistry();
    Scope syntacticScope = Scope.createLatticeBottom(new Node(Token.BLOCK));
    ControlFlowGraph<Node> cfg = ControlFlowGraph.create(new Node(Token.BLOCK), false, false);
    ReverseAbstractInterpreter reverseInterpreter = new ClosureCodingConvention().getReverseAbstractInterpreter(registry, false);
    Map<String, CodingConvention.AssertionFunctionSpec> assertionMap = new HashMap<String, CodingConvention.AssertionFunctionSpec>();

    TypeInference inference = new TypeInference(
        abstractCompiler,
        cfg,
        reverseInterpreter,
        syntacticScope,
        assertionMap
    );

    Node n = new Node(Token.NUMBER, 1.0);
    FlowScope bottom = inference.createInitialEstimateLattice();
    FlowScope result = inference.flowThrough(n, bottom);
    assertSame(bottom, result);
  }
}