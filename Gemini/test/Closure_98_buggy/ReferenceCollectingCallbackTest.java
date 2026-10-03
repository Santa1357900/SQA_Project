package com.google.javascript.jscomp;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import com.google.javascript.jscomp.Scope.Var;
import com.google.javascript.rhino.Node;
import com.google.javascript.rhino.Token;
import junit.framework.TestCase;

import java.util.Map;

public class ReferenceCollectingCallbackTest extends TestCase {

    public void testReferenceCollectionBasics() throws Throwable {
        ReferenceCollectingCallback.ReferenceCollection collection = new ReferenceCollectingCallback.ReferenceCollection();
        assertFalse(collection.isWellDefined());
        assertFalse(collection.isEscaped());
        assertNull(collection.getInitializingReference());
        assertNull(collection.getInitializingReferenceForConstants());
        assertFalse(collection.isAssignedOnceInLifetime());
        assertTrue(collection.isNeverAssigned());
        assertFalse(collection.firstReferenceIsAssigningDeclaration());
    }

    public void testBasicBlockProvablyExecutesBefore() throws Throwable {
        Node root = new Node(Token.BLOCK);
        ReferenceCollectingCallback.BasicBlock parentBlock = new ReferenceCollectingCallback.BasicBlock(null, root);
        ReferenceCollectingCallback.BasicBlock childBlock = new ReferenceCollectingCallback.BasicBlock(parentBlock, root);

        assertTrue(parentBlock.provablyExecutesBefore(childBlock));
        assertTrue(parentBlock.provablyExecutesBefore(parentBlock));
        assertFalse(childBlock.provablyExecutesBefore(parentBlock));
        assertSame(parentBlock, childBlock.getParent());
    }

    public void testBehaviorCallback() throws Throwable {
        ReferenceCollectingCallback.Behavior behavior = ReferenceCollectingCallback.DO_NOTHING_BEHAVIOR;
        assertNotNull(behavior);
    }

    public void testReferenceCollectingCallbackConstruction() throws Throwable {
        Compiler compiler = new Compiler();
        ReferenceCollectingCallback.Behavior behavior = ReferenceCollectingCallback.DO_NOTHING_BEHAVIOR;
        
        ReferenceCollectingCallback callback1 = new ReferenceCollectingCallback(compiler, behavior);
        assertNotNull(callback1);

        ReferenceCollectingCallback callback2 = new ReferenceCollectingCallback(compiler, behavior, Predicates.<Var>alwaysTrue());
        assertNotNull(callback2);
    }
}