package com.google.javascript.jscomp;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.javascript.rhino.InputId;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import org.junit.Test;

import java.util.Iterator;

import static org.junit.Assert.*;

public class ReferenceCollectingCallbackTest {

  @Test
  public void testDoNothingBehavior() throws Throwable {
    ReferenceCollectingCallback.Behavior behavior = ReferenceCollectingCallback.DO_NOTHING_BEHAVIOR;
    assertNotNull(behavior);
  }

  @Test
  public void testCreateRefForTest() throws Throwable {
    CompilerInput input = new CompilerInput(new JSSourceFile("testfile", "var x;"));
    ReferenceCollectingCallback.Reference ref = ReferenceCollectingCallback.Reference.createRefForTest(input);
    assertNotNull(ref);
    assertEquals(input.getInputId(), ref.getInputId());
    assertNotNull(ref.getNode());
    assertEquals(Token.NAME, ref.getNode().getType());
  }

  @Test
  public void testBasicBlockGlobalScope() throws Throwable {
    Node root = new Node(Token.BLOCK);
    ReferenceCollectingCallback.BasicBlock block = new ReferenceCollectingCallback.BasicBlock(null, root);
    assertTrue(block.isGlobalScopeBlock());
    assertNull(block.getParent());
    assertTrue(block.provablyExecutesBefore(block));
  }

  @Test
  public void testBasicBlockDescendantAndHoisted() throws Throwable {
    Node parentRoot = new Node(Token.BLOCK);
    ReferenceCollectingCallback.BasicBlock parentBlock = new ReferenceCollectingCallback.BasicBlock(null, parentRoot);

    Node childRoot = new Node(Token.FUNCTION);
    ReferenceCollectingCallback.BasicBlock childBlock = new ReferenceCollectingCallback.BasicBlock(parentBlock, childRoot);

    assertFalse(childBlock.isGlobalScopeBlock());
    assertEquals(parentBlock, childBlock.getParent());
    assertTrue(parentBlock.provablyExecutesBefore(childBlock));
    assertFalse(childBlock.provablyExecutesBefore(parentBlock));
  }

  @Test
  public void testReferenceCollectionMethods() throws Throwable {
    ReferenceCollectingCallback.ReferenceCollection collection = new ReferenceCollectingCallback.ReferenceCollection();
    assertFalse(collection.isWellDefined());
    assertFalse(collection.isEscaped());
    assertTrue(collection.isNeverAssigned());
    assertFalse(collection.isAssignedOnceInLifetime());
    assertNull(collection.getInitializingReference());
    assertNull(collection.getInitializingReferenceForConstants());
    assertFalse(collection.firstReferenceIsAssigningDeclaration());

    Iterator<ReferenceCollectingCallback.Reference> iterator = collection.iterator();
    assertNotNull(iterator);
    assertFalse(iterator.hasNext());
  }

  @Test
  public void testReferenceCollectionWithVar() throws Throwable {
    CompilerInput input = new CompilerInput(new JSSourceFile("testfile", "var x;"));
    ReferenceCollectingCallback.Reference ref = ReferenceCollectingCallback.Reference.createRefForTest(input);
    
    ReferenceCollectingCallback.ReferenceCollection collection = new ReferenceCollectingCallback.ReferenceCollection();
    collection.add(ref);

    assertFalse(collection.isWellDefined());
    assertFalse(collection.isEscaped());
    assertFalse(collection.isNeverAssigned());
  }

  @Test
  public void testReferenceCloneWithNewScope() throws Throwable {
    CompilerInput input = new CompilerInput(new JSSourceFile("testfile", "var x;"));
    ReferenceCollectingCallback.Reference ref = ReferenceCollectingCallback.Reference.createRefForTest(input);
    
    Scope newScope = new Scope(new Node(Token.BLOCK), (Compiler) null);
    ReferenceCollectingCallback.Reference cloned = ref.cloneWithNewScope(newScope);
    assertNotNull(cloned);
    assertEquals(newScope, cloned.getScope());
    assertEquals(ref.getNode(), cloned.getNode());
    assertEquals(ref.getInputId(), cloned.getInputId());
  }

  @Test
  public void testReferenceBasicGetters() throws Throwable {
    CompilerInput input = new CompilerInput(new JSSourceFile("testfile", "var x;"));
    ReferenceCollectingCallback.Reference ref = ReferenceCollectingCallback.Reference.createRefForTest(input);
    
    assertNotNull(ref.getNode());
    assertNotNull(ref.getInputId());
    assertNull(ref.getBasicBlock());
    assertNull(ref.getSourceFile());
  }

  @Test
  public void testCallbackConstructorsAndSymbols() throws Throwable {
    Compiler compiler = new Compiler();
    ReferenceCollectingCallback callback = new ReferenceCollectingCallback(
        compiler,
        ReferenceCollectingCallback.DO_NOTHING_BEHAVIOR
    );

    Iterable<Var> symbols = callback.getAllSymbols();
    assertNotNull(symbols);
    
    Iterator<Var> it = symbols.iterator();
    assertFalse(it.hasNext());
  }

  @Test
  public void testCallbackWithFilter() throws Throwable {
    Compiler compiler = new Compiler();
    Predicate<Var> filter = Predicates.alwaysTrue();
    ReferenceCollectingCallback callback = new ReferenceCollectingCallback(
        compiler,
        ReferenceCollectingCallback.DO_NOTHING_BEHAVIOR,
        filter
    );

    assertNotNull(callback);
    assertNull(callback.getReferences(null));
  }
}