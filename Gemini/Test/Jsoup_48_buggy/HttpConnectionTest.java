package org.jsoup.helper;

import org.jsoup.Connection;
import org.jsoup.parser.Parser;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.IllegalCharsetNameException;
import java.util.*;

import static org.junit.Assert.*;

public class HttpConnectionTest {

    @Test
    public void testConnectStringAndUrl() throws Throwable {
        Connection conStr = HttpConnection.connect("http://example.com");
        assertNotNull(conStr);
        assertNotNull(conStr.request().url());

        Connection conUrl = HttpConnection.connect(new URL("http://example.com"));
        assertNotNull(conUrl);
        assertNotNull(conUrl.request().url());
    }

    @Test
    public void testEncodeUrl() throws Throwable {
        Connection con = HttpConnection.connect("http://example.com/a b c");
        assertEquals("http://example.com/a%20b%20c", con.request().url().toExternalForm());

        Connection conNull = HttpConnection.connect("http://example.com");
        try {
            conNull.url((String) null);
            fail("Should throw exception");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Must supply a valid URL"));
        }
    }

    @Test
    public void testMalformedUrl() throws Throwable {
        Connection con = HttpConnection.connect("http://example.com");
        try {
            con.url("malformed-url-string");
            fail("Should throw exception");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Malformed URL"));
        }
    }

    @Test
    public void testRequestSettersAndGetters() throws Throwable {
        HttpConnection.Request req = new HttpConnection.Request();
        
        req.timeout(5000);
        assertEquals(5000, req.timeout());

        req.maxBodySize(2048);
        assertEquals(2048, req.maxBodySize());

        req.followRedirects(false);
        assertFalse(req.followRedirects());

        req.ignoreHttpErrors(true);
        assertTrue(req.ignoreHttpErrors());

        req.ignoreContentType(true);
        assertTrue(req.ignoreContentType());

        req.validateTLSCertificates(false);
        assertFalse(req.validateTLSCertificates());

        Parser parser = Parser.xmlParser();
        req.parser(parser);
        assertEquals(parser, req.parser());

        req.postDataCharset("UTF-8");
        assertEquals("UTF-8", req.postDataCharset());

        try {
            req.timeout(-1);
            fail("Should throw exception");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Timeout"));
        }

        try {
            req.maxBodySize(-1);
            fail("Should throw exception");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("maxSize"));
        }

        try {
            req.postDataCharset("INVALID-CHARSET-NAME");
            fail("Should throw exception");
        } catch (IllegalCharsetNameException e) {
            // expected
        }
    }

    @Test
    public void testConnectionConfigMethods() throws Throwable {
        Connection con = HttpConnection.connect("http://example.com");
        
        con.userAgent("Mozilla");
        assertEquals("Mozilla", con.request().header("User-Agent"));

        con.timeout(1234);
        assertEquals(1234, con.request().timeout());

        con.maxBodySize(4321);
        assertEquals(4321, con.request().maxBodySize());

        con.followRedirects(false);
        assertFalse(con.request().followRedirects());

        con.referrer("http://ref.com");
        assertEquals("http://ref.com", con.request().header("Referer"));

        con.method(Connection.Method.POST);
        assertEquals(Connection.Method.POST, con.request().method());

        con.ignoreHttpErrors(true);
        assertTrue(con.request().ignoreHttpErrors());

        con.ignoreContentType(true);
        assertTrue(con.request().ignoreContentType());

        con.validateTLSCertificates(false);
        assertFalse(con.request().validateTLSCertificates());

        Parser p = Parser.htmlParser();
        con.parser(p);
        assertEquals(p, con.request().parser());

        con.postDataCharset("UTF-8");
        assertEquals("UTF-8", con.request().postDataCharset());

        Connection.Response res = new HttpConnection.Response();
        con.response(res);
        assertEquals(res, con.response());

        Connection.Request req2 = new HttpConnection.Request();
        con.request(req2);
        assertEquals(req2, con.request());
    }

    @Test
    public void testDataHandling() throws Throwable {
        Connection con = HttpConnection.connect("http://example.com");

        con.data("key1", "val1");
        
        Map<String, String> map = new HashMap<String, String>();
        map.put("key2", "val2");
        con.data(map);

        con.data("key3", "val3", "file.txt", new ByteArrayInputStream("data".getBytes()));
        con.data("key4", "val4", "key5", "val5");

        Collection<Connection.KeyVal> col = new ArrayList<Connection.KeyVal>();
        col.add(HttpConnection.KeyVal.create("key6", "val6"));
        con.data(col);

        assertEquals(6, con.request().data().size());

        try {
            con.data((Map<String, String>) null);
            fail("Should throw exception");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            con.data((String[]) null);
            fail("Should throw exception");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            con.data("oddKey");
            fail("Should throw exception");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            con.data((Collection<Connection.KeyVal>) null);
            fail("Should throw exception");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testHeadersAndCookies() throws Throwable {
        Connection con = HttpConnection.connect("http://example.com");

        con.header("Custom-Header", "Value1");
        assertEquals("Value1", con.request().header("Custom-Header"));
        assertTrue(con.request().hasHeader("Custom-Header"));
        assertTrue(con.request().hasHeaderWithValue("Custom-Header", "Value1"));

        con.request().removeHeader("Custom-Header");
        assertFalse(con.request().hasHeader("Custom-Header"));

        con.cookie("cookie1", "cval1");
        assertEquals("cval1", con.request().cookie("cookie1"));
        assertTrue(con.request().hasCookie("cookie1"));

        Map<String, String> cookies = new HashMap<String, String>();
        cookies.put("cookie2", "cval2");
        con.cookies(cookies);
        assertTrue(con.request().hasCookie("cookie2"));

        con.request().removeCookie("cookie1");
        assertFalse(con.request().hasCookie("cookie1"));
        
        assertNotNull(con.request().headers());
        assertNotNull(con.request().cookies());

        try {
            con.cookies(null);
            fail("Should throw exception");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testKeyValClass() throws Throwable {
        Connection.KeyVal kv = HttpConnection.KeyVal.create("testKey", "testVal");
        assertEquals("testKey", kv.key());
        assertEquals("testVal", kv.value());
        assertFalse(kv.hasInputStream());
        assertNull(kv.inputStream());
        assertEquals("testKey=testVal", kv.toString());

        InputStream is = new ByteArrayInputStream(new byte[0]);
        Connection.KeyVal kvStream = HttpConnection.KeyVal.create("streamKey", "filename.txt", is);
        assertEquals("streamKey", kvStream.key());
        assertEquals("filename.txt", kvStream.value());
        assertTrue(kvStream.hasInputStream());
        assertEquals(is, kvStream.inputStream());

        try {
            HttpConnection.KeyVal.create("", "val");
            fail("Should throw exception");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testBaseClassEdgeCases() throws Throwable {
        HttpConnection.Request req = new HttpConnection.Request();
        
        try {
            req.url((URL) null);
            fail("Should throw exception");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            req.method(null);
            fail("Should throw exception");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            req.header(null, "val");
            fail("Should throw exception");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            req.header("Name", null);
            fail("Should throw exception");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            req.hasHeader(null);
            fail("Should throw exception");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            req.removeHeader(null);
            fail("Should throw exception");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            req.cookie(null, "val");
            fail("Should throw exception");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            req.hasCookie(null);
            fail("Should throw exception");
        } catch (IllegalArgumentException e) {
            // expected
        }

        try {
            req.removeCookie(null);
            fail("Should throw exception");
        } catch (IllegalArgumentException e) {
            // expected
        }
    }

    @Test
    public void testResponseProcessHeaders() throws Throwable {
        HttpConnection.Response res = new HttpConnection.Response();
        Map<String, List<String>> headers = new HashMap<String, List<String>>();
        
        List<String> setCookieVals = new ArrayList<String>();
        setCookieVals.add("name1=val1; Path=/; Domain=example.com");
        setCookieVals.add(null);
        headers.put("Set-Cookie", setCookieVals);

        List<String> normalVals = new ArrayList<String>();
        normalVals.add("HeaderVal");
        headers.put("X-Custom", normalVals);

        headers.put(null, normalVals); // HTTP/1.1 line test

        res.processResponseHeaders(headers);
        assertEquals("val1", res.cookie("name1"));
        assertEquals("HeaderVal", res.header("X-Custom"));
    }

    @Test
    public void testExecuteInvalidProtocol() throws Throwable {
        HttpConnection.Request req = new HttpConnection.Request();
        req.url(new URL("ftp://example.com"));
        try {
            HttpConnection.Response.execute(req);
            fail("Should throw exception");
        } catch (IOException e) {
            assertTrue(e.getMessage().contains("Only http & https protocols supported"));
        }
    }
}