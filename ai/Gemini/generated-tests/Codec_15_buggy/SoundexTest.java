package org.apache.commons.codec.language;

import org.apache.commons.codec.EncoderException;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class SoundexTest {

    @Test
    public void testDefaultConstructor() throws Throwable {
        Soundex soundex = new Soundex();
        assertNotNull(soundex);
        assertEquals(4, soundex.getMaxLength());
    }

    @Test
    public void testCharArrayConstructor() throws Throwable {
        char[] mapping = Soundex.US_ENGLISH_MAPPING_STRING.toCharArray();
        Soundex soundex = new Soundex(mapping);
        assertNotNull(soundex);
    }

    @Test
    public void testStringConstructor() throws Throwable {
        Soundex soundex = new Soundex(Soundex.US_ENGLISH_MAPPING_STRING);
        assertNotNull(soundex);
    }

    @Test
    public void testGetMaxLengthAndSetMaxLength() throws Throwable {
        Soundex soundex = new Soundex();
        soundex.setMaxLength(5);
        assertEquals(5, soundex.getMaxLength());
    }

    @Test
    public void testSoundexNull() throws Throwable {
        Soundex soundex = new Soundex();
        assertNull(soundex.soundex(null));
    }

    @Test
    public void testSoundexEmptyString() throws Throwable {
        Soundex soundex = new Soundex();
        assertEquals("", soundex.soundex(""));
    }

    @Test
    public void testSoundexBasic() throws Throwable {
        Soundex soundex = new Soundex();
        assertEquals("S530", soundex.soundex("Smith"));
        assertEquals("S530", soundex.soundex("Smythe"));
        assertEquals("W425", soundex.soundex("Washington"));
        assertEquals("R163", soundex.soundex("Robert"));
        assertEquals("B650", soundex.soundex("Barbra"));
    }

    @Test
    public void testSoundexHWRule() throws Throwable {
        Soundex soundex = new Soundex();
        // Testing H and W separation rules
        assertEquals("T522", soundex.soundex("TASHCHENKO"));
        assertEquals("A261", soundex.soundex("Ashcraft"));
    }

    @Test
    public void testSoundexInvalidCharacter() throws Throwable {
        Soundex soundex = new Soundex();
        try {
            soundex.soundex("Smith1");
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("The character is not mapped"));
        }
    }

    @Test
    public void testEncodeString() throws Throwable {
        Soundex soundex = new Soundex();
        assertEquals("S530", soundex.encode("Smith"));
    }

    @Test
    public void testEncodeObjectValid() throws Throwable {
        Soundex soundex = new Soundex();
        Object result = soundex.encode((Object) "Smith");
        assertEquals("S530", result);
    }

    @Test
    public void testEncodeObjectInvalid() throws Throwable {
        Soundex soundex = new Soundex();
        try {
            soundex.encode(Integer.valueOf(123));
            fail("Should have thrown EncoderException");
        } catch (EncoderException e) {
            assertTrue(e.getMessage().contains("Parameter supplied to Soundex encode is not of type"));
        }
    }

    @Test
    public void testDifference() throws Throwable {
        Soundex soundex = new Soundex();
        int diff = soundex.difference("Smith", "Smythe");
        assertEquals(4, diff);
    }

    @Test
    public void testUsEnglishInstance() throws Throwable {
        assertNotNull(Soundex.US_ENGLISH);
        assertEquals("S530", Soundex.US_ENGLISH.soundex("Smith"));
    }
}