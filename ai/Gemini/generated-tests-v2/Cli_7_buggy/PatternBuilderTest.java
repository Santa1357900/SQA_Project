package org.apache.commons.cli2.builder;

import junit.framework.TestCase;
import org.apache.commons.cli2.Option;

public class PatternBuilderTest extends TestCase {

    public void testDefaultConstructor() throws Throwable {
        PatternBuilder builder = new PatternBuilder();
        assertNotNull(builder);
    }

    public void testExplicitConstructor() throws Throwable {
        GroupBuilder gbuilder = new GroupBuilder();
        DefaultOptionBuilder obuilder = new DefaultOptionBuilder();
        ArgumentBuilder abuilder = new ArgumentBuilder();
        PatternBuilder builder = new PatternBuilder(gbuilder, obuilder, abuilder);
        assertNotNull(builder);
    }

    public void testReset() throws Throwable {
        PatternBuilder builder = new PatternBuilder();
        builder.withPattern("a");
        PatternBuilder resetBuilder = builder.reset();
        assertNotNull(resetBuilder);
    }

    public void testCreateSingleOption() throws Throwable {
        PatternBuilder builder = new PatternBuilder();
        builder.withPattern("a");
        Option option = builder.create();
        assertNotNull(option);
        assertEquals("a", option.getPreferredName());
    }

    public void testCreateMultipleOptions() throws Throwable {
        PatternBuilder builder = new PatternBuilder();
        builder.withPattern("ab");
        Option option = builder.create();
        assertNotNull(option);
    }

    public void testPatternTypesAndValidators() throws Throwable {
        char[] types = new char[] {'@', ':', '%', '+', '#', '<', '>', '*', '/'};
        for (int i = 0; i < types.length; i++) {
            PatternBuilder builder = new PatternBuilder();
            String pattern = "a" + types[i];
            builder.withPattern(pattern);
            Option option = builder.create();
            assertNotNull(option);
        }
    }

    public void testPatternRequired() throws Throwable {
        PatternBuilder builder = new PatternBuilder();
        builder.withPattern("!a+");
        Option option = builder.create();
        assertNotNull(option);
        assertTrue(option.isRequired());
    }

    public void testPatternWithSpacesAndUnknownChars() throws Throwable {
        PatternBuilder builder = new PatternBuilder();
        builder.withPattern("a b");
        Option option = builder.create();
        assertNotNull(option);
    }

    public void testValidatorMethodCoverage() throws Throwable {
        char[] allChars = new char[] {'@', '+', ':', '%', '#', '<', '>', '*', '/', 'x'};
        for (int i = 0; i < allChars.length; i++) {
            PatternBuilder builder = new PatternBuilder();
            builder.withPattern("a" + allChars[i]);
            Option option = builder.create();
            assertNotNull(option);
        }
    }
}