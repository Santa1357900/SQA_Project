package com.google.javascript.jscomp;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.JSType;
import com.google.javascript.rhino.jstype.JSTypeRegistry;
import com.google.javascript.rhino.jstype.BooleanLiteralSet;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.List;
import java.util.Map;
import java.util.HashMap;

public class TypeInferenceTest {

  @Test
  public void testBooleanOutcomes() throws Throwable {
    BooleanLiteralSet left = BooleanLiteralSet.TRUE;
    BooleanLiteralSet right = BooleanLiteralSet.FALSE;
    BooleanLiteralSet result = TypeInference.getBooleanOutcomes(left, right, true);
    assertNotNull(result);
  }

  @Test
  public void testBooleanOutcomesConditionFalse() throws Throwable {
    BooleanLiteralSet left = BooleanLiteralSet.BOTH;
    BooleanLiteralSet right = BooleanLiteralSet.EMPTY;
    BooleanLiteralSet result = TypeInference.getBooleanOutcomes(left, right, false);
    assertNotNull(result);
  }

  @Test
  public void testFunctionLiteralUndefinedThis() throws Throwable {
    DiagnosticType dt = TypeInference.FUNCTION_LITERAL_UNDEFINED_THIS;
    assertNotNull(dt);
    assertTrue(dt.id.contains("JSC_FUNCTION_LITERAL_UNDEFINED_THIS"));
  }

  @Test
  public void testFlowThroughWithBottomScope() throws Throwable {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    compiler.initOptions(options);
    JSTypeRegistry registry = compiler.getTypeRegistry();
    
    ControlFlowGraph<Node> cfg = new ControlFlowGraph<Node>(new Node(Token.BLOCK), true, true);
    Scope scope = Scope.createLatticeBottom(new Node(Token.BLOCK));
    Map<String, CodingConvention.AssertionFunctionSpec> assertionMap = new HashMap<String, CodingConvention.AssertionFunctionSpec>();

    TypeInference inference = new TypeInference(
        compiler,
        cfg,
        null,
        scope,
        assertionMap
    );

    FlowScope initial = inference.createInitialEstimateLattice();
    assertNotNull(initial);

    Node node = new Node(Token.NAME, "testName");
    FlowScope result = inference.flowThrough(node, initial);
    assertEquals(initial, result);
  }
}