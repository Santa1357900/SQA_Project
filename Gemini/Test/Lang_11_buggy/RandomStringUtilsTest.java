package org.apache.commons.lang3;

import org.junit.Test;
import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class RandomStringUtilsTest {

    @Test
    public void testConstructor() throws Throwable {
        RandomStringUtils utils = new RandomStringUtils();
        assertNotNull(utils);
    }

    @Test
    public void testRandomZeroCount() throws Throwable {
        String result = RandomStringUtils.random(0);
        assertEquals("", result);
    }

    @Test
    public void testRandomNegativeCount() throws Throwable {
        try {
            RandomStringUtils.random(-1);
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("less than 0"));
        }
    }

    @Test
    public void testRandomAscii() throws Throwable {
        String result = RandomStringUtils.randomAscii(5);
        assertNotNull(result);
        assertEquals(5, result.length());
    }

    @Test
    public void testRandomAlphabetic() throws Throwable {
        String result = RandomStringUtils.randomAlphabetic(10);
        assertNotNull(result);
        assertEquals(10, result.length());
        for (int i = 0; i < result.length(); i++) {
            assertTrue(Character.isLetter(result.charAt(i)));
        }
    }

    @Test
    public void testRandomAlphanumeric() throws Throwable {
        String result = RandomStringUtils.randomAlphanumeric(10);
        assertNotNull(result);
        assertEquals(10, result.length());
        for (int i = 0; i < result.length(); i++) {
            char c = result.charAt(i);
            assertTrue(Character.isLetter(c) || Character.isDigit(c));
        }
    }

    @Test
    public void testRandomNumeric() throws Throwable {
        String result = RandomStringUtils.randomNumeric(10);
        assertNotNull(result);
        assertEquals(10, result.length());
        for (int i = 0; i < result.length(); i++) {
            assertTrue(Character.isDigit(result.charAt(i)));
        }
    }

    @Test
    public void testRandomWithOptions() throws Throwable {
        String result = RandomStringUtils.random(5, true, true);
        assertNotNull(result);
        assertEquals(5, result.length());
    }

    @Test
    public void testRandomWithStartEnd() throws Throwable {
        String result = RandomStringUtils.random(5, 'a', 'z', true, false);
        assertNotNull(result);
        assertEquals(5, result.length());
    }

    @Test
    public void testRandomWithCharsArray() throws Throwable {
        char[] chars = new char[]{'a', 'b', 'c'};
        String result = RandomStringUtils.random(5, 0, 3, false, false, chars);
        assertNotNull(result);
        assertEquals(5, result.length());
    }

    @Test
    public void testRandomWithEmptyCharsArray() throws Throwable {
        char[] chars = new char[0];
        try {
            RandomStringUtils.random(5, 0, 0, false, false, chars, new Random());
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("must not be empty"));
        }
    }

    @Test
    public void testRandomWithStringChars() throws Throwable {
        String result = RandomStringUtils.random(5, "abc");
        assertNotNull(result);
        assertEquals(5, result.length());
    }

    @Test
    public void testRandomWithNullStringChars() throws Throwable {
        String result = RandomStringUtils.random(5, (String) null);
        assertNotNull(result);
        assertEquals(5, result.length());
    }

    @Test
    public void testRandomWithCharVarargs() throws Throwable {
        String result = RandomStringUtils.random(5, 'x', 'y', 'z');
        assertNotNull(result);
        assertEquals(5, result.length());
    }

    @Test
    public void testRandomWithNullCharVarargs() throws Throwable {
        char[] chars = null;
        String result = RandomStringUtils.random(5, chars);
        assertNotNull(result);
        assertEquals(5, result.length());
    }

    @Test
    public void testRandomOnlyLettersAndNumbersFalse() throws Throwable {
        String result = RandomStringUtils.random(5, 0, 0, false, false, null, new Random());
        assertNotNull(result);
        assertEquals(5, result.length());
    }

    @Test
    public void testRandomWithSpecificRandom() throws Throwable {
        Random rnd = new Random(123L);
        String result = RandomStringUtils.random(5, 65, 90, true, true, null, rnd);
        assertNotNull(result);
        assertEquals(5, result.length());
    }

    @Test
    public void testSurrogatesHandling() throws Throwable {
        // Force testing surrogates or specific ranges by mocking or supplying custom generator/chars if possible,
        // or just invoke random with a specific setup to cover branches.
        char[] surrogateChars = new char[10];
        for (int i = 0; i < 10; i++) {
            surrogateChars[i] = (char) (56320 + i); // low surrogate range
        }
        String result = RandomStringUtils.random(4, 0, surrogateChars.length, false, false, surrogateChars, new Random());
        assertNotNull(result);
    }
}