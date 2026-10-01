package com.google.javascript.jscomp;

import org.junit.Test;
import static org.junit.Assert.*;
import com.google.common.base.Supplier;

public class MakeDeclaredNamesUniqueClaudeTest {

  private Supplier<String> newCountingSupplier() {
    return new Supplier<String>() {
      private int count = 0;
      public String get() {
        count++;
        return String.valueOf(count);
      }
    };
  }

  // Constructor MakeDeclaredNamesUnique() should produce a valid ScopedCallback
  @Test
  public void testConstructor_default_implementsScopedCallback() throws Throwable {
    MakeDeclaredNamesUnique renamer = new MakeDeclaredNamesUnique();
    assertTrue(renamer instanceof NodeTraversal.ScopedCallback);
  }

  // Constructor MakeDeclaredNamesUnique(Renamer) should accept a custom Renamer
  @Test
  public void testConstructor_withCustomRenamer_implementsScopedCallback() throws Throwable {
    MakeDeclaredNamesUnique.Renamer custom = new MakeDeclaredNamesUnique.ContextualRenamer();
    MakeDeclaredNamesUnique instance = new MakeDeclaredNamesUnique(custom);
    assertTrue(instance instanceof NodeTraversal.ScopedCallback);
  }

  // getContextualRenameInverter must return a ContextualRenameInverter usable as a CompilerPass
  @Test
  public void testGetContextualRenameInverter_returnsContextualRenameInverterInstance() throws Throwable {
    Compiler compiler = new Compiler();
    CompilerPass pass = MakeDeclaredNamesUnique.getContextualRenameInverter(compiler);
    assertNotNull(pass);
    assertTrue(pass instanceof MakeDeclaredNamesUnique.ContextualRenameInverter);
  }

  // getOrginalName should strip a single UNIQUE_ID_SEPARATOR suffix
  @Test
  public void testGetOrginalName_withSingleSeparator_stripsSuffix() throws Throwable {
    String result = MakeDeclaredNamesUnique.ContextualRenameInverter.getOrginalName("foo$$1");
    assertEquals("foo", result);
  }

  // getOrginalName with no separator returns the name unchanged
  @Test
  public void testGetOrginalName_noSeparator_returnsSameName() throws Throwable {
    String result = MakeDeclaredNamesUnique.ContextualRenameInverter.getOrginalName("plain");
    assertEquals("plain", result);
  }

  // getOrginalName with multiple separators should strip only after the last one
  @Test
  public void testGetOrginalName_multipleSeparators_stripsOnlyLastSuffix() throws Throwable {
    String result = MakeDeclaredNamesUnique.ContextualRenameInverter.getOrginalName("foo$$bar$$2");
    assertEquals("foo$$bar", result);
  }

  // getOrginalName on empty string: no separator found, must return empty string
  @Test
  public void testGetOrginalName_emptyString_returnsEmptyString() throws Throwable {
    String result = MakeDeclaredNamesUnique.ContextualRenameInverter.getOrginalName("");
    assertEquals("", result);
  }

  // getOrginalName boundary: separator located exactly at the end of the name
  @Test
  public void testGetOrginalName_separatorAtEnd_returnsPrefixOnly() throws Throwable {
    String result = MakeDeclaredNamesUnique.ContextualRenameInverter.getOrginalName("name$$");
    assertEquals("name", result);
  }

  // Global ContextualRenamer: addDeclaredName only reserves the name, no replacement created
  @Test
  public void testContextualRenamer_global_addDeclaredName_doesNotReplace() throws Throwable {
    MakeDeclaredNamesUnique.ContextualRenamer global = new MakeDeclaredNamesUnique.ContextualRenamer();
    global.addDeclaredName("foo");
    assertNull(global.getReplacementName("foo"));
  }

  // Global ContextualRenamer: unknown name has no replacement
  @Test
  public void testContextualRenamer_global_getReplacementName_unknownName_returnsNull() throws Throwable {
    MakeDeclaredNamesUnique.ContextualRenamer global = new MakeDeclaredNamesUnique.ContextualRenamer();
    assertNull(global.getReplacementName("bar"));
  }

  // ContextualRenamer never strips const-ness
  @Test
  public void testContextualRenamer_stripConstIfReplaced_alwaysFalse() throws Throwable {
    MakeDeclaredNamesUnique.ContextualRenamer global = new MakeDeclaredNamesUnique.ContextualRenamer();
    assertFalse(global.stripConstIfReplaced());
  }

  // forChildScope must return a distinct, usable Renamer instance
  @Test
  public void testContextualRenamer_forChildScope_returnsNonNullRenamer() throws Throwable {
    MakeDeclaredNamesUnique.ContextualRenamer global = new MakeDeclaredNamesUnique.ContextualRenamer();
    MakeDeclaredNamesUnique.Renamer child = global.forChildScope();
    assertNotNull(child);
    assertNotSame(global, child);
  }

  // Child scope: first local declaration of a name unused elsewhere keeps its original form (id==0 branch)
  @Test
  public void testContextualRenamer_childScope_firstDeclarationUnusedName_noSuffix() throws Throwable {
    MakeDeclaredNamesUnique.ContextualRenamer global = new MakeDeclaredNamesUnique.ContextualRenamer();
    MakeDeclaredNamesUnique.Renamer child = global.forChildScope();
    child.addDeclaredName("localOnly");
    assertNull(child.getReplacementName("localOnly"));
  }

  // Child scope: a name already reserved in the global scope gets suffix $$1
  @Test
  public void testContextualRenamer_childScope_declarationConflictsWithGlobal_getsSuffixOne() throws Throwable {
    MakeDeclaredNamesUnique.ContextualRenamer global = new MakeDeclaredNamesUnique.ContextualRenamer();
    global.addDeclaredName("x");
    MakeDeclaredNamesUnique.Renamer child = global.forChildScope();
    child.addDeclaredName("x");
    assertEquals("x$$1", child.getReplacementName("x"));
  }

  // Sibling scopes share usage counts: second declaration of same name across siblings increments the suffix
  @Test
  public void testContextualRenamer_childScope_secondSiblingDeclaration_suffixIncrements() throws Throwable {
    MakeDeclaredNamesUnique.ContextualRenamer global = new MakeDeclaredNamesUnique.ContextualRenamer();
    MakeDeclaredNamesUnique.Renamer child1 = global.forChildScope();
    child1.addDeclaredName("y");
    MakeDeclaredNamesUnique.Renamer child2 = global.forChildScope();
    child2.addDeclaredName("y");
    assertEquals("y$$1", child2.getReplacementName("y"));
  }

  // Declaring the same name twice in the same scope must be idempotent (declarations map guard)
  @Test
  public void testContextualRenamer_childScope_duplicateAddDeclaredName_isIdempotent() throws Throwable {
    MakeDeclaredNamesUnique.ContextualRenamer global = new MakeDeclaredNamesUnique.ContextualRenamer();
    global.addDeclaredName("z");
    MakeDeclaredNamesUnique.Renamer child = global.forChildScope();
    child.addDeclaredName("z");
    String first = child.getReplacementName("z");
    child.addDeclaredName("z");
    String second = child.getReplacementName("z");
    assertEquals("z$$1", first);
    assertEquals(first, second);
  }

  // Child scope: a name never declared has no replacement
  @Test
  public void testContextualRenamer_childScope_undeclaredName_returnsNull() throws Throwable {
    MakeDeclaredNamesUnique.ContextualRenamer global = new MakeDeclaredNamesUnique.ContextualRenamer();
    MakeDeclaredNamesUnique.Renamer child = global.forChildScope();
    assertNull(child.getReplacementName("neverDeclared"));
  }

  // Declaring the same name twice globally must reserve it only once (child suffix stays at $$1, not $$2)
  @Test
  public void testContextualRenamer_global_duplicateDeclaration_doesNotInflateSuffix() throws Throwable {
    MakeDeclaredNamesUnique.ContextualRenamer global = new MakeDeclaredNamesUnique.ContextualRenamer();
    global.addDeclaredName("g");
    global.addDeclaredName("g");
    MakeDeclaredNamesUnique.Renamer child = global.forChildScope();
    child.addDeclaredName("g");
    assertEquals("g$$1", child.getReplacementName("g"));
  }

  // InlineRenamer constructor requires a non-empty idPrefix (Preconditions.checkArgument branch)
  @Test
  public void testInlineRenamer_constructor_emptyIdPrefix_throwsIllegalArgumentException() throws Throwable {
    try {
      new MakeDeclaredNamesUnique.InlineRenamer(newCountingSupplier(), "", false);
      fail("expected IllegalArgumentException");
    } catch (IllegalArgumentException expected) {
    }
  }

  // Simple name without separator: unique name is name + "$$" + idPrefix + supplier value
  @Test
  public void testInlineRenamer_addDeclaredName_simpleName_appendsSeparatorPrefixAndId() throws Throwable {
    MakeDeclaredNamesUnique.InlineRenamer renamer =
        new MakeDeclaredNamesUnique.InlineRenamer(newCountingSupplier(), "p_", false);
    renamer.addDeclaredName("foo");
    assertEquals("foo$$p_1", renamer.getReplacementName("foo"));
  }

  // Name already containing a separator: only the portion before the last separator is kept
  @Test
  public void testInlineRenamer_addDeclaredName_nameWithExistingSeparator_stripsOldSuffix() throws Throwable {
    MakeDeclaredNamesUnique.InlineRenamer renamer =
        new MakeDeclaredNamesUnique.InlineRenamer(newCountingSupplier(), "p_", false);
    renamer.addDeclaredName("bar$$old");
    assertEquals("bar$$p_1", renamer.getReplacementName("bar$$old"));
  }

  // Empty name branch: getUniqueName returns the empty string unchanged
  @Test
  public void testInlineRenamer_addDeclaredName_emptyName_returnsEmptyString() throws Throwable {
    MakeDeclaredNamesUnique.InlineRenamer renamer =
        new MakeDeclaredNamesUnique.InlineRenamer(newCountingSupplier(), "p_", false);
    renamer.addDeclaredName("");
    assertEquals("", renamer.getReplacementName(""));
  }

  // Declaring the same name twice must not recompute the unique id (declarations map guard)
  @Test
  public void testInlineRenamer_addDeclaredName_duplicateCall_doesNotRecompute() throws Throwable {
    MakeDeclaredNamesUnique.InlineRenamer renamer =
        new MakeDeclaredNamesUnique.InlineRenamer(newCountingSupplier(), "p_", false);
    renamer.addDeclaredName("dup");
    String first = renamer.getReplacementName("dup");
    renamer.addDeclaredName("dup");
    String second = renamer.getReplacementName("dup");
    assertEquals("dup$$p_1", first);
    assertEquals(first, second);
  }

  // Undeclared name has no replacement in InlineRenamer
  @Test
  public void testInlineRenamer_getReplacementName_undeclaredName_returnsNull() throws Throwable {
    MakeDeclaredNamesUnique.InlineRenamer renamer =
        new MakeDeclaredNamesUnique.InlineRenamer(newCountingSupplier(), "p_", false);
    assertNull(renamer.getReplacementName("nope"));
  }

  // forChildScope creates an InlineRenamer with its own independent declarations map
  @Test
  public void testInlineRenamer_forChildScope_createsIndependentDeclarations() throws Throwable {
    MakeDeclaredNamesUnique.InlineRenamer parent =
        new MakeDeclaredNamesUnique.InlineRenamer(newCountingSupplier(), "p_", false);
    parent.addDeclaredName("v");
    MakeDeclaredNamesUnique.Renamer child = parent.forChildScope();
    assertNull(child.getReplacementName("v"));
  }

  // stripConstIfReplaced reflects the removeConstness constructor flag when true
  @Test
  public void testInlineRenamer_stripConstIfReplaced_trueWhenConfigured() throws Throwable {
    MakeDeclaredNamesUnique.InlineRenamer renamer =
        new MakeDeclaredNamesUnique.InlineRenamer(newCountingSupplier(), "p_", true);
    assertTrue(renamer.stripConstIfReplaced());
  }

  // stripConstIfReplaced reflects the removeConstness constructor flag when false
  @Test
  public void testInlineRenamer_stripConstIfReplaced_falseWhenConfigured() throws Throwable {
    MakeDeclaredNamesUnique.InlineRenamer renamer =
        new MakeDeclaredNamesUnique.InlineRenamer(newCountingSupplier(), "p_", false);
    assertFalse(renamer.stripConstIfReplaced());
  }

  // forChildScope must propagate the same idPrefix used to construct the parent renamer
  @Test
  public void testInlineRenamer_forChildScope_sameIdPrefixUsedInChild() throws Throwable {
    MakeDeclaredNamesUnique.InlineRenamer parent =
        new MakeDeclaredNamesUnique.InlineRenamer(newCountingSupplier(), "pfx_", false);
    MakeDeclaredNamesUnique.Renamer child = parent.forChildScope();
    child.addDeclaredName("c");
    String replacement = child.getReplacementName("c");
    assertNotNull(replacement);
    assertTrue(replacement.startsWith("c$$pfx_"));
  }
}
