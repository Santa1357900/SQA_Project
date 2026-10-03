package org.apache.commons.lang3.text.translate;

import java.io.IOException;
import java.io.PipedWriter;
import java.io.StringWriter;
import java.io.Writer;

import org.junit.Test;
import static org.junit.Assert.*;

public class CharSequenceTranslatorClaudeTest {

    private static class IdentityTranslator extends CharSequenceTranslator {
        @Override
        public int translate(CharSequence input, int index, Writer out) throws IOException {
            return 0;
        }
    }

    private static class TokenTranslator extends CharSequenceTranslator {
        private final String token;
        private final String replacement;

        TokenTranslator(String token, String replacement) {
            this.token = token;
            this.replacement = replacement;
        }

        @Override
        public int translate(CharSequence input, int index, Writer out) throws IOException {
            int tlen = token.length();
            if (index + tlen <= input.length()) {
                CharSequence sub = input.subSequence(index, index + tlen);
                if (sub.toString().equals(token)) {
                    out.write(replacement);
                    return tlen;
                }
            }
            return 0;
        }
    }

    // translate(CharSequence): input null -> ต้องคืน null ตาม Javadoc
    @Test
    public void testTranslate_nullInput_returnsNull() throws Throwable {
        CharSequenceTranslator translator = new IdentityTranslator();
        assertNull(translator.translate((CharSequence) null));
    }

    // translate(CharSequence): input ว่าง -> loop 0 รอบ, ผลลัพธ์เป็นสตริงว่าง
    @Test
    public void testTranslate_emptyInput_returnsEmptyString() throws Throwable {
        CharSequenceTranslator translator = new IdentityTranslator();
        assertEquals("", translator.translate(""));
    }

    // translate(CharSequence): translator ที่ไม่แปลงอะไรเลย ต้อง copy ข้อความเดิมกลับมาทั้งหมด
    @Test
    public void testTranslate_identityTranslator_returnsSameString() throws Throwable {
        CharSequenceTranslator translator = new IdentityTranslator();
        assertEquals("Hello, World! 123", translator.translate("Hello, World! 123"));
    }

    // translate(CharSequence): ไม่มี token ตรงกันเลย -> consumed=0 ทุกตำแหน่ง, ผลลัพธ์เท่าต้นฉบับ
    @Test
    public void testTranslate_tokenTranslator_noMatch_returnsOriginal() throws Throwable {
        CharSequenceTranslator translator = new TokenTranslator("zz", "Q");
        assertEquals("abc", translator.translate("abc"));
    }

    // translate(CharSequence): token ตรงกันพอดีทั้งสตริง -> ถูกแทนที่ด้วย replacement
    @Test
    public void testTranslate_tokenTranslator_singleMatch_replacesToken() throws Throwable {
        CharSequenceTranslator translator = new TokenTranslator("a", "X");
        assertEquals("X", translator.translate("a"));
    }

    // translate(CharSequence,Writer): out เป็น null -> ต้อง throw IllegalArgumentException
    @Test
    public void testTranslateWriter_nullWriter_throwsIllegalArgumentException() throws Throwable {
        CharSequenceTranslator translator = new IdentityTranslator();
        try {
            translator.translate("abc", (Writer) null);
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("Writer"));
        }
    }

    // translate(CharSequence,Writer): input เป็น null -> คืนทันทีโดยไม่เขียนอะไรลง writer
    @Test
    public void testTranslateWriter_nullInput_writerStaysEmpty() throws Throwable {
        StringWriter sw = new StringWriter();
        CharSequenceTranslator translator = new IdentityTranslator();
        translator.translate((CharSequence) null, sw);
        assertEquals("", sw.toString());
    }

    // translate(CharSequence,Writer): input ว่าง -> while loop ไม่ทำงานเลย, writer ว่าง
    @Test
    public void testTranslateWriter_emptyInput_noWriteOccurs() throws Throwable {
        StringWriter sw = new StringWriter();
        CharSequenceTranslator translator = new IdentityTranslator();
        translator.translate("", sw);
        assertEquals("", sw.toString());
    }

    // translate(CharSequence,Writer): สาขา consumed==0 ต้องเขียนตัวอักษรเดิมกลับทุกตัว
    @Test
    public void testTranslateWriter_consumedZero_writesCharacterVerbatim() throws Throwable {
        StringWriter sw = new StringWriter();
        CharSequenceTranslator translator = new IdentityTranslator();
        translator.translate("hello", sw);
        assertEquals("hello", sw.toString());
    }

    // translate(CharSequence,Writer): สาขา consumed>0 กรณี consume 1 codepoint
    @Test
    public void testTranslateWriter_consumedOne_singleCodepointReplacement() throws Throwable {
        StringWriter sw = new StringWriter();
        CharSequenceTranslator translator = new TokenTranslator("a", "X");
        translator.translate("a", sw);
        assertEquals("X", sw.toString());
    }

    // translate(CharSequence,Writer): สาขา consumed>0 กรณี consume หลาย codepoint อยู่กลางสตริง
    @Test
    public void testTranslateWriter_consumedMultiple_tokenInMiddle() throws Throwable {
        StringWriter sw = new StringWriter();
        CharSequenceTranslator translator = new TokenTranslator("abc", "Y");
        translator.translate("xabcz", sw);
        assertEquals("xYz", sw.toString());
    }

    // translate(CharSequence,Writer): loop หลายรอบ สลับระหว่าง consumed=0 และ consumed>0
    @Test
    public void testTranslateWriter_multipleIterations_mixedConsumedAndPassthrough() throws Throwable {
        StringWriter sw = new StringWriter();
        CharSequenceTranslator translator = new TokenTranslator("ab", "Z");
        translator.translate("xaby", sw);
        assertEquals("xZy", sw.toString());
    }

    // ล่าบั๊ก: translator passthrough (consumed=0 เสมอ) ต้อง copy supplementary character (surrogate pair)
    // และตัวอักษรหลังมันให้ครบถ้วนตรงกับต้นฉบับทุกตัว ตามสัญญาของ class (escape/unescape ต้องไม่สูญหายข้อมูล)
    @Test
    public void testTranslateWriter_supplementaryCharacterPassthrough_preservesAllCharacters() throws Throwable {
        String supplementary = "\uD800\uDC00";
        String input = "a" + supplementary + "b";
        CharSequenceTranslator translator = new IdentityTranslator();
        assertEquals(input, translator.translate(input));
    }

    // translate(CharSequence,Writer): IOException จาก Writer ต้อง propagate ออกมาตรงๆ ตาม Javadoc
    @Test
    public void testTranslateWriter_ioExceptionFromWriter_propagates() throws Throwable {
        CharSequenceTranslator translator = new IdentityTranslator();
        PipedWriter writer = new PipedWriter();
        try {
            translator.translate("x", writer);
            fail("expected IOException");
        } catch (IOException expected) {
        }
    }

    // with(): ไม่มี translator เพิ่ม -> ผลลัพธ์ยังคงเป็น CharSequenceTranslator ที่ใช้งานได้ปกติกับ input ว่าง
    @Test
    public void testWith_zeroAdditionalTranslators_emptyInputReturnsEmpty() throws Throwable {
        CharSequenceTranslator translator = new IdentityTranslator();
        CharSequenceTranslator merged = translator.with();
        assertEquals("", merged.translate(""));
    }

    // with(): ไม่มี translator เพิ่ม -> input null ยังคงคืน null (governed by final translate(CharSequence))
    @Test
    public void testWith_zeroAdditionalTranslators_nullInputReturnsNull() throws Throwable {
        CharSequenceTranslator translator = new IdentityTranslator();
        CharSequenceTranslator merged = translator.with();
        assertNull(merged.translate((CharSequence) null));
    }

    // with(): รวม translator หนึ่งตัว -> ได้ instance ใหม่ ไม่ใช่ object เดิม
    @Test
    public void testWith_oneAdditionalTranslator_returnsNewInstance() throws Throwable {
        CharSequenceTranslator translator = new IdentityTranslator();
        CharSequenceTranslator other = new IdentityTranslator();
        CharSequenceTranslator merged = translator.with(other);
        assertNotNull(merged);
        assertNotSame(translator, merged);
    }

    // with(): รวมหลาย translator (varargs หลายตัว) -> ไม่เป็น null และใช้งานกับ input ว่างได้ปกติ
    @Test
    public void testWith_multipleTranslators_notNullAndUsable() throws Throwable {
        CharSequenceTranslator translator = new IdentityTranslator();
        CharSequenceTranslator t1 = new IdentityTranslator();
        CharSequenceTranslator t2 = new TokenTranslator("a", "Z");
        CharSequenceTranslator merged = translator.with(t1, t2);
        assertNotNull(merged);
        assertEquals("", merged.translate(""));
    }

    // hex(0): ค่าศูนย์ -> "0"
    @Test
    public void testHex_zero_returnsZero() throws Throwable {
        assertEquals("0", CharSequenceTranslator.hex(0));
    }

    // hex(7): เลขหลักเดียวไม่ต้องใช้ตัวอักษร
    @Test
    public void testHex_singleDigit_returnsSameDigit() throws Throwable {
        assertEquals("7", CharSequenceTranslator.hex(7));
    }

    // hex(10): ต้องแปลงเป็นตัวพิมพ์ใหญ่ "A" ไม่ใช่ "a"
    @Test
    public void testHex_ten_returnsUppercaseLetter() throws Throwable {
        assertEquals("A", CharSequenceTranslator.hex(10));
    }

    // hex(255): ค่าที่มีตัวอักษรหลายตัว ต้องเป็นตัวพิมพ์ใหญ่ทั้งหมด
    @Test
    public void testHex_twoFiftyFive_returnsUppercaseHex() throws Throwable {
        assertEquals("FF", CharSequenceTranslator.hex(255));
    }

    // hex(16): ขอบเขตค่า 0x10 -> "10"
    @Test
    public void testHex_sixteen_returnsOneZero() throws Throwable {
        assertEquals("10", CharSequenceTranslator.hex(16));
    }

    // hex(-1): ค่าลบ -> Integer.toHexString แทนด้วยเลข 32 บิตแบบ unsigned ทั้งหมดเป็น F
    @Test
    public void testHex_negativeOne_returnsFullHex() throws Throwable {
        assertEquals("FFFFFFFF", CharSequenceTranslator.hex(-1));
    }

    // hex(Integer.MAX_VALUE): ค่าขอบบนสุดของ int
    @Test
    public void testHex_maxValue_returnsExpectedHex() throws Throwable {
        assertEquals("7FFFFFFF", CharSequenceTranslator.hex(Integer.MAX_VALUE));
    }

    // hex(Integer.MIN_VALUE): ค่าขอบล่างสุดของ int
    @Test
    public void testHex_minValue_returnsExpectedHex() throws Throwable {
        assertEquals("80000000", CharSequenceTranslator.hex(Integer.MIN_VALUE));
    }
}
