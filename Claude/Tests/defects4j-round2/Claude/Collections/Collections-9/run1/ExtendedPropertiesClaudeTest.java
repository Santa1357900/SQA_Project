package org.apache.commons.collections;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.Vector;

import org.junit.Test;
import static org.junit.Assert.*;

public class ExtendedPropertiesClaudeTest {

    // Default constructor: object starts empty and not initialized
    @Test
    public void testConstructorDefault_createsEmptyUninitializedProperties() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        assertFalse(props.isInitialized());
        assertTrue(props.isEmpty());
    }

    // isInitialized becomes true after any addProperty call
    @Test
    public void testIsInitialized_afterAddProperty_returnsTrue() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.addProperty("key", "value");
        assertTrue(props.isInitialized());
    }

    // getInclude default (no setInclude called) returns static "include"
    @Test
    public void testGetInclude_default_returnsIncludeConstant() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        assertEquals("include", props.getInclude());
    }

    // setInclude branches: custom name, empty string hack->null, null hack->null
    @Test
    public void testSetInclude_variousInputs_getIncludeReflectsBackwardCompatHack() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.setInclude("myInclude");
        assertEquals("myInclude", props.getInclude());
        props.setInclude("");
        assertNull(props.getInclude());
        props.setInclude(null);
        assertNull(props.getInclude());
    }

    // load(): simple key=value line parsed and stored
    @Test
    public void testLoad_simpleKeyValue_addsProperty() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        String content = "key = value\n";
        InputStream in = new ByteArrayInputStream(content.getBytes("ISO-8859-1"));
        props.load(in);
        assertEquals("value", props.getString("key"));
    }

    // load(): comment lines and blank lines are skipped by PropertiesReader
    @Test
    public void testLoad_commentsAndBlankLines_areSkipped() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        String content = "# a comment\n\nkey = value\n";
        InputStream in = new ByteArrayInputStream(content.getBytes("ISO-8859-1"));
        props.load(in);
        Iterator it = props.getKeys();
        assertTrue(it.hasNext());
        assertEquals("key", it.next());
        assertFalse(it.hasNext());
    }

    // load(): trailing backslash continues value onto next physical line
    @Test
    public void testLoad_lineContinuation_concatenatesValue() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        String content = "key = aaa\\\nbbb\n";
        InputStream in = new ByteArrayInputStream(content.getBytes("ISO-8859-1"));
        props.load(in);
        assertEquals("aaabbb", props.getString("key"));
    }

    // load(): same key repeated accumulates values into a Vector in order
    @Test
    public void testLoad_duplicateKeys_accumulateIntoVector() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        String content = "key = foo\nkey = bar\n";
        InputStream in = new ByteArrayInputStream(content.getBytes("ISO-8859-1"));
        props.load(in);
        Vector v = props.getVector("key");
        assertEquals(2, v.size());
        assertEquals("foo", v.get(0));
        assertEquals("bar", v.get(1));
    }

    // load(): a key matching getInclude() is special-cased and not added as a normal property
    @Test
    public void testLoad_includeKey_notAddedAsRegularProperty() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        String content = "include = nonexistent-file.properties\n";
        InputStream in = new ByteArrayInputStream(content.getBytes("ISO-8859-1"));
        props.load(in);
        assertNull(props.getProperty("include"));
    }

    // getProperty(): missing key with no defaults returns null
    @Test
    public void testGetProperty_missingKeyNoDefaults_returnsNull() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        assertNull(props.getProperty("missing"));
    }

    // addProperty(): comma-delimited value is split into multiple entries
    @Test
    public void testAddProperty_commaSeparatedValue_splitsIntoThreeEntries() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.addProperty("key", "a,b,c");
        String[] arr = props.getStringArray("key");
        assertEquals(3, arr.length);
        assertEquals("a", arr[0]);
        assertEquals("b", arr[1]);
        assertEquals("c", arr[2]);
    }

    // addProperty(): backslash-escaped comma is kept literal, not treated as separator
    @Test
    public void testAddProperty_escapedComma_keepsSingleValue() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.addProperty("key", "a\\,b");
        assertEquals("a,b", props.getString("key"));
    }

    // addProperty(): value whose ONLY comma is at index 0 must still be tokenized (boundary of indexOf>0 check)
    @Test
    public void testAddProperty_valueStartingWithComma_tokenizedCorrectly() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.addProperty("key", ",value");
        assertEquals("value", props.getString("key"));
    }

    // setProperty(): clears previous value then adds the new one (no accumulation)
    @Test
    public void testSetProperty_overwritesPreviousValue() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.addProperty("key", "old");
        props.setProperty("key", "new");
        assertEquals("new", props.getString("key"));
    }

    // save(): String and List values are written as key=escapedValue lines
    @Test
    public void testSave_writesKeyValueAndEscapesCommas() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.addProperty("key", "a,b");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        props.save(out, "# header");
        String result = out.toString();
        assertTrue(result.indexOf("key=a") >= 0);
        assertTrue(result.indexOf("key=b") >= 0);
    }

    // combine(): existing keys overwritten, new keys added from the other ExtendedProperties
    @Test
    public void testCombine_overwritesExistingAndAddsNew() throws Throwable {
        ExtendedProperties p1 = new ExtendedProperties();
        p1.addProperty("a", "1");
        ExtendedProperties p2 = new ExtendedProperties();
        p2.addProperty("a", "2");
        p2.addProperty("b", "3");
        p1.combine(p2);
        assertEquals("2", p1.getString("a"));
        assertEquals("3", p1.getString("b"));
    }

    // clearProperty(): removes an existing key; is a no-op for a missing key
    @Test
    public void testClearProperty_existingAndMissingKey_behavesCorrectly() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.addProperty("key", "value");
        props.clearProperty("key");
        assertNull(props.getProperty("key"));
        props.clearProperty("missing");
        assertFalse(props.containsKey("missing"));
    }

    // getKeys(): iteration order matches insertion order (keysAsListed)
    @Test
    public void testGetKeys_returnsInsertionOrder() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.addProperty("b", "1");
        props.addProperty("a", "2");
        Iterator it = props.getKeys();
        assertEquals("b", it.next());
        assertEquals("a", it.next());
        assertFalse(it.hasNext());
    }

    // getKeys(prefix): only keys starting with the given prefix are returned
    @Test
    public void testGetKeysWithPrefix_filtersMatchingKeys() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.addProperty("foo.bar", "1");
        props.addProperty("other", "2");
        Iterator it = props.getKeys("foo");
        assertTrue(it.hasNext());
        assertEquals("foo.bar", it.next());
        assertFalse(it.hasNext());
    }

    // subset(): matching keys get the prefix (and separator) stripped
    @Test
    public void testSubset_validPrefix_stripsPrefixFromKeys() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.addProperty("db.url", "x");
        props.addProperty("db.name", "y");
        ExtendedProperties sub = props.subset("db");
        assertEquals("x", sub.getString("url"));
        assertEquals("y", sub.getString("name"));
    }

    // subset(): key exactly equal to prefix keeps the prefix itself as new key
    @Test
    public void testSubset_keyEqualsPrefixExactly_usesPrefixAsNewKey() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.addProperty("db", "value");
        ExtendedProperties sub = props.subset("db");
        assertEquals("value", sub.getString("db"));
    }

    // subset(): no key matches the prefix -> returns null
    @Test
    public void testSubset_noMatch_returnsNull() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.addProperty("foo", "1");
        assertNull(props.subset("zzz"));
    }

    // getString(key): missing key, no defaults object -> null
    @Test
    public void testGetString_missingKeyNoDefault_returnsNull() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        assertNull(props.getString("missing"));
    }

    // getString(key,default): missing key returns supplied default value
    @Test
    public void testGetString_missingKeyWithDefault_returnsDefault() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        assertEquals("def", props.getString("missing", "def"));
    }

    // getString(): List value returns its first element
    @Test
    public void testGetString_listValue_returnsFirstElement() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.addProperty("key", "a,b");
        assertEquals("a", props.getString("key"));
    }

    // getString(): value that is neither String nor List throws ClassCastException
    @Test
    public void testGetString_nonStringNonListValue_throwsClassCastException() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.put("key", new Integer(5));
        try {
            props.getString("key");
            fail("expected ClassCastException");
        } catch (ClassCastException expected) {
        }
    }

    // interpolate(): ${name} is substituted with the value of property "name"
    @Test
    public void testGetString_interpolatesDefinedVariable() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.addProperty("name", "world");
        props.addProperty("greeting", "Hello ${name}");
        assertEquals("Hello world", props.getString("greeting"));
    }

    // interpolate(): undefined variable placeholder is left untouched
    @Test
    public void testGetString_interpolateUndefinedVariable_keepsPlaceholder() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.addProperty("greeting", "Hello ${missing}");
        assertEquals("Hello ${missing}", props.getString("greeting"));
    }

    // interpolate(): circular ${a}->${b}->${a} reference throws IllegalStateException
    @Test
    public void testGetString_interpolateLoop_throwsIllegalStateException() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.addProperty("a", "${b}");
        props.addProperty("b", "${a}");
        try {
            props.getString("a");
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
        }
    }

    // getProperties(key): tokens of form key=value are parsed into a Properties object
    @Test
    public void testGetProperties_validTokens_parsesKeyValuePairs() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.addProperty("key", "a=1,b=2");
        Properties result = props.getProperties("key");
        assertEquals("1", result.getProperty("a"));
        assertEquals("2", result.getProperty("b"));
    }

    // getProperties(key): token without an equals sign throws IllegalArgumentException
    @Test
    public void testGetProperties_malformedToken_throwsIllegalArgumentException() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.addProperty("key", "noequalsign");
        try {
            props.getProperties("key");
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
        }
    }

    // getStringArray(): missing key with no defaults returns a zero-length array
    @Test
    public void testGetStringArray_missingKeyNoDefaults_returnsEmptyArray() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        String[] arr = props.getStringArray("missing");
        assertEquals(0, arr.length);
    }

    // getVector(key): missing key with no default value returns a new empty Vector (not null)
    @Test
    public void testGetVector_missingKeyNoDefaultValue_returnsEmptyVector() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        Vector v = props.getVector("missing");
        assertNotNull(v);
        assertEquals(0, v.size());
    }

    // getVector(): value that is neither List nor String throws ClassCastException
    @Test
    public void testGetVector_nonListNonStringValue_throwsClassCastException() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.put("key", new Integer(7));
        try {
            props.getVector("key");
            fail("expected ClassCastException");
        } catch (ClassCastException expected) {
        }
    }

    // getList(): a single String value is converted into a one-element List
    @Test
    public void testGetList_stringValue_convertsToSingleElementList() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.addProperty("key", "v");
        List list = props.getList("key");
        assertEquals(1, list.size());
        assertEquals("v", list.get(0));
    }

    // getBoolean(key): "yes"/"off" style strings are parsed per testBoolean contract
    @Test
    public void testGetBoolean_validVariants_parsedCorrectly() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.addProperty("t", "yes");
        props.addProperty("f", "off");
        assertTrue(props.getBoolean("t"));
        assertFalse(props.getBoolean("f"));
    }

    // getBoolean(key): missing key with no default throws NoSuchElementException
    @Test
    public void testGetBoolean_missingKeyNoDefault_throwsNoSuchElementException() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        try {
            props.getBoolean("missing");
            fail("expected NoSuchElementException");
        } catch (NoSuchElementException expected) {
        }
    }

    // testBoolean(): true/false synonyms recognized case-insensitively, unknown value -> null
    @Test
    public void testTestBoolean_variousInputs_returnsExpectedStrings() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        assertEquals("true", props.testBoolean("TRUE"));
        assertEquals("false", props.testBoolean("No"));
        assertNull(props.testBoolean("maybe"));
    }

    // numeric getters (byte/short/int/long/float/double): valid numeric strings parsed correctly
    @Test
    public void testNumericGetters_validStrings_parsedCorrectly() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.addProperty("b", "5");
        props.addProperty("s", "100");
        props.addProperty("i", "42");
        props.addProperty("l", "123456789012");
        props.addProperty("f", "3.5");
        props.addProperty("d", "2.25");
        assertEquals((byte) 5, props.getByte("b"));
        assertEquals((short) 100, props.getShort("s", (short) 0));
        assertEquals(42, props.getInt("i"));
        assertEquals(123456789012L, props.getLong("l", 0L));
        assertEquals(3.5f, props.getFloat("f", 0f), 0.0001f);
        assertEquals(2.25, props.getDouble("d"), 0.0001);
    }

    // getDouble(key): missing key with no default throws NoSuchElementException
    @Test
    public void testGetDouble_missingKeyNoDefault_throwsNoSuchElementException() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        try {
            props.getDouble("missing");
            fail("expected NoSuchElementException");
        } catch (NoSuchElementException expected) {
        }
    }

    // convertProperties(): every key from a java.util.Properties is copied over
    @Test
    public void testConvertProperties_fromJavaProperties_copiesEntries() throws Throwable {
        Properties p = new Properties();
        p.setProperty("a", "1");
        p.setProperty("b", "2");
        ExtendedProperties ep = ExtendedProperties.convertProperties(p);
        assertEquals("1", ep.getString("a"));
        assertEquals("2", ep.getString("b"));
    }

    // put(): behaves like addProperty (accumulates) and returns prior value via getProperty
    @Test
    public void testPut_duplicateKey_accumulatesAndReturnsOldValue() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        Object old1 = props.put("k", "v1");
        assertNull(old1);
        Object old2 = props.put("k", "v2");
        assertEquals("v1", old2);
        Vector v = props.getVector("k");
        assertEquals(2, v.size());
        assertEquals("v1", v.get(0));
        assertEquals("v2", v.get(1));
    }

    // putAll(): source is an ExtendedProperties -> all keys copied preserving values
    @Test
    public void testPutAll_fromExtendedProperties_copiesAllKeys() throws Throwable {
        ExtendedProperties source = new ExtendedProperties();
        source.addProperty("a", "1");
        source.addProperty("b", "2");
        ExtendedProperties target = new ExtendedProperties();
        target.putAll(source);
        assertEquals("1", target.getString("a"));
        assertEquals("2", target.getString("b"));
    }

    // putAll(): source is a plain Map -> entrySet branch copies entries
    @Test
    public void testPutAll_fromPlainMap_copiesEntries() throws Throwable {
        Map map = new HashMap();
        map.put("x", "10");
        ExtendedProperties target = new ExtendedProperties();
        target.putAll(map);
        assertEquals("10", target.getString("x"));
    }

    // remove(): existing key is cleared and its old value returned; missing key returns null
    @Test
    public void testRemove_existingAndMissingKey_behavesCorrectly() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.put("k", "v");
        Object removed = props.remove("k");
        assertEquals("v", removed);
        assertNull(props.getProperty("k"));
        assertNull(props.remove("missing"));
    }
}
