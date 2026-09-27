package org.apache.commons.collections;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.Vector;

public class ExtendedPropertiesTest {

    @Test
    public void testEmptyConstructor() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        assertFalse(props.isInitialized());
        assertNull(props.getInclude());
        props.setInclude("customInclude");
        assertEquals("customInclude", props.getInclude());
        props.setInclude(null);
        assertNull(props.getInclude());
        props.setInclude("");
        assertNull(props.getInclude());
    }

    @Test
    public void testFileConstructorAndLoad() throws Throwable {
        File tempFile = File.createTempFile("testProps", ".properties");
        tempFile.deleteOnExit();
        java.io.FileWriter writer = new java.io.FileWriter(tempFile);
        writer.write("key1 = value1\n");
        writer.write("key2 = token1,token2\n");
        writer.write("long = line1 \\\n line2\n");
        writer.write("include = nonExistent.properties\n");
        writer.close();

        ExtendedProperties props = new ExtendedProperties(tempFile.getAbsolutePath());
        assertTrue(props.isInitialized());
        assertEquals("value1", props.getString("key1"));

        Vector vec = props.getVector("key2");
        assertNotNull(vec);
        assertEquals(2, vec.size());

        // Test output save
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        props.save(out, "Header Comment");
        assertTrue(out.toString().length() > 0);
    }

    @Test
    public void testFileConstructorWithDefault() throws Throwable {
        File tempFile = File.createTempFile("testMain", ".properties");
        File defaultFile = File.createTempFile("testDefault", ".properties");
        tempFile.deleteOnExit();
        defaultFile.deleteOnExit();

        java.io.FileWriter fw1 = new java.io.FileWriter(tempFile);
        fw1.write("main.key = mainVal\n");
        fw1.close();

        java.io.FileWriter fw2 = new java.io.FileWriter(defaultFile);
        fw2.write("default.key = defaultVal\n");
        fw2.close();

        ExtendedProperties props = new ExtendedProperties(tempFile.getAbsolutePath(), defaultFile.getAbsolutePath());
        assertEquals("mainVal", props.getString("main.key"));
        assertEquals("defaultVal", props.getString("default.key"));
    }

    @Test
    public void testLoadFromStreamWithEncodings() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        String data = "foo=bar\n#comment\n=emptykey\n";
        ByteArrayInputStream bais = new ByteArrayInputStream(data.getBytes("UTF-8"));
        props.load(bais, "UTF-8");
        assertEquals("bar", props.getString("foo"));

        ByteArrayInputStream bais2 = new ByteArrayInputStream(data.getBytes("8859_1"));
        props.load(bais2, "INVALID_ENC");
        assertEquals("bar", props.getString("foo"));
    }

    @Test
    public void testPropertiesReaderAndTokenizer() throws Throwable {
        String content = "a = 1\\\n2\n# comment\n\nb = token1\\, token2, token3";
        ExtendedProperties.PropertiesReader reader = new ExtendedProperties.PropertiesReader(new StringReader(content));
        assertNotNull(reader.readProperty());
        assertNotNull(reader.readProperty());
        assertNull(reader.readProperty());

        ExtendedProperties.PropertiesTokenizer tokenizer = new ExtendedProperties.PropertiesTokenizer("token1\\,token2,token3");
        assertTrue(tokenizer.hasMoreTokens());
        assertEquals("token1,token2", tokenizer.nextToken());
        assertEquals("token3", tokenizer.nextToken());
        assertFalse(tokenizer.hasMoreTokens());
    }

    @Test
    public void testInterpolationInfiniteLoop() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.setProperty("var1", "${var2}");
        props.setProperty("var2", "${var1}");
        try {
            props.getString("var1");
            fail("Expected IllegalStateException due to infinite loop");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("infinite loop"));
        }
    }

    @Test
    public void testInterpolationDefaultsAndMissing() throws Throwable {
        ExtendedProperties defaults = new ExtendedProperties();
        defaults.setProperty("def.key", "defVal");

        ExtendedProperties props = new ExtendedProperties();
        // use reflection or subclassing or rely on constructor defaults if available, 
        // but here defaults field is private, so we can test missing / standard interpolation:
        props.setProperty("test", "${missing}");
        assertEquals("${missing}", props.getString("test"));
        
        props.setProperty("test2", "${test.unresolved}");
        assertNull(props.getString("nonexistent"));
        assertEquals("fallback", props.getString("nonexistent", "fallback"));
    }

    @Test
    public void testDataTypesGettersAndCastExceptions() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.setProperty("bool1", "true");
        props.setProperty("bool2", "off");
        props.setProperty("bool3", "invalid");
        props.setProperty("byte1", "12");
        props.setProperty("short1", "123");
        props.setProperty("int1", "1234");
        props.setProperty("long1", "123456");
        props.setProperty("float1", "1.23");
        props.setProperty("double1", "1.2345");
        props.setProperty("stringVal", "hello");
        props.setProperty("listVal", new ArrayList<String>());

        assertTrue(props.getBoolean("bool1"));
        assertFalse(props.getBoolean("bool2", true));
        assertNull(props.testBoolean("bad"));
        assertEquals("true", props.testBoolean("YES"));
        assertEquals("false", props.testBoolean("NO"));

        assertEquals((byte) 12, props.getByte("byte1"));
        assertEquals((byte) 5, props.getByte("missing", (byte) 5));
        assertEquals((byte) 5, props.getByte("missing", Byte.valueOf((byte)5))).byteValue();

        assertEquals((short) 123, props.getShort("short1"));
        assertEquals((short) 5, props.getShort("missing", (short) 5));
        assertEquals(Short.valueOf((short)5), props.getShort("missing", Short.valueOf((short)5)));

        assertEquals(1234, props.getInt("int1"));
        assertEquals(1234, props.getInteger("int1"));
        assertEquals(5, props.getInt("missing", 5));
        assertEquals(Integer.valueOf(5), props.getInteger("missing", Integer.valueOf(5)));

        assertEquals(123456L, props.getLong("long1"));
        assertEquals(5L, props.getLong("missing", 5L));
        assertEquals(Long.valueOf(5L), props.getLong("missing", Long.valueOf(5L)));

        assertEquals(1.23f, props.getFloat("float1"), 0.01f);
        assertEquals(5.0f, props.getFloat("missing", 5.0f), 0.01f);
        assertEquals(Float.valueOf(5.0f), props.getFloat("missing", Float.valueOf(5.0f)));

        assertEquals(1.2345, props.getDouble("double1"), 0.0001);
        assertEquals(5.0, props.getDouble("missing", 5.0), 0.0001);
        assertEquals(Double.valueOf(5.0), props.getDouble("missing", Double.valueOf(5.0)));

        // ClassCastException testing
        try {
            props.getString("int1");
            fail("Expected ClassCastException");
        } catch (ClassCastException e) {
            assertTrue(true);
        }

        try {
            props.getBoolean("stringVal");
            fail("Expected ClassCastException");
        } catch (ClassCastException e) {
            assertTrue(true);
        }

        try {
            props.getByte("stringVal");
            fail("Expected ClassCastException");
        } catch (ClassCastException e) {
            assertTrue(true);
        }

        try {
            props.getShort("stringVal");
            fail("Expected ClassCastException");
        } catch (ClassCastException e) {
            assertTrue(true);
        }

        try {
            props.getInteger("stringVal");
            fail("Expected ClassCastException");
        } catch (ClassCastException e) {
            assertTrue(true);
        }

        try {
            props.getLong("stringVal");
            fail("Expected ClassCastException");
        } catch (ClassCastException e) {
            assertTrue(true);
        }

        try {
            props.getFloat("stringVal");
            fail("Expected ClassCastException");
        } catch (ClassCastException e) {
            assertTrue(true);
        }

        try {
            props.getDouble("stringVal");
            fail("Expected ClassCastException");
        } catch (ClassCastException e) {
            assertTrue(true);
        }

        // NoSuchElementException testing
        try {
            props.getBoolean("nonexistent.strict");
            fail("Expected NoSuchElementException");
        } catch (NoSuchElementException e) {
            assertTrue(true);
        }
        try {
            props.getByte("nonexistent.strict");
            fail("Expected NoSuchElementException");
        } catch (NoSuchElementException e) {
            assertTrue(true);
        }
        try {
            props.getShort("nonexistent.strict");
            fail("Expected NoSuchElementException");
        } catch (NoSuchElementException e) {
            assertTrue(true);
        }
        try {
            props.getInteger("nonexistent.strict");
            fail("Expected NoSuchElementException");
        } catch (NoSuchElementException e) {
            assertTrue(true);
        }
        try {
            props.getLong("nonexistent.strict");
            fail("Expected NoSuchElementException");
        } catch (NoSuchElementException e) {
            assertTrue(true);
        }
        try {
            props.getFloat("nonexistent.strict");
            fail("Expected NoSuchElementException");
        } catch (NoSuchElementException e) {
            assertTrue(true);
        }
        try {
            props.getDouble("nonexistent.strict");
            fail("Expected NoSuchElementException");
        } catch (NoSuchElementException e) {
            assertTrue(true);
        }
    }

    @Test
    public void testGetPropertiesAndStringArray() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.setProperty("prop.list", "k1=v1,k2=v2");
        Properties p = props.getProperties("prop.list");
        assertEquals("v1", p.getProperty("k1"));

        props.setProperty("invalid.prop", "malformedToken");
        try {
            props.getProperties("invalid.prop");
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(true);
        }

        String[] arr = props.getStringArray("prop.list");
        assertEquals(2, arr.length);

        String[] emptyArr = props.getStringArray("nonexistent");
        assertEquals(0, emptyArr.length);

        props.setProperty("badType", Integer.valueOf(123));
        try {
            props.getStringArray("badType");
            fail("Expected ClassCastException");
        } catch (ClassCastException e) {
            assertTrue(true);
        }
    }

    @Test
    public void testVectorAndListOperations() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.setProperty("vectorKey", "val1,val2");
        Vector v = props.getVector("vectorKey");
        assertEquals(2, v.size());

        Vector defVector = new Vector();
        assertEquals(defVector, props.getVector("missing", defVector));
        assertNotNull(props.getVector("missing", null));

        props.setProperty("badVector", Integer.valueOf(1));
        try {
            props.getVector("badVector");
            fail("Expected ClassCastException");
        } catch (ClassCastException e) {
            assertTrue(true);
        }

        List list = props.getList("vectorKey");
        assertEquals(2, list.size());
        List defList = new ArrayList();
        assertEquals(defList, props.getList("missing", defList));
        assertNotNull(props.getList("missing", null));

        props.setProperty("badList", Integer.valueOf(1));
        try {
            props.getList("badList");
            fail("Expected ClassCastException");
        } catch (ClassCastException e) {
            assertTrue(true);
        }
    }

    @Test
    public void testSubsetAndCombineAndManipulation() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.setProperty("prefix.a", "1");
        props.setProperty("prefix.b", "2");
        props.setProperty("prefix", "exact");
        props.setProperty("other", "3");

        ExtendedProperties subset = props.subset("prefix");
        assertNotNull(subset);
        assertEquals("1", subset.getString("a"));
        assertEquals("exact", subset.getString("prefix"));

        assertNull(props.subset("nonexistent"));

        ExtendedProperties props2 = new ExtendedProperties();
        props2.setProperty("prefix.a", "99");
        props.combine(props2);
        assertEquals("99", props.getString("prefix.a"));

        Iterator keysWithPrefix = props.getKeys("prefix");
        assertTrue(keysWithPrefix.hasNext());

        props.display();

        props.clearProperty("prefix.a");
        assertNull(props.getString("prefix.a"));

        Object putRet = props.put("putKey", "putVal");
        assertNull(putRet);
        assertEquals("putVal", props.getString("putKey"));

        Map<String, String> map = new HashMap<String, String>();
        map.put("mapKey", "mapVal");
        props.putAll(map);
        assertEquals("mapVal", props.getString("mapKey"));

        ExtendedProperties otherExtMap = new ExtendedProperties();
        otherExtMap.setProperty("extKey", "extVal");
        props.putAll(otherExtMap);
        assertEquals("extVal", props.getString("extKey"));

        Object removeRet = props.remove("putKey");
        assertEquals("putVal", removeRet);

        Properties standardProps = new Properties();
        standardProps.setProperty("stdKey", "stdVal");
        ExtendedProperties converted = ExtendedProperties.convertProperties(standardProps);
        assertEquals("stdVal", converted.getString("stdKey"));
    }
}