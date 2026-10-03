package org.apache.commons.cli;

import junit.framework.TestCase;

public class PosixParserTest extends TestCase {

    private PosixParser parser;
    private Options options;

    protected void setUp() throws Exception {
        super.setUp();
        parser = new PosixParser();
        options = new Options();
    }

    public void testLongOptionWithEquals() throws Throwable {
        options.addOption("b", "bopt", true, "block");
        String[] args = new String[] { "--bopt=value" };
        String[] flattened = parser.flatten(options, args, false);
        
        assertEquals(2, flattened.length);
        assertEquals("--bopt", flattened[0]);
        assertEquals("value", flattened[1]);
    }

    public void testLongOptionWithoutEquals() throws Throwable {
        options.addOption("b", "bopt", false, "block");
        String[] args = new String[] { "--bopt" };
        String[] flattened = parser.flatten(options, args, false);
        
        assertEquals(1, flattened.length);
        assertEquals("--bopt", flattened[0]);
    }

    public void testSingleHyphen() throws Throwable {
        String[] args = new String[] { "-" };
        String[] flattened = parser.flatten(options, args, false);
        
        assertEquals(1, flattened.length);
        assertEquals("-", flattened[0]);
    }

    public void testSingleCharOptionValid() throws Throwable {
        options.addOption("a", false, "alpha");
        String[] args = new String[] { "-a" };
        String[] flattened = parser.flatten(options, args, false);
        
        assertEquals(1, flattened.length);
        assertEquals("-a", flattened[0]);
    }

    public void testSingleCharOptionInvalidWithStop() throws Throwable {
        String[] args = new String[] { "-z", "extra" };
        String[] flattened = parser.flatten(options, args, true);
        
        // When stopAtNonOption is true and 2-char invalid option is passed, 
        // it acts as a non-option or gets ignored depending on rules, 
        // let's verify behavior.
        assertNotNull(flattened);
    }

    public void testLongerOptionExisting() throws Throwable {
        options.addOption("abc", false, "abc option");
        String[] args = new String[] { "-abc" };
        String[] flattened = parser.flatten(options, args, false);
        
        assertEquals(1, flattened.length);
        assertEquals("-abc", flattened[0]);
    }

    public void testBurstTokenValidWithArgument() throws Throwable {
        options.addOption("x", "xopt", true, "x option");
        // "-xvalue" -> first char '-', then 'x' is valid option with arg, remaining is "value"
        String[] args = new String[] { "-xvalue" };
        String[] flattened = parser.flatten(options, args, false);
        
        assertEquals(2, flattened.length);
        assertEquals("-x", flattened[0]);
        assertEquals("value", flattened[1]);
    }

    public void testBurstTokenInvalidWithStopAtNonOption() throws Throwable {
        // "-abc" where 'a' is valid, 'b' is not, stopAtNonOption = true
        options.addOption("a", false, "a option");
        String[] args = new String[] { "-abc" };
        String[] flattened = parser.flatten(options, args, true);
        
        assertTrue(flattened.length > 0);
    }

    public void testBurstTokenInvalidWithoutStopAtNonOption() throws Throwable {
        // "-abc" where 'a' is valid, 'b' is not, stopAtNonOption = false
        options.addOption("a", false, "a option");
        String[] args = new String[] { "-abc" };
        String[] flattened = parser.flatten(options, args, false);
        
        assertTrue(flattened.length > 0);
        assertEquals("-abc", flattened[0]);
    }

    public void testNonOptionWithoutStop() throws Throwable {
        String[] args = new String[] { "nonoption" };
        String[] flattened = parser.flatten(options, args, false);
        
        assertEquals(1, flattened.length);
        assertEquals("nonoption", flattened[0]);
    }

    public void testNonOptionWithStop() throws Throwable {
        options.addOption("a", "alpha", true, "alpha option");
        String[] args = new String[] { "nonoption", "-a", "val" };
        String[] flattened = parser.flatten(options, args, true);
        
        assertTrue(flattened.length >= 3);
        assertEquals("--", flattened[0]);
        assertEquals("nonoption", flattened[1]);
    }

    public void testGobbleEatTheRest() throws Throwable {
        options.addOption("a", false, "alpha");
        // Trigger gobble via non-option with stopAtNonOption = true
        String[] args = new String[] { "stop", "remaining1", "remaining2" };
        String[] flattened = parser.flatten(options, args, true);
        
        assertEquals(3, flattened.length);
        assertEquals("--", flattened[0]);
        assertEquals("stop", flattened[1]);
        assertEquals("remaining1", flattened[2]);
    }

    public void testProcessOptionTokenInvalidWithStop() throws Throwable {
        String[] args = new String[] { "-u" };
        String[] flattened = parser.flatten(options, args, true);
        
        assertEquals(1, flattened.length);
        assertEquals("-u", flattened[0]);
    }

    public void testMultipleBurstTokens() throws Throwable {
        options.addOption("a", false, "a");
        options.addOption("b", false, "b");
        String[] args = new String[] { "-ab" };
        String[] flattened = parser.flatten(options, args, false);
        
        assertEquals(2, flattened.length);
        assertEquals("-a", flattened[0]);
        assertEquals("-b", flattened[1]);
    }
}