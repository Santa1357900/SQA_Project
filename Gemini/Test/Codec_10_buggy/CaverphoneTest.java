package org.apache.commons.codec.language;

import org.apache.commons.codec.EncoderException;
import org.junit.Test;
import static org.junit.Assert.*;

public class CaverphoneTest {

    @Test
    public void testConstructor() throws Throwable {
        Caverphone caverphone = new Caverphone();
        assertNotNull(caverphone);
    }

    @Test
    public void testCaverphoneNullAndEmpty() throws Throwable {
        Caverphone caverphone = new Caverphone();
        assertEquals("1111111111", caverphone.caverphone(null));
        assertEquals("1111111111", caverphone.caverphone(""));
    }

    @Test
    public void testCaverphoneBasicAndRules() throws Throwable {
        Caverphone caverphone = new Caverphone();
        
        // Test various replacement and start rules
        String resultCough = caverphone.caverphone("cough");
        assertNotNull(resultCough);
        
        String resultRough = caverphone.caverphone("rough");
        assertNotNull(resultRough);
        
        String resultTough = caverphone.caverphone("tough");
        assertNotNull(resultTough);
        
        String resultEnough = caverphone.caverphone("enough");
        assertNotNull(resultEnough);
        
        String resultTrough = caverphone.caverphone("trough");
        assertNotNull(resultTrough);
        
        String resultGn = caverphone.caverphone("gnat");
        assertNotNull(resultGn);
        
        String resultMb = caverphone.caverphone("mb");
        assertNotNull(resultMb);
    }

    @Test
    public void testCaverphoneComplexTransformations() throws Throwable {
        Caverphone caverphone = new Caverphone();
        
        // Testing characters: c, q, x, v, dg, tio, tia, d, ph, b, sh, z, etc.
        String input = "Quick brown fox jumps over the lazy dog 123!";
        String encoded = caverphone.caverphone(input);
        assertEquals(10, encoded.length());
        
        // Testing vowels at start and middle
        assertEquals("A111111111", caverphone.caverphone("a"));
        assertEquals("3111111111", caverphone.caverphone("ba"));
    }

    @Test
    public void testCaverphoneSubstitutionsAndEndRules() throws Throwable {
        Caverphone caverphone = new Caverphone();
        
        // Testing 'y', 'w', 'h', 'r', 'l' rules at start and end
        assertNotNull(caverphone.caverphone("yellow"));
        assertNotNull(caverphone.caverphone("window"));
        assertNotNull(caverphone.caverphone("hello"));
        assertNotNull(caverphone.caverphone("roar"));
        assertNotNull(caverphone.caverphone("apple"));
    }

    @Test
    public void testEncodeObjectValid() throws Throwable {
        Caverphone caverphone = new Caverphone();
        Object obj = caverphone.encode((Object) "Test");
        assertNotNull(obj);
        assertTrue(obj instanceof String);
    }

    @Test
    public void testEncodeObjectInvalid() throws Throwable {
        Caverphone caverphone = new Caverphone();
        try {
            caverphone.encode(Integer.valueOf(123));
            fail("Expected EncoderException");
        } catch (EncoderException e) {
            assertTrue(e.getMessage().contains("is not of type java.lang.String"));
        }
    }

    @Test
    public void testEncodeString() throws Throwable {
        Caverphone caverphone = new Caverphone();
        String result = caverphone.encode("TestString");
        assertNotNull(result);
        assertEquals(10, result.length());
    }

    @Test
    public void testIsCaverphoneEqual() throws Throwable {
        Caverphone caverphone = new Caverphone();
        assertTrue(caverphone.isCaverphoneEqual("Rough", "rough"));
        assertFalse(caverphone.isCaverphoneEqual("Rough", "Different"));
    }
}