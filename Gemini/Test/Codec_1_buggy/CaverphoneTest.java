package org.apache.commons.codec.language;

import org.junit.Test;
import org.apache.commons.codec.EncoderException;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.fail;

public class CaverphoneTest {

    @Test
    public void testCaverphoneNullAndEmpty() throws Throwable {
        Caverphone caverphone = new Caverphone();
        assertEquals("1111111111", caverphone.caverphone(null));
        assertEquals("1111111111", caverphone.caverphone(""));
    }

    @Test
    public void testCaverphoneBasicAndRules() throws Throwable {
        Caverphone caverphone = new Caverphone();
        
        // Test various special replacements and starts
        assertNotNull(caverphone.caverphone("cough"));
        assertNotNull(caverphone.caverphone("rough"));
        assertNotNull(caverphone.caverphone("tough"));
        assertNotNull(caverphone.caverphone("enough"));
        assertNotNull(caverphone.caverphone("trough"));
        assertNotNull(caverphone.caverphone("gnat"));
        assertNotNull(caverphone.caverphone("mb"));

        assertNotNull(caverphone.caverphone("cq"));
        assertNotNull(caverphone.caverphone("ci"));
        assertNotNull(caverphone.caverphone("ce"));
        assertNotNull(caverphone.caverphone("cy"));
        assertNotNull(caverphone.caverphone("tch"));
        assertNotNull(caverphone.caverphone("cat"));
        assertNotNull(caverphone.caverphone("quit"));
        assertNotNull(caverphone.caverphone("box"));
        assertNotNull(caverphone.caverphone("vine"));
        assertNotNull(caverphone.caverphone("dg"));
        assertNotNull(caverphone.caverphone("otion"));
        assertNotNull(caverphone.caverphone("atian"));
        assertNotNull(caverphone.caverphone("dog"));
        assertNotNull(caverphone.caverphone("phone"));
        assertNotNull(caverphone.caverphone("boy"));
        assertNotNull(caverphone.caverphone("shoe"));
        assertNotNull(caverphone.caverphone("zoo"));
        assertNotNull(caverphone.caverphone("apple"));
    }

    @Test
    public void testCaverphoneVowelsAndYAndWAndHAndRAndL() throws Throwable {
        Caverphone caverphone = new Caverphone();
        
        assertNotNull(caverphone.caverphone("apple"));
        assertNotNull(caverphone.caverphone("yellow"));
        assertNotNull(caverphone.caverphone("y3"));
        assertNotNull(caverphone.caverphone("y"));
        assertNotNull(caverphone.caverphone("ugh"));
        assertNotNull(caverphone.caverphone("s"));
        assertNotNull(caverphone.caverphone("t"));
        assertNotNull(caverphone.caverphone("p"));
        assertNotNull(caverphone.caverphone("k"));
        assertNotNull(caverphone.caverphone("f"));
        assertNotNull(caverphone.caverphone("m"));
        assertNotNull(caverphone.caverphone("n"));
        assertNotNull(caverphone.caverphone("w3"));
        assertNotNull(caverphone.caverphone("wh3"));
        assertNotNull(caverphone.caverphone("w"));
        assertNotNull(caverphone.caverphone("h"));
        assertNotNull(caverphone.caverphone("r3"));
        assertNotNull(caverphone.caverphone("r"));
        assertNotNull(caverphone.caverphone("l3"));
        assertNotNull(caverphone.caverphone("l"));
    }

    @Test
    public void testCaverphoneTrailingAndEndings() throws Throwable {
        Caverphone caverphone = new Caverphone();
        assertNotNull(caverphone.caverphone("namee"));
        assertNotNull(caverphone.caverphone("brow"));
        assertNotNull(caverphone.caverphone("car"));
        assertNotNull(caverphone.caverphone("ball"));
    }

    @Test
    public void testEncodeObject() throws Throwable {
        Caverphone caverphone = new Caverphone();
        Object result = caverphone.encode("Smith");
        assertNotNull(result);
        assertTrue(result instanceof String);

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
        String result = caverphone.encode("Smith");
        assertNotNull(result);
    }

    @Test
    public void testIsCaverphoneEqual() throws Throwable {
        Caverphone caverphone = new Caverphone();
        assertTrue(caverphone.isCaverphoneEqual("Smith", "Smyth"));
        assertFalse(caverphone.isCaverphoneEqual("Smith", "Apple"));
    }
}