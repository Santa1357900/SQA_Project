package com.google.javascript.jscomp;

import static org.junit.Assert.*;
import org.junit.Before;
import org.junit.Test;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Token;
import java.util.List;
import java.util.ArrayList;

public class PeepholeOptimizationsPassClaudeTest {

  private Compiler compiler;
  private Node externs;
  private Node root;

  @Before
  public void setUp() throws Throwable {
    compiler = new Compiler();
    externs = IR.block();
    root = IR.block();
  }

  /** Records call counts for optimizeSubtree/beginTraversal/endTraversal, no structural change. */
  private static class RecordingOptimization extends AbstractPeepholeOptimization {
    int optimizeCallCount = 0;
    int beginCallCount = 0;
    int endCallCount = 0;

    @Override
    Node optimizeSubtree(Node subtree) {
      optimizeCallCount++;
      return subtree;
    }

    @Override
    void beginTraversal(AbstractCompiler compiler) {
      beginCallCount++;
    }

    @Override
    void endTraversal(AbstractCompiler compiler) {
      endCallCount++;
    }
  }

  /** Returns null unconditionally, simulating node removal. */
  private static class NullReturningOptimization extends AbstractPeepholeOptimization {
    int callCount = 0;

    @Override
    Node optimizeSubtree(Node subtree) {
      callCount++;
      return null;
    }
  }

  /** Returns a distinct replacement node exactly once, then stabilizes. */
  private static class ChangeOnceOptimization extends AbstractPeepholeOptimization {
    int callCount = 0;
    private final Node replacement;

    ChangeOnceOptimization(Node replacement) {
      this.replacement = replacement;
    }

    @Override
    Node optimizeSubtree(Node subtree) {
      callCount++;
      if (callCount == 1) {
        return replacement;
      }
      return subtree;
    }
  }

  /** Captures the last node instance it was invoked with. */
  private static class CapturingOptimization extends AbstractPeepholeOptimization {
    Node capturedNode;

    @Override
    Node optimizeSubtree(Node subtree) {
      capturedNode = subtree;
      return subtree;
    }
  }

  /** Reports a code change on every invocation, used to probe scope retraversal. */
  private static class AlwaysChangingOptimization extends AbstractPeepholeOptimization {
    private final AbstractCompiler compilerRef;
    int callCount = 0;

    AlwaysChangingOptimization(AbstractCompiler compilerRef) {
      this.compilerRef = compilerRef;
    }

    @Override
    Node optimizeSubtree(Node subtree) {
      callCount++;
      compilerRef.reportCodeChange();
      return subtree;
    }
  }

  /** Logs the order in which it is invoked. */
  private static class OrderTrackingOptimization extends AbstractPeepholeOptimization {
    private final List<String> log;
    private final String name;

    OrderTrackingOptimization(List<String> log, String name) {
      this.log = log;
      this.name = name;
    }

    @Override
    Node optimizeSubtree(Node subtree) {
      log.add(name);
      return subtree;
    }
  }

  // getCompiler() must return exactly the instance passed to the constructor
  @Test
  public void testGetCompiler_returnsSameInstancePassedToConstructor() throws Throwable {
    PeepholeOptimizationsPass pass = new PeepholeOptimizationsPass(compiler);
    assertSame(compiler, pass.getCompiler());
  }

  // process() with zero optimizations must not corrupt internal compiler reference
  @Test
  public void testProcess_noOptimizations_getCompilerUnaffected() throws Throwable {
    PeepholeOptimizationsPass pass = new PeepholeOptimizationsPass(compiler);
    pass.process(externs, root);
    assertSame(compiler, pass.getCompiler());
  }

  // single optimization that never changes the node is visited exactly once for a childless root
  @Test
  public void testProcess_singleOptimizationNoChange_optimizeCalledOnce() throws Throwable {
    RecordingOptimization opt = new RecordingOptimization();
    PeepholeOptimizationsPass pass = new PeepholeOptimizationsPass(compiler, opt);
    pass.process(externs, root);
    assertEquals(1, opt.optimizeCallCount);
  }

  // beginTraversal/endTraversal must each be invoked exactly once per process() call
  @Test
  public void testProcess_beginAndEndTraversalCalledOnOptimization() throws Throwable {
    RecordingOptimization opt = new RecordingOptimization();
    PeepholeOptimizationsPass pass = new PeepholeOptimizationsPass(compiler, opt);
    pass.process(externs, root);
    assertEquals(1, opt.beginCallCount);
    assertEquals(1, opt.endCallCount);
  }

  // calling process() twice must invoke begin/endTraversal twice (once per call)
  @Test
  public void testProcess_beginTraversalCalledExactlyOncePerProcessCall() throws Throwable {
    RecordingOptimization opt = new RecordingOptimization();
    PeepholeOptimizationsPass pass = new PeepholeOptimizationsPass(compiler, opt);
    pass.process(externs, root);
    pass.process(externs, root);
    assertEquals(2, opt.beginCallCount);
    assertEquals(2, opt.endCallCount);
  }

  // multiple optimizations each receive begin/end traversal callbacks
  @Test
  public void testProcess_multipleOptimizations_allReceiveBeginAndEndTraversal() throws Throwable {
    RecordingOptimization opt1 = new RecordingOptimization();
    RecordingOptimization opt2 = new RecordingOptimization();
    PeepholeOptimizationsPass pass = new PeepholeOptimizationsPass(compiler, opt1, opt2);
    pass.process(externs, root);
    assertEquals(1, opt1.beginCallCount);
    assertEquals(1, opt2.beginCallCount);
  }

  // visit() must pass the root node instance itself into optimizeSubtree
  @Test
  public void testVisit_optimizationReceivesRootNodeAsSubtree() throws Throwable {
    CapturingOptimization opt = new CapturingOptimization();
    PeepholeOptimizationsPass pass = new PeepholeOptimizationsPass(compiler, opt);
    pass.process(externs, root);
    assertSame(root, opt.capturedNode);
  }

  // when an optimization returns null mid-array, subsequent optimizations must be skipped
  @Test
  public void testVisit_secondOptimizationReturnsNull_thirdOptimizationNotCalled() throws Throwable {
    RecordingOptimization opt1 = new RecordingOptimization();
    NullReturningOptimization opt2 = new NullReturningOptimization();
    RecordingOptimization opt3 = new RecordingOptimization();
    PeepholeOptimizationsPass pass = new PeepholeOptimizationsPass(compiler, opt1, opt2, opt3);
    pass.process(externs, root);
    assertEquals(1, opt1.optimizeCallCount);
    assertEquals(1, opt2.callCount);
    assertEquals(0, opt3.optimizeCallCount);
  }

  // when the FIRST optimization returns null, no later optimization in the array runs at all
  @Test
  public void testVisit_firstOptimizationReturnsNull_remainingOptimizationsSkipped() throws Throwable {
    NullReturningOptimization opt1 = new NullReturningOptimization();
    RecordingOptimization opt2 = new RecordingOptimization();
    PeepholeOptimizationsPass pass = new PeepholeOptimizationsPass(compiler, opt1, opt2);
    pass.process(externs, root);
    assertEquals(1, opt1.callCount);
    assertEquals(0, opt2.optimizeCallCount);
  }

  // a single optimization that changes the node once causes the somethingChanged loop to run twice
  @Test
  public void testVisit_optimizationReturnsDifferentNodeOnce_loopsTwiceThenStabilizes() throws Throwable {
    Node replacement = IR.name("x");
    ChangeOnceOptimization opt = new ChangeOnceOptimization(replacement);
    PeepholeOptimizationsPass pass = new PeepholeOptimizationsPass(compiler, opt);
    pass.process(externs, root);
    assertEquals(2, opt.callCount);
  }

  // same as above, but replacement is a STRING node (edge value variety)
  @Test
  public void testVisit_optimizationReturnsStringNode_loopStabilizesAfterTwoCalls() throws Throwable {
    Node replacement = IR.string("");
    ChangeOnceOptimization opt = new ChangeOnceOptimization(replacement);
    PeepholeOptimizationsPass pass = new PeepholeOptimizationsPass(compiler, opt);
    pass.process(externs, root);
    assertEquals(2, opt.callCount);
  }

  // same as above, but replacement is a NUMBER node with a negative value (edge value variety)
  @Test
  public void testVisit_optimizationReturnsNumberNode_loopStabilizesAfterTwoCalls() throws Throwable {
    Node replacement = IR.number(-1);
    ChangeOnceOptimization opt = new ChangeOnceOptimization(replacement);
    PeepholeOptimizationsPass pass = new PeepholeOptimizationsPass(compiler, opt);
    pass.process(externs, root);
    assertEquals(2, opt.callCount);
  }

  // repeated process() calls on the same pass instance independently re-invoke optimizations
  @Test
  public void testProcess_calledTwiceOnSamePass_optimizationInvokedEachTime() throws Throwable {
    RecordingOptimization opt = new RecordingOptimization();
    PeepholeOptimizationsPass pass = new PeepholeOptimizationsPass(compiler, opt);
    pass.process(externs, root);
    pass.process(externs, root);
    assertEquals(2, opt.optimizeCallCount);
  }

  // PeepholeOptimizationsPass must be usable polymorphically via the CompilerPass interface
  @Test
  public void testProcess_calledViaCompilerPassInterface_invokesOptimization() throws Throwable {
    RecordingOptimization opt = new RecordingOptimization();
    CompilerPass pass = new PeepholeOptimizationsPass(compiler, opt);
    pass.process(externs, root);
    assertEquals(1, opt.optimizeCallCount);
  }

  // process() only traverses root, the externs argument is never passed into any optimization
  @Test
  public void testProcess_externsNodeNotTraversed_onlyRootPassedToOptimization() throws Throwable {
    Node externsMarker = IR.string("externs-marker");
    CapturingOptimization opt = new CapturingOptimization();
    PeepholeOptimizationsPass pass = new PeepholeOptimizationsPass(compiler, opt);
    pass.process(externsMarker, root);
    assertSame(root, opt.capturedNode);
  }

  // a node returned by optimizeSubtree is never attached back into a parent (no tree mutation)
  @Test
  public void testVisit_replacementReturnedByOptimization_neverAttachedToAnyParent() throws Throwable {
    Node replacement = IR.name("y");
    ChangeOnceOptimization opt = new ChangeOnceOptimization(replacement);
    PeepholeOptimizationsPass pass = new PeepholeOptimizationsPass(compiler, opt);
    pass.process(externs, root);
    assertNull(replacement.getParent());
  }

  // chained optimizations: first changes the node, second is stable; both are re-invoked until fixed point
  @Test
  public void testVisit_firstOptimizationChangesSecondStable_bothCalledMultipleTimesUntilStable() throws Throwable {
    Node replacement = IR.name("z");
    ChangeOnceOptimization opt1 = new ChangeOnceOptimization(replacement);
    RecordingOptimization opt2 = new RecordingOptimization();
    PeepholeOptimizationsPass pass = new PeepholeOptimizationsPass(compiler, opt1, opt2);
    pass.process(externs, root);
    assertEquals(2, opt1.callCount);
    assertEquals(2, opt2.optimizeCallCount);
  }

  // the second optimization in the chain must receive the node returned by the first, not the original
  @Test
  public void testVisit_secondOptimizationReceivesNodeReturnedByFirst() throws Throwable {
    Node replacement = IR.name("w");
    ChangeOnceOptimization opt1 = new ChangeOnceOptimization(replacement);
    CapturingOptimization opt2 = new CapturingOptimization();
    PeepholeOptimizationsPass pass = new PeepholeOptimizationsPass(compiler, opt1, opt2);
    pass.process(externs, root);
    assertSame(replacement, opt2.capturedNode);
  }

  // optimizations in the array must be invoked strictly in array order
  @Test
  public void testVisit_multipleOptimizations_invokedInArrayOrder() throws Throwable {
    List<String> log = new ArrayList<String>();
    OrderTrackingOptimization opt1 = new OrderTrackingOptimization(log, "first");
    OrderTrackingOptimization opt2 = new OrderTrackingOptimization(log, "second");
    PeepholeOptimizationsPass pass = new PeepholeOptimizationsPass(compiler, opt1, opt2);
    pass.process(externs, root);
    assertEquals("first", log.get(0));
    assertEquals("second", log.get(1));
  }

  // when several optimizations all leave the node unchanged, each is invoked exactly once
  @Test
  public void testVisit_allOptimizationsReturnSameNode_eachCalledExactlyOnce() throws Throwable {
    RecordingOptimization opt1 = new RecordingOptimization();
    RecordingOptimization opt2 = new RecordingOptimization();
    PeepholeOptimizationsPass pass = new PeepholeOptimizationsPass(compiler, opt1, opt2);
    pass.process(externs, root);
    assertEquals(1, opt1.optimizeCallCount);
    assertEquals(1, opt2.optimizeCallCount);
  }

  // a null element in the optimizations array must cause a NullPointerException during traversal
  @Test
  public void testProcess_nullOptimizationElement_throwsNullPointerException() throws Throwable {
    PeepholeOptimizationsPass pass =
        new PeepholeOptimizationsPass(compiler, (AbstractPeepholeOptimization) null);
    try {
      pass.process(externs, root);
      fail("expected NullPointerException");
    } catch (NullPointerException expected) {
    }
  }

  // a FUNCTION-typed node with a null parent must never be eligible for scope retraversal,
  // regardless of how many times a change is reported (parent-null check applies to functions too)
  @Test
  public void testProcess_functionNodeWithNullParentAndReportedChange_doesNotInfinitelyRetraverse()
      throws Throwable {
    Node functionRoot = Node.newString(Token.FUNCTION, "");
    AlwaysChangingOptimization opt = new AlwaysChangingOptimization(compiler);
    PeepholeOptimizationsPass pass = new PeepholeOptimizationsPass(compiler, opt);
    pass.process(externs, functionRoot);
    assertEquals(1, opt.callCount);
  }


}
