package com.google.javascript.jscomp;

import org.junit.Test;
import static org.junit.Assert.*;

import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import com.google.javascript.rhino.jstype.BooleanLiteralSet;
import com.google.javascript.rhino.jstype.JSTypeNative;

import java.util.HashMap;
import java.util.Map;

public class TypeInferenceTest {

    @Test
    public void testGetBooleanOutcomesAllCombinations() throws Throwable {
        BooleanLiteralSet leftTrue = BooleanLiteralSet.TRUE;
        BooleanLiteralSet rightFalse = BooleanLiteralSet.FALSE;
        
        BooleanLiteralSet result = TypeInference.getBooleanOutcomes(leftTrue, rightFalse, true);
        assertNotNull(result);
        
        BooleanLiteralSet resultConditionFalse = TypeInference.getBooleanOutcomes(BooleanLiteralSet.BOTH, BooleanLiteralSet.EMPTY, false);
        assertNotNull(resultConditionFalse);
    }

    @Test
    public void testBooleanOutcomePairEdgeCases() throws Throwable {
        Compiler compiler = new Compiler();
        AbstractCompiler abstractCompiler = compiler;
        Node root = new Node(Token.BLOCK);
        Scope scope = new Scope(root, null);
        ControlFlowGraph<Node> cfg = ControlFlowGraph.create(root, false, false);
        ReverseAbstractInterpreter interpreter = new ClosureCodingConvention().getReverseAbstractInterpreter(compiler.getTypeRegistry(), null);
        Map<String, CodingConvention.AssertionFunctionSpec> assertionMap = new HashMap<String, CodingConvention.AssertionFunctionSpec>();

        TypeInference inference = new TypeInference(abstractCompiler, cfg, interpreter, scope, assertionMap);
        
        FlowScope entryLattice = inference.createEntryLattice();
        assertNotNull(entryLattice);
        
        FlowScope initialEstimate = inference.createInitialEstimateLattice();
        assertNotNull(initialEstimate);
    }
}