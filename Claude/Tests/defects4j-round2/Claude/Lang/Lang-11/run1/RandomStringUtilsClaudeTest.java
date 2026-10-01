package org.apache.commons.lang3;

import java.util.Random;
import org.junit.Test;
import static org.junit.Assert.*;

public class RandomStringUtilsClaudeTest {

    // covers: public no-arg constructor
    @Test
    public void testConstructor_default_createsInstance() throws Throwable {
        RandomStringUtils instance = new RandomStringUtils();
        assertNotNull(instance);
    }

    // covers: count==0 branch in random(int)
    @Test
    public void testRandom_countZero_returnsEmptyString() throws Throwable {
        assertEquals("", RandomStringUtils.random(0));
    }

    // covers: count<0 branch in random(int)
    @Test
    public void testRandom_countNegative_throwsIllegalArgumentException() throws Throwable {
        try {
            RandomStringUtils.random(-3);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("less than 0"));
        }
    }

    // covers: count>0 happy path, full character range
    @Test
    public void testRandom_countPositive_returnsStringOfRequestedLength() throws Throwable {
        String result = RandomStringUtils.random(12);
        assertEquals(12, result.length());
    }

    // covers: randomAscii delegates to range [32,127)
    @Test
    public void testRandomAscii_countPositive_allCharsInPrintableAsciiRange() throws Throwable {
        String result = RandomStringUtils.randomAscii(50);
        assertEquals(50, result.length());
        for (int i = 0; i < result.length(); i++) {
            char c = result.charAt(i);
            assertTrue(c >= 32 && c <= 126);
        }
    }

    // covers: count==0 short-circuit reused by randomAscii
    @Test
    public void testRandomAscii_countZero_returnsEmptyString() throws Throwable {
        assertEquals("", RandomStringUtils.randomAscii(0));
    }

    // covers: letters=true,numbers=false filter branch
    @Test
    public void testRandomAlphabetic_countPositive_allCharsAreLetters() throws Throwable {
        String result = RandomStringUtils.randomAlphabetic(40);
        assertEquals(40, result.length());
        for (int i = 0; i < result.length(); i++) {
            assertTrue(Character.isLetter(result.charAt(i)));
        }
    }

    // covers: letters=true,numbers=true combined filter branch
    @Test
    public void testRandomAlphanumeric_countPositive_allCharsAreLetterOrDigit() throws Throwable {
        String result = RandomStringUtils.randomAlphanumeric(40);
        assertEquals(40, result.length());
        for (int i = 0; i < result.length(); i++) {
            char c = result.charAt(i);
            assertTrue(Character.isLetter(c) || Character.isDigit(c));
        }
    }

    // covers: letters=false,numbers=true filter branch
    @Test
    public void testRandomNumeric_countPositive_allCharsAreDigits() throws Throwable {
        String result = RandomStringUtils.randomNumeric(40);
        assertEquals(40, result.length());
        for (int i = 0; i < result.length(); i++) {
            assertTrue(Character.isDigit(result.charAt(i)));
        }
    }

    // covers: random(count,letters,numbers) both false -> accept-all branch
    @Test
    public void testRandom3arg_lettersFalseNumbersFalse_returnsRequestedLength() throws Throwable {
        String result = RandomStringUtils.random(25, false, false);
        assertEquals(25, result.length());
    }

    // covers: random(count,letters,numbers) letters-only branch
    @Test
    public void testRandom3arg_lettersTrueNumbersFalse_allCharsAreLetters() throws Throwable {
        String result = RandomStringUtils.random(30, true, false);
        for (int i = 0; i < result.length(); i++) {
            assertTrue(Character.isLetter(result.charAt(i)));
        }
    }

    // covers: random(count,letters,numbers) numbers-only branch
    @Test
    public void testRandom3arg_lettersFalseNumbersTrue_allCharsAreDigits() throws Throwable {
        String result = RandomStringUtils.random(30, false, true);
        for (int i = 0; i < result.length(); i++) {
            assertTrue(Character.isDigit(result.charAt(i)));
        }
    }

    // covers: random(count,letters,numbers) combined letters-or-digits branch
    @Test
    public void testRandom3arg_lettersTrueNumbersTrue_allCharsAreLetterOrDigit() throws Throwable {
        String result = RandomStringUtils.random(30, true, true);
        for (int i = 0; i < result.length(); i++) {
            char c = result.charAt(i);
            assertTrue(Character.isLetter(c) || Character.isDigit(c));
        }
    }

    // covers: random(count,start,end,letters,numbers) explicit non-zero range
    @Test
    public void testRandom5arg_rangeStartEnd_charsWithinBounds() throws Throwable {
        String result = RandomStringUtils.random(20, 65, 91, false, false);
        for (int i = 0; i < result.length(); i++) {
            char c = result.charAt(i);
            assertTrue(c >= 65 && c < 91);
        }
    }

    // covers: count<0 propagated through 5-arg overload
    @Test
    public void testRandom5arg_negativeCount_throwsIllegalArgumentException() throws Throwable {
        try {
            RandomStringUtils.random(-1, 0, 0, false, false);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("less than 0"));
        }
    }

    // covers: letters filter applied over custom mixed range containing letters and digits
    @Test
    public void testRandom5arg_lettersTrueInMixedRange_allCharsAreLetters() throws Throwable {
        String result = RandomStringUtils.random(30, 48, 123, true, false);
        for (int i = 0; i < result.length(); i++) {
            assertTrue(Character.isLetter(result.charAt(i)));
        }
    }

    // covers: random(count,start,end,letters,numbers,chars) with start==end==0 and non-null chars
    @Test
    public void testRandom6argVarargs_withCharsArray_onlyUsesGivenChars() throws Throwable {
        char[] chars = {'m', 'n', 'o', 'p'};
        String result = RandomStringUtils.random(20, 0, 0, false, false, chars);
        String setStr = new String(chars);
        for (int i = 0; i < result.length(); i++) {
            assertTrue(setStr.indexOf(result.charAt(i)) >= 0);
        }
    }

    // covers: empty chars array check reached via 6-arg varargs overload
    @Test
    public void testRandom6argVarargs_emptyCharsArray_throwsIllegalArgumentException() throws Throwable {
        try {
            RandomStringUtils.random(5, 0, 0, false, false, new char[0]);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("empty"));
        }
    }

    // covers: null chars forwarded correctly through 6-arg varargs overload
    @Test
    public void testRandom6argVarargs_nullChars_returnsRequestedLength() throws Throwable {
        String result = RandomStringUtils.random(10, 0, 0, false, false, (char[]) null);
        assertEquals(10, result.length());
    }

    // covers: 7-arg method with user-supplied seeded Random is reproducible
    @Test
    public void testRandom7arg_seededRandom_deterministicSameSeedSameResult() throws Throwable {
        char[] chars = {'a', 'b', 'c', 'd'};
        String s1 = RandomStringUtils.random(10, 0, 0, false, false, chars, new Random(12345L));
        String s2 = RandomStringUtils.random(10, 0, 0, false, false, chars, new Random(12345L));
        assertEquals(s1, s2);
    }

    // covers: chars != null && chars.length==0 branch in the 7-arg master method
    @Test
    public void testRandom7arg_charsArrayEmpty_throwsIllegalArgumentException() throws Throwable {
        try {
            RandomStringUtils.random(5, 0, 0, false, false, new char[0], new Random(1L));
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("must not be empty"));
        }
    }

    // covers: count==0 branch short-circuits before the empty-chars check is evaluated
    @Test
    public void testRandom7arg_countZero_returnsEmptyStringEvenWithEmptyChars() throws Throwable {
        String result = RandomStringUtils.random(0, 0, 0, false, false, new char[0], new Random(1L));
        assertEquals("", result);
    }

    // covers: count<0 branch in the 7-arg master method
    @Test
    public void testRandom7arg_countNegative_throwsIllegalArgumentException() throws Throwable {
        try {
            RandomStringUtils.random(-1, 0, 0, false, false, (char[]) null, new Random(1L));
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("less than 0"));
        }
    }

    // covers: start==end (non-zero) -> gap==0 -> Random#nextInt contract throws IllegalArgumentException
    @Test
    public void testRandom7arg_startEndEqual_throwsIllegalArgumentExceptionFromZeroBound() throws Throwable {
        try {
            RandomStringUtils.random(3, 5, 5, false, false, (char[]) null, new Random(1L));
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // covers: gap==1 deterministic index selection regardless of random source
    @Test
    public void testRandom7arg_singleCharArray_everyCharEqualsGivenChar() throws Throwable {
        char[] chars = {'Q'};
        String result = RandomStringUtils.random(6, 0, 1, false, false, chars, new Random(999L));
        assertEquals(6, result.length());
        for (int i = 0; i < result.length(); i++) {
            assertEquals('Q', result.charAt(i));
        }
    }

    // covers: start==0 && end==0, chars==null, !letters&&!numbers -> Integer.MAX_VALUE branch
    @Test
    public void testRandom7arg_startZeroEndZeroLettersNumbersFalse_fullRangeNoException() throws Throwable {
        String result = RandomStringUtils.random(5, 0, 0, false, false, (char[]) null, new Random(7L));
        assertEquals(5, result.length());
    }

    // covers: random(int,String) chars==null branch
    @Test
    public void testRandomStringOverload_nullChars_returnsRequestedLength() throws Throwable {
        String result = RandomStringUtils.random(8, (String) null);
        assertEquals(8, result.length());
    }

    // covers: random(int,String) empty string -> empty char array check downstream
    @Test
    public void testRandomStringOverload_emptyString_throwsIllegalArgumentException() throws Throwable {
        try {
            RandomStringUtils.random(5, "");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("empty"));
        }
    }

    // covers: random(int,String) non-null non-empty chars path
    @Test
    public void testRandomStringOverload_validChars_onlyFromGivenSet() throws Throwable {
        String set = "xyz";
        String result = RandomStringUtils.random(20, set);
        for (int i = 0; i < result.length(); i++) {
            assertTrue(set.indexOf(result.charAt(i)) >= 0);
        }
    }

    // covers: count<0 propagated through random(int,String) overload
    @Test
    public void testRandomStringOverload_negativeCount_throwsIllegalArgumentException() throws Throwable {
        try {
            RandomStringUtils.random(-5, "abc");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("less than 0"));
        }
    }

    // covers: random(int,char...) chars==null branch
    @Test
    public void testRandomCharArrayOverload_nullChars_returnsRequestedLength() throws Throwable {
        String result = RandomStringUtils.random(7, (char[]) null);
        assertEquals(7, result.length());
    }

    // covers: random(int,char...) non-null chars path, full array used as range
    @Test
    public void testRandomCharArrayOverload_validChars_onlyFromGivenSet() throws Throwable {
        char[] set = {'1', '2', '3'};
        String result = RandomStringUtils.random(15, set);
        String setStr = new String(set);
        for (int i = 0; i < result.length(); i++) {
            assertTrue(setStr.indexOf(result.charAt(i)) >= 0);
        }
    }

    // covers: empty char array triggers empty-chars check downstream
    @Test
    public void testRandomCharArrayOverload_emptyArray_throwsIllegalArgumentException() throws Throwable {
        try {
            RandomStringUtils.random(4, new char[0]);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("empty"));
        }
    }

    // covers: count<0 propagated through random(int,char...) overload
    @Test
    public void testRandomCharArrayOverload_negativeCount_throwsIllegalArgumentException() throws Throwable {
        try {
            RandomStringUtils.random(-2, new char[]{'a'});
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("less than 0"));
        }
    }

    // covers: random(int,char...) with single-element array yields deterministic repeated char
    @Test
    public void testRandomCharArrayOverload_singleChar_deterministicRepeatedChar() throws Throwable {
        char[] set = {'Z'};
        String result = RandomStringUtils.random(9, set);
        assertEquals(9, result.length());
        for (int i = 0; i < result.length(); i++) {
            assertEquals('Z', result.charAt(i));
        }
    }
}
