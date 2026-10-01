package org.apache.commons.collections;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.Vector;

import org.junit.Test;
import static org.junit.Assert.*;

public class ExtendedPropertiesClaudeTest {

    // covers: isInitialized false before any load/addProperty, true after addProperty
    @Test
    public void testInitialState_defaultConstructorThenAddProperty() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        assertFalse(props.isInitialized());
        props.addProperty("k", "v");
        assertTrue(props.isInitialized());
    }

    // covers: getInclude() default branch returning static "include"
    @Test
    public void testGetInclude_default_returnsInclude() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        assertEquals("include", props.getInclude());
    }

    // covers: setInclude(null) hack converts to null via getInclude(); custom value round trip
    @Test
    public void testSetInclude_nullAndCustomValue_behavesPerContract() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.setInclude(null);
        assertNull(props.getInclude());
        props.setInclude("myinclude");
        assertEquals("myinclude", props.getInclude());
    }

    // covers: PropertiesReader skips blank lines and lines starting with '#'
    @Test
    public void testLoad_commentsAndBlankLines_skipped() throws Throwable {
        String text = "\n# a comment\n\nkey=value\n";
        ExtendedProperties props = new ExtendedProperties();
        props.load(new ByteArrayInputStream(text.getBytes("ISO-8859-1")));
        Iterator it = props.getKeys();
        assertEquals("key", it.next());
        assertFalse(it.hasNext());
        assertEquals("value", props.getString("key"));
    }

    // covers: PropertiesReader.readProperty continuation when line ends with odd number of backslashes
    @Test
    public void testLoad_lineContinuation_concatenatesValue() throws Throwable {
        String text = "longvalue = aaa\\\nbbb\n";
        ExtendedProperties props = new ExtendedProperties();
        props.load(new ByteArrayInputStream(text.getBytes("ISO-8859-1")));
        assertEquals("aaabbb", props.getString("longvalue"));
    }

    // covers: addProperty called twice for same key -> addPropertyInternal converts to Vector
    @Test
    public void testLoad_duplicateKey_appendsAsVector() throws Throwable {
        String text = "key=a\nkey=b\n";
        ExtendedProperties props = new ExtendedProperties();
        props.load(new ByteArrayInputStream(text.getBytes("ISO-8859-1")));
        String[] arr = props.getStringArray("key");
        assertEquals(2, arr.length);
        assertEquals("a", arr[0]);
        assertEquals("b", arr[1]);
    }

    // covers: addProperty splits comma separated tokens into multiple entries
    @Test
    public void testLoad_tokensOnALine_splitsIntoArray() throws Throwable {
        String text = "key=first token, second token\n";
        ExtendedProperties props = new ExtendedProperties();
        props.load(new ByteArrayInputStream(text.getBytes("ISO-8859-1")));
        String[] arr = props.getStringArray("key");
        assertEquals(2, arr.length);
        assertEquals("first token", arr[0]);
        assertEquals("second token", arr[1]);
    }

    // covers: PropertiesTokenizer.nextToken keeps escaped comma as literal within single token
    @Test
    public void testLoad_escapedComma_unescapedToLiteralComma() throws Throwable {
        String text = "key=Hi\\, what up\n";
        ExtendedProperties props = new ExtendedProperties();
        props.load(new ByteArrayInputStream(text.getBytes("ISO-8859-1")));
        assertEquals("Hi, what up", props.getString("key"));
    }

    // covers: getProperty returns null when key absent and no defaults set
    @Test
    public void testGetProperty_missingKey_returnsNull() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        assertNull(props.getProperty("missing"));
    }

    // covers: addProperty with no comma stores single String value
    @Test
    public void testAddProperty_singleToken_storesString() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.addProperty("k", "v");
        assertEquals("v", props.getString("k"));
    }

    // covers: addProperty splits comma separated String into multiple tokens
    @Test
    public void testAddProperty_multipleTokens_storesVector() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.addProperty("k", "a,b,c");
        String[] arr = props.getStringArray("k");
        assertEquals(3, arr.length);
        assertEquals("c", arr[2]);
    }

    // covers: addPropertyInternal converts existing String value into Vector on second add
    @Test
    public void testAddProperty_sameKeyTwice_convertsToVector() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.addProperty("k", "a");
        props.addProperty("k", "b");
        Vector v = props.getVector("k");
        assertEquals(2, v.size());
        assertEquals("a", v.get(0));
        assertEquals("b", v.get(1));
    }

    // covers: addProperty else-branch for non-String values
    @Test
    public void testAddProperty_nonStringValue_storesDirectly() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.addProperty("k", new Integer(5));
        assertEquals(new Integer(5), props.get("k"));
    }

    // covers: setProperty = clearProperty + addProperty, replacing prior vectorized value
    @Test
    public void testSetProperty_overwritesExisting() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.addProperty("k", "a,b");
        props.setProperty("k", "z");
        assertEquals("z", props.getString("k"));
        assertEquals(1, props.getStringArray("k").length);
    }

    // covers: save(null,...) returns immediately; save writes header and key=value lines
    @Test
    public void testSave_nullOutputAndHeaderWrite_behavesPerContract() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.setProperty("key", "val");
        props.save(null, "H");
        assertEquals("val", props.getString("key"));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        props.save(out, "MyHeader");
        String content = out.toString();
        assertTrue(content.indexOf("MyHeader") >= 0);
        assertTrue(content.indexOf("key=val") >= 0);
    }



    // covers: combine iterates source keys and setProperty on target, overwriting duplicates
    @Test
    public void testCombine_overwritesAndAddsProperties() throws Throwable {
        ExtendedProperties a = new ExtendedProperties();
        a.setProperty("x", "1");
        ExtendedProperties b = new ExtendedProperties();
        b.setProperty("x", "2");
        b.setProperty("y", "3");
        a.combine(b);
        assertEquals("2", a.getString("x"));
        assertEquals("3", a.getString("y"));
    }

    // covers: clearProperty removes existing key from map and keysAsListed; no-op for missing key
    @Test
    public void testClearProperty_existingAndMissingKey_behavesPerContract() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.setProperty("k", "v");
        props.clearProperty("missing");
        assertEquals("v", props.getString("k"));
        props.clearProperty("k");
        assertFalse(props.containsKey("k"));
        assertFalse(props.getKeys().hasNext());
    }

    // covers: keysAsListed preserves order across multiple setProperty calls
    @Test
    public void testGetKeys_preservesInsertionOrder() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.setProperty("k1", "1");
        props.setProperty("k2", "2");
        props.setProperty("k3", "3");
        Iterator it = props.getKeys();
        assertEquals("k1", it.next());
        assertEquals("k2", it.next());
        assertEquals("k3", it.next());
        assertFalse(it.hasNext());
    }

    // covers: getKeys(prefix) filters keys using startsWith
    @Test
    public void testGetKeysWithPrefix_filtersMatchingKeys() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.setProperty("a.x", "1");
        props.setProperty("a.y", "2");
        props.setProperty("b.z", "3");
        Iterator it = props.getKeys("a.");
        List found = new ArrayList();
        while (it.hasNext()) {
            found.add(it.next());
        }
        assertEquals(2, found.size());
        assertTrue(found.contains("a.x"));
        assertTrue(found.contains("a.y"));
    }

    // covers: subset() strips "prefix." leaving remainder key
    @Test
    public void testSubset_withDotSeparator_returnsStrippedKeys() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.setProperty("a.x", "1");
        props.setProperty("a.y", "2");
        ExtendedProperties sub = props.subset("a");
        assertEquals("1", sub.getString("x"));
        assertEquals("2", sub.getString("y"));
    }

    // covers: subset() branch where key length equals prefix length -> newKey = prefix
    @Test
    public void testSubset_exactPrefixMatch_returnsPrefixKey() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.setProperty("a", "value");
        ExtendedProperties sub = props.subset("a");
        assertEquals("value", sub.getString("a"));
    }

    // covers: subset() returns null when validSubset stays false
    @Test
    public void testSubset_noMatchingKeys_returnsNull() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.setProperty("x", "1");
        assertNull(props.subset("zzz"));
    }

    // covers: getString value instanceof List branch returns first element
    @Test
    public void testGetString_fromListValue_returnsFirstElement() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.addProperty("k", "a,b");
        assertEquals("a", props.getString("k"));
    }

    // covers: getString value==null, no defaults -> interpolate(defaultValue)
    @Test
    public void testGetString_missingKeyWithDefault_returnsDefault() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        assertEquals("def", props.getString("missing", "def"));
        assertNull(props.getString("missing2"));
    }

    // covers: getString throws ClassCastException for non String/List value
    @Test
    public void testGetString_wrongType_throwsClassCastException() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.put("k", new Integer(5));
        try {
            props.getString("k");
            fail("expected ClassCastException");
        } catch (ClassCastException expected) {
        }
    }

    // covers: getProperties parses "key=value" tokens from array
    @Test
    public void testGetProperties_validTokens_parsesKeyValuePairs() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.setProperty("p", "a=1,b=2");
        Properties result = props.getProperties("p");
        assertEquals("1", result.getProperty("a"));
        assertEquals("2", result.getProperty("b"));
    }

    // covers: getProperties throws IllegalArgumentException when token lacks '='
    @Test
    public void testGetProperties_malformedToken_throwsIllegalArgumentException() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.setProperty("p", "novalue");
        try {
            props.getProperties("p");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // covers: getStringArray value==null, defaults==null -> empty array
    @Test
    public void testGetStringArray_missingKeyNoDefaults_returnsEmptyArray() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        String[] arr = props.getStringArray("missing");
        assertEquals(0, arr.length);
    }

    // covers: getVector value instanceof String branch wraps into Vector and stores it back
    @Test
    public void testGetVector_fromStringValue_convertsAndStores() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.put("k", "single");
        Vector v = props.getVector("k");
        assertEquals(1, v.size());
        assertEquals("single", v.get(0));
        assertTrue(props.get("k") instanceof Vector);
    }

    // covers: getVector value==null, defaults==null -> returns supplied defaultValue instance
    @Test
    public void testGetVector_missingKeyWithDefault_returnsSameDefaultVector() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        Vector def = new Vector();
        def.add("d");
        Vector result = props.getVector("missing", def);
        assertSame(def, result);
    }

    // covers: getList returns new ArrayList copy, independent of internal storage
    @Test
    public void testGetList_returnsIndependentCopy() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.addProperty("k", "a,b");
        List list = props.getList("k");
        assertEquals(2, list.size());
        list.add("c");
        assertEquals(2, props.getStringArray("k").length);
    }

    // covers: getBoolean value instanceof Boolean branch
    @Test
    public void testGetBoolean_storedBoolean_returnsValue() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.put("k", Boolean.TRUE);
        assertTrue(props.getBoolean("k"));
    }

    // covers: getBoolean(key) throws when value and default both null
    @Test
    public void testGetBoolean_missingKey_throwsNoSuchElementException() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        try {
            props.getBoolean("missing");
            fail("expected NoSuchElementException");
        } catch (NoSuchElementException expected) {
        }
    }



    // covers: getBoolean throws ClassCastException for non Boolean/String value
    @Test
    public void testGetBoolean_wrongType_throwsClassCastException() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.put("k", new Integer(1));
        try {
            props.getBoolean("k", Boolean.FALSE);
            fail("expected ClassCastException");
        } catch (ClassCastException expected) {
        }
    }

    // covers: testBoolean recognizes true/on/yes, false/off/no case-insensitively, else null
    @Test
    public void testTestBoolean_variousInputs_matchContract() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        assertEquals("true", props.testBoolean("TRUE"));
        assertEquals("true", props.testBoolean("yes"));
        assertEquals("false", props.testBoolean("Off"));
        assertNull(props.testBoolean("maybe"));
    }

    // covers: getInteger parses valid String (boundary MAX_VALUE) and returns default when missing
    @Test
    public void testGetInteger_validAndMissingWithDefault_parsesOrReturnsDefault() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.put("k", String.valueOf(Integer.MAX_VALUE));
        assertEquals(Integer.MAX_VALUE, props.getInteger("k"));
        assertEquals(7, props.getInteger("missing", 7));
    }

    // covers: getInteger propagates NumberFormatException from Integer(String)
    @Test
    public void testGetInteger_invalidFormat_throwsNumberFormatException() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.put("k", "notnum");
        try {
            props.getInteger("k");
            fail("expected NumberFormatException");
        } catch (NumberFormatException expected) {
        }
    }

    // covers: getDouble parses valid String and returns default when missing
    @Test
    public void testGetDouble_validAndMissingWithDefault_parsesWithDelta() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.put("k", "3.14");
        assertEquals(3.14, props.getDouble("k"), 1e-9);
        assertEquals(2.5, props.getDouble("missing2", 2.5), 1e-9);
    }

    // covers: convertProperties iterates propertyNames and setProperty on new instance
    @Test
    public void testConvertProperties_fromJavaProperties_copiesValues() throws Throwable {
        Properties p = new Properties();
        p.setProperty("a", "1");
        ExtendedProperties ep = ExtendedProperties.convertProperties(p);
        assertEquals("1", ep.getString("a"));
    }

    // covers: putAll(Map) ExtendedProperties branch iterates getKeys and puts raw values
    @Test
    public void testPutAll_fromExtendedProperties_copiesValues() throws Throwable {
        ExtendedProperties src = new ExtendedProperties();
        src.setProperty("a", "1");
        src.setProperty("b", "2");
        ExtendedProperties dst = new ExtendedProperties();
        dst.putAll(src);
        assertEquals("1", dst.getString("a"));
        assertEquals("2", dst.getString("b"));
    }

    // covers: interpolateHelper substitutes ${name} with property value
    @Test
    public void testInterpolate_simpleVariable_substituted() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.setProperty("name", "world");
        assertEquals("Hello world", props.interpolate("Hello ${name}"));
    }

    // covers: interpolateHelper keeps ${var} intact when variable undefined and no defaults
    @Test
    public void testInterpolate_undefinedVariable_keepsPlaceholder() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        assertEquals("Hello ${undefined}", props.interpolate("Hello ${undefined}"));
    }

    // covers: interpolateHelper detects loop via priorVariables.contains and throws
    @Test
    public void testInterpolate_circularReference_throwsIllegalStateException() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.setProperty("a", "${b}");
        props.setProperty("b", "${a}");
        try {
            props.interpolate("${a}");
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
        }
    }
}
