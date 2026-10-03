package com.fasterxml.jackson.databind.deser.std;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.File;
import java.net.URI;
import java.net.URL;
import java.util.Currency;
import java.util.Locale;
import java.util.TimeZone;
import java.nio.charset.Charset;
import java.util.regex.Pattern;
import java.net.InetAddress;
import java.net.InetSocketAddress;

public class FromStringDeserializerTest {

    @Test
    public void testTypesArray() throws Throwable {
        Class<?>[] types = FromStringDeserializer.types();
        assertNotNull(types);
        assertTrue(types.length > 0);
    }

    @Test
    public void testFindDeserializerValidTypes() throws Throwable {
        assertNotNull(FromStringDeserializer.findDeserializer(File.class));
        assertNotNull(FromStringDeserializer.findDeserializer(URL.class));
        assertNotNull(FromStringDeserializer.findDeserializer(URI.class));
        assertNotNull(FromStringDeserializer.findDeserializer(Class.class));
        assertNotNull(FromStringDeserializer.findDeserializer(com.fasterxml.jackson.databind.JavaType.class));
        assertNotNull(FromStringDeserializer.findDeserializer(Currency.class));
        assertNotNull(FromStringDeserializer.findDeserializer(Pattern.class));
        assertNotNull(FromStringDeserializer.findDeserializer(Locale.class));
        assertNotNull(FromStringDeserializer.findDeserializer(Charset.class));
        assertNotNull(FromStringDeserializer.findDeserializer(TimeZone.class));
        assertNotNull(FromStringDeserializer.findDeserializer(InetAddress.class));
        assertNotNull(FromStringDeserializer.findDeserializer(InetSocketAddress.class));
    }

    @Test
    public void testFindDeserializerInvalidType() throws Throwable {
        assertNull(FromStringDeserializer.findDeserializer(Integer.class));
    }

    @Test
    public void testStdDeserializeFile() throws Throwable {
        FromStringDeserializer.Std des = FromStringDeserializer.findDeserializer(File.class);
        assertNotNull(des);
        Object res = des._deserialize("test.txt", null);
        assertNotNull(res);
        assertTrue(res instanceof File);
    }

    @Test
    public void testStdDeserializeURI() throws Throwable {
        FromStringDeserializer.Std des = FromStringDeserializer.findDeserializer(URI.class);
        assertNotNull(des);
        Object res = des._deserialize("http://localhost", null);
        assertNotNull(res);
        assertTrue(res instanceof URI);
    }

    @Test
    public void testStdDeserializeCurrency() throws Throwable {
        FromStringDeserializer.Std des = FromStringDeserializer.findDeserializer(Currency.class);
        assertNotNull(des);
        Object res = des._deserialize("USD", null);
        assertNotNull(res);
        assertTrue(res instanceof Currency);
    }

    @Test
    public void testStdDeserializePattern() throws Throwable {
        FromStringDeserializer.Std des = FromStringDeserializer.findDeserializer(Pattern.class);
        assertNotNull(des);
        Object res = des._deserialize("abc", null);
        assertNotNull(res);
        assertTrue(res instanceof Pattern);
    }

    @Test
    public void testStdDeserializeLocaleSingle() throws Throwable {
        FromStringDeserializer.Std des = FromStringDeserializer.findDeserializer(Locale.class);
        assertNotNull(des);
        Object res = des._deserialize("en", null);
        assertNotNull(res);
        assertTrue(res instanceof Locale);
    }

    @Test
    public void testStdDeserializeLocaleTwoPieces() throws Throwable {
        FromStringDeserializer.Std des = FromStringDeserializer.findDeserializer(Locale.class);
        assertNotNull(des);
        Object res = des._deserialize("en_US", null);
        assertNotNull(res);
        assertTrue(res instanceof Locale);
    }

    @Test
    public void testStdDeserializeLocaleThreePieces() throws Throwable {
        FromStringDeserializer.Std des = FromStringDeserializer.findDeserializer(Locale.class);
        assertNotNull(des);
        Object res = des._deserialize("en_US_VARIANT", null);
        assertNotNull(res);
        assertTrue(res instanceof Locale);
    }

    @Test
    public void testStdDeserializeCharset() throws Throwable {
        FromStringDeserializer.Std des = FromStringDeserializer.findDeserializer(Charset.class);
        assertNotNull(des);
        Object res = des._deserialize("UTF-8", null);
        assertNotNull(res);
        assertTrue(res instanceof Charset);
    }

    @Test
    public void testStdDeserializeTimeZone() throws Throwable {
        FromStringDeserializer.Std des = FromStringDeserializer.findDeserializer(TimeZone.class);
        assertNotNull(des);
        Object res = des._deserialize("GMT", null);
        assertNotNull(res);
        assertTrue(res instanceof TimeZone);
    }

    @Test
    public void testStdDeserializeInetAddress() throws Throwable {
        FromStringDeserializer.Std des = FromStringDeserializer.findDeserializer(InetAddress.class);
        assertNotNull(des);
        Object res = des._deserialize("127.0.0.1", null);
        assertNotNull(res);
        assertTrue(res instanceof InetAddress);
    }

    @Test
    public void testStdDeserializeInetSocketAddressHostPort() throws Throwable {
        FromStringDeserializer.Std des = FromStringDeserializer.findDeserializer(InetSocketAddress.class);
        assertNotNull(des);
        Object res = des._deserialize("localhost:8080", null);
        assertNotNull(res);
        assertTrue(res instanceof InetSocketAddress);
    }

    @Test
    public void testStdDeserializeInetSocketAddressBracketedIPv6() throws Throwable {
        FromStringDeserializer.Std des = FromStringDeserializer.findDeserializer(InetSocketAddress.class);
        assertNotNull(des);
        Object res = des._deserialize("[0:0:0:0:0:0:0:1]:8080", null);
        assertNotNull(res);
        assertTrue(res instanceof InetSocketAddress);
    }

    @Test
    public void testStdDeserializeInetSocketAddressPlain() throws Throwable {
        FromStringDeserializer.Std des = FromStringDeserializer.findDeserializer(InetSocketAddress.class);
        assertNotNull(des);
        Object res = des._deserialize("localhost", null);
        assertNotNull(res);
        assertTrue(res instanceof InetSocketAddress);
    }

    @Test
    public void testStdDeserializeFromEmptyStringUri() throws Throwable {
        FromStringDeserializer.Std des = FromStringDeserializer.findDeserializer(URI.class);
        assertNotNull(des);
        Object res = des._deserializeFromEmptyString();
        assertNotNull(res);
        assertTrue(res instanceof URI);
    }

    @Test
    public void testStdDeserializeFromEmptyStringOther() throws Throwable {
        FromStringDeserializer.Std des = FromStringDeserializer.findDeserializer(File.class);
        assertNotNull(des);
        Object res = des._deserializeFromEmptyString();
        assertNull(res);
    }
}