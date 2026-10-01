package org.apache.commons.codec.language;

import org.apache.commons.codec.EncoderException;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

public class DoubleMetaphoneClaudeTest {

    private DoubleMetaphone dm;

    @Before
    public void setUp() throws Throwable {
        dm = new DoubleMetaphone();
    }

    // Covers cleanInput: null input, empty-after-trim input, whitespace-only input -> null
    @Test
    public void testDoubleMetaphone_invalidInputs_returnNull() throws Throwable {
        assertNull(dm.doubleMetaphone(null));
        assertNull(dm.doubleMetaphone(""));
        assertNull(dm.doubleMetaphone("   "));
    }

    // Covers handleAEIOUY: index==0 branch appends 'A' regardless of which vowel
    @Test
    public void testDoubleMetaphone_leadingVowels_mapToA() throws Throwable {
        assertEquals("A", dm.doubleMetaphone("A"));
        assertEquals("A", dm.doubleMetaphone("E"));
        assertEquals("A", dm.doubleMetaphone("I"));
        assertEquals("A", dm.doubleMetaphone("O"));
        assertEquals("A", dm.doubleMetaphone("U"));
        assertEquals("A", dm.doubleMetaphone("Y"));
    }

    // Covers handleAEIOUY: non-initial vowel is skipped (no append), combined with 'B' case
    @Test
    public void testDoubleMetaphone_nonInitialVowel_skipped() throws Throwable {
        assertEquals("P", dm.doubleMetaphone("BA"));
    }

    // Covers 'B' case: BB collapses to a single 'P'
    @Test
    public void testHandleB_doubleB_collapsesToSingleP() throws Throwable {
        assertEquals("P", dm.doubleMetaphone("BB"));
    }

    // Covers 'F' case: FF collapses to a single 'F'
    @Test
    public void testHandleF_doubleF_collapsesToSingleF() throws Throwable {
        assertEquals("F", dm.doubleMetaphone("FF"));
    }

    // Covers 'K' case: KK collapses to a single 'K'
    @Test
    public void testHandleK_doubleK_collapsesToSingleK() throws Throwable {
        assertEquals("K", dm.doubleMetaphone("KK"));
    }

    // Covers 'Q' case (-> K) and 'V' case (-> F), single occurrence each
    @Test
    public void testHandleQ_and_V_singleMapping() throws Throwable {
        assertEquals("K", dm.doubleMetaphone("Q"));
        assertEquals("F", dm.doubleMetaphone("V"));
    }

    // Covers handleC: "CK" matches contains(... "CK","CG","CQ") -> K
    @Test
    public void testHandleC_CK_mapsToK() throws Throwable {
        assertEquals("K", dm.doubleMetaphone("CK"));
    }

    // Covers handleC -> handleCH -> conditionCH0: Greek root ("CHEM") -> K
    @Test
    public void testHandleC_CH_greekRoot_mapsToK() throws Throwable {
        assertEquals("KM", dm.doubleMetaphone("CHEM"));
    }

    // Covers isSilentStart branch for all five prefixes (GN, KN, PN, WR, PS)
    @Test
    public void testSilentStart_allPrefixes_silenceLeadingLetter() throws Throwable {
        assertEquals("N", dm.doubleMetaphone("GN"));
        assertEquals("N", dm.doubleMetaphone("KN"));
        assertEquals("N", dm.doubleMetaphone("PN"));
        assertEquals("R", dm.doubleMetaphone("WR"));
        assertEquals("S", dm.doubleMetaphone("PS"));
    }

    // Covers handleD: plain 'D' and "DD" both collapse to a single 'T'
    @Test
    public void testHandleD_plainAndDoubled_mapToSingleT() throws Throwable {
        assertEquals("T", dm.doubleMetaphone("D"));
        assertEquals("T", dm.doubleMetaphone("DD"));
    }

    // Covers handleD: "DG" followed by I/E/Y -> 'J' (e.g. "Edge")
    @Test
    public void testHandleD_DGfollowedByIEY_mapsToJ() throws Throwable {
        assertEquals("J", dm.doubleMetaphone("DGI"));
    }

    // Covers handleD: "DG" followed by other letter -> "TK" (e.g. "Edgar")
    @Test
    public void testHandleD_TK() throws Throwable {
        assertEquals("TK", dm.doubleMetaphone("DGA"));
    }

    // Covers handleH: kept when first/after-vowel and before a vowel
    @Test
    public void testHandleH_betweenVowels_kept() throws Throwable {
        assertEquals("AH", dm.doubleMetaphone("AHA"));
    }

    // Covers handleH: silent when not preceded by start/vowel
    @Test
    public void testHandleH_silentAfterConsonant() throws Throwable {
        assertEquals("P", dm.doubleMetaphone("BH"));
    }

    // Covers handleJ: index==0 && not "JOSE" -> append('J','A')
    @Test
    public void testHandleJ_initialJ_primaryJ_alternateA() throws Throwable {
        assertEquals("J", dm.doubleMetaphone("JA"));
        assertEquals("A", dm.doubleMetaphone("JA", true));
    }

    // Covers handleL: "LL" not matching conditionL0 -> single 'L' in both buffers
    @Test
    public void testHandleL_doubleL_defaultBranch_singleL() throws Throwable {
        assertEquals("AL", dm.doubleMetaphone("ALL"));
    }

    // Covers handleP: "PH" -> 'F'
    @Test
    public void testHandleP_PH_mapsToF() throws Throwable {
        assertEquals("F", dm.doubleMetaphone("PH"));
    }

    // Covers handleP: "PP" collapses to a single 'P'
    @Test
    public void testHandleP_doublePB_collapses() throws Throwable {
        assertEquals("P", dm.doubleMetaphone("PP"));
    }

    // Covers handleR: plain, non-terminal-"IE" case -> appends 'R' normally
    @Test
    public void testHandleR_plain_appendsR() throws Throwable {
        assertEquals("R", dm.doubleMetaphone("R"));
    }

    // Covers handleR: word ending in "...IER", not slavoGermanic, not ME/MA-preceded -> R only in alternate
    @Test
    public void testHandleR_wordEndingIER_alternateOnlyR() throws Throwable {
        assertEquals("T", dm.doubleMetaphone("TIER"));
        assertEquals("TR", dm.doubleMetaphone("TIER", true));
    }

    // Covers handleS: "ISL" pattern -> S is silent
    @Test
    public void testHandleS_ISLpattern_silent() throws Throwable {
        assertEquals("AL", dm.doubleMetaphone("ISLE"));
    }

    // Covers handleS: "SH" followed by germanic suffix "HOLM" -> 'S' (not 'X')
    @Test
    public void testHandleS_SHgermanicSuffix_mapsToS() throws Throwable {
        assertEquals("SLM", dm.doubleMetaphone("SHOLM"));
    }

    // Covers handleS: "SH" without germanic suffix -> 'X'
    @Test
    public void testHandleS_SHnonGermanic_mapsToX() throws Throwable {
        assertEquals("X", dm.doubleMetaphone("SHA"));
    }

    // Covers handleT: "TION" -> 'X'
    @Test
    public void testHandleT_TION_mapsToX() throws Throwable {
        assertEquals("XN", dm.doubleMetaphone("TION"));
    }

    // Covers handleT: "TH" default branch -> primary '0', alternate 'T'
    @Test
    public void testHandleT_TH_mapsToZeroPrimary_TAlternate() throws Throwable {
        assertEquals("0", dm.doubleMetaphone("TH"));
        assertEquals("T", dm.doubleMetaphone("TH", true));
    }

    // Covers handleT: "THOM" special-case (thomas/thames) -> 'T' in both buffers
    @Test
    public void testHandleT_THOM_specialCase_mapsToT() throws Throwable {
        assertEquals("TM", dm.doubleMetaphone("THOM"));
    }

    // Covers handleW: initial "WH" before a non-vowel -> appends 'A' only
    @Test
    public void testHandleW_WHbeforeConsonant_mapsToA() throws Throwable {
        assertEquals("A", dm.doubleMetaphone("WHO"));
    }

    // Covers handleW: initial W before a vowel -> primary 'A', alternate 'F'
    @Test
    public void testHandleW_initialBeforeVowel_primaryA_alternateF() throws Throwable {
        assertEquals("A", dm.doubleMetaphone("WA"));
        assertEquals("F", dm.doubleMetaphone("WA", true));
    }

    // Covers handleX: index==0 -> 'S'; non-initial, non-silent -> "KS"
    @Test
    public void testHandleX_initialAndNonInitial_mapping() throws Throwable {
        assertEquals("S", dm.doubleMetaphone("X"));
        assertEquals("AKS", dm.doubleMetaphone("AX"));
    }

    // Covers handleX: French ending "EAU" + trailing X -> silent (no KS appended)
    @Test
    public void testHandleX_frenchEauEnding_silent() throws Throwable {
        assertEquals("A", dm.doubleMetaphone("EAUX"));
    }

    // Covers handleZ: "ZH" -> 'J'; default Z (not ZO/ZI/ZA, not slavoGermanic) -> 'S'
    @Test
    public void testHandleZ_zhAndDefault_mapping() throws Throwable {
        assertEquals("J", dm.doubleMetaphone("ZH"));
        assertEquals("AS", dm.doubleMetaphone("AZ"));
    }

    // Covers handleG: "GG" collapses to a single 'K'
    @Test
    public void testHandleG_doubleG_collapsesToSingleK() throws Throwable {
        assertEquals("AK", dm.doubleMetaphone("AGG"));
    }

    // Covers handleG: index==1, leading vowel, !slavoGermanic, "GN" -> primary "KN", alternate "N"
    @Test
    public void testHandleG_initialVowelThenGN_notSlavoGermanic() throws Throwable {
        assertEquals("AKNS", dm.doubleMetaphone("AGNES"));
        assertEquals("ANS", dm.doubleMetaphone("AGNES", true));
    }

    // Covers encode(Object): non-String argument throws EncoderException
    @Test
    public void testEncodeObject_nonString_throwsEncoderException() throws Throwable {
        try {
            dm.encode(Integer.valueOf(5));
            fail("expected EncoderException");
        } catch (EncoderException expected) {
            assertTrue(expected.getMessage().contains("String"));
        }
    }

    // Covers encode(Object): String argument delegates to doubleMetaphone(value,false)
    @Test
    public void testEncodeObject_stringInput_delegatesToDoubleMetaphone() throws Throwable {
        Object input = "WA";
        Object result = dm.encode(input);
        assertEquals("A", (String) result);
    }

    // Covers encode(String): delegates to doubleMetaphone(value)
    @Test
    public void testEncodeString_delegatesToDoubleMetaphone() throws Throwable {
        assertEquals("A", dm.encode("WA"));
    }

    // Covers isDoubleMetaphoneEqual(String,String): equal primary codes true, different codes false
    @Test
    public void testIsDoubleMetaphoneEqual_twoArg_equalAndDifferentCodes() throws Throwable {
        assertTrue(dm.isDoubleMetaphoneEqual("BA", "PA"));
        assertFalse(dm.isDoubleMetaphoneEqual("BA", "ZA"));
    }

    // Covers isDoubleMetaphoneEqual(String,String,boolean): primary mismatch but alternate match
    @Test
    public void testIsDoubleMetaphoneEqual_threeArg_alternateComparison() throws Throwable {
        assertFalse(dm.isDoubleMetaphoneEqual("WA", "VA"));
        assertTrue(dm.isDoubleMetaphoneEqual("WA", "VA", true));
    }

    // Covers getMaxCodeLen default, and setMaxCodeLen truncating single-char and multi-char appends
    @Test
    public void testGetAndSetMaxCodeLen_affectsEncodingLength() throws Throwable {
        assertEquals(4, dm.getMaxCodeLen());
        dm.setMaxCodeLen(1);
        assertEquals(1, dm.getMaxCodeLen());
        assertEquals("P", dm.doubleMetaphone("BA"));
        dm.setMaxCodeLen(2);
        assertEquals("AK", dm.doubleMetaphone("AX"));
    }

    // Covers charAt: within bounds, negative index, and index == length boundary
    @Test
    public void testCharAt_boundsHandling() throws Throwable {
        assertEquals('E', dm.charAt("HELLO", 1));
        assertEquals(Character.MIN_VALUE, dm.charAt("HELLO", -1));
        assertEquals(Character.MIN_VALUE, dm.charAt("HI", 2));
    }

    // Covers contains: exact match, start+length exceeding value length, negative start
    @Test
    public void testContains_boundsAndMatching() throws Throwable {
        assertTrue(DoubleMetaphone.contains("HELLO", 0, 2, new String[] { "HE" }));
        assertFalse(DoubleMetaphone.contains("HI", 0, 5, new String[] { "HELLO" }));
        assertFalse(DoubleMetaphone.contains("HI", -1, 2, new String[] { "HI" }));
    }

    // Covers DoubleMetaphoneResult: append(char) hits both buffers; append(char,char) hits each separately
    @Test
    public void testDoubleMetaphoneResult_appendCharVariants() throws Throwable {
        DoubleMetaphone.DoubleMetaphoneResult result = dm.new DoubleMetaphoneResult(4);
        result.append('X');
        assertEquals("X", result.getPrimary());
        assertEquals("X", result.getAlternate());
        result.append('A', 'B');
        assertEquals("XA", result.getPrimary());
        assertEquals("XB", result.getAlternate());
    }

    // Covers DoubleMetaphoneResult: appendPrimary/appendAlternate truncate at maxLength; isComplete needs both full
    @Test
    public void testDoubleMetaphoneResult_appendStringTruncation_andIsComplete() throws Throwable {
        DoubleMetaphone.DoubleMetaphoneResult result = dm.new DoubleMetaphoneResult(3);
        result.appendPrimary("ABCDE");
        assertEquals("ABC", result.getPrimary());
        assertFalse(result.isComplete());
        result.appendAlternate("XYZ");
        assertTrue(result.isComplete());
    }
}
