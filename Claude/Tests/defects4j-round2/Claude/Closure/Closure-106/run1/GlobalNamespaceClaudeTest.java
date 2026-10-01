package com.google.javascript.jscomp;

import static org.junit.Assert.*;
import org.junit.Test;

import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;

import java.util.List;

public class GlobalNamespaceClaudeTest {

  // Name.addProperty: creates a child with correct name and parent link
  @Test
  public void testAddProperty_createsChildWithCorrectParentAndName() throws Throwable {
    GlobalNamespace.Name root = new GlobalNamespace.Name("a", null, false);
    GlobalNamespace.Name child = root.addProperty("b", false);
    assertEquals("b", child.name);
    assertSame(root, child.parent);
  }

  // Name.addProperty: props list grows with multiple children
  @Test
  public void testAddProperty_multipleChildren_propsListGrows() throws Throwable {
    GlobalNamespace.Name root = new GlobalNamespace.Name("a", null, false);
    root.addProperty("b", false);
    root.addProperty("c", false);
    assertEquals(2, root.props.size());
  }

  // Name.fullName: simple top-level name returns itself
  @Test
  public void testFullName_simpleName_returnsNameItself() throws Throwable {
    GlobalNamespace.Name root = new GlobalNamespace.Name("a", null, false);
    assertEquals("a", root.fullName());
  }

  // Name.fullName: nested names are dot-joined
  @Test
  public void testFullName_nestedName_returnsDotSeparatedPath() throws Throwable {
    GlobalNamespace.Name root = new GlobalNamespace.Name("a", null, false);
    GlobalNamespace.Name child = root.addProperty("b", false);
    GlobalNamespace.Name grand = child.addProperty("c", false);
    assertEquals("a.b.c", grand.fullName());
  }

  // Name.isSimpleName: true when parent is null
  @Test
  public void testIsSimpleName_rootName_true() throws Throwable {
    GlobalNamespace.Name root = new GlobalNamespace.Name("a", null, false);
    assertTrue(root.isSimpleName());
  }

  // Name.isSimpleName: false when it has a parent
  @Test
  public void testIsSimpleName_childName_false() throws Throwable {
    GlobalNamespace.Name root = new GlobalNamespace.Name("a", null, false);
    GlobalNamespace.Name child = root.addProperty("b", false);
    assertFalse(child.isSimpleName());
  }

  // Name.addRef: SET_FROM_GLOBAL becomes declaration and bumps globalSets
  @Test
  public void testAddRef_setFromGlobal_setsDeclarationAndIncrementsGlobalSets() throws Throwable {
    GlobalNamespace.Name n = new GlobalNamespace.Name("a", null, false);
    GlobalNamespace.Ref ref = GlobalNamespace.Ref.createRefForTesting(GlobalNamespace.Ref.Type.SET_FROM_GLOBAL);
    n.addRef(ref);
    assertSame(ref, n.declaration);
    assertEquals(1, n.globalSets);
  }

  // Name.addRef: SET_FROM_LOCAL increments localSets and stores the ref
  @Test
  public void testAddRef_setFromLocal_incrementsLocalSetsAndAddsToRefs() throws Throwable {
    GlobalNamespace.Name n = new GlobalNamespace.Name("a", null, false);
    GlobalNamespace.Ref ref = GlobalNamespace.Ref.createRefForTesting(GlobalNamespace.Ref.Type.SET_FROM_LOCAL);
    n.addRef(ref);
    assertEquals(1, n.localSets);
    assertTrue(n.refs.contains(ref));
  }

  // Name.addRef: DIRECT_GET bumps totalGets only
  @Test
  public void testAddRef_directGet_incrementsTotalGets() throws Throwable {
    GlobalNamespace.Name n = new GlobalNamespace.Name("a", null, false);
    n.addRef(GlobalNamespace.Ref.createRefForTesting(GlobalNamespace.Ref.Type.DIRECT_GET));
    assertEquals(1, n.totalGets);
    assertEquals(0, n.aliasingGets);
  }

  // Name.addRef: ALIASING_GET bumps both aliasingGets and totalGets
  @Test
  public void testAddRef_aliasingGet_incrementsAliasingAndTotalGets() throws Throwable {
    GlobalNamespace.Name n = new GlobalNamespace.Name("a", null, false);
    n.addRef(GlobalNamespace.Ref.createRefForTesting(GlobalNamespace.Ref.Type.ALIASING_GET));
    assertEquals(1, n.totalGets);
    assertEquals(1, n.aliasingGets);
  }

  // Name.addRef: CALL_GET bumps callGets and totalGets
  @Test
  public void testAddRef_callGet_incrementsCallAndTotalGets() throws Throwable {
    GlobalNamespace.Name n = new GlobalNamespace.Name("a", null, false);
    n.addRef(GlobalNamespace.Ref.createRefForTesting(GlobalNamespace.Ref.Type.CALL_GET));
    assertEquals(1, n.totalGets);
    assertEquals(1, n.callGets);
  }

  // Name.addRef: PROTOTYPE_GET bumps totalGets but not aliasing/call
  @Test
  public void testAddRef_prototypeGet_incrementsTotalGetsOnly() throws Throwable {
    GlobalNamespace.Name n = new GlobalNamespace.Name("a", null, false);
    n.addRef(GlobalNamespace.Ref.createRefForTesting(GlobalNamespace.Ref.Type.PROTOTYPE_GET));
    assertEquals(1, n.totalGets);
    assertEquals(0, n.aliasingGets);
    assertEquals(0, n.callGets);
  }

  // Name.addRef: second SET_FROM_GLOBAL is stored as ref, not new declaration
  @Test
  public void testAddRef_secondGlobalSet_addedToRefsNotDeclaration() throws Throwable {
    GlobalNamespace.Name n = new GlobalNamespace.Name("a", null, false);
    GlobalNamespace.Ref first = GlobalNamespace.Ref.createRefForTesting(GlobalNamespace.Ref.Type.SET_FROM_GLOBAL);
    GlobalNamespace.Ref second = GlobalNamespace.Ref.createRefForTesting(GlobalNamespace.Ref.Type.SET_FROM_GLOBAL);
    n.addRef(first);
    n.addRef(second);
    assertSame(first, n.declaration);
    assertEquals(2, n.globalSets);
    assertTrue(n.refs.contains(second));
  }

  // Name.removeRef: removing the declaration promotes another global set ref
  @Test
  public void testRemoveRef_declarationWithOtherGlobalSet_promotesNewDeclaration() throws Throwable {
    GlobalNamespace.Name n = new GlobalNamespace.Name("a", null, false);
    GlobalNamespace.Ref first = GlobalNamespace.Ref.createRefForTesting(GlobalNamespace.Ref.Type.SET_FROM_GLOBAL);
    GlobalNamespace.Ref second = GlobalNamespace.Ref.createRefForTesting(GlobalNamespace.Ref.Type.SET_FROM_GLOBAL);
    n.addRef(first);
    n.addRef(second);
    n.removeRef(first);
    assertSame(second, n.declaration);
    assertEquals(1, n.globalSets);
  }

  // Name.needsToBeStubbed: true when only local sets exist
  @Test
  public void testNeedsToBeStubbed_onlyLocalSets_true() throws Throwable {
    GlobalNamespace.Name n = new GlobalNamespace.Name("a", null, false);
    n.addRef(GlobalNamespace.Ref.createRefForTesting(GlobalNamespace.Ref.Type.SET_FROM_LOCAL));
    assertTrue(n.needsToBeStubbed());
  }

  // Name.needsToBeStubbed: false once a global set exists
  @Test
  public void testNeedsToBeStubbed_withGlobalSet_false() throws Throwable {
    GlobalNamespace.Name n = new GlobalNamespace.Name("a", null, false);
    n.addRef(GlobalNamespace.Ref.createRefForTesting(GlobalNamespace.Ref.Type.SET_FROM_LOCAL));
    n.addRef(GlobalNamespace.Ref.createRefForTesting(GlobalNamespace.Ref.Type.SET_FROM_GLOBAL));
    assertFalse(n.needsToBeStubbed());
  }

  // Name.setIsClassOrEnum propagates hasClassOrEnumDescendant up to ancestors (visible via isNamespace)
  @Test
  public void testSetIsClassOrEnum_propagatesToAncestors_viaIsNamespace() throws Throwable {
    GlobalNamespace.Name root = new GlobalNamespace.Name("a", null, false);
    root.type = GlobalNamespace.Name.Type.OBJECTLIT;
    GlobalNamespace.Name child = root.addProperty("b", false);
    child.setIsClassOrEnum();
    assertTrue(root.isNamespace());
  }

  // Name.isNamespace: false when type is not OBJECTLIT even with a class/enum descendant
  @Test
  public void testIsNamespace_wrongType_false() throws Throwable {
    GlobalNamespace.Name root = new GlobalNamespace.Name("a", null, false);
    root.type = GlobalNamespace.Name.Type.FUNCTION;
    GlobalNamespace.Name child = root.addProperty("b", false);
    child.setIsClassOrEnum();
    assertFalse(root.isNamespace());
  }

  // Name.canEliminate: false when canCollapseUnannotatedChildNames is false (type OTHER)
  @Test
  public void testCanEliminate_otherType_false() throws Throwable {
    GlobalNamespace.Name n = new GlobalNamespace.Name("a", null, false);
    n.addRef(GlobalNamespace.Ref.createRefForTesting(GlobalNamespace.Ref.Type.SET_FROM_GLOBAL));
    assertFalse(n.canEliminate());
  }

  // Name.canEliminate: true for a function with a single global set and no gets
  @Test
  public void testCanEliminate_functionNoGets_true() throws Throwable {
    GlobalNamespace.Name n = new GlobalNamespace.Name("a", null, false);
    n.type = GlobalNamespace.Name.Type.FUNCTION;
    n.addRef(GlobalNamespace.Ref.createRefForTesting(GlobalNamespace.Ref.Type.SET_FROM_GLOBAL));
    assertTrue(n.canEliminate());
  }

  // Name.canEliminate: false once a direct get exists (totalGets > 0)
  @Test
  public void testCanEliminate_withDirectGet_false() throws Throwable {
    GlobalNamespace.Name n = new GlobalNamespace.Name("a", null, false);
    n.type = GlobalNamespace.Name.Type.FUNCTION;
    n.addRef(GlobalNamespace.Ref.createRefForTesting(GlobalNamespace.Ref.Type.SET_FROM_GLOBAL));
    n.addRef(GlobalNamespace.Ref.createRefForTesting(GlobalNamespace.Ref.Type.DIRECT_GET));
    assertFalse(n.canEliminate());
  }

  // Name.canCollapseUnannotatedChildNames: false when type is OTHER, regardless of sets
  @Test
  public void testCanCollapseUnannotatedChildNames_typeOther_false() throws Throwable {
    GlobalNamespace.Name n = new GlobalNamespace.Name("a", null, false);
    n.addRef(GlobalNamespace.Ref.createRefForTesting(GlobalNamespace.Ref.Type.SET_FROM_GLOBAL));
    assertFalse(n.canCollapseUnannotatedChildNames());
  }

  // Name.canCollapseUnannotatedChildNames: false when globalSets != 1 (zero sets)
  @Test
  public void testCanCollapseUnannotatedChildNames_globalSetsZero_false() throws Throwable {
    GlobalNamespace.Name n = new GlobalNamespace.Name("a", null, false);
    n.type = GlobalNamespace.Name.Type.OBJECTLIT;
    assertFalse(n.canCollapseUnannotatedChildNames());
  }

  // Name.canCollapseUnannotatedChildNames: false when localSets != 0
  @Test
  public void testCanCollapseUnannotatedChildNames_localSetsNonZero_false() throws Throwable {
    GlobalNamespace.Name n = new GlobalNamespace.Name("a", null, false);
    n.type = GlobalNamespace.Name.Type.OBJECTLIT;
    n.addRef(GlobalNamespace.Ref.createRefForTesting(GlobalNamespace.Ref.Type.SET_FROM_GLOBAL));
    n.addRef(GlobalNamespace.Ref.createRefForTesting(GlobalNamespace.Ref.Type.SET_FROM_LOCAL));
    assertFalse(n.canCollapseUnannotatedChildNames());
  }

  // Name.canCollapseUnannotatedChildNames: true when marked as class/enum
  @Test
  public void testCanCollapseUnannotatedChildNames_classOrEnum_true() throws Throwable {
    GlobalNamespace.Name n = new GlobalNamespace.Name("a", null, false);
    n.type = GlobalNamespace.Name.Type.OBJECTLIT;
    n.addRef(GlobalNamespace.Ref.createRefForTesting(GlobalNamespace.Ref.Type.SET_FROM_GLOBAL));
    n.setIsClassOrEnum();
    assertTrue(n.canCollapseUnannotatedChildNames());
  }

  // Name.canCollapseUnannotatedChildNames: OBJECTLIT type with an aliasing get blocks collapse
  @Test
  public void testCanCollapseUnannotatedChildNames_objectLitWithAliasingGet_false() throws Throwable {
    GlobalNamespace.Name n = new GlobalNamespace.Name("a", null, false);
    n.type = GlobalNamespace.Name.Type.OBJECTLIT;
    n.addRef(GlobalNamespace.Ref.createRefForTesting(GlobalNamespace.Ref.Type.SET_FROM_GLOBAL));
    n.addRef(GlobalNamespace.Ref.createRefForTesting(GlobalNamespace.Ref.Type.ALIASING_GET));
    assertFalse(n.canCollapseUnannotatedChildNames());
  }

  // Bug-revealing: per the class comment, a lone global set that is a twin reference
  // must NOT allow collapsing, even for FUNCTION-typed names.
  @Test
  public void testCanCollapseUnannotatedChildNames_functionWithTwinAliasingGet_shouldNotCollapse() throws Throwable {
    GlobalNamespace.Name n = new GlobalNamespace.Name("b", null, false);
    n.type = GlobalNamespace.Name.Type.FUNCTION;
    GlobalNamespace.Ref setRef = GlobalNamespace.Ref.createRefForTesting(GlobalNamespace.Ref.Type.SET_FROM_GLOBAL);
    GlobalNamespace.Ref getRef = GlobalNamespace.Ref.createRefForTesting(GlobalNamespace.Ref.Type.ALIASING_GET);
    GlobalNamespace.Ref.markTwins(setRef, getRef);
    n.addRef(setRef);
    n.addRef(getRef);
    assertFalse(n.canCollapseUnannotatedChildNames());
  }

  // Name.canCollapseUnannotatedChildNames: a non-collapsible parent blocks the child
  @Test
  public void testCanCollapseUnannotatedChildNames_parentBlocks_false() throws Throwable {
    GlobalNamespace.Name parent = new GlobalNamespace.Name("a", null, false);
    parent.type = GlobalNamespace.Name.Type.OTHER;
    GlobalNamespace.Name child = parent.addProperty("b", false);
    child.type = GlobalNamespace.Name.Type.OBJECTLIT;
    child.addRef(GlobalNamespace.Ref.createRefForTesting(GlobalNamespace.Ref.Type.SET_FROM_GLOBAL));
    assertFalse(child.canCollapseUnannotatedChildNames());
  }

  // Name.canCollapse: externs names never collapse
  @Test
  public void testCanCollapse_inExterns_false() throws Throwable {
    GlobalNamespace.Name n = new GlobalNamespace.Name("a", null, true);
    n.addRef(GlobalNamespace.Ref.createRefForTesting(GlobalNamespace.Ref.Type.SET_FROM_GLOBAL));
    assertFalse(n.canCollapse());
  }

  // Name.canCollapse: class/enum names collapse even without any sets
  @Test
  public void testCanCollapse_classOrEnumNoSets_true() throws Throwable {
    GlobalNamespace.Name n = new GlobalNamespace.Name("a", null, false);
    n.setIsClassOrEnum();
    assertTrue(n.canCollapse());
  }

  // Name.canCollapse: false without any sets and not class/enum
  @Test
  public void testCanCollapse_noSetsNoClassOrEnum_false() throws Throwable {
    GlobalNamespace.Name n = new GlobalNamespace.Name("a", null, false);
    assertFalse(n.canCollapse());
  }

  // Name.canCollapse: true for top-level name with a global set
  @Test
  public void testCanCollapse_withGlobalSetAndNoParent_true() throws Throwable {
    GlobalNamespace.Name n = new GlobalNamespace.Name("a", null, false);
    n.addRef(GlobalNamespace.Ref.createRefForTesting(GlobalNamespace.Ref.Type.SET_FROM_GLOBAL));
    assertTrue(n.canCollapse());
  }

  // Name.toString: contains the reported counters
  @Test
  public void testToString_containsExpectedCounts() throws Throwable {
    GlobalNamespace.Name n = new GlobalNamespace.Name("a", null, false);
    n.addRef(GlobalNamespace.Ref.createRefForTesting(GlobalNamespace.Ref.Type.SET_FROM_GLOBAL));
    n.addRef(GlobalNamespace.Ref.createRefForTesting(GlobalNamespace.Ref.Type.DIRECT_GET));
    String s = n.toString();
    assertTrue(s.contains("globalSets=1"));
    assertTrue(s.contains("totalGets=1"));
  }

  // Ref.isSet: true for SET_FROM_GLOBAL
  @Test
  public void testRefIsSet_setFromGlobal_true() throws Throwable {
    GlobalNamespace.Ref ref = GlobalNamespace.Ref.createRefForTesting(GlobalNamespace.Ref.Type.SET_FROM_GLOBAL);
    assertTrue(ref.isSet());
  }

  // Ref.isSet: false for DIRECT_GET
  @Test
  public void testRefIsSet_directGet_false() throws Throwable {
    GlobalNamespace.Ref ref = GlobalNamespace.Ref.createRefForTesting(GlobalNamespace.Ref.Type.DIRECT_GET);
    assertFalse(ref.isSet());
  }

  // Ref.markTwins: valid pair (set + aliasing get) links twins both ways
  @Test
  public void testMarkTwins_validPair_setsTwinOnBoth() throws Throwable {
    GlobalNamespace.Ref a = GlobalNamespace.Ref.createRefForTesting(GlobalNamespace.Ref.Type.SET_FROM_GLOBAL);
    GlobalNamespace.Ref b = GlobalNamespace.Ref.createRefForTesting(GlobalNamespace.Ref.Type.ALIASING_GET);
    GlobalNamespace.Ref.markTwins(a, b);
    assertSame(b, a.getTwin());
    assertSame(a, b.getTwin());
  }

  // Ref.markTwins: invalid pair (neither is a set+alias combo) throws IllegalArgumentException
  @Test
  public void testMarkTwins_invalidPair_throwsIllegalArgumentException() throws Throwable {
    GlobalNamespace.Ref a = GlobalNamespace.Ref.createRefForTesting(GlobalNamespace.Ref.Type.DIRECT_GET);
    GlobalNamespace.Ref b = GlobalNamespace.Ref.createRefForTesting(GlobalNamespace.Ref.Type.CALL_GET);
    try {
      GlobalNamespace.Ref.markTwins(a, b);
      fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
    }
  }

  // Ref.cloneAndReclassify: new ref has the requested type but keeps sourceName
  @Test
  public void testCloneAndReclassify_changesTypeKeepsOtherFields() throws Throwable {
    GlobalNamespace.Ref original = GlobalNamespace.Ref.createRefForTesting(GlobalNamespace.Ref.Type.SET_FROM_GLOBAL);
    GlobalNamespace.Ref clone = original.cloneAndReclassify(GlobalNamespace.Ref.Type.DIRECT_GET);
    assertEquals(GlobalNamespace.Ref.Type.DIRECT_GET, clone.type);
    assertEquals(original.sourceName, clone.sourceName);
  }

  // Ref.getTwin: null when no twin was ever assigned
  @Test
  public void testGetTwin_noTwinSet_returnsNull() throws Throwable {
    GlobalNamespace.Ref ref = GlobalNamespace.Ref.createRefForTesting(GlobalNamespace.Ref.Type.DIRECT_GET);
    assertNull(ref.getTwin());
  }

  // GlobalNamespace two-arg constructor: empty root yields empty forest and index
  @Test
  public void testTwoArgConstructor_emptyRoot_emptyForestAndIndex() throws Throwable {
    Compiler compiler = new Compiler();
    Node root = IR.block();
    GlobalNamespace ns = new GlobalNamespace(compiler, root);
    assertTrue(ns.getNameForest().isEmpty());
    assertTrue(ns.getNameIndex().isEmpty());
  }

  // GlobalNamespace three-arg constructor with null externsRoot behaves like two-arg
  @Test
  public void testThreeArgConstructor_nullExterns_sameAsTwoArg() throws Throwable {
    Compiler compiler = new Compiler();
    Node root = IR.block();
    GlobalNamespace ns = new GlobalNamespace(compiler, null, root);
    assertTrue(ns.getNameForest().isEmpty());
  }

  // GlobalNamespace.getNameForest: process() runs only once (generated flag caching)
  @Test
  public void testGetNameForest_calledTwice_consistentResults() throws Throwable {
    Compiler compiler = new Compiler();
    Node root = IR.block();
    GlobalNamespace ns = new GlobalNamespace(compiler, root);
    List<GlobalNamespace.Name> first = ns.getNameForest();
    List<GlobalNamespace.Name> second = ns.getNameForest();
    assertSame(first, second);
  }

  // GlobalNamespace three-arg constructor with a non-null but empty externsRoot
  @Test
  public void testThreeArgConstructor_nonNullEmptyExterns_emptyForest() throws Throwable {
    Compiler compiler = new Compiler();
    Node externs = IR.block();
    Node root = IR.block();
    GlobalNamespace ns = new GlobalNamespace(compiler, externs, root);
    assertTrue(ns.getNameIndex().isEmpty());
  }
}
