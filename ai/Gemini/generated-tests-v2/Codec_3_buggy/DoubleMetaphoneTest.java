package org.apache.commons.codec.language;

import org.junit.Test;
import org.apache.commons.codec.EncoderException;
import static org.junit.Assert.*;

public class DoubleMetaphoneTest {

    @Test
    public void testDefaultConstructor() throws Throwable {
        DoubleMetaphone dm = new DoubleMetaphone();
        assertEquals(4, dm.getMaxCodeLen());
    }

    @Test
    public void testMaxCodeLenGetSet() throws Throwable {
        DoubleMetaphone dm = new DoubleMetaphone();
        dm.setMaxCodeLen(6);
        assertEquals(6, dm.getMaxCodeLen());
    }

    @Test
    public void testCleanInputNull() throws Throwable {
        DoubleMetaphone dm = new DoubleMetaphone();
        assertNull(dm.doubleMetaphone(null));
    }

    @Test
    public void testCleanInputEmpty() throws Throwable {
        DoubleMetaphone dm = new DoubleMetaphone();
        assertNull(dm.doubleMetaphone(""));
        assertNull(dm.doubleMetaphone("   "));
    }

    @Test
    public void testEncodeObjectString() throws Throwable {
        DoubleMetaphone dm = new DoubleMetaphone();
        Object encoded = dm.encode("Smith");
        assertEquals("SMTH", encoded);
    }

    @Test
    public void testEncodeObjectNonString() throws Throwable {
        DoubleMetaphone dm = new DoubleMetaphone();
        try {
            dm.encode(Integer.valueOf(123));
            fail("Expected EncoderException");
        } catch (EncoderException e) {
            assertTrue(e.getMessage().contains("not of type String"));
        }
    }

    @Test
    public void testEncodeStringWrapper() throws Throwable {
        DoubleMetaphone dm = new DoubleMetaphone();
        assertEquals("SMTH", dm.encode("Smith"));
    }

    @Test
    public void testIsDoubleMetaphoneEqual() throws Throwable {
        DoubleMetaphone dm = new DoubleMetaphone();
        assertTrue(dm.isDoubleMetaphoneEqual("Smith", "Smyth"));
        assertTrue(dm.isDoubleMetaphoneEqual("Smith", "Smyth", true));
        assertFalse(dm.isDoubleMetaphoneEqual("Smith", "Apple"));
    }

    @Test
    public void testSilentStart() throws Throwable {
        DoubleMetaphone dm = new DoubleMetaphone();
        assertEquals("N", dm.doubleMetaphone("Gnat"));
        assertEquals("N", dm.doubleMetaphone("Knot"));
        assertEquals("N", dm.doubleMetaphone("Pneumonia"));
        assertEquals("R", dm.doubleMetaphone("Wreck"));
        assertEquals("S", dm.doubleMetaphone("Psychology"));
    }

    @Test
    public void testVowelsAndY() throws Throwable {
        DoubleMetaphone dm = new DoubleMetaphone();
        assertEquals("A", dm.doubleMetaphone("A"));
        assertEquals("E", dm.doubleMetaphone("E"));
        assertEquals("I", dm.doubleMetaphone("I"));
        assertEquals("O", dm.doubleMetaphone("O"));
        assertEquals("U", dm.doubleMetaphone("U"));
        assertEquals("Y", dm.doubleMetaphone("Y"));
        assertEquals("A", dm.doubleMetaphone("Apple"));
    }

    @Test
    public void testLetterB() throws Throwable {
        DoubleMetaphone dm = new DoubleMetaphone();
        assertEquals("P", dm.doubleMetaphone("B"));
        assertEquals("P", dm.doubleMetaphone("BB"));
    }

    @Test
    public void testCedillaAndTildeN() throws Throwable {
        DoubleMetaphone dm = new DoubleMetaphone();
        assertEquals("S", dm.doubleMetaphone("\u00C7ar"));
        assertEquals("N", dm.doubleMetaphone("\u00D1o"));
    }

    @Test
    public void testLetterC() throws Throwable {
        DoubleMetaphone dm = new DoubleMetaphone();
        assertEquals("K", dm.doubleMetaphone("Cat"));
        assertEquals("S", dm.doubleMetaphone("Caesar"));
        assertEquals("X", dm.doubleMetaphone("Focaccia"));
        assertEquals("S", dm.doubleMetaphone("Czerny"));
        assertEquals("WICZ", dm.doubleMetaphone("Wicz"));
        assertEquals("X", dm.doubleMetaphone("Ci"));
        assertEquals("S", dm.doubleMetaphone("Ce"));
        assertEquals("K", dm.doubleMetaphone("CK"));
        assertEquals("K", dm.doubleMetaphone("CG"));
        assertEquals("K", dm.doubleMetaphone("CQ"));
        assertEquals("SK", dm.doubleMetaphone("McClelland"));
        assertEquals("K", dm.doubleMetaphone("CC"));
        assertEquals("X", dm.doubleMetaphone("Bacci"));
        assertEquals("KS", dm.doubleMetaphone("Accident"));
        assertEquals("K", dm.doubleMetaphone("Bacchus"));
    }

    @Test
    public void testLetterCH() throws Throwable {
        DoubleMetaphone dm = new DoubleMetaphone();
        assertEquals("K", dm.doubleMetaphone("Michael"));
        assertEquals("K", dm.doubleMetaphone("Chemistry"));
        assertEquals("X", dm.doubleMetaphone("Child"));
        assertEquals("K", dm.doubleMetaphone("McChef"));
    }

    @Test
    public void testLetterD() throws Throwable {
        DoubleMetaphone dm = new DoubleMetaphone();
        assertEquals("J", dm.doubleMetaphone("Edge"));
        assertEquals("TK", dm.doubleMetaphone("Edgar"));
        assertEquals("T", dm.doubleMetaphone("Dt"));
        assertEquals("T", dm.doubleMetaphone("Dd"));
        assertEquals("T", dm.doubleMetaphone("Dog"));
    }

    @Test
    public void testLetterF() throws Throwable {
        DoubleMetaphone dm = new DoubleMetaphone();
        assertEquals("F", dm.doubleMetaphone("Foot"));
        assertEquals("F", dm.doubleMetaphone("FF"));
    }

    @Test
    public void testLetterG() throws Throwable {
        DoubleMetaphone dm = new DoubleMetaphone();
        assertEquals("K", dm.doubleMetaphone("Ghost"));
        assertEquals("KN", dm.doubleMetaphone("Sign"));
        assertEquals("KL", dm.doubleMetaphone("Gli"));
        assertEquals("KJ", dm.doubleMetaphone("Ges"));
        assertEquals("K", dm.doubleMetaphone("Vanguard"));
        assertEquals("J", dm.doubleMetaphone("Gier"));
        assertEquals("K", dm.doubleMetaphone("GG"));
        assertEquals("K", dm.doubleMetaphone("Get"));
    }

    @Test
    public void testLetterGH() throws Throwable {
        DoubleMetaphone dm = new DoubleMetaphone();
        assertEquals("K", dm.doubleMetaphone("Light"));
        assertEquals("J", dm.doubleMetaphone("Ghi"));
        assertEquals("K", dm.doubleMetaphone("Ghiother"));
        assertEquals("F", dm.doubleMetaphone("Laugh"));
    }

    @Test
    public void testLetterH() throws Throwable {
        DoubleMetaphone dm = new DoubleMetaphone();
        assertEquals("H", dm.doubleMetaphone("Ahead"));
        assertEquals("", dm.doubleMetaphone("H"));
    }

    @Test
    public void testLetterJ() throws Throwable {
        DoubleMetaphone dm = new DoubleMetaphone();
        assertEquals("H", dm.doubleMetaphone("Jose"));
        assertEquals("H", dm.doubleMetaphone("San Jacinto"));
        assertEquals("J", dm.doubleMetaphone("Jelly"));
        assertEquals("J", dm.doubleMetaphone("JJ"));
    }

    @Test
    public void testLetterK() throws Throwable {
        DoubleMetaphone dm = new DoubleMetaphone();
        assertEquals("K", dm.doubleMetaphone("King"));
        assertEquals("K", dm.doubleMetaphone("KK"));
    }

    @Test
    public void testLetterL() throws Throwable {
        DoubleMetaphone dm = new DoubleMetaphone();
        assertEquals("L", dm.doubleMetaphone("Ball"));
        assertEquals("L", dm.doubleMetaphone("Balle"));
    }

    @Test
    public void testLetterM() throws Throwable {
        DoubleMetaphone dm = new DoubleMetaphone();
        assertEquals("M", dm.doubleMetaphone("Man"));
        assertEquals("M", dm.doubleMetaphone("MM"));
        assertEquals("M", dm.doubleMetaphone("Thumb"));
    }

    @Test
    public void testLetterN() throws Throwable {
        DoubleMetaphone dm = new DoubleMetaphone();
        assertEquals("N", dm.doubleMetaphone("Name"));
        assertEquals("N", dm.doubleMetaphone("NN"));
    }

    @Test
    public void testLetterP() throws Throwable {
        DoubleMetaphone dm = new DoubleMetaphone();
        assertEquals("F", dm.doubleMetaphone("Phone"));
        assertEquals("P", dm.doubleMetaphone("Paper"));
        assertEquals("P", dm.doubleMetaphone("Pb"));
    }

    @Test
    public void testLetterQ() throws Throwable {
        DoubleMetaphone dm = new DoubleMetaphone();
        assertEquals("K", dm.doubleMetaphone("Queen"));
        assertEquals("K", dm.doubleMetaphone("QQ"));
    }

    @Test
    public void testLetterR() throws Throwable {
        DoubleMetaphone dm = new DoubleMetaphone();
        assertEquals("R", dm.doubleMetaphone("River"));
        assertEquals("R", dm.doubleMetaphone("RR"));
        assertEquals("R", dm.doubleMetaphone("Marie"));
    }

    @Test
    public void testLetterS() throws Throwable {
        DoubleMetaphone dm = new DoubleMetaphone();
        assertEquals("S", dm.doubleMetaphone("Island"));
        assertEquals("X", dm.doubleMetaphone("Sugar"));
        assertEquals("S", dm.doubleMetaphone("Sheim"));
        assertEquals("X", dm.doubleMetaphone("Ship"));
        assertEquals("S", dm.doubleMetaphone("Sio"));
        assertEquals("SX", dm.doubleMetaphone("Smith"));
        assertEquals("X", dm.doubleMetaphone("Sch"));
        assertEquals("S", dm.doubleMetaphone("Scie"));
        assertEquals("SK", dm.doubleMetaphone("Sc"));
        assertEquals("X", dm.doubleMetaphone("Schooner"));
    }

    @Test
    public void testLetterT() throws Throwable {
        DoubleMetaphone dm = new DoubleMetaphone();
        assertEquals("X", dm.doubleMetaphone("Action"));
        assertEquals("X", dm.doubleMetaphone("Tia"));
        assertEquals("0", dm.doubleMetaphone("The"));
        assertEquals("T", dm.doubleMetaphone("Thomas"));
        assertEquals("T", dm.doubleMetaphone("Tt"));
        assertEquals("T", dm.doubleMetaphone("Td"));
    }

    @Test
    public void testLetterV() throws Throwable {
        DoubleMetaphone dm = new DoubleMetaphone();
        assertEquals("F", dm.doubleMetaphone("Vine"));
        assertEquals("F", dm.doubleMetaphone("Vv"));
    }

    @Test
    public void testLetterW() throws Throwable {
        DoubleMetaphone dm = new DoubleMetaphone();
        assertEquals("R", dm.doubleMetaphone("Writ"));
        assertEquals("AF", dm.doubleMetaphone("Water"));
        assertEquals("A", dm.doubleMetaphone("Who"));
        assertEquals("TSFX", dm.doubleMetaphone("Wicz"));
    }

    @Test
    public void testLetterX() throws Throwable {
        DoubleMetaphone dm.new DoubleMetaphone();
        assertEquals("S", dm.doubleMetaphone("Xanadu"));
        assertEquals("KS", dm.doubleMetaphone("Box"));
        assertEquals("KS", dm.doubleMetaphone("X"));
    }

    @Test
    public void testLetterZ() throws Throwable {
        DoubleMetaphone dm = new DoubleMetaphone();
        assertEquals("J", dm.doubleMetaphone("Zhao"));
        assertEquals("STS", dm.doubleMetaphone("Zo"));
        assertEquals("S", dm.doubleMetaphone("Zz"));
    }

    @Test
    public void testDefaultAndSpecialCharacters() throws Throwable {
        DoubleMetaphone dm = new DoubleMetaphone();
        assertEquals("1", dm.doubleMetaphone("123"));
        assertEquals("K", dm.doubleMetaphone("K"));
    }

    @Test
    public void testDoubleMetaphoneResultInnerClass() throws Throwable {
        DoubleMetaphone dm = new DoubleMetaphone();
        DoubleMetaphone.DoubleMetaphoneResult result = dm.new DoubleMetaphoneResult(4);
        result.append('A');
        result.append('B', 'C');
        result.append("DE");
        result.append("FG", "HI");
        result.appendPrimary('J');
        result.appendAlternate('K');
        result.appendPrimary("L");
        result.appendAlternate("M");
        
        assertNotNull(result.getPrimary());
        assertNotNull(result.getAlternate());
        assertTrue(result.isComplete());
    }
}