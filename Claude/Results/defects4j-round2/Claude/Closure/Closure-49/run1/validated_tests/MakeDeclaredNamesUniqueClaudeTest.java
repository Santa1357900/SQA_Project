package com.google.javascript.jscomp;

import org.junit.Test;
import static org.junit.Assert.*;
import com.google.common.base.Supplier;

public class MakeDeclaredNamesUniqueClaudeTest {

  // Deterministic supplier that always returns the same fixed string.
  private static class ConstantSupplier implements Supplier<String> {
    private final String value;
    ConstantSupplier(String value) {
      this.value = value;
    }
    public String get() {
      return value;
    }
  }

  // Deterministic supplier that returns 0,1,2,... on successive calls.
  private static class SequenceSupplier implements Supplier<String> {
    private int counter = 0;
    public String get() {
      String s = String.valueOf(counter);
      counter++;
      return s;
    }
  }

  // Constructor: no-arg must not throw and must produce a usable instance.
  @Test
  public void testDefaultConstructor_createsInstance() throws Throwable {
    MakeDeclaredNamesUnique pass = new MakeDeclaredNamesUnique();
    assertNotNull(pass);
  }

  // Constructor: custom Renamer must not throw and must produce a usable instance.
  @Test
  public void testConstructorWithRenamer_createsInstance() throws Throwable {
    MakeDeclaredNamesUnique.Renamer renamer = new MakeDeclaredNamesUnique.ContextualRenamer();
    MakeDeclaredNamesUnique pass = new MakeDeclaredNamesUnique(renamer);
    assertNotNull(pass);
  }

  // Field: ARGUMENTS constant must equal "arguments".
  @Test
  public void testARGUMENTS_constantValue() throws Throwable {
    assertEquals("arguments", MakeDeclaredNamesUnique.ARGUMENTS);
  }

  // getContextualRenameInverter: must return a ContextualRenameInverter instance.
  @Test
  public void testGetContextualRenameInverter_returnsContextualRenameInverterInstance() throws Throwable {
    Compiler compiler = new Compiler();
    CompilerPass pass = MakeDeclaredNamesUnique.getContextualRenameInverter(compiler);
    assertTrue(pass instanceof MakeDeclaredNamesUnique.ContextualRenameInverter);
  }

  // getOrginalName: no separator present -> returns the same name unchanged.
  @Test
  public void testGetOrginalName_noSeparatorReturnsSameName() throws Throwable {
    String result = MakeDeclaredNamesUnique.ContextualRenameInverter.getOrginalName("foo");
    assertEquals("foo", result);
  }

  // getOrginalName: single separator -> returns the part before the last separator.
  @Test
  public void testGetOrginalName_withSeparatorReturnsPrefixBeforeLastSeparator() throws Throwable {
    String result = MakeDeclaredNamesUnique.ContextualRenameInverter.getOrginalName("foo$$1");
    assertEquals("foo", result);
  }

  // getOrginalName: multiple separators -> strips only up to the last occurrence.
  @Test
  public void testGetOrginalName_withMultipleSeparatorsReturnsUpToLastSeparator() throws Throwable {
    String result = MakeDeclaredNamesUnique.ContextualRenameInverter.getOrginalName("foo$$1$$2");
    assertEquals("foo$$1", result);
  }

  // getOrginalName: empty string input -> returns empty string.
  @Test
  public void testGetOrginalName_emptyStringReturnsEmptyString() throws Throwable {
    String result = MakeDeclaredNamesUnique.ContextualRenameInverter.getOrginalName("");
    assertEquals("", result);
  }

  // getOrginalName: separator located at index 0 -> returns empty prefix.
  @Test
  public void testGetOrginalName_separatorAtStartReturnsEmptyPrefix() throws Throwable {
    String result = MakeDeclaredNamesUnique.ContextualRenameInverter.getOrginalName("$$5");
    assertEquals("", result);
  }

  // ContextualRenamer global branch: the very first declaration of a name must stay unrenamed.
  @Test
  public void testContextualRenamerGlobal_firstDeclarationNeverRenamed() throws Throwable {
    MakeDeclaredNamesUnique.Renamer root = new MakeDeclaredNamesUnique.ContextualRenamer();
    root.addDeclaredName("foo");
    assertNull(root.getReplacementName("foo"));
  }

  // ContextualRenamer global branch: ARGUMENTS must be ignored, no exception, no replacement.
  @Test
  public void testContextualRenamerGlobal_argumentsIgnoredNoException() throws Throwable {
    MakeDeclaredNamesUnique.Renamer root = new MakeDeclaredNamesUnique.ContextualRenamer();
    root.addDeclaredName(MakeDeclaredNamesUnique.ARGUMENTS);
    assertNull(root.getReplacementName(MakeDeclaredNamesUnique.ARGUMENTS));
  }

  // ContextualRenamer global branch: multiple distinct names are each reserved independently.
  @Test
  public void testContextualRenamerGlobal_multipleDistinctNamesAllUnrenamed() throws Throwable {
    MakeDeclaredNamesUnique.Renamer root = new MakeDeclaredNamesUnique.ContextualRenamer();
    root.addDeclaredName("a");
    root.addDeclaredName("b");
    assertNull(root.getReplacementName("a"));
    assertNull(root.getReplacementName("b"));
  }

  // ContextualRenamer local branch: first local declaration of a name is kept unrenamed.
  @Test
  public void testContextualRenamerForChildScope_firstLocalDeclarationUnrenamed() throws Throwable {
    MakeDeclaredNamesUnique.Renamer root = new MakeDeclaredNamesUnique.ContextualRenamer();
    MakeDeclaredNamesUnique.Renamer child = root.forChildScope();
    child.addDeclaredName("x");
    assertNull(child.getReplacementName("x"));
  }

  // ContextualRenamer local branch: second sibling scope declaring same name gets suffix $$1.
  @Test
  public void testContextualRenamerForChildScope_secondSiblingDeclarationGetsSuffix1() throws Throwable {
    MakeDeclaredNamesUnique.Renamer root = new MakeDeclaredNamesUnique.ContextualRenamer();
    MakeDeclaredNamesUnique.Renamer child1 = root.forChildScope();
    child1.addDeclaredName("x");
    MakeDeclaredNamesUnique.Renamer child2 = root.forChildScope();
    child2.addDeclaredName("x");
    assertEquals("x" + MakeDeclaredNamesUnique.ContextualRenamer.UNIQUE_ID_SEPARATOR + "1",
        child2.getReplacementName("x"));
  }

  // ContextualRenamer local branch: third sibling scope declaring same name gets suffix $$2.
  @Test
  public void testContextualRenamerForChildScope_thirdSiblingDeclarationGetsSuffix2() throws Throwable {
    MakeDeclaredNamesUnique.Renamer root = new MakeDeclaredNamesUnique.ContextualRenamer();
    MakeDeclaredNamesUnique.Renamer child1 = root.forChildScope();
    child1.addDeclaredName("x");
    MakeDeclaredNamesUnique.Renamer child2 = root.forChildScope();
    child2.addDeclaredName("x");
    MakeDeclaredNamesUnique.Renamer child3 = root.forChildScope();
    child3.addDeclaredName("x");
    assertEquals("x" + MakeDeclaredNamesUnique.ContextualRenamer.UNIQUE_ID_SEPARATOR + "2",
        child3.getReplacementName("x"));
  }

  // ContextualRenamer local branch: ARGUMENTS is never added to declarations, replacement stays null.
  @Test
  public void testContextualRenamerForChildScope_argumentsNeverRenamed() throws Throwable {
    MakeDeclaredNamesUnique.Renamer root = new MakeDeclaredNamesUnique.ContextualRenamer();
    MakeDeclaredNamesUnique.Renamer child1 = root.forChildScope();
    child1.addDeclaredName(MakeDeclaredNamesUnique.ARGUMENTS);
    MakeDeclaredNamesUnique.Renamer child2 = root.forChildScope();
    child2.addDeclaredName(MakeDeclaredNamesUnique.ARGUMENTS);
    assertNull(child1.getReplacementName(MakeDeclaredNamesUnique.ARGUMENTS));
    assertNull(child2.getReplacementName(MakeDeclaredNamesUnique.ARGUMENTS));
  }

  // ContextualRenamer local branch: redeclaring the same name twice in one scope does not double-count.
  @Test
  public void testContextualRenamerForChildScope_duplicateDeclarationInSameScopeIdempotent() throws Throwable {
    MakeDeclaredNamesUnique.Renamer root = new MakeDeclaredNamesUnique.ContextualRenamer();
    MakeDeclaredNamesUnique.Renamer child1 = root.forChildScope();
    child1.addDeclaredName("x");
    child1.addDeclaredName("x");
    MakeDeclaredNamesUnique.Renamer child2 = root.forChildScope();
    child2.addDeclaredName("x");
    assertEquals("x" + MakeDeclaredNamesUnique.ContextualRenamer.UNIQUE_ID_SEPARATOR + "1",
        child2.getReplacementName("x"));
  }

  // ContextualRenamer local branch: different names maintain independent usage counters.
  @Test
  public void testContextualRenamerForChildScope_differentNamesIndependentCounts() throws Throwable {
    MakeDeclaredNamesUnique.Renamer root = new MakeDeclaredNamesUnique.ContextualRenamer();
    MakeDeclaredNamesUnique.Renamer child = root.forChildScope();
    child.addDeclaredName("a");
    child.addDeclaredName("b");
    assertNull(child.getReplacementName("a"));
    assertNull(child.getReplacementName("b"));
  }

  // getReplacementName: unknown/undeclared name returns null.
  @Test
  public void testContextualRenamerGetReplacementName_unknownNameReturnsNull() throws Throwable {
    MakeDeclaredNamesUnique.Renamer root = new MakeDeclaredNamesUnique.ContextualRenamer();
    assertNull(root.getReplacementName("neverDeclared"));
  }

  // stripConstIfReplaced: ContextualRenamer always returns false.
  @Test
  public void testContextualRenamerStripConstIfReplaced_alwaysFalse() throws Throwable {
    MakeDeclaredNamesUnique.Renamer root = new MakeDeclaredNamesUnique.ContextualRenamer();
    assertFalse(root.stripConstIfReplaced());
  }

  // Global declaration followed by local declaration of the same name: local must be renamed to avoid collision.
  @Test
  public void testContextualRenamerGlobalThenLocalSameName_localGetsRenamed() throws Throwable {
    MakeDeclaredNamesUnique.Renamer root = new MakeDeclaredNamesUnique.ContextualRenamer();
    root.addDeclaredName("x");
    MakeDeclaredNamesUnique.Renamer child = root.forChildScope();
    child.addDeclaredName("x");
    assertEquals("x" + MakeDeclaredNamesUnique.ContextualRenamer.UNIQUE_ID_SEPARATOR + "1",
        child.getReplacementName("x"));
  }

  // InlineRenamer constructor: empty idPrefix must throw IllegalArgumentException.
  @Test
  public void testInlineRenamerConstructor_emptyIdPrefixThrowsIllegalArgumentException() throws Throwable {
    try {
      new MakeDeclaredNamesUnique.InlineRenamer(new ConstantSupplier("1"), "", false);
      fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
    }
  }

  // InlineRenamer constructor: non-empty idPrefix must not throw.
  @Test
  public void testInlineRenamerConstructor_nonEmptyIdPrefixDoesNotThrow() throws Throwable {
    MakeDeclaredNamesUnique.InlineRenamer renamer =
        new MakeDeclaredNamesUnique.InlineRenamer(new ConstantSupplier("1"), "p", false);
    assertNotNull(renamer);
  }

  // InlineRenamer.addDeclaredName: declaring ARGUMENTS must throw IllegalStateException.
  @Test
  public void testInlineRenamerAddDeclaredName_argumentsThrowsIllegalStateException() throws Throwable {
    MakeDeclaredNamesUnique.InlineRenamer renamer =
        new MakeDeclaredNamesUnique.InlineRenamer(new ConstantSupplier("1"), "p", false);
    try {
      renamer.addDeclaredName(MakeDeclaredNamesUnique.ARGUMENTS);
      fail("expected IllegalStateException");
    } catch (IllegalStateException expected) {
    }
  }

  // InlineRenamer.addDeclaredName: replacement must be name + SEPARATOR + idPrefix + supplier value.
  @Test
  public void testInlineRenamerAddDeclaredName_returnsUniqueNameWithPrefixAndSuffix() throws Throwable {
    MakeDeclaredNamesUnique.InlineRenamer renamer =
        new MakeDeclaredNamesUnique.InlineRenamer(new ConstantSupplier("Z"), "pfx_", false);
    renamer.addDeclaredName("abc");
    String expected = "abc" + MakeDeclaredNamesUnique.ContextualRenamer.UNIQUE_ID_SEPARATOR + "pfx_Z";
    assertEquals(expected, renamer.getReplacementName("abc"));
  }

  // InlineRenamer.addDeclaredName: declaring the same name twice keeps the first generated mapping.
  @Test
  public void testInlineRenamerAddDeclaredName_duplicateDeclarationKeepsFirstMapping() throws Throwable {
    MakeDeclaredNamesUnique.InlineRenamer renamer =
        new MakeDeclaredNamesUnique.InlineRenamer(new SequenceSupplier(), "id_", false);
    renamer.addDeclaredName("foo");
    renamer.addDeclaredName("foo");
    String expected = "foo" + MakeDeclaredNamesUnique.ContextualRenamer.UNIQUE_ID_SEPARATOR + "id_0";
    assertEquals(expected, renamer.getReplacementName("foo"));
  }

  // InlineRenamer.addDeclaredName: empty name input returns empty string replacement (per getUniqueName contract).
  @Test
  public void testInlineRenamerAddDeclaredName_emptyNameReturnsEmptyReplacement() throws Throwable {
    MakeDeclaredNamesUnique.InlineRenamer renamer =
        new MakeDeclaredNamesUnique.InlineRenamer(new ConstantSupplier("1"), "p", false);
    renamer.addDeclaredName("");
    assertEquals("", renamer.getReplacementName(""));
  }

  // InlineRenamer.addDeclaredName: an existing separator suffix in the name is stripped before re-suffixing.
  @Test
  public void testInlineRenamerAddDeclaredName_stripsExistingSeparatorSuffix() throws Throwable {
    MakeDeclaredNamesUnique.InlineRenamer renamer =
        new MakeDeclaredNamesUnique.InlineRenamer(new ConstantSupplier("Z"), "pfx", false);
    String input = "abc" + MakeDeclaredNamesUnique.ContextualRenamer.UNIQUE_ID_SEPARATOR + "7";
    renamer.addDeclaredName(input);
    String expected = "abc" + MakeDeclaredNamesUnique.ContextualRenamer.UNIQUE_ID_SEPARATOR + "pfxZ";
    assertEquals(expected, renamer.getReplacementName(input));
  }

  // InlineRenamer.addDeclaredName: different names consume successive supplier values.
  @Test
  public void testInlineRenamerAddDeclaredName_differentNamesGetDifferentSuffixes() throws Throwable {
    MakeDeclaredNamesUnique.InlineRenamer renamer =
        new MakeDeclaredNamesUnique.InlineRenamer(new SequenceSupplier(), "id_", false);
    renamer.addDeclaredName("foo");
    renamer.addDeclaredName("bar");
    String expectedFoo = "foo" + MakeDeclaredNamesUnique.ContextualRenamer.UNIQUE_ID_SEPARATOR + "id_0";
    String expectedBar = "bar" + MakeDeclaredNamesUnique.ContextualRenamer.UNIQUE_ID_SEPARATOR + "id_1";
    assertEquals(expectedFoo, renamer.getReplacementName("foo"));
    assertEquals(expectedBar, renamer.getReplacementName("bar"));
  }

  // InlineRenamer.forChildScope: returns a fresh renamer with its own independent declarations.
  @Test
  public void testInlineRenamerForChildScope_returnsIndependentRenamer() throws Throwable {
    MakeDeclaredNamesUnique.InlineRenamer renamer =
        new MakeDeclaredNamesUnique.InlineRenamer(new ConstantSupplier("1"), "p", false);
    renamer.addDeclaredName("x");
    MakeDeclaredNamesUnique.Renamer child = renamer.forChildScope();
    assertNull(child.getReplacementName("x"));
  }

  // stripConstIfReplaced: InlineRenamer returns the configured value (true).
  @Test
  public void testInlineRenamerStripConstIfReplaced_trueConfiguration() throws Throwable {
    MakeDeclaredNamesUnique.InlineRenamer renamer =
        new MakeDeclaredNamesUnique.InlineRenamer(new ConstantSupplier("1"), "p", true);
    assertTrue(renamer.stripConstIfReplaced());
  }

  // stripConstIfReplaced: InlineRenamer returns the configured value (false).
  @Test
  public void testInlineRenamerStripConstIfReplaced_falseConfiguration() throws Throwable {
    MakeDeclaredNamesUnique.InlineRenamer renamer =
        new MakeDeclaredNamesUnique.InlineRenamer(new ConstantSupplier("1"), "p", false);
    assertFalse(renamer.stripConstIfReplaced());
  }

  // getReplacementName: InlineRenamer returns null for an undeclared name.
  @Test
  public void testInlineRenamerGetReplacementName_unknownNameReturnsNull() throws Throwable {
    MakeDeclaredNamesUnique.InlineRenamer renamer =
        new MakeDeclaredNamesUnique.InlineRenamer(new ConstantSupplier("1"), "p", false);
    assertNull(renamer.getReplacementName("neverDeclared"));
  }

  // BoilerplateRenamer at global level behaves like ContextualRenamer: first declaration stays unrenamed.
  @Test
  public void testBoilerplateRenamerGlobal_firstDeclarationNeverRenamedLikeContextual() throws Throwable {
    MakeDeclaredNamesUnique.Renamer root =
        new MakeDeclaredNamesUnique.BoilerplateRenamer(new ConstantSupplier("1"), "bp_");
    root.addDeclaredName("g");
    assertNull(root.getReplacementName("g"));
  }

  // BoilerplateRenamer.forChildScope: must return an InlineRenamer per the "boilerplate library" contract.
  @Test
  public void testBoilerplateRenamerForChildScope_returnsInlineRenamerInstance() throws Throwable {
    MakeDeclaredNamesUnique.Renamer root =
        new MakeDeclaredNamesUnique.BoilerplateRenamer(new ConstantSupplier("1"), "bp_");
    MakeDeclaredNamesUnique.Renamer child = root.forChildScope();
    assertTrue(child instanceof MakeDeclaredNamesUnique.InlineRenamer);
  }

  // BoilerplateRenamer.forChildScope: unlike ContextualRenamer, even the first local declaration is renamed.
  @Test
  public void testBoilerplateRenamerForChildScope_firstLocalDeclarationIsRenamedUnlikeContextual() throws Throwable {
    MakeDeclaredNamesUnique.Renamer root =
        new MakeDeclaredNamesUnique.BoilerplateRenamer(new ConstantSupplier("1"), "bp_");
    MakeDeclaredNamesUnique.Renamer child = root.forChildScope();
    child.addDeclaredName("local1");
    assertNotNull(child.getReplacementName("local1"));
  }

  // BoilerplateRenamer.forChildScope: the InlineRenamer it creates always has removeConstness = false.
  @Test
  public void testBoilerplateRenamerForChildScope_stripConstIfReplacedAlwaysFalse() throws Throwable {
    MakeDeclaredNamesUnique.Renamer root =
        new MakeDeclaredNamesUnique.BoilerplateRenamer(new ConstantSupplier("1"), "bp_");
    MakeDeclaredNamesUnique.Renamer child = root.forChildScope();
    assertFalse(child.stripConstIfReplaced());
  }
}
