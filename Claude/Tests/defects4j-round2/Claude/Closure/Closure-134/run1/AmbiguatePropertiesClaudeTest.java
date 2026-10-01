package com.google.javascript.jscomp;

import static org.junit.Assert.*;

import org.junit.Before;
import org.junit.Test;

import com.google.javascript.rhino.Node;

import java.util.Map;

public class AmbiguatePropertiesClaudeTest {

  private Compiler compiler;
  private CompilerOptions options;

  @Before
  public void setUp() throws Throwable {
    compiler = new Compiler();
    options = new CompilerOptions();
    options.setCheckTypes(true);
  }

  private Node[] compileAndGetRoots(String externsCode, String jsCode) {
    SourceFile externs = SourceFile.fromCode("externs.js", externsCode);
    SourceFile input = SourceFile.fromCode("input.js", jsCode);
    compiler.compile(externs, input, options);
    Node root = compiler.getRoot();
    Node externsRoot = root.getFirstChild();
    Node jsRoot = root.getLastChild();
    return new Node[] { externsRoot, jsRoot };
  }

  private Node[] compileAndGetRoots(String jsCode) {
    return compileAndGetRoots("", jsCode);
  }

  // Constructor: loop over TypeValidator mismatches (>=1 iteration); basic construction succeeds.
  @Test
  public void testConstructor_withTypeMismatch_doesNotThrowAndMapEmpty() throws Throwable {
    compileAndGetRoots("/** @type {number} */ var n = 'str';");
    AmbiguateProperties ap = new AmbiguateProperties(compiler, new char[0]);
    assertTrue(ap.getRenamingMap().isEmpty());
  }

  // getRenamingMap must start empty prior to any process() call.
  @Test
  public void testGetRenamingMap_beforeProcess_isEmpty() throws Throwable {
    compileAndGetRoots("var x = 1;");
    AmbiguateProperties ap = new AmbiguateProperties(compiler, new char[0]);
    Map<String, String> map = ap.getRenamingMap();
    assertNotNull(map);
    assertEquals(0, map.size());
  }

  // 0-iteration case for both NodeTraversal loops: empty externs and empty program.
  @Test
  public void testProcess_emptyProgram_rendersEmptyRenamingMap() throws Throwable {
    Node[] roots = compileAndGetRoots("", "");
    AmbiguateProperties ap = new AmbiguateProperties(compiler, new char[0]);
    ap.process(roots[0], roots[1]);
    assertTrue(ap.getRenamingMap().isEmpty());
  }

  // Two unrelated constructor types with one property each may share the same renamed name.
  @Test
  public void testProcess_unrelatedTypesSingleProperty_getSameRenamedName() throws Throwable {
    String js = "/** @constructor */ function Foo(){} "
        + "/** @constructor */ function Bar(){} "
        + "var f = new Foo(); f.fooprop = 1; "
        + "var b = new Bar(); b.barprop = 2;";
    Node[] roots = compileAndGetRoots(js);
    AmbiguateProperties ap = new AmbiguateProperties(compiler, new char[0]);
    ap.process(roots[0], roots[1]);
    Map<String, String> map = ap.getRenamingMap();
    assertNotNull(map.get("fooprop"));
    assertEquals(map.get("fooprop"), map.get("barprop"));
  }

  // Class javadoc example: same-type properties (Foo.fooprop/fooprop2) must differ.
  @Test
  public void testProcess_javadocExample_sameTypePropertiesDiffer() throws Throwable {
    String js = "/** @constructor */ function Foo(){} "
        + "/** @constructor */ function Bar(){} "
        + "var f = new Foo(); f.fooprop = 0; f.fooprop2 = 0; "
        + "var b = new Bar(); b.barprop = 0;";
    Node[] roots = compileAndGetRoots(js);
    AmbiguateProperties ap = new AmbiguateProperties(compiler, new char[0]);
    ap.process(roots[0], roots[1]);
    Map<String, String> map = ap.getRenamingMap();
    assertFalse(map.get("fooprop").equals(map.get("fooprop2")));
    assertEquals(map.get("fooprop"), map.get("barprop"));
  }

  // Properties whose name starts with SKIP_PREFIX must never be renamed.
  @Test
  public void testProcess_skipPrefixProperty_notInRenamingMap() throws Throwable {
    String propName = AmbiguateProperties.SKIP_PREFIX + "Foo";
    String js = "/** @constructor */ function Foo(){} "
        + "var f = new Foo(); f." + propName + " = 1; f." + propName + " = 2;";
    Node[] roots = compileAndGetRoots(js);
    AmbiguateProperties ap = new AmbiguateProperties(compiler, new char[0]);
    ap.process(roots[0], roots[1]);
    assertNull(ap.getRenamingMap().get(propName));
  }

  // Boundary: a property name exactly equal to SKIP_PREFIX also satisfies startsWith.
  @Test
  public void testProcess_skipPrefixExactBoundary_notInRenamingMap() throws Throwable {
    String propName = AmbiguateProperties.SKIP_PREFIX;
    String js = "/** @constructor */ function Foo(){} "
        + "var f = new Foo(); f." + propName + " = 1;";
    Node[] roots = compileAndGetRoots(js);
    AmbiguateProperties ap = new AmbiguateProperties(compiler, new char[0]);
    ap.process(roots[0], roots[1]);
    assertNull(ap.getRenamingMap().get(propName));
  }

  // Prefix appearing mid-string (not at index 0) must NOT trigger skip-ambiguating.
  @Test
  public void testProcess_nonSkipPrefixSubstring_isEligibleForRenaming() throws Throwable {
    String propName = "my" + AmbiguateProperties.SKIP_PREFIX + "Prop";
    String js = "/** @constructor */ function Foo(){} "
        + "var f = new Foo(); f." + propName + " = 1;";
    Node[] roots = compileAndGetRoots(js);
    AmbiguateProperties ap = new AmbiguateProperties(compiler, new char[0]);
    ap.process(roots[0], roots[1]);
    assertNotNull(ap.getRenamingMap().get(propName));
  }

  // A property declared in externs (via GETPROP) must never be a renaming candidate.
  @Test
  public void testProcess_externedPropertyViaGetProp_notRenamed() throws Throwable {
    String externs = "/** @constructor */ function Foo(){} Foo.prototype.barprop;";
    String js = "var f = new Foo(); f.barprop = 1;";
    Node[] roots = compileAndGetRoots(externs, js);
    AmbiguateProperties ap = new AmbiguateProperties(compiler, new char[0]);
    ap.process(roots[0], roots[1]);
    assertNull(ap.getRenamingMap().get("barprop"));
  }

  // Externs OBJECTLIT branch collects both keys (multi-round loop), regardless of quoting.
  @Test
  public void testProcess_externedPropertyViaObjectLit_notRenamed() throws Throwable {
    String externs = "var externObj = {fooKey: 1, barKey: 2};";
    String js = "/** @constructor */ function Foo(){} "
        + "var f = new Foo(); f.fooKey = 1; f.barKey = 2;";
    Node[] roots = compileAndGetRoots(externs, js);
    AmbiguateProperties ap = new AmbiguateProperties(compiler, new char[0]);
    ap.process(roots[0], roots[1]);
    Map<String, String> map = ap.getRenamingMap();
    assertNull(map.get("fooKey"));
    assertNull(map.get("barKey"));
  }

  // Single-key externs OBJECTLIT: exactly one loop round.
  @Test
  public void testProcess_externsObjectLitSingleKey_notRenamed() throws Throwable {
    String externs = "var externObj = {onlyKey: 1};";
    String js = "/** @constructor */ function Foo(){} var f = new Foo(); f.onlyKey = 1;";
    Node[] roots = compileAndGetRoots(externs, js);
    AmbiguateProperties ap = new AmbiguateProperties(compiler, new char[0]);
    ap.process(roots[0], roots[1]);
    assertNull(ap.getRenamingMap().get("onlyKey"));
  }

  // GETELEM with a quoted string key only reserves the name; never becomes a candidate.
  @Test
  public void testProcess_quotedPropertyViaGetElem_notRecordedAsCandidate() throws Throwable {
    String js = "var obj = {}; obj['quotedProp'] = 5;";
    Node[] roots = compileAndGetRoots(js);
    AmbiguateProperties ap = new AmbiguateProperties(compiler, new char[0]);
    ap.process(roots[0], roots[1]);
    assertNull(ap.getRenamingMap().get("quotedProp"));
  }

  // Quoted object-literal keys are reserved, not renaming candidates.
  @Test
  public void testProcess_quotedKeyInObjectLiteral_notRecordedAsCandidate() throws Throwable {
    String js = "var obj = {'quotedKey': 1};";
    Node[] roots = compileAndGetRoots(js);
    AmbiguateProperties ap = new AmbiguateProperties(compiler, new char[0]);
    ap.process(roots[0], roots[1]);
    assertNull(ap.getRenamingMap().get("quotedKey"));
  }

  // Numeric object-literal keys fail the Token.STRING check and are skipped.
  @Test
  public void testProcess_numericKeyInObjectLiteral_notRecordedAsCandidate() throws Throwable {
    String js = "var obj = {1: 'a'};";
    Node[] roots = compileAndGetRoots(js);
    AmbiguateProperties ap = new AmbiguateProperties(compiler, new char[0]);
    ap.process(roots[0], roots[1]);
    assertNull(ap.getRenamingMap().get("1"));
  }

  // Mixing quoted (GETELEM) and unquoted (GETPROP) access: only the unquoted one is a candidate.
  @Test
  public void testProcess_mixedQuotedAndUnquotedProperties_onlyUnquotedAreRenamed() throws Throwable {
    String js = "/** @constructor */ function Foo(){} var f = new Foo(); "
        + "f.unquotedprop = 1; f['quotedOnly'] = 2;";
    Node[] roots = compileAndGetRoots(js);
    AmbiguateProperties ap = new AmbiguateProperties(compiler, new char[0]);
    ap.process(roots[0], roots[1]);
    Map<String, String> map = ap.getRenamingMap();
    assertNotNull(map.get("unquotedprop"));
    assertNull(map.get("quotedOnly"));
  }

  // A UnionType restricted to a single non-null alternate must still be a valid candidate.
  @Test
  public void testProcess_nullablePropertyType_stillEligibleForRenaming() throws Throwable {
    String js = "/** @constructor */ function Foo(){} "
        + "var f1 = new Foo(); "
        + "/** @type {?Foo} */ var f2 = f1; "
        + "f2.nullableprop = 5;";
    Node[] roots = compileAndGetRoots(js);
    AmbiguateProperties ap = new AmbiguateProperties(compiler, new char[0]);
    ap.process(roots[0], roots[1]);
    assertNotNull(ap.getRenamingMap().get("nullableprop"));
  }

  // A property typed as Foo|Bar is related to both alternates and must differ from either-only props.
  @Test
  public void testProcess_unionOfTwoDistinctTypes_relatedToBothAlternates() throws Throwable {
    String js = "/** @constructor */ function Foo(){} "
        + "/** @constructor */ function Bar(){} "
        + "var fb = /** @type {(Foo|Bar)} */ (new Foo()); fb.commonprop = 1; "
        + "var foo2 = new Foo(); foo2.fooonly = 2; "
        + "var bar2 = new Bar(); bar2.baronly = 3;";
    Node[] roots = compileAndGetRoots(js);
    AmbiguateProperties ap = new AmbiguateProperties(compiler, new char[0]);
    ap.process(roots[0], roots[1]);
    Map<String, String> map = ap.getRenamingMap();
    assertFalse(map.get("commonprop").equals(map.get("fooonly")));
    assertFalse(map.get("commonprop").equals(map.get("baronly")));
  }

  // NULL_TYPE is an explicit invalidating type; properties referenced on it are never renamed.
  @Test
  public void testProcess_propertyOnNullType_notRenamed() throws Throwable {
    String js = "null.nullprop = 1;";
    Node[] roots = compileAndGetRoots(js);
    AmbiguateProperties ap = new AmbiguateProperties(compiler, new char[0]);
    ap.process(roots[0], roots[1]);
    assertNull(ap.getRenamingMap().get("nullprop"));
  }

  // Sanity on the Map returned after a real process() run: non-empty new name string.
  @Test
  public void testGetRenamingMap_afterProcess_isMutableMapWithStringEntries() throws Throwable {
    String js = "/** @constructor */ function Foo(){} var f = new Foo(); f.aprop = 1;";
    Node[] roots = compileAndGetRoots(js);
    AmbiguateProperties ap = new AmbiguateProperties(compiler, new char[0]);
    ap.process(roots[0], roots[1]);
    String newName = ap.getRenamingMap().get("aprop");
    assertNotNull(newName);
    assertTrue(newName.length() > 0);
  }

  // Many (3) mutually unrelated single-property types may all be colored with the same name.
  @Test
  public void testProcess_threeUnrelatedTypesSingleProperty_allShareSameName() throws Throwable {
    String js = "/** @constructor */ function A(){} "
        + "/** @constructor */ function B(){} "
        + "/** @constructor */ function C(){} "
        + "var a=new A(); a.pa=1; var b=new B(); b.pb=2; var c=new C(); c.pc=3;";
    Node[] roots = compileAndGetRoots(js);
    AmbiguateProperties ap = new AmbiguateProperties(compiler, new char[0]);
    ap.process(roots[0], roots[1]);
    Map<String, String> map = ap.getRenamingMap();
    String n1 = map.get("pa");
    assertEquals(n1, map.get("pb"));
    assertEquals(n1, map.get("pc"));
  }

  // Many occurrences of the same property on the same type still yield exactly one map entry.
  @Test
  public void testProcess_propertyUsedManyTimesOnSameType_singleConsistentNewName() throws Throwable {
    String js = "/** @constructor */ function Foo(){} var f = new Foo(); "
        + "f.rep = 1; f.rep = 2; f.rep = 3;";
    Node[] roots = compileAndGetRoots(js);
    AmbiguateProperties ap = new AmbiguateProperties(compiler, new char[0]);
    ap.process(roots[0], roots[1]);
    Map<String, String> map = ap.getRenamingMap();
    assertEquals(1, map.size());
    assertNotNull(map.get("rep"));
  }

  // Non-empty reservedCharacters array is accepted by the constructor and NameGenerator.
  @Test
  public void testProcess_withNonEmptyReservedCharacters_processCompletesAndRenames() throws Throwable {
    String js = "/** @constructor */ function Foo(){} var f = new Foo(); f.someprop = 1;";
    Node[] roots = compileAndGetRoots(js);
    AmbiguateProperties ap = new AmbiguateProperties(compiler, new char[] {'$', '_'});
    ap.process(roots[0], roots[1]);
    assertNotNull(ap.getRenamingMap().get("someprop"));
  }

  // Unicode property identifiers must be handled like any other property name.
  @Test
  public void testProcess_unicodePropertyName_isRenamedConsistently() throws Throwable {
    String js = "/** @constructor */ function Foo(){} var f = new Foo(); f.h\u00e9llo = 1;";
    Node[] roots = compileAndGetRoots(js);
    AmbiguateProperties ap = new AmbiguateProperties(compiler, new char[0]);
    ap.process(roots[0], roots[1]);
    assertNotNull(ap.getRenamingMap().get("h\u00e9llo"));
  }
}
