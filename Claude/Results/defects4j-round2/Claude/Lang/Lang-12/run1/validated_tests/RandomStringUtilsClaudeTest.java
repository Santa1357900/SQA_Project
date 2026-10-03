package org.apache.commons.lang3;

import java.util.Random;

import org.junit.Test;
import static org.junit.Assert.*;

public class RandomStringUtilsClaudeTest {

    /** Random ที่คืนค่าตามลำดับที่กำหนดไว้ล่วงหน้า เพื่อทำให้เทสต์ branch ของ surrogate/reject เป็น deterministic */
    private static class FixedRandom extends Random {
        private final int[] values;
        private int index = 0;
        FixedRandom(int[] values) {
            this.values = values;
        }
        public int nextInt(int n) {
            return values[index++];
        }
    }

    private boolean containsChar(String set, char c) {
        return set.indexOf(c) != -1;
    }

    // constructor สาธารณะต้องสร้าง instance ได้
    @Test
    public void testConstructor_createsInstance() throws Throwable {
        RandomStringUtils instance = new RandomStringUtils();
        assertNotNull(instance);
    }

    // random(int) กรณี count==0 -> คืน ""
    @Test
    public void testRandom_countZero_returnsEmptyString() throws Throwable {
        String result = RandomStringUtils.random(0);
        assertEquals("", result);
    }

    // random(int) กรณี count<0 -> throw IllegalArgumentException
    @Test
    public void testRandom_countNegative_throwsIllegalArgumentException() throws Throwable {
        try {
            RandomStringUtils.random(-1);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // random(int) กรณี count>0 -> ความยาวตรงตามที่ร้องขอ
    @Test
    public void testRandom_countPositive_returnsStringOfRequestedLength() throws Throwable {
        String result = RandomStringUtils.random(10);
        assertEquals(10, result.length());
    }

    // randomAscii -> ทุกตัวอักษรต้องอยู่ในช่วง 32-126 ตาม Javadoc
    @Test
    public void testRandomAscii_onlyAsciiPrintableRange() throws Throwable {
        String result = RandomStringUtils.randomAscii(50);
        assertEquals(50, result.length());
        for (int i = 0; i < result.length(); i++) {
            char c = result.charAt(i);
            assertTrue(c >= 32 && c <= 126);
        }
    }

    // randomAscii กรณี count==0
    @Test
    public void testRandomAscii_countZero_returnsEmptyString() throws Throwable {
        assertEquals("", RandomStringUtils.randomAscii(0));
    }

    // randomAlphabetic -> ทุกตัวต้องเป็น letter
    @Test
    public void testRandomAlphabetic_onlyLetters() throws Throwable {
        String result = RandomStringUtils.randomAlphabetic(50);
        assertEquals(50, result.length());
        for (int i = 0; i < result.length(); i++) {
            assertTrue(Character.isLetter(result.charAt(i)));
        }
    }

    // randomAlphanumeric -> ทุกตัวต้องเป็น letter หรือ digit
    @Test
    public void testRandomAlphanumeric_onlyLettersOrDigits() throws Throwable {
        String result = RandomStringUtils.randomAlphanumeric(50);
        assertEquals(50, result.length());
        for (int i = 0; i < result.length(); i++) {
            char c = result.charAt(i);
            assertTrue(Character.isLetter(c) || Character.isDigit(c));
        }
    }

    // randomNumeric -> ทุกตัวต้องเป็น digit
    @Test
    public void testRandomNumeric_onlyDigits() throws Throwable {
        String result = RandomStringUtils.randomNumeric(50);
        assertEquals(50, result.length());
        for (int i = 0; i < result.length(); i++) {
            assertTrue(Character.isDigit(result.charAt(i)));
        }
    }

    // random(count,letters,numbers) ทั้งสอง true -> letter หรือ digit
    @Test
    public void testRandomLettersNumbersOverload_bothTrue_onlyLettersOrDigits() throws Throwable {
        String result = RandomStringUtils.random(50, true, true);
        for (int i = 0; i < result.length(); i++) {
            char c = result.charAt(i);
            assertTrue(Character.isLetter(c) || Character.isDigit(c));
        }
    }

    // random(count,letters,numbers) ทั้งสอง false, count==0 -> ""
    @Test
    public void testRandomLettersNumbersOverload_bothFalse_countZero_returnsEmptyString() throws Throwable {
        assertEquals("", RandomStringUtils.random(0, false, false));
    }

    // random(count,start,end,letters,numbers) -> จำกัดช่วงตัวอักษรตาม start/end (end แบบ exclusive)
    @Test
    public void testRandomStartEnd_rangeRespected() throws Throwable {
        String result = RandomStringUtils.random(100, 'a', 'd', false, false);
        for (int i = 0; i < result.length(); i++) {
            assertTrue(containsChar("abc", result.charAt(i)));
        }
    }

    // random(count,start,end,letters,numbers) count<0 -> throw
    @Test
    public void testRandomStartEnd_countNegative_throwsIllegalArgumentException() throws Throwable {
        try {
            RandomStringUtils.random(-1, 0, 10, false, false);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // random(count,start,end,letters,numbers,chars...) -> ใช้ตัวอักษรจาก set ที่ระบุเท่านั้น
    @Test
    public void testRandomStartEndVarargsChars_charsFromGivenSet() throws Throwable {
        String result = RandomStringUtils.random(50, 0, 3, false, false, new char[] {'x', 'y', 'z'});
        for (int i = 0; i < result.length(); i++) {
            assertTrue(containsChar("xyz", result.charAt(i)));
        }
    }

    // core method count==0 -> "" (ไม่แตะ random source เลย)
    @Test
    public void testRandomFull_countZero_returnsEmptyString() throws Throwable {
        String result = RandomStringUtils.random(0, 0, 0, false, false, (char[]) null, new Random(0));
        assertEquals("", result);
    }

    // core method count<0 -> throw IllegalArgumentException
    @Test
    public void testRandomFull_countNegative_throwsIllegalArgumentException() throws Throwable {
        try {
            RandomStringUtils.random(-5, 0, 10, false, false, (char[]) null, new Random(0));
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // gap==0 (start==end แต่ไม่ใช่ทั้งคู่เป็น 0) -> Random.nextInt(0) ต้อง throw IllegalArgumentException
    @Test
    public void testRandomFull_gapZero_throwsIllegalArgumentException() throws Throwable {
        try {
            RandomStringUtils.random(1, 5, 5, false, false);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // ตัวอักษรที่ไม่ผ่านเงื่อนไข letters/numbers ต้องถูกข้าม (count++) แล้วลองใหม่จนกว่าจะผ่าน
    @Test
    public void testRandomFull_rejectThenAccept_filtersNonMatchingChars() throws Throwable {
        FixedRandom fr = new FixedRandom(new int[] {0, 1});
        String result = RandomStringUtils.random(1, 0, 2, true, false, new char[] {'1', 'a'}, fr);
        assertEquals("a", result);
    }

    // index ที่สุ่มได้เกินขอบเขตของ chars array -> ArrayIndexOutOfBoundsException ตาม Javadoc
    @Test
    public void testRandomFull_indexBeyondCharsArray_throwsArrayIndexOutOfBoundsException() throws Throwable {
        FixedRandom fr = new FixedRandom(new int[] {4});
        try {
            RandomStringUtils.random(1, 0, 5, false, false, new char[] {'a', 'b', 'c'}, fr);
            fail("expected ArrayIndexOutOfBoundsException");
        } catch (ArrayIndexOutOfBoundsException expected) {
        }
    }

    // high surrogate ที่สุ่มได้ต้องถูกจับคู่กับ low surrogate ที่ตามมา
    @Test
    public void testRandomFull_highSurrogate_pairsWithLowSurrogate() throws Throwable {
        FixedRandom fr = new FixedRandom(new int[] {0, 5});
        String result = RandomStringUtils.random(2, 55296, 55297, false, false, (char[]) null, fr);
        assertEquals(2, result.length());
        assertEquals(55296, result.charAt(0));
        assertEquals(56325, result.charAt(1));
    }

    // low surrogate ที่สุ่มได้ต้องถูกจับคู่กับ high surrogate ที่นำหน้า
    @Test
    public void testRandomFull_lowSurrogate_pairsWithHighSurrogate() throws Throwable {
        FixedRandom fr = new FixedRandom(new int[] {0, 10});
        String result = RandomStringUtils.random(2, 56320, 56321, false, false, (char[]) null, fr);
        assertEquals(2, result.length());
        assertEquals(55306, result.charAt(0));
        assertEquals(56320, result.charAt(1));
    }

    // private high surrogate (56192-56319) ต้องถูกข้าม (skip) แล้วสุ่มใหม่
    @Test
    public void testRandomFull_privateHighSurrogate_isSkippedAndRetried() throws Throwable {
        FixedRandom fr = new FixedRandom(new int[] {0, 1});
        char[] chars = new char[] {(char) 56192, 'A'};
        String result = RandomStringUtils.random(1, 0, 2, false, false, chars, fr);
        assertEquals("A", result);
    }

    // random(count,String) chars==null -> ใช้ full character set, ตรวจความยาว
    @Test
    public void testRandomString_null_usesFullCharacterSet() throws Throwable {
        String result = RandomStringUtils.random(5, (String) null);
        assertEquals(5, result.length());
    }

    // BUG: random(count,"") ตาม Javadoc ต้อง throw IllegalArgumentException เพราะ chars ว่าง
    @Test
    public void testRandomString_emptyString_throwsIllegalArgumentException() throws Throwable {
        try {
            RandomStringUtils.random(5, "");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // random(count,String) ใช้ตัวอักษรจาก string ที่ระบุเท่านั้น
    @Test
    public void testRandomString_validChars_onlyFromGivenSet() throws Throwable {
        String result = RandomStringUtils.random(50, "xyz");
        for (int i = 0; i < result.length(); i++) {
            assertTrue(containsChar("xyz", result.charAt(i)));
        }
    }

    // random(count,String) count<0 -> throw IllegalArgumentException
    @Test
    public void testRandomString_countNegative_throwsIllegalArgumentException() throws Throwable {
        try {
            RandomStringUtils.random(-1, "abc");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // random(count,char...) chars==null -> ใช้ full character set, ตรวจความยาว
    @Test
    public void testRandomCharArray_null_usesFullCharacterSet() throws Throwable {
        String result = RandomStringUtils.random(5, (char[]) null);
        assertEquals(5, result.length());
    }

    // BUG: random(count,new char[0]) ตาม Javadoc ต้อง throw IllegalArgumentException เพราะ chars array ว่าง
    @Test
    public void testRandomCharArray_emptyArray_throwsIllegalArgumentException() throws Throwable {
        try {
            RandomStringUtils.random(5, new char[0]);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // random(count,char...) ใช้ตัวอักษรจาก array ที่ระบุเท่านั้น
    @Test
    public void testRandomCharArray_validChars_onlyFromGivenSet() throws Throwable {
        String result = RandomStringUtils.random(50, new char[] {'m', 'n'});
        for (int i = 0; i < result.length(); i++) {
            assertTrue(containsChar("mn", result.charAt(i)));
        }
    }

    // random(count,char...) count<0 -> throw IllegalArgumentException
    @Test
    public void testRandomCharArray_countNegative_throwsIllegalArgumentException() throws Throwable {
        try {
            RandomStringUtils.random(-1, new char[] {'a'});
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }
}
