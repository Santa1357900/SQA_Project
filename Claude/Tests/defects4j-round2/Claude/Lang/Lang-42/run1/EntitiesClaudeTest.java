package org.apache.commons.lang;

import java.io.StringWriter;
import org.junit.Test;
import static org.junit.Assert.*;

public class EntitiesClaudeTest {

    // Branch: new Entities() starts with an empty entity map (default LookupEntityMap)
    @Test
    public void testConstructor_newEntities_hasNoEntitiesInitially() throws Throwable {
        Entities e = new Entities();
        assertEquals(-1, e.entityValue("amp"));
        assertNull(e.entityName(38));
    }

    // Branch: addEntity() + entityName()/entityValue() basic round trip
    @Test
    public void testAddEntity_singleEntity_roundTrip() throws Throwable {
        Entities e = new Entities();
        e.addEntity("copy", 169);
        assertEquals(169, e.entityValue("copy"));
        assertEquals("copy", e.entityName(169));
    }

    // Branch: addEntities() loop over multiple array rows (multi-iteration)
    @Test
    public void testAddEntities_multipleEntries_roundTrip() throws Throwable {
        Entities e = new Entities();
        String[][] custom = {{"one", "1"}, {"two", "2"}, {"three", "3"}};
        e.addEntities(custom);
        assertEquals(1, e.entityValue("one"));
        assertEquals(2, e.entityValue("two"));
        assertEquals("three", e.entityName(3));
    }

    // Branch: entityName() for a value never registered returns null
    @Test
    public void testEntityName_unknownValue_returnsNull() throws Throwable {
        Entities e = new Entities();
        assertNull(e.entityName(12345));
    }

    // Branch: entityValue() for a name never registered returns -1
    @Test
    public void testEntityValue_unknownName_returnsMinusOne() throws Throwable {
        Entities e = new Entities();
        assertEquals(-1, e.entityValue("doesNotExist"));
    }

    // Branch: escape() boundary of 'c > 0x7F' condition: 0x7F literal, 0x80 numeric
    @Test
    public void testEscape_charBoundary0x7FAnd0x80_literalVsNumeric() throws Throwable {
        Entities e = new Entities();
        String low = e.escape("\u007F");
        String high = e.escape("\u0080");
        assertEquals("\u007F", low);
        assertEquals("&#128;", high);
    }

    // Branch: escape() when entityName() resolves - writes &name; (Javadoc example)
    @Test
    public void testEscape_knownEntity_writesNamedEntity() throws Throwable {
        Entities e = new Entities();
        e.addEntity("foo", 0xA1);
        assertEquals("&foo;", e.escape("\u00A1"));
    }

    // Branch: escape() with empty string - loop runs zero times
    @Test
    public void testEscape_emptyString_returnsEmptyString() throws Throwable {
        Entities e = new Entities();
        assertEquals("", e.escape(""));
    }

    // Branch: escape(Writer,String) mixes known entity and plain literal char
    @Test
    public void testEscapeWriter_matchesStringOverload() throws Throwable {
        Entities e = new Entities();
        e.addEntity("foo", 0xA1);
        StringWriter writer = new StringWriter();
        e.escape(writer, "\u00A1A");
        assertEquals("&foo;A", writer.toString());
    }

    // Branch: unescape(String) with no '&' returns the same string immediately
    @Test
    public void testUnescape_noAmpersand_returnsSameString() throws Throwable {
        Entities e = Entities.XML;
        assertEquals("plain text", e.unescape("plain text"));
    }

    // Branch: unescape() resolves known entity name to character (Javadoc example)
    @Test
    public void testUnescape_knownEntity_returnsCharacter() throws Throwable {
        Entities e = new Entities();
        e.addEntity("foo", 0xA1);
        assertEquals("\u00A1", e.unescape("&foo;"));
    }

    // Branch: unescape() with unrecognized entity name keeps it literal
    @Test
    public void testUnescape_unknownNamedEntity_keepsLiteral() throws Throwable {
        Entities e = new Entities();
        assertEquals("&unknown;", e.unescape("&unknown;"));
    }

    // Branch: unescape() with '&' but no ';' found (semiColonIdx == -1)
    @Test
    public void testUnescape_missingSemicolon_keepsLiteral() throws Throwable {
        Entities e = Entities.XML;
        assertEquals("&amp", e.unescape("&amp"));
    }

    // Branch: unescape() detects a second '&' before the ';' (&...&...;)
    @Test
    public void testUnescape_doubleAmpersandBeforeSemicolon_treatsFirstAsLiteral() throws Throwable {
        Entities e = Entities.XML;
        assertEquals("&&", e.unescape("&&amp;"));
    }

    // Branch: unescape() numeric decimal entity (#N default switch case)
    @Test
    public void testUnescape_decimalNumericEntity_returnsCharacter() throws Throwable {
        Entities e = new Entities();
        assertEquals("A", e.unescape("&#65;"));
    }

    // Branch: unescape() numeric hex entity with lowercase 'x'
    @Test
    public void testUnescape_hexNumericEntityLowercaseX_returnsCharacter() throws Throwable {
        Entities e = new Entities();
        assertEquals("A", e.unescape("&#x41;"));
    }

    // Branch: unescape() numeric hex entity with uppercase 'X'
    @Test
    public void testUnescape_hexNumericEntityUppercaseX_returnsCharacter() throws Throwable {
        Entities e = new Entities();
        assertEquals("A", e.unescape("&#X41;"));
    }

    // Branch: unescape() numeric entity greater than 0xFFFF is rejected (-1)
    @Test
    public void testUnescape_numericEntityOverflow_keepsLiteral() throws Throwable {
        Entities e = new Entities();
        assertEquals("&#70000;", e.unescape("&#70000;"));
    }

    // Branch: unescape() numeric entity with non-numeric content triggers NumberFormatException catch
    @Test
    public void testUnescape_numericEntityNonNumericContent_keepsLiteral() throws Throwable {
        Entities e = new Entities();
        assertEquals("&#abc;", e.unescape("&#abc;"));
    }

    // Branch: unescape(Writer,String) with no '&' writes the string directly
    @Test
    public void testUnescapeWriter_noAmpersand_writesSameString() throws Throwable {
        Entities e = new Entities();
        StringWriter writer = new StringWriter();
        e.unescape(writer, "no entities");
        assertEquals("no entities", writer.toString());
    }

    // Branch: unescape(Writer,String) with '&' delegates to doUnescape prefix+entity+suffix
    @Test
    public void testUnescapeWriter_withAmpersand_delegatesToDoUnescape() throws Throwable {
        Entities e = Entities.XML;
        StringWriter writer = new StringWriter();
        e.unescape(writer, "a&amp;b");
        assertEquals("a&b", writer.toString());
    }

    // Branch: fillWithHtml40Entities() adds BASIC + ISO8859_1 + HTML40 arrays
    @Test
    public void testFillWithHtml40Entities_populatesBasicIso8859AndHtml40() throws Throwable {
        Entities e = new Entities();
        Entities.fillWithHtml40Entities(e);
        assertEquals(38, e.entityValue("amp"));
        assertEquals(160, e.entityValue("nbsp"));
        assertEquals(8364, e.entityValue("euro"));
    }

    // Branch: XML constant contains BASIC+APOS but not ISO8859_1 entities
    @Test
    public void testXmlConstant_containsBasicAndApos_notIso8859() throws Throwable {
        assertEquals(38, Entities.XML.entityValue("amp"));
        assertEquals(39, Entities.XML.entityValue("apos"));
        assertEquals(-1, Entities.XML.entityValue("nbsp"));
    }

    // Branch: HTML32 constant contains BASIC+ISO8859_1 but not APOS
    @Test
    public void testHtml32Constant_containsIso8859_notApos() throws Throwable {
        assertEquals(160, Entities.HTML32.entityValue("nbsp"));
        assertEquals(34, Entities.HTML32.entityValue("quot"));
        assertEquals(-1, Entities.HTML32.entityValue("apos"));
    }

    // Branch: HTML40 constant contains HTML40_ARRAY and BASIC entries
    @Test
    public void testHtml40Constant_containsEuroAndBasic() throws Throwable {
        assertEquals(8364, Entities.HTML40.entityValue("euro"));
        assertEquals(38, Entities.HTML40.entityValue("amp"));
        assertEquals(160, Entities.HTML40.entityValue("nbsp"));
    }

    // Branch: PrimitiveEntityMap.add/name/value round trip
    @Test
    public void testPrimitiveEntityMap_addNameValue_roundTrip() throws Throwable {
        Entities.PrimitiveEntityMap map = new Entities.PrimitiveEntityMap();
        map.add("test", 500);
        assertEquals(500, map.value("test"));
        assertEquals("test", map.name(500));
    }

    // Branch: PrimitiveEntityMap unknown name/value defaults
    @Test
    public void testPrimitiveEntityMap_unknownValue_returnsNullAndMinusOne() throws Throwable {
        Entities.PrimitiveEntityMap map = new Entities.PrimitiveEntityMap();
        assertEquals(-1, map.value("missing"));
        assertNull(map.name(999));
    }

    // Branch: HashEntityMap (MapIntMap over HashMap) round trip
    @Test
    public void testHashEntityMap_addNameValue_roundTrip() throws Throwable {
        Entities.HashEntityMap map = new Entities.HashEntityMap();
        map.add("alpha", 1);
        assertEquals(1, map.value("alpha"));
        assertEquals("alpha", map.name(1));
    }

    // Branch: TreeEntityMap (MapIntMap over TreeMap) round trip
    @Test
    public void testTreeEntityMap_addNameValue_roundTrip() throws Throwable {
        Entities.TreeEntityMap map = new Entities.TreeEntityMap();
        map.add("beta", 2);
        assertEquals(2, map.value("beta"));
        assertEquals("beta", map.name(2));
    }

    // Branch: LookupEntityMap.name() for value < LOOKUP_TABLE_SIZE uses lookupTable()
    @Test
    public void testLookupEntityMap_valueBelow256_usesLookupTable() throws Throwable {
        Entities.LookupEntityMap map = new Entities.LookupEntityMap();
        map.add("A", 65);
        assertEquals("A", map.name(65));
    }

    // Branch: LookupEntityMap.name() for value >= LOOKUP_TABLE_SIZE uses super.name()
    @Test
    public void testLookupEntityMap_valueAtOrAbove256_usesSuperName() throws Throwable {
        Entities.LookupEntityMap map = new Entities.LookupEntityMap();
        map.add("big", 1000);
        assertEquals("big", map.name(1000));
    }

    // Branch: ArrayEntityMap with zero entries - name()/value() loops run 0 times
    @Test
    public void testArrayEntityMap_emptyMap_returnsDefaults() throws Throwable {
        Entities.ArrayEntityMap map = new Entities.ArrayEntityMap();
        assertNull(map.name(1));
        assertEquals(-1, map.value("x"));
    }

    // Branch: ArrayEntityMap.add/name/value basic round trip (multi-entry)
    @Test
    public void testArrayEntityMap_addAndRetrieve_roundTrip() throws Throwable {
        Entities.ArrayEntityMap map = new Entities.ArrayEntityMap();
        map.add("x", 10);
        map.add("y", 20);
        assertEquals(10, map.value("x"));
        assertEquals("y", map.name(20));
    }

    // Branch: ArrayEntityMap.ensureCapacity() triggers growth when exceeding growBy
    @Test
    public void testArrayEntityMap_ensureCapacityGrowth_multipleAdds() throws Throwable {
        Entities.ArrayEntityMap map = new Entities.ArrayEntityMap(2);
        map.add("a", 1);
        map.add("b", 2);
        map.add("c", 3);
        assertEquals(3, map.value("c"));
        assertEquals("a", map.name(1));
    }

    // Branch: BinaryEntityMap sorted insertion (binarySearch insert path) and lookup
    @Test
    public void testBinaryEntityMap_sortedInsertionAndLookup() throws Throwable {
        Entities.BinaryEntityMap map = new Entities.BinaryEntityMap();
        map.add("c", 30);
        map.add("a", 10);
        map.add("b", 20);
        assertEquals("a", map.name(10));
        assertEquals("b", map.name(20));
        assertEquals("c", map.name(30));
        assertEquals(20, map.value("b"));
    }

    // Branch: BinaryEntityMap.name() when binarySearch index < 0 (not found) returns null
    @Test
    public void testBinaryEntityMap_nameForMissingValue_returnsNull() throws Throwable {
        Entities.BinaryEntityMap map = new Entities.BinaryEntityMap();
        map.add("only", 50);
        assertNull(map.name(999));
    }

    // Branch: BinaryEntityMap.add() duplicate value found at index 0 must be ignored, not throw
    @Test
    public void testBinaryEntityMap_duplicateValueAtIndexZero_secondInsertIgnored() throws Throwable {
        Entities.BinaryEntityMap map = new Entities.BinaryEntityMap();
        map.add("first", 100);
        map.add("second", 100);
        assertEquals(100, map.value("first"));
        assertEquals(-1, map.value("second"));
    }

    // Branch: BinaryEntityMap(int growBy) constructor path with subsequent adds
    @Test
    public void testBinaryEntityMap_growByConstructor_worksCorrectly() throws Throwable {
        Entities.BinaryEntityMap map = new Entities.BinaryEntityMap(1);
        map.add("x", 5);
        map.add("y", 15);
        assertEquals("x", map.name(5));
        assertEquals("y", map.name(15));
    }
}
