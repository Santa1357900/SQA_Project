package com.fasterxml.jackson.core.util;

import java.io.IOException;
import java.io.StringWriter;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.io.SerializedString;

public class DefaultPrettyPrinterClaudeTest
{
    private JsonFactory factory;

    // Simple deterministic Indenter used to verify nesting level passed to writeIndentation,
    // avoiding dependency on platform-specific line separators.
    private static class MarkerIndenter implements DefaultPrettyPrinter.Indenter
    {
        public void writeIndentation(JsonGenerator g, int level) throws IOException {
            g.writeRaw("<" + level + ">");
        }
        public boolean isInline() {
            return false;
        }
    }

    @Before
    public void setUp() throws Throwable {
        factory = new JsonFactory();
    }

    // Default constructor must use the documented single-space root separator constant.
    @Test
    public void testDefaultConstructor_usesDefaultRootSeparatorConstant() throws Throwable {
        DefaultPrettyPrinter pp = new DefaultPrettyPrinter();
        assertSame(DefaultPrettyPrinter.DEFAULT_ROOT_VALUE_SEPARATOR, pp._rootSeparator);
    }

    // String constructor with non-null value must be used as root separator between root values.
    @Test
    public void testStringConstructor_nonNull_usedAsRootSeparator() throws Throwable {
        DefaultPrettyPrinter pp = new DefaultPrettyPrinter("|");
        StringWriter sw = new StringWriter();
        JsonGenerator g = factory.createGenerator(sw);
        g.setPrettyPrinter(pp);
        g.writeNumber(1);
        g.writeNumber(2);
        g.flush();
        assertEquals("1|2", sw.toString());
    }

    // String constructor with null means no separator is printed between root values (per Javadoc).
    @Test
    public void testStringConstructor_null_noSeparatorWritten() throws Throwable {
        DefaultPrettyPrinter pp = new DefaultPrettyPrinter((String) null);
        StringWriter sw = new StringWriter();
        JsonGenerator g = factory.createGenerator(sw);
        g.setPrettyPrinter(pp);
        g.writeNumber(1);
        g.writeNumber(2);
        g.flush();
        assertEquals("12", sw.toString());
    }

    // SerializableString constructor stores that exact separator.
    @Test
    public void testSerializableStringConstructor_usedAsRootSeparator() throws Throwable {
        DefaultPrettyPrinter pp = new DefaultPrettyPrinter(new SerializedString(";"));
        StringWriter sw = new StringWriter();
        JsonGenerator g = factory.createGenerator(sw);
        g.setPrettyPrinter(pp);
        g.writeNumber(1);
        g.writeNumber(2);
        g.flush();
        assertEquals("1;2", sw.toString());
    }

    // Copy constructor must copy configuration into a distinct instance.
    @Test
    public void testCopyConstructor_copiesConfig_distinctInstance() throws Throwable {
        DefaultPrettyPrinter base = new DefaultPrettyPrinter().withoutSpacesInObjectEntries();
        DefaultPrettyPrinter copy = new DefaultPrettyPrinter(base);
        assertNotSame(base, copy);
        assertFalse(copy._spacesInObjectEntries);
        assertSame(base._rootSeparator, copy._rootSeparator);
    }

    // Two-arg copy constructor must override root separator while copying other state.
    @Test
    public void testCopyConstructorWithSeparator_overridesRootSeparator() throws Throwable {
        DefaultPrettyPrinter base = new DefaultPrettyPrinter();
        SerializedString newSep = new SerializedString("~");
        DefaultPrettyPrinter pp2 = new DefaultPrettyPrinter(base, newSep);
        assertNotSame(base, pp2);
        assertSame(newSep, pp2._rootSeparator);
    }

    // withRootSeparator with same reference returns this (identity branch).
    @Test
    public void testWithRootSeparator_sameReference_returnsThis() throws Throwable {
        DefaultPrettyPrinter pp = new DefaultPrettyPrinter();
        DefaultPrettyPrinter res = pp.withRootSeparator(pp._rootSeparator);
        assertSame(pp, res);
    }

    // withRootSeparator with an equal (but different instance) value also returns this.
    @Test
    public void testWithRootSeparator_equalValue_returnsThis() throws Throwable {
        DefaultPrettyPrinter pp = new DefaultPrettyPrinter();
        DefaultPrettyPrinter res = pp.withRootSeparator(new SerializedString(" "));
        assertSame(pp, res);
    }

    // withRootSeparator with a different value returns a new instance that uses the new separator.
    @Test
    public void testWithRootSeparator_differentValue_returnsNewInstance() throws Throwable {
        DefaultPrettyPrinter pp = new DefaultPrettyPrinter();
        DefaultPrettyPrinter res = pp.withRootSeparator(new SerializedString("-"));
        assertNotSame(pp, res);
        StringWriter sw = new StringWriter();
        JsonGenerator g = factory.createGenerator(sw);
        g.setPrettyPrinter(res);
        g.writeNumber(1);
        g.writeNumber(2);
        g.flush();
        assertEquals("1-2", sw.toString());
    }

    // withRootSeparator(String) with null must result in no root separator being stored.
    @Test
    public void testWithRootSeparatorString_null_setsNullSeparator() throws Throwable {
        DefaultPrettyPrinter pp = new DefaultPrettyPrinter();
        DefaultPrettyPrinter res = pp.withRootSeparator((String) null);
        assertNull(res._rootSeparator);
    }

    // indentArraysWith(null) must fall back to NopIndenter.
    @Test
    public void testIndentArraysWith_null_usesNopIndenter() throws Throwable {
        DefaultPrettyPrinter pp = new DefaultPrettyPrinter();
        pp.indentArraysWith(null);
        assertSame(DefaultPrettyPrinter.NopIndenter.instance, pp._arrayIndenter);
    }

    // indentArraysWith(custom) stores the given indenter directly.
    @Test
    public void testIndentArraysWith_custom_setsIndenter() throws Throwable {
        DefaultPrettyPrinter pp = new DefaultPrettyPrinter();
        DefaultPrettyPrinter.Indenter custom = new MarkerIndenter();
        pp.indentArraysWith(custom);
        assertSame(custom, pp._arrayIndenter);
    }

    // indentObjectsWith(null) must fall back to NopIndenter.
    @Test
    public void testIndentObjectsWith_null_usesNopIndenter() throws Throwable {
        DefaultPrettyPrinter pp = new DefaultPrettyPrinter();
        pp.indentObjectsWith(null);
        assertSame(DefaultPrettyPrinter.NopIndenter.instance, pp._objectIndenter);
    }

    // indentObjectsWith(custom) stores the given indenter directly.
    @Test
    public void testIndentObjectsWith_custom_setsIndenter() throws Throwable {
        DefaultPrettyPrinter pp = new DefaultPrettyPrinter();
        DefaultPrettyPrinter.Indenter custom = new MarkerIndenter();
        pp.indentObjectsWith(custom);
        assertSame(custom, pp._objectIndenter);
    }

    // withArrayIndenter with the currently-used indenter returns this (no new instance).
    @Test
    public void testWithArrayIndenter_sameValue_returnsThis() throws Throwable {
        DefaultPrettyPrinter pp = new DefaultPrettyPrinter();
        DefaultPrettyPrinter res = pp.withArrayIndenter(DefaultPrettyPrinter.FixedSpaceIndenter.instance);
        assertSame(pp, res);
    }

    // withArrayIndenter with a different indenter returns a new instance, original left untouched.
    @Test
    public void testWithArrayIndenter_differentValue_returnsNewInstance() throws Throwable {
        DefaultPrettyPrinter pp = new DefaultPrettyPrinter();
        DefaultPrettyPrinter.Indenter custom = new MarkerIndenter();
        DefaultPrettyPrinter res = pp.withArrayIndenter(custom);
        assertNotSame(pp, res);
        assertSame(custom, res._arrayIndenter);
        assertSame(DefaultPrettyPrinter.FixedSpaceIndenter.instance, pp._arrayIndenter);
    }

    // withArrayIndenter(null) on a new instance results in NopIndenter being used.
    @Test
    public void testWithArrayIndenter_null_usesNopIndenterOnNewInstance() throws Throwable {
        DefaultPrettyPrinter pp = new DefaultPrettyPrinter();
        DefaultPrettyPrinter res = pp.withArrayIndenter(null);
        assertSame(DefaultPrettyPrinter.NopIndenter.instance, res._arrayIndenter);
    }

    // withObjectIndenter with the currently-used indenter returns this.
    @Test
    public void testWithObjectIndenter_sameValue_returnsThis() throws Throwable {
        DefaultPrettyPrinter pp = new DefaultPrettyPrinter();
        DefaultPrettyPrinter res = pp.withObjectIndenter(DefaultIndenter.SYSTEM_LINEFEED_INSTANCE);
        assertSame(pp, res);
    }

    // withObjectIndenter(null) returns a new instance using NopIndenter.
    @Test
    public void testWithObjectIndenter_null_returnsNewInstanceWithNop() throws Throwable {
        DefaultPrettyPrinter pp = new DefaultPrettyPrinter();
        DefaultPrettyPrinter res = pp.withObjectIndenter(null);
        assertNotSame(pp, res);
        assertSame(DefaultPrettyPrinter.NopIndenter.instance, res._objectIndenter);
    }

    // withSpacesInObjectEntries when already true returns this (no change needed).
    @Test
    public void testWithSpacesInObjectEntries_alreadyTrue_returnsThis() throws Throwable {
        DefaultPrettyPrinter pp = new DefaultPrettyPrinter();
        DefaultPrettyPrinter res = pp.withSpacesInObjectEntries();
        assertSame(pp, res);
    }

    // withoutSpacesInObjectEntries returns a new instance with the flag set to false.
    @Test
    public void testWithoutSpacesInObjectEntries_returnsNewInstanceFalse() throws Throwable {
        DefaultPrettyPrinter pp = new DefaultPrettyPrinter();
        DefaultPrettyPrinter res = pp.withoutSpacesInObjectEntries();
        assertNotSame(pp, res);
        assertFalse(res._spacesInObjectEntries);
    }

    // Toggling back via withSpacesInObjectEntries yields another new instance with flag true.
    @Test
    public void testWithSpacesInObjectEntries_afterWithout_returnsNewInstanceTrue() throws Throwable {
        DefaultPrettyPrinter without = new DefaultPrettyPrinter().withoutSpacesInObjectEntries();
        DefaultPrettyPrinter withAgain = without.withSpacesInObjectEntries();
        assertNotSame(without, withAgain);
        assertTrue(withAgain._spacesInObjectEntries);
    }

    // withSeparators mutates this instance's separators and recomputed field-value separator string.
    @Test
    public void testWithSeparators_setsFieldsAndReturnsThis() throws Throwable {
        DefaultPrettyPrinter source = new DefaultPrettyPrinter();
        Separators sep = source._separators;
        DefaultPrettyPrinter pp2 = new DefaultPrettyPrinter();
        DefaultPrettyPrinter ret = pp2.withSeparators(sep);
        assertSame(pp2, ret);
        assertSame(sep, pp2._separators);
        assertEquals(" : ", pp2._objectFieldValueSeparatorWithSpaces);
    }

    // createInstance must return a distinct object that preserves current configuration.
    @Test
    public void testCreateInstance_returnsDistinctCopyWithSameConfig() throws Throwable {
        DefaultPrettyPrinter pp = new DefaultPrettyPrinter().withoutSpacesInObjectEntries();
        DefaultPrettyPrinter copy = pp.createInstance();
        assertNotSame(pp, copy);
        assertFalse(copy._spacesInObjectEntries);
        assertSame(pp._arrayIndenter, copy._arrayIndenter);
    }

    // writeRootValueSeparator writes the configured separator when non-null.
    @Test
    public void testWriteRootValueSeparator_nonNull_writesSeparator() throws Throwable {
        DefaultPrettyPrinter pp = new DefaultPrettyPrinter();
        StringWriter sw = new StringWriter();
        JsonGenerator g = factory.createGenerator(sw);
        pp.writeRootValueSeparator(g);
        g.flush();
        assertEquals(" ", sw.toString());
    }

    // writeRootValueSeparator writes nothing when separator is null.
    @Test
    public void testWriteRootValueSeparator_null_writesNothing() throws Throwable {
        DefaultPrettyPrinter pp = new DefaultPrettyPrinter((String) null);
        StringWriter sw = new StringWriter();
        JsonGenerator g = factory.createGenerator(sw);
        pp.writeRootValueSeparator(g);
        g.flush();
        assertEquals("", sw.toString());
    }

    // writeStartObject with a non-inline object indenter writes '{' and increments nesting.
    @Test
    public void testWriteStartObject_nonInlineIndenter_incrementsNesting() throws Throwable {
        DefaultPrettyPrinter pp = new DefaultPrettyPrinter();
        StringWriter sw = new StringWriter();
        JsonGenerator g = factory.createGenerator(sw);
        pp.writeStartObject(g);
        g.flush();
        assertEquals("{", sw.toString());
        assertEquals(1, pp._nesting);
    }

    // writeStartObject with an inline object indenter leaves nesting unchanged.
    @Test
    public void testWriteStartObject_inlineIndenter_nestingUnchanged() throws Throwable {
        DefaultPrettyPrinter pp = new DefaultPrettyPrinter();
        pp.indentObjectsWith(DefaultPrettyPrinter.NopIndenter.instance);
        StringWriter sw = new StringWriter();
        JsonGenerator g = factory.createGenerator(sw);
        pp.writeStartObject(g);
        g.flush();
        assertEquals("{", sw.toString());
        assertEquals(0, pp._nesting);
    }

    // beforeObjectEntries invokes the indenter with the current (post-increment) nesting level.
    @Test
    public void testBeforeObjectEntries_usesCurrentNestingLevel() throws Throwable {
        DefaultPrettyPrinter pp = new DefaultPrettyPrinter();
        pp.indentObjectsWith(new MarkerIndenter());
        StringWriter sw = new StringWriter();
        JsonGenerator g = factory.createGenerator(sw);
        pp.writeStartObject(g);
        pp.beforeObjectEntries(g);
        g.flush();
        assertEquals("{<1>", sw.toString());
    }

    // writeObjectFieldValueSeparator writes a space-padded colon when spaces flag is true (default).
    @Test
    public void testWriteObjectFieldValueSeparator_spacesTrue_writesSpacedColon() throws Throwable {
        DefaultPrettyPrinter pp = new DefaultPrettyPrinter();
        StringWriter sw = new StringWriter();
        JsonGenerator g = factory.createGenerator(sw);
        pp.writeObjectFieldValueSeparator(g);
        g.flush();
        assertEquals(" : ", sw.toString());
    }

    // writeObjectFieldValueSeparator writes a bare colon when spaces flag is false.
    @Test
    public void testWriteObjectFieldValueSeparator_spacesFalse_writesBareColon() throws Throwable {
        DefaultPrettyPrinter pp = new DefaultPrettyPrinter().withoutSpacesInObjectEntries();
        StringWriter sw = new StringWriter();
        JsonGenerator g = factory.createGenerator(sw);
        pp.writeObjectFieldValueSeparator(g);
        g.flush();
        assertEquals(":", sw.toString());
    }

    // writeObjectEntrySeparator writes comma followed by indentation at current nesting.
    @Test
    public void testWriteObjectEntrySeparator_writesCommaThenIndentation() throws Throwable {
        DefaultPrettyPrinter pp = new DefaultPrettyPrinter();
        pp.indentObjectsWith(new MarkerIndenter());
        StringWriter sw = new StringWriter();
        JsonGenerator g = factory.createGenerator(sw);
        pp.writeObjectEntrySeparator(g);
        g.flush();
        assertEquals(",<0>", sw.toString());
    }

    // writeEndObject with entries>0 decrements nesting first then indents at the closed level.
    @Test
    public void testWriteEndObject_withEntries_indentsAtDecrementedLevel() throws Throwable {
        DefaultPrettyPrinter pp = new DefaultPrettyPrinter();
        pp.indentObjectsWith(new MarkerIndenter());
        StringWriter sw = new StringWriter();
        JsonGenerator g = factory.createGenerator(sw);
        pp.writeStartObject(g);
        pp.writeEndObject(g, 1);
        g.flush();
        assertEquals("{<0>}", sw.toString());
        assertEquals(0, pp._nesting);
    }

    // writeEndObject with zero entries writes a single space then closing brace (no indenter call).
    @Test
    public void testWriteEndObject_noEntries_writesSpaceThenBrace() throws Throwable {
        DefaultPrettyPrinter pp = new DefaultPrettyPrinter();
        StringWriter sw = new StringWriter();
        JsonGenerator g = factory.createGenerator(sw);
        pp.writeStartObject(g);
        pp.writeEndObject(g, 0);
        g.flush();
        assertEquals("{ }", sw.toString());
    }

    // writeStartArray with default inline array indenter leaves nesting unchanged.
    @Test
    public void testWriteStartArray_inlineDefaultIndenter_nestingUnchanged() throws Throwable {
        DefaultPrettyPrinter pp = new DefaultPrettyPrinter();
        StringWriter sw = new StringWriter();
        JsonGenerator g = factory.createGenerator(sw);
        pp.writeStartArray(g);
        g.flush();
        assertEquals("[", sw.toString());
        assertEquals(0, pp._nesting);
    }

    // writeStartArray with a non-inline array indenter increments nesting.
    @Test
    public void testWriteStartArray_nonInlineIndenter_incrementsNesting() throws Throwable {
        DefaultPrettyPrinter pp = new DefaultPrettyPrinter();
        pp.indentArraysWith(new MarkerIndenter());
        StringWriter sw = new StringWriter();
        JsonGenerator g = factory.createGenerator(sw);
        pp.writeStartArray(g);
        g.flush();
        assertEquals("[", sw.toString());
        assertEquals(1, pp._nesting);
    }

    // beforeArrayValues with default FixedSpaceIndenter writes exactly one space (per Javadoc).
    @Test
    public void testBeforeArrayValues_fixedSpaceIndenter_writesSingleSpace() throws Throwable {
        DefaultPrettyPrinter pp = new DefaultPrettyPrinter();
        StringWriter sw = new StringWriter();
        JsonGenerator g = factory.createGenerator(sw);
        pp.beforeArrayValues(g);
        g.flush();
        assertEquals(" ", sw.toString());
    }

    // writeArrayValueSeparator default writes comma then the array indenter's single space.
    @Test
    public void testWriteArrayValueSeparator_default_writesCommaAndSpace() throws Throwable {
        DefaultPrettyPrinter pp = new DefaultPrettyPrinter();
        StringWriter sw = new StringWriter();
        JsonGenerator g = factory.createGenerator(sw);
        pp.writeArrayValueSeparator(g);
        g.flush();
        assertEquals(", ", sw.toString());
    }

    // writeEndArray with values>0 and non-inline indenter indents at the decremented nesting level.
    @Test
    public void testWriteEndArray_withValues_indentsAtDecrementedLevel() throws Throwable {
        DefaultPrettyPrinter pp = new DefaultPrettyPrinter();
        pp.indentArraysWith(new MarkerIndenter());
        StringWriter sw = new StringWriter();
        JsonGenerator g = factory.createGenerator(sw);
        pp.writeStartArray(g);
        pp.writeEndArray(g, 1);
        g.flush();
        assertEquals("[<0>]", sw.toString());
        assertEquals(0, pp._nesting);
    }

    // writeEndArray with zero values writes a single space then closing bracket.
    @Test
    public void testWriteEndArray_noValues_writesSpaceThenBracket() throws Throwable {
        DefaultPrettyPrinter pp = new DefaultPrettyPrinter();
        StringWriter sw = new StringWriter();
        JsonGenerator g = factory.createGenerator(sw);
        pp.writeStartArray(g);
        pp.writeEndArray(g, 0);
        g.flush();
        assertEquals("[ ]", sw.toString());
    }

    // NopIndenter is inline and adds no indentation whatsoever (per Javadoc).
    @Test
    public void testNopIndenter_isInlineAndWritesNothing() throws Throwable {
        assertTrue(DefaultPrettyPrinter.NopIndenter.instance.isInline());
        StringWriter sw = new StringWriter();
        JsonGenerator g = factory.createGenerator(sw);
        DefaultPrettyPrinter.NopIndenter.instance.writeIndentation(g, 5);
        g.flush();
        assertEquals("", sw.toString());
    }

    // FixedSpaceIndenter is inline and always writes a single space regardless of level.
    @Test
    public void testFixedSpaceIndenter_isInlineAndWritesSingleSpaceAnyLevel() throws Throwable {
        assertTrue(DefaultPrettyPrinter.FixedSpaceIndenter.instance.isInline());
        StringWriter sw = new StringWriter();
        JsonGenerator g = factory.createGenerator(sw);
        DefaultPrettyPrinter.FixedSpaceIndenter.instance.writeIndentation(g, 100);
        g.flush();
        assertEquals(" ", sw.toString());
    }

    // Integration: real generator round-trip must produce properly spaced colon inside braces.
    @Test
    public void testIntegration_defaultPrettyPrinter_objectFieldColonSpacing() throws Throwable {
        StringWriter sw = new StringWriter();
        JsonGenerator g = factory.createGenerator(sw);
        g.setPrettyPrinter(new DefaultPrettyPrinter());
        g.writeStartObject();
        g.writeFieldName("a");
        g.writeNumber(1);
        g.writeEndObject();
        g.flush();
        String out = sw.toString();
        assertTrue(out.contains("\"a\" : 1"));
        assertTrue(out.startsWith("{"));
        assertTrue(out.endsWith("}"));
    }
}
