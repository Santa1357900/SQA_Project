package org.apache.commons.collections;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Properties;
import java.util.Vector;

public class ExtendedPropertiesTest {

    @Test
    public void testDefaultConstructorAndInitialization() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        assertFalse(props.isInitialized());
        assertNull(props.getInclude());
        
        props.setInclude("customInclude");
        assertEquals("customInclude", props.getInclude());
    }

    @Test
    public void testFileConstructorsAndLoad() throws Throwable {
        File tempFile = File.createTempFile("testProps", ".properties");
        tempFile.deleteOnExit();
        
        java.io.FileWriter writer = new java.io.FileWriter(tempFile);
        writer.write("key1 = value1\n");
        writer.write("key2 = token1, token2\n");
        writer.write("long.val = line1 \\\n line2\n");
        writer.close();

        ExtendedProperties props = new ExtendedProperties(tempFile.getAbsolutePath());
        assertTrue(props.isInitialized());
        assertEquals("value1", props.getString("key1"));
        
        List list = props.getList("key2");
        assertNotNull(list);
        assertEquals(2, list.size());
    }

    @Test
    public void testFileConstructorWithDefaults() throws Throwable {
        File tempFile = File.createTempFile("mainProps", ".properties");
        tempFile.deleteOnExit();
        File defaultFile = File.createTempFile("defaultProps", ".properties");
        defaultFile.deleteOnExit();

        java.io.FileWriter w1 = new java.io.FileWriter(tempFile);
        w1.write("main.key = mainVal\n");
        w1.close();

        java.io.FileWriter w2 = new java.io.FileWriter(defaultFile);
        w2.write("def.key = defVal\n");
        w2.close();

        ExtendedProperties props = new ExtendedProperties(tempFile.getAbsolutePath(), defaultFile.getAbsolutePath());
        assertEquals("mainVal", props.getString("main.key"));
        assertEquals("defVal", props.getString("def.key"));
    }

    @Test
    public void testLoadInputStreamWithEncoding() throws Throwable {
        String data = "foo=bar\n#comment\nempty=\n";
        ByteArrayInputStream bais = new ByteArrayInputStream(data.getBytes("UTF-8"));
        ExtendedProperties props = new ExtendedProperties();
        props.load(bais, "UTF-8");
        assertEquals("bar", props.getString("foo"));
    }

    @Test
    public void testLoadInputStreamUnsupportedEncodingFallback() throws Throwable {
        String data = "foo=bar";
        ByteArrayInputStream bais = new ByteArrayInputStream(data.getBytes("ISO-8859-1"));
        ExtendedProperties props = new ExtendedProperties();
        props.load(bais, "INVALID_ENCODING_NAME");
        assertEquals("bar", props.getString("foo"));
    }

    @Test
    public void testIncludeMechanism() throws Throwable {
        File includedFile = File.createTempFile("included", ".properties");
        includedFile.deleteOnExit();
        java.io.FileWriter fwInc = new java.io.FileWriter(includedFile);
        fwInc.write("inc.key = incVal\n");
        fwInc.close();

        File mainFile = File.createTempFile("main", ".properties");
        mainFile.deleteOnExit();
        java.io.FileWriter fwMain = new java.io.FileWriter(mainFile);
        fwMain.write("include = " + includedFile.getAbsolutePath() + "\n");
        fwMain.close();

        ExtendedProperties props = new ExtendedProperties(mainFile.getAbsolutePath());
        assertEquals("incVal", props.getString("inc.key"));
    }

    @Test
    public void testIncludeRelativePathAndDotSlash() throws Throwable {
        File dir = new File(System.getProperty("java.io.tmpdir"));
        File incFile = new File(dir, "relInc.properties");
        incFile.deleteOnExit();
        java.io.FileWriter fw = new java.io.FileWriter(incFile);
        fw.write("rel.key = relVal\n");
        fw.close();

        File mainFile = File.createTempFile("mainRel", ".properties");
        mainFile.deleteOnExit();
        java.io.FileWriter fwMain = new java.io.FileWriter(mainFile);
        fwMain.write("include = ." + System.getProperty("file.separator") + incFile.getName() + "\n");
        fwMain.close();

        ExtendedProperties props = new ExtendedProperties(mainFile.getAbsolutePath());
        assertEquals("relVal", props.getString("rel.key"));
    }

    @Test
    public void testAddPropertyAndGetProperty() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.addProperty("p1", "val1");
        props.addProperty("p1", "val2"); // turns into List
        
        Object obj = props.getProperty("p1");
        assertTrue(obj instanceof List);
        
        // Add non-string object
        props.addProperty("p2", Integer.valueOf(10));
        assertEquals(Integer.valueOf(10), props.getProperty("p2"));

        // Add string with commas and escaped commas/backslashes
        props.addProperty("p3", "a\\,b,c\\\\d");
        List l3 = props.getList("p3");
        assertEquals("a,b", l3.get(0));
        assertEquals("c\\d", l3.get(1));
    }

    @Test
    public void testSetPropertyAndClearProperty() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.setProperty("testKey", "testVal");
        assertEquals("testVal", props.getString("testKey"));

        props.clearProperty("testKey");
        assertNull(props.getString("testKey"));
        
        // Clearing non-existent property
        props.clearProperty("nonExistent");
    }

    @Test
    public void testSaveAndCombine() throws Throwable {
        ExtendedProperties props1 = new ExtendedProperties();
        props1.setProperty("k1", "v1");
        props1.addProperty("k2", "v2a");
        props1.addProperty("k2", "v2b");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        props1.save(baos, "Header Comment");
        assertTrue(baos.size() > 0);

        // Save with null output and null header
        props1.save(null, null);
        ByteArrayOutputStream baos2 = new ByteArrayOutputStream();
        props1.save(baos2, null);

        ExtendedProperties props2 = new ExtendedProperties();
        props2.setProperty("k3", "v3");
        props1.combine(props2);
        assertEquals("v3", props1.getString("k3"));
    }

    @Test
    public void testGetKeysAndSubset() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.setProperty("prefix.a", "1");
        props.setProperty("prefix.b", "2");
        props.setProperty("other", "3");

        Iterator it = props.getKeys("prefix");
        List keys = new ArrayList();
        while (it.hasNext()) {
            keys.add(it.next());
        }
        assertEquals(2, keys.size());

        ExtendedProperties subset = props.subset("prefix");
        assertNotNull(subset);
        assertEquals("1", subset.getString("a"));

        // Exact match key subset
        props.setProperty("exact", "val");
        ExtendedProperties exactSubset = props.subset("exact");
        assertNotNull(exactSubset);
        assertEquals("val", exactSubset.getString("exact"));

        // Invalid subset
        assertNull(props.subset("nonexistent"));
    }

    @Test
    public void testInterpolationAndInfiniteLoop() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.setProperty("base", "world");
        props.setProperty("greeting", "Hello ${base}");
        assertEquals("Hello world", props.getString("greeting"));

        // Undefined variable interpolation
        props.setProperty("undef", "Hello ${missing}");
        assertEquals("Hello ${missing}", props.getString("undef"));

        // Null base interpolation
        assertEquals("", props.getString("emptyKey", ""));

        // Infinite loop detection
        props.setProperty("loop1", "${loop2}");
        props.setProperty("loop2", "${loop1}");
        try {
            props.getString("loop1");
            fail("Should have thrown IllegalStateException for infinite loop");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("infinite loop"));
        }
    }

    @Test
    public void testGettersBasicTypes() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.setProperty("str", "hello");
        props.setProperty("bool1", "true");
        props.setProperty("bool2", "yes");
        props.setProperty("bool3", "off");
        props.setProperty("byte", "12");
        props.setProperty("short", "123");
        props.setProperty("int", "1234");
        props.setProperty("long", "123456");
        props.setProperty("float", "1.23");
        props.setProperty("double", "1.2345");

        assertEquals("hello", props.getString("str"));
        assertTrue(props.getBoolean("bool1"));
        assertTrue(props.getBoolean("bool2"));
        assertFalse(props.getBoolean("bool3", true));
        assertEquals((byte) 12, props.getByte("str", (byte) 5)); // fallback to default since wrong type
        assertEquals((byte) 12, props.getByte("byte"));
        assertEquals((short) 123, props.getShort("short"));
        assertEquals(1234, props.getInt("int"));
        assertEquals(1234, props.getInt("int", 0));
        assertEquals(123456L, props.getLong("long"));
        assertEquals(1.23f, props.getFloat("float"), 0.001f);
        assertEquals(1.2345, props.getDouble("double"), 0.0001);

        // Test non-existent with defaults
        assertEquals(99, props.getInt("missing.int", 99));
        assertEquals(99L, props.getLong("missing.long", 99L));
        assertEquals(99f, props.getFloat("missing.float", 99f), 0.001f);
        assertEquals(99.0, props.getDouble("missing.double", 99.0), 0.001);
        assertEquals((byte) 9, props.getByte("missing.byte", (byte) 9));
        assertEquals((short) 9, props.getShort("missing.short", (short) 9));
        assertEquals(Boolean.TRUE, props.getBoolean("missing.bool", Boolean.TRUE));
    }

    @Test(expected = NoSuchElementException.class)
    public void testGetBooleanMissingThrows() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.getBoolean("nonexistent");
    }

    @Test(expected = NoSuchElementException.class)
    public void testGetByteMissingThrows() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.getByte("nonexistent");
    }

    @Test(expected = NoSuchElementException.class)
    public void testGetShortMissingThrows() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.getShort("nonexistent");
    }

    @Test(expected = NoSuchElementException.class)
    public void testGetIntegerMissingThrows() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.getInteger("nonexistent");
    }

    @Test(expected = NoSuchElementException.class)
    public void testGetLongMissingThrows() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.getLong("nonexistent");
    }

    @Test(expected = NoSuchElementException.class)
    public void testGetFloatMissingThrows() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.getFloat("nonexistent");
    }

    @Test(expected = NoSuchElementException.class)
    public void testGetDoubleMissingThrows() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.getDouble("nonexistent");
    }

    @Test(expected = ClassCastException.class)
    public void testGetStringClassCastException() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.put("key", Integer.valueOf(123));
        props.getString("key");
    }

    @Test(expected = ClassCastException.class)
    public void testGetBooleanClassCastException() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.put("key", Integer.valueOf(123));
        props.getBoolean("key");
    }

    @Test(expected = ClassCastException.class)
    public void testGetByteClassCastException() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.put("key", Integer.valueOf(123));
        props.getByte("key");
    }

    @Test(expected = ClassCastException.class)
    public void testGetShortClassCastException() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.put("key", Integer.valueOf(123));
        props.getShort("key");
    }

    @Test(expected = ClassCastException.class)
    public void testGetIntegerClassCastException() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.put("key", "notAnInt");
        props.getInteger("key");
    }

    @Test(expected = ClassCastException.class)
    public void testGetLongClassCastException() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.put("key", Integer.valueOf(123));
        props.getLong("key");
    }

    @Test(expected = ClassCastException.class)
    public void testGetFloatClassCastException() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.put("key", Integer.valueOf(123));
        props.getFloat("key");
    }

    @Test(expected = ClassCastException.class)
    public void testGetDoubleClassCastException() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.put("key", Integer.valueOf(123));
        props.getDouble("key");
    }

    @Test(expected = ClassCastException.class)
    public void testGetStringArrayClassCastException() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.put("key", Integer.valueOf(123));
        props.getStringArray("key");
    }

    @Test(expected = ClassCastException.class)
    public void testGetVectorClassCastException() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.put("key", Integer.valueOf(123));
        props.getVector("key");
    }

    @Test(expected = ClassCastException.class)
    public void testGetListClassCastException() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.put("key", Integer.valueOf(123));
        props.getList("key");
    }

    @Test
    public void testGetPropertiesAndStringArrayAndVectorAndList() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.setProperty("propKey", "a=1, b=2");
        Properties javaProps = props.getProperties("propKey");
        assertEquals("1", javaProps.getProperty("a"));
        assertEquals("2", javaProps.getProperty("b"));

        // Malformed property token without equals sign
        props.setProperty("badKey", "malformedToken");
        try {
            props.getProperties("badKey");
            fail("Should throw IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("does not contain"));
        }

        // String array
        props.setProperty("arrKey", "val1");
        String[] arr = props.getStringArray("arrKey");
        assertEquals(1, arr.length);

        // Vector
        Vector vec = props.getVector("arrKey");
        assertEquals(1, vec.size());
        Vector defaultVec = new Vector();
        assertEquals(defaultVec, props.getVector("nonexistent", defaultVec));
        assertNotNull(props.getVector("nonexistent"));

        // List
        List list = props.getList("arrKey");
        assertEquals(1, list.size());
        List defaultList = new ArrayList();
        assertEquals(defaultList, props.getList("nonexistent", defaultList));
        assertNotNull(props.getList("nonexistent"));
        
        // List from String conversion
        props.setProperty("singleStr", "value");
        List convertedList = props.getList("singleStr");
        assertEquals(1, convertedList.size());

        // Vector from String conversion
        Vector convertedVec = props.getVector("singleStr");
        assertEquals(1, convertedVec.size());
    }

    @Test
    public void testConvertPropertiesStatic() throws Throwable {
        Properties p = new Properties();
        p.setProperty("test.prop", "testValue");
        ExtendedProperties ep = ExtendedProperties.convertProperties(p);
        assertEquals("testValue", ep.getString("test.prop"));
    }

    @Test
    public void testDisplayMethod() throws Throwable {
        ExtendedProperties props = new ExtendedProperties();
        props.setProperty("display.key", "display.value");
        props.display(); // Just ensure it executes without error
    }

    @Test
    public void testPropertiesReaderAndTokenizerDirectly() throws Throwable {
        String content = "# Comment line\n" +
                         "  \n" +
                         "key.one = value1 \\\n" +
                         "          value2\n" +
                         "key.two = token1\\, token2, token3";
        
        ExtendedProperties.PropertiesReader reader = new ExtendedProperties.PropertiesReader(new StringReader(content));
        String propLine1 = reader.readProperty();
        assertNotNull(propLine1);
        
        String propLine2 = reader.readProperty();
        assertNotNull(propLine2);
        
        assertNull(reader.readProperty()); // EOF

        ExtendedProperties.PropertiesTokenizer tokenizer = new ExtendedProperties.PropertiesTokenizer("token1\\, token2, token3");
        assertTrue(tokenizer.hasMoreTokens());
        assertEquals("token1, token2", tokenizer.nextToken());
        assertTrue(tokenizer.hasMoreTokens());
        assertEquals("token3", tokenizer.nextToken());
        assertFalse(tokenizer.hasMoreTokens());
    }
}