package com.google.javascript.jscomp;

import static org.junit.Assert.*;

import org.junit.Test;

import com.google.javascript.jscomp.GlobalNamespace.Name;
import com.google.javascript.jscomp.GlobalNamespace.Ref;
import com.google.javascript.rhino.IR;
import com.google.javascript.rhino.Node;

import java.util.ArrayList;
import java.util.List;

public class GlobalNamespaceClaudeTest {

  /** Parses the given JS source and builds a GlobalNamespace over it. */
  private GlobalNamespace parse(String js) throws Throwable {
    Compiler compiler = new Compiler();
    CompilerOptions options = new CompilerOptions();
    List<SourceFile> externs = new ArrayList<SourceFile>();
    List<SourceFile> inputs = new ArrayList<SourceFile>();
    inputs.add(SourceFile.fromCode("test.js", js));
    compiler.compile(externs, inputs, options);
    Node root = compiler.getRoot();
    return new GlobalNamespace(compiler, root);
  }

  // Covers hasExternsRoot(): single-arg constructor delegates with null externsRoot
  @Test
  public void testHasExternsRoot_singleArgConstructor_false() throws Throwable {
    GlobalNamespace ns = new GlobalNamespace(new Compiler(), IR.block());
    assertFalse(ns.hasExternsRoot());
  }

  // Covers hasExternsRoot(): explicit null externsRoot in 3-arg constructor
  @Test
  public void testHasExternsRoot_twoArgConstructorNullExterns_false() throws Throwable {
    GlobalNamespace ns = new GlobalNamespace(new Compiler(), null, IR.block());
    assertFalse(ns.hasExternsRoot());
  }

  // Covers hasExternsRoot(): non-null externsRoot
  @Test
  public void testHasExternsRoot_threeArgConstructorNonNullExterns_true() throws Throwable {
    GlobalNamespace ns = new GlobalNamespace(new Compiler(), IR.block(), IR.block());
    assertTrue(ns.hasExternsRoot());
  }

  // Covers getParentScope(): always returns null
  @Test
  public void testGetParentScope_alwaysNull() throws Throwable {
    GlobalNamespace ns = new GlobalNamespace(new Compiler(), IR.block());
    assertNull(ns.getParentScope());
  }

  // Covers getRootNode(): root.getParent(); compiler.getRoot() has no parent
  @Test
  public void testGetRootNode_topRootHasNoParent() throws Throwable {
    GlobalNamespace ns = parse("var a = 1;");
    assertNull(ns.getRootNode());
  }

  // Covers collect() Token.NAME/VAR branch: simple var with non-objectlit/function value -> OTHER
  @Test
  public void testGetOwnSlot_simpleVarDeclaration_typeOther() throws Throwable {
    GlobalNamespace ns = parse("var a = 1;");
    Name nameA = ns.getOwnSlot("a");
    assertNotNull(nameA);
    assertEquals(Name.Type.OTHER, nameA.type);
    assertEquals(1, nameA.globalSets);
  }

  // Covers getValueType(): OBJECTLIT branch via var initializer
  @Test
  public void testGetOwnSlot_objectLiteralVar_typeObjectLit() throws Throwable {
    GlobalNamespace ns = parse("var a = {};");
    Name nameA = ns.getOwnSlot("a");
    assertEquals(Name.Type.OBJECTLIT, nameA.type);
  }

  // Covers collect() Token.NAME/FUNCTION branch: top-level function declaration -> FUNCTION
  @Test
  public void testGetOwnSlot_functionDeclaration_typeFunction() throws Throwable {
    GlobalNamespace ns = parse("function f(){}");
    Name nameF = ns.getOwnSlot("f");
    assertNotNull(nameF);
    assertEquals(Name.Type.FUNCTION, nameF.type);
    assertEquals(1, nameF.globalSets);
  }

  // Covers collect() Token.NAME/FUNCTION branch: named function expression is skipped entirely
  @Test
  public void testGetOwnSlot_namedFunctionExpression_notRegistered() throws Throwable {
    GlobalNamespace ns = parse("var g = function f() { return f; };");
    assertNull(ns.getOwnSlot("f"));
    assertEquals(Name.Type.FUNCTION, ns.getOwnSlot("g").type);
  }

  // Covers isGlobalVarReference(): local var is excluded (v.isLocal() true)
  @Test
  public void testGetOwnSlot_localVariable_notRegistered() throws Throwable {
    GlobalNamespace ns = parse("function f() { var local = 1; }");
    assertNull(ns.getOwnSlot("local"));
  }

  // Covers getOrCreateName(): nested property assignment creates qualified name
  @Test
  public void testGetOwnSlot_propertyAssignment_createsNestedName() throws Throwable {
    GlobalNamespace ns = parse("var a = {}; a.b = 2;");
    Name nameAB = ns.getOwnSlot("a.b");
    assertNotNull(nameAB);
    assertEquals(1, nameAB.globalSets);
    assertEquals(Name.Type.OTHER, nameAB.type);
  }

  // Covers getNameForest(): only top-level (root) names are included
  @Test
  public void testGetNameForest_containsOnlyTopLevelNames() throws Throwable {
    GlobalNamespace ns = parse("var a = {}; var b = 1; a.c = 2;");
    List<Name> forest = ns.getNameForest();
    assertEquals(2, forest.size());
  }

  // Covers getNameIndex(): indexes every qualified name, nested or not
  @Test
  public void testGetNameIndex_containsQualifiedPropertyNames() throws Throwable {
    GlobalNamespace ns = parse("var a = {}; a.b = 1;");
    assertTrue(ns.getNameIndex().containsKey("a"));
    assertTrue(ns.getNameIndex().containsKey("a.b"));
    assertEquals(2, ns.getNameIndex().size());
  }

  // Covers getAllSymbols(): returns every registered Name
  @Test
  public void testGetAllSymbols_returnsAllRegisteredNames() throws Throwable {
    GlobalNamespace ns = parse("var a = {}; a.b = 1;");
    int count = 0;
    boolean hasA = false;
    boolean hasAB = false;
    for (Name n : ns.getAllSymbols()) {
      count++;
      if (n.getFullName().equals("a")) hasA = true;
      if (n.getFullName().equals("a.b")) hasAB = true;
    }
    assertEquals(2, count);
    assertTrue(hasA);
    assertTrue(hasAB);
  }

  // Covers getReferences(): unmodifiable view over the slot's refs
  @Test
  public void testGetReferences_returnsUnmodifiableListOfRefs() throws Throwable {
    GlobalNamespace ns = parse("var a = 1;");
    Name nameA = ns.getOwnSlot("a");
    List<Ref> refs = (List<Ref>) ns.getReferences(nameA);
    assertEquals(1, refs.size());
    try {
      refs.remove(0);
      fail("expected UnsupportedOperationException");
    } catch (UnsupportedOperationException expected) {
    }
  }

  // Covers getScope(): always returns the GlobalNamespace instance itself
  @Test
  public void testGetScope_returnsNamespaceItself() throws Throwable {
    GlobalNamespace ns = parse("var a = 1;");
    Name nameA = ns.getOwnSlot("a");
    assertSame(ns, ns.getScope(nameA));
  }

  // Covers getSlot(): delegates to getOwnSlot(); unknown names return null
  @Test
  public void testGetSlot_delegatesToGetOwnSlot_andUnknownReturnsNull() throws Throwable {
    GlobalNamespace ns = parse("var a = 1;");
    assertSame(ns.getOwnSlot("a"), ns.getSlot("a"));
    assertNull(ns.getSlot("doesNotExistXYZ"));
  }

  // Covers maybeHandlePrototypePrefix(): ".prototype" suffix and ".prototype." infix are not
  // registered as qualified names; a PROTOTYPE_GET ref is added on the base name instead.
  @Test
  public void testPrototypePrefixHandling_notRegisteredDirectly() throws Throwable {
    GlobalNamespace ns1 = parse("var a = function(){}; a.prototype = {};");
    assertNull(ns1.getOwnSlot("a.prototype"));
    assertNotNull(ns1.getOwnSlot("a"));

    GlobalNamespace ns2 = parse("var a = function(){}; a.prototype.b = 1;");
    assertNull(ns2.getOwnSlot("a.prototype.b"));
    assertNull(ns2.getOwnSlot("a.prototype"));
    assertNotNull(ns2.getOwnSlot("a"));
  }

  // Covers isNestedAssign(): outer assignment (discarded by ExprResult) is not nested,
  // inner assignment (whose result is used) is nested and creates a twinned ALIASING_GET
  @Test
  public void testIsNestedAssign_outerFalseInnerTrue() throws Throwable {
    GlobalNamespace ns = parse("var a; var b; a = b = 1;");
    Name nameA = ns.getOwnSlot("a");
    Name nameB = ns.getOwnSlot("b");
    assertEquals(1, nameA.globalSets);
    assertEquals(0, nameA.aliasingGets);
    assertEquals(1, nameB.globalSets);
    assertEquals(1, nameB.aliasingGets);
  }

  // Covers handleGet(): CALL branch where the name is the call target -> CALL_GET
  @Test
  public void testHandleGet_callTarget_setsCallGetType() throws Throwable {
    GlobalNamespace ns = parse("var a = {}; a.foo();");
    Name nameFoo = ns.getOwnSlot("a.foo");
    assertNotNull(nameFoo);
    assertEquals(1, nameFoo.callGets);
    assertEquals(1, nameFoo.totalGets);
    assertEquals(0, nameFoo.globalSets);
  }

  // Covers handleGet(): CALL branch where the name is a plain argument -> ALIASING_GET
  @Test
  public void testHandleGet_functionArgument_setsAliasingGetType() throws Throwable {
    GlobalNamespace ns = parse("var a = {}; var foo = function(x){}; foo(a);");
    Name nameA = ns.getOwnSlot("a");
    assertEquals(1, nameA.aliasingGets);
    assertEquals(1, nameA.totalGets);
  }

  // Covers handleGet(): TYPEOF branch keeps the default DIRECT_GET type
  @Test
  public void testHandleGet_typeofOperand_setsDirectGetType() throws Throwable {
    GlobalNamespace ns = parse("var a = 1; var t = typeof a;");
    Name nameA = ns.getOwnSlot("a");
    assertEquals(1, nameA.totalGets);
    assertEquals(0, nameA.aliasingGets);
  }

  // Covers handleGet(): DELPROP branch -> DELETE_PROP, which also blocks canCollapse()
  @Test
  public void testHandleGet_deleteStatement_incrementsDeletePropsAndBlocksCollapse() throws Throwable {
    GlobalNamespace ns = parse("var a = {}; a.b = 1; delete a.b;");
    Name nameAB = ns.getOwnSlot("a.b");
    assertEquals(1, nameAB.deleteProps);
    assertFalse(nameAB.canCollapse());
  }

  // Covers getValueType(): OR branch recurses into the last child
  @Test
  public void testGetValueType_orExpression_usesLastChildType() throws Throwable {
    GlobalNamespace ns = parse("var b = {}; var a = b || {};");
    assertEquals(Name.Type.OBJECTLIT, ns.getOwnSlot("a").type);
  }

  // Covers getValueType(): HOOK branch, both the "second operand valid" and
  // "falls back to third operand" paths
  @Test
  public void testGetValueType_hookExpression_branches() throws Throwable {
    GlobalNamespace ns1 = parse("var a = true ? {} : null;");
    assertEquals(Name.Type.OBJECTLIT, ns1.getOwnSlot("a").type);

    GlobalNamespace ns2 = parse("var a = true ? 1 : {};");
    assertEquals(Name.Type.OBJECTLIT, ns2.getOwnSlot("a").type);
  }

  // Covers isTypeDeclaration(): @constructor JSDoc forces declaredType and canCollapse()
  @Test
  public void testIsTypeDeclaration_constructorJSDoc_setsDeclaredType() throws Throwable {
    GlobalNamespace ns = parse("/** @constructor */ var Foo = function() {};");
    Name nameFoo = ns.getOwnSlot("Foo");
    assertTrue(nameFoo.isDeclaredType());
    assertTrue(nameFoo.canCollapse());
  }

  // Covers setDeclaredType(): propagates hasDeclaredTypeDescendant to ancestors -> isNamespace()
  @Test
  public void testIsNamespace_propagatesDeclaredTypeToAncestor() throws Throwable {
    GlobalNamespace ns = parse("var ns = {}; /** @constructor */ ns.Foo = function() {};");
    assertTrue(ns.getOwnSlot("ns").isNamespace());
  }

  // Covers canEliminate(): OTHER type blocks elimination; OBJECTLIT with no gets allows it
  @Test
  public void testCanEliminate_branches() throws Throwable {
    GlobalNamespace ns1 = parse("var a = 1;");
    assertFalse(ns1.getOwnSlot("a").canEliminate());

    GlobalNamespace ns2 = parse("var a = {};");
    assertTrue(ns2.getOwnSlot("a").canEliminate());
  }

  // Covers needsToBeStubbed(): globalSets == 0 and localSets > 0
  @Test
  public void testNeedsToBeStubbed_onlyLocalSet_true() throws Throwable {
    GlobalNamespace ns = parse("var a = {}; function f() { a.b = 1; }");
    Name nameAB = ns.getOwnSlot("a.b");
    assertEquals(0, nameAB.globalSets);
    assertEquals(1, nameAB.localSets);
    assertTrue(nameAB.needsToBeStubbed());
  }

  // Covers shouldKeepKeys(): OBJECTLIT aliased elsewhere keeps its keys and blocks child collapse
  @Test
  public void testShouldKeepKeys_aliasedObjectLiteral_true() throws Throwable {
    GlobalNamespace ns = parse("var a = {b: 1}; var c = a;");
    Name nameA = ns.getOwnSlot("a");
    assertTrue(nameA.shouldKeepKeys());
    assertFalse(ns.getOwnSlot("a.b").canCollapse());
  }

  // Covers isSimpleStubDeclaration(): true with exactly one EXPR_RESULT ref, false with more
  @Test
  public void testIsSimpleStubDeclaration_branches() throws Throwable {
    GlobalNamespace ns1 = parse("var a = {}; a.b;");
    assertTrue(ns1.getOwnSlot("a.b").isSimpleStubDeclaration());

    GlobalNamespace ns2 = parse("var a = {}; a.b; a.b;");
    assertFalse(ns2.getOwnSlot("a.b").isSimpleStubDeclaration());
  }

  // Covers addRef()/getDeclaration(): first SET_FROM_GLOBAL ref becomes the declaration
  @Test
  public void testGetDeclaration_returnsFirstGlobalSetRef() throws Throwable {
    GlobalNamespace ns = parse("var a = 1;");
    Ref decl = ns.getOwnSlot("a").getDeclaration();
    assertNotNull(decl);
    assertTrue(decl.isSet());
  }

  // Covers isSimpleName(): true for a root name, false for a nested property
  @Test
  public void testIsSimpleName_branches() throws Throwable {
    GlobalNamespace ns = parse("var a = {}; a.b = 1;");
    assertTrue(ns.getOwnSlot("a").isSimpleName());
    assertFalse(ns.getOwnSlot("a.b").isSimpleName());
  }

  // Covers getFullName()/getBaseName(): recursive concatenation through ancestors
  @Test
  public void testGetFullNameAndBaseName_nestedProperty() throws Throwable {
    GlobalNamespace ns = parse("var a = {}; a.b.c = 1;");
    Name nameABC = ns.getOwnSlot("a.b.c");
    assertEquals("c", nameABC.getBaseName());
    assertEquals("a.b.c", nameABC.getFullName());
  }

  // Covers getNameForObjLitKey(): NAME-gramps branch, and nested STRING_KEY-gramps branch
  @Test
  public void testGetNameForObjLitKey_nameAndNestedStringKeyGramps() throws Throwable {
    GlobalNamespace ns = parse("var w = {x: {y: 1}};");
    assertNotNull(ns.getOwnSlot("w.x"));
    assertNotNull(ns.getOwnSlot("w.x.y"));
  }

  // Covers getNameForObjLitKey(): ASSIGN-gramps branch
  @Test
  public void testGetNameForObjLitKey_assignGramps() throws Throwable {
    GlobalNamespace ns = parse("var w = {}; w.x = {y: 1};");
    assertNotNull(ns.getOwnSlot("w.x.y"));
  }

  // Covers getNameForObjLitKey(): invalid-identifier key returns null, and an unsupported
  // gramps type (e.g. CALL) also returns null
  @Test
  public void testGetNameForObjLitKey_nullReturnBranches() throws Throwable {
    GlobalNamespace ns1 = parse("var a = {'x-y': 1};");
    assertNull(ns1.getOwnSlot("a").props);

    GlobalNamespace ns2 = parse("var foo = function(x){}; foo({a: 1});");
    assertNull(ns2.getOwnSlot("a"));
  }

  // Covers Ref.isSet(): true for SET_FROM_GLOBAL/SET_FROM_LOCAL, false for other ref types
  @Test
  public void testRefIsSet_trueForSetTypesFalseForGets() throws Throwable {
    Ref setGlobal = Ref.createRefForTesting(Ref.Type.SET_FROM_GLOBAL);
    Ref setLocal = Ref.createRefForTesting(Ref.Type.SET_FROM_LOCAL);
    Ref get = Ref.createRefForTesting(Ref.Type.DIRECT_GET);
    assertTrue(setGlobal.isSet());
    assertTrue(setLocal.isSet());
    assertFalse(get.isSet());
  }

  // Covers Ref.markTwins(): valid pairing (one SET, one ALIASING_GET) links both refs
  @Test
  public void testRefMarkTwins_validPair_linksBothRefs() throws Throwable {
    Ref set = Ref.createRefForTesting(Ref.Type.SET_FROM_GLOBAL);
    Ref alias = Ref.createRefForTesting(Ref.Type.ALIASING_GET);
    Ref.markTwins(set, alias);
    assertSame(alias, set.getTwin());
    assertSame(set, alias.getTwin());
  }

  // Covers Ref.markTwins(): Preconditions.checkArgument fails without an ALIASING_GET/SET pair
  @Test
  public void testRefMarkTwins_invalidPair_throwsIllegalArgumentException() throws Throwable {
    Ref a = Ref.createRefForTesting(Ref.Type.SET_FROM_GLOBAL);
    Ref b = Ref.createRefForTesting(Ref.Type.SET_FROM_LOCAL);
    try {
      Ref.markTwins(a, b);
      fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
    }
  }

  // Covers Ref.cloneAndReclassify(): preserves preOrderIndex/node, changes type
  @Test
  public void testRefCloneAndReclassify_preservesIndexChangesType() throws Throwable {
    Ref original = Ref.createRefForTesting(Ref.Type.DIRECT_GET);
    Ref clone = original.cloneAndReclassify(Ref.Type.ALIASING_GET);
    assertEquals(original.preOrderIndex, clone.preOrderIndex);
    assertEquals(Ref.Type.ALIASING_GET, clone.type);
    assertNull(clone.getNode());
  }

  // Covers Ref(Type, int) via createRefForTesting(): optional fields default to null/empty
  @Test
  public void testRefCreateRefForTesting_nullOptionalFields() throws Throwable {
    Ref ref = Ref.createRefForTesting(Ref.Type.CALL_GET);
    assertNull(ref.getSymbol());
    assertNull(ref.getSourceFile());
    assertEquals("", ref.getSourceName());
    assertNull(ref.getModule());
  }
}
