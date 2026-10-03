package com.google.javascript.jscomp;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.BooleanLiteralSet;
import org.junit.Test;

import java.util.HashMap;
import java.util.List;

import static org.junit.Assert.*;

public class TypeInferenceTest {

  @Test
  public void testGetBooleanOutcomesCases() throws Throwable {
    BooleanLiteralSet leftBoth = BooleanLiteralSet.BOTH;
    BooleanLiteralSet leftEmpty = BooleanLiteralSet.EMPTY;
    BooleanLiteralSet leftTrue = BooleanLiteralSet.TRUE;
    BooleanLiteralSet leftFalse = BooleanLiteralSet.FALSE;

    BooleanLiteralSet rightBoth = BooleanLiteralSet.BOTH;
    BooleanLiteralSet rightEmpty = BooleanLiteralSet.EMPTY;
    BooleanLiteralSet rightTrue = BooleanLiteralSet.TRUE;

    // condition = true (e.g. for &&, left side must be true to evaluate right)
    BooleanLiteralSet res1 = TypeInference.getBooleanOutcomes(leftBoth, rightBoth, true);
    assertNotNull(res1);

    BooleanLiteralSet res2 = TypeInference.getBooleanOutcomes(leftEmpty, rightEmpty, true);
    assertNotNull(res2);

    BooleanLiteralSet res3 = TypeInference.getBooleanOutcomes(leftTrue, rightTrue, false);
    assertNotNull(res3);

    BooleanLiteralSet res4 = TypeInference.getBooleanOutcomes(leftFalse, rightBoth, true);
    assertNotNull(res4);
  }

  @Test
  public void testBooleanOutcomePairLogic() throws Throwable {
    Compiler compiler = new Compiler();
    ControlFlowGraph<Node> cfg = ControlFlowGraph.create(new Node(Token.BLOCK), false, false);
    Scope scope = Scope.createLatticeBottom(new Node(Token.BLOCK));
    
    TypeInference inference = new TypeInference(
        compiler,
        cfg,
        null,
        scope,
        new HashMap<String, CodingConvention.AssertionFunctionSpec>()
    );

    Node andNode = new Node(Token.AND);
    andNode.addChildToBack(new Node(Token.TRUE));
    andNode.addChildToBack(new Node(Token.FALSE));

    FlowScope entry = inference.createEntryLattice();
    List<FlowScope> branched = inference.branchedFlowThrough(andNode, entry);
    assertNotNull(branched);
  }
}