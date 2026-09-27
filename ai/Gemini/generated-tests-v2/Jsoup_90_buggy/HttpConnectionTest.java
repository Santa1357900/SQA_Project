package org.jsoup.helper;

import org.jsoup.Connection;
import org.jsoup.parser.Parser;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.Proxy;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class HttpConnectionTest {

    @Test
    public void testConnectStringValid() throws Throwable {
        Connection conn = HttpConnection.connect("http://example.com");
        assertNotNull(conn);
        assertNotNull(conn.request());
        assertEquals("http://example.com", conn.request().url().toExternalForm());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testConnectStringInvalid() throws Throwable {
        HttpConnection.connect("not-a-url");
    }

    @Test
    public void testConnectUrlValid() throws Throwable {
        URL url = new URL("http://example.com");
        Connection conn = HttpConnection.connect(url);
        assertNotNull(conn);
        assertEquals(url, conn.request().url());
    }

    @Test
    public void testEncodeUrl() throws Throwable {
        URL url = new URL("http://example.com/a b");
        URL encoded = HttpConnection.encodeUrl(url);
        assertNotNull(encoded);
        assertTrue(encoded.toExternalForm().contains("%20"));
    }

    @Test
    public void testEncodeUrlMalformed() throws Throwable {
        URL url = new URL("http://example.com");
        // Test with invalid format string that doesn't parse via URI
        URL result = HttpConnection.encodeUrl(null);
        assertNull(result);
    }

    @Test
    public void testProxySetup() throws Throwable {
        Connection conn = HttpConnection.connect("http://example.com");
        conn.proxy("localhost", 8080);
        assertNotNull(conn.request().proxy());
        assertEquals(Proxy.Type.HTTP, conn.request().proxy().type());

        Proxy proxy = new Proxy(Proxy.Type.HTTP, java.net.InetSocketAddress.createUnresolved("127.0.0.1", 80));
        conn.proxy(proxy);
        assertEquals(proxy, conn.request().proxy());
    }

    @Test
    public void testUserAgentAndReferrer() throws Throwable {
        Connection conn = HttpConnection.connect("http://example.com");
        conn.userAgent("TestAgent");
        conn.referrer("http://referrer.com");
        
        assertEquals("TestAgent", conn.request().header("User-Agent"));
        assertEquals("http://referrer.com", conn.request().header("Referer"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testUserAgentNull() throws Throwable {
        Connection conn = HttpConnection.connect("http://example.com");
        conn.userAgent(null);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testReferrerNull() throws Throwable {
        Connection conn = HttpConnection.connect("http://example.com");
        conn.referrer(null);
    }

    @Test
    public void testTimeoutsAndLimits() throws Throwable {
        Connection conn = HttpConnection.connect("http://example.com");
        conn.timeout(5000);
        conn.maxBodySize(2048);
        conn.followRedirects(false);
        conn.ignoreHttpErrors(true);
        conn.ignoreContentType(true);

        assertEquals(5000, conn.request().timeout());
        assertEquals(2048, conn.request().maxBodySize());
        assertFalse(conn.request().followRedirects());
        assertTrue(conn.request().ignoreHttpErrors());
        assertTrue(conn.request().ignoreContentType());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testTimeoutNegative() throws Throwable {
        Connection conn = HttpConnection.connect("http://example.com");
        conn.timeout(-1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testMaxBodySizeNegative() throws Throwable {
        Connection conn = HttpConnection.connect("http://example.com");
        conn.maxBodySize(-1);
    }

    @Test
    public void testMethodAndParser() throws Throwable {
        Connection conn = HttpConnection.connect("http://example.com");
        conn.method(Connection.Method.POST);
        Parser parser = Parser.xmlParser();
        conn.parser(parser);

        assertEquals(Connection.Method.POST, conn.request().method());
        assertEquals(parser, conn.request().parser());
    }

    @Test
    public void testDataHandling() throws Throwable {
        Connection conn = HttpConnection.connect("http://example.com");
        conn.data("key1", "val1");
        
        InputStream stream = new ByteArrayInputStream("test".getBytes());
        conn.data("key2", "file.txt", stream);
        conn.data("key3", "file2.txt", stream, "text/plain");

        assertNotNull(conn.data("key1"));
        assertEquals("val1", conn.data("key1").value());
        assertNull(conn.data("nonexistent"));

        Map<String, String> map = new HashMap<String, String>();
        map.put("mapK1", "mapV1");
        conn.data(map);
        assertEquals("mapV1", conn.data("mapK1").value());

        conn.data("k4", "v4", "k5", "v5");
        assertEquals("v4", conn.data("k4").value());
        assertEquals("v5", conn.data("k5").value());

        Collection<Connection.KeyVal> col = new ArrayList<Connection.KeyVal>();
        col.add(HttpConnection.KeyVal.create("colK", "colV"));
        conn.data(col);
        assertEquals("colV", conn.data("colK").value());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testDataEvenCheckFail() throws Throwable {
        Connection conn = HttpConnection.connect("http://example.com");
        conn.data("oddKey");
    }

    @Test
    public void testRequestBodyAndHeaders() throws Throwable {
        Connection conn = HttpConnection.connect("http://example.com");
        conn.requestBody("raw body content");
        assertEquals("raw body content", conn.request().requestBody());

        conn.header("Custom-Header", "CustomValue");
        assertEquals("CustomValue", conn.request().header("Custom-Header"));
        assertTrue(conn.request().hasHeader("Custom-Header"));
        assertTrue(conn.request().hasHeaderWithValue("Custom-Header", "CustomValue"));

        Map<String, String> headers = new HashMap<String, String>();
        headers.put("H1", "V1");
        conn.headers(headers);
        assertEquals("V1", conn.request().header("H1"));

        conn.removeHeader("H1");
        assertFalse(conn.request().hasHeader("H1"));

        assertNotNull(conn.request().multiHeaders());
    }

    @Test
    public void testCookiesHandling() throws Throwable {
        Connection conn = HttpConnection.connect("http://example.com");
        conn.cookie("cookie1", "val1");
        assertEquals("val1", conn.request().cookie("cookie1"));
        assertTrue(conn.request().hasCookie("cookie1"));

        Map<String, String> cookies = new HashMap<String, String>();
        cookies.put("cookie2", "val2");
        conn.cookies(cookies);
        assertEquals("val2", conn.request().cookie("cookie2"));

        assertNotNull(conn.request().cookies());

        conn.removeCookie("cookie1");
        assertFalse(conn.request().hasCookie("cookie1"));
    }

    @Test
    public void testRequestResponseSettersGetters() throws Throwable {
        HttpConnection conn = (HttpConnection) HttpConnection.connect("http://example.com");
        Connection.Request req = conn.request();
        Connection.Response resp = conn.response();

        assertSame(req, conn.request(req).request());
        assertSame(resp, conn.response(resp).response());
    }

    @Test
    public void testPostDataCharset() throws Throwable {
        Connection conn = HttpConnection.connect("http://example.com");
        conn.postDataCharset("UTF-8");
        assertEquals("UTF-8", conn.request().postDataCharset());
    }

    @Test(expected = java.nio.charset.IllegalCharsetNameException.class)
    public void testPostDataCharsetInvalid() throws Throwable {
        Connection conn = HttpConnection.connect("http://example.com");
        conn.postDataCharset("INVALID-CHARSET-NAME!!!");
    }

    @Test
    public void testKeyValCreationAndMethods() throws Throwable {
        HttpConnection.KeyVal kv1 = HttpConnection.KeyVal.create("k", "v");
        assertEquals("k", kv1.key());
        assertEquals("v", kv1.value());
        assertFalse(kv1.hasInputStream());
        assertNull(kv1.inputStream());
        assertEquals("k=v", kv1.toString());

        ByteArrayInputStream bais = new ByteArrayInputStream(new byte[0]);
        HttpConnection.KeyVal kv2 = HttpConnection.KeyVal.create("k2", "file.txt", bais);
        kv2.contentType("application/octet-stream");
        assertEquals("file.txt", kv2.value());
        assertTrue(kv2.hasInputStream());
        assertEquals(bais, kv2.inputStream());
        assertEquals("application/octet-stream", kv2.contentType());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testKeyValEmptyKey() throws Throwable {
        HttpConnection.KeyVal.create("", "v");
    }

    @Test(expected = IllegalArgumentException.class)
    public void testKeyValNullValue() throws Throwable {
        HttpConnection.KeyVal.create("k", null);
    }

    @Test
    public void testBaseHeaderFixEncoding() throws Throwable {
        HttpConnection.Request req = new HttpConnection.Request();
        req.addHeader("Test", "value");
        assertNotNull(req.header("test"));
    }
}