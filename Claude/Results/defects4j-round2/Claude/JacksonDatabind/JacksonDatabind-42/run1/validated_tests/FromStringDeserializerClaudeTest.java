package com.fasterxml.jackson.databind.deser.std;

import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.DeserializationFeature;

import java.io.File;
import java.net.URL;
import java.net.URI;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.Currency;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Pattern;
import java.util.Arrays;
import java.util.List;

public class FromStringDeserializerClaudeTest
{
    private ObjectMapper mapper;

    @Before
    public void setUp() throws Throwable {
        mapper = new ObjectMapper();
    }

    // covers types(): returns exactly the 12 supported classes
    @Test
    public void testTypes_returnsTwelveSupportedTypes() throws Throwable {
        Class<?>[] types = FromStringDeserializer.types();
        assertEquals(12, types.length);
    }

    // covers types(): array contains specific known members
    @Test
    public void testTypes_containsFileAndInetSocketAddress() throws Throwable {
        List<Class<?>> list = Arrays.asList(FromStringDeserializer.types());
        assertTrue(list.contains(File.class));
        assertTrue(list.contains(InetSocketAddress.class));
    }

    // covers findDeserializer: File branch
    @Test
    public void testFindDeserializer_file_returnsStdKindFile() throws Throwable {
        FromStringDeserializer.Std d = FromStringDeserializer.findDeserializer(File.class);
        assertNotNull(d);
        assertEquals(FromStringDeserializer.Std.STD_FILE, d._kind);
    }

    // covers findDeserializer: URL branch
    @Test
    public void testFindDeserializer_url_returnsStdKindUrl() throws Throwable {
        FromStringDeserializer.Std d = FromStringDeserializer.findDeserializer(URL.class);
        assertEquals(FromStringDeserializer.Std.STD_URL, d._kind);
    }

    // covers findDeserializer: URI branch
    @Test
    public void testFindDeserializer_uri_returnsStdKindUri() throws Throwable {
        FromStringDeserializer.Std d = FromStringDeserializer.findDeserializer(URI.class);
        assertEquals(FromStringDeserializer.Std.STD_URI, d._kind);
    }

    // covers findDeserializer: Currency branch
    @Test
    public void testFindDeserializer_currency_returnsStdKindCurrency() throws Throwable {
        FromStringDeserializer.Std d = FromStringDeserializer.findDeserializer(Currency.class);
        assertEquals(FromStringDeserializer.Std.STD_CURRENCY, d._kind);
    }

    // covers findDeserializer: Pattern branch
    @Test
    public void testFindDeserializer_pattern_returnsStdKindPattern() throws Throwable {
        FromStringDeserializer.Std d = FromStringDeserializer.findDeserializer(Pattern.class);
        assertEquals(FromStringDeserializer.Std.STD_PATTERN, d._kind);
    }

    // covers findDeserializer: Locale branch
    @Test
    public void testFindDeserializer_locale_returnsStdKindLocale() throws Throwable {
        FromStringDeserializer.Std d = FromStringDeserializer.findDeserializer(Locale.class);
        assertEquals(FromStringDeserializer.Std.STD_LOCALE, d._kind);
    }

    // covers findDeserializer: Charset branch
    @Test
    public void testFindDeserializer_charset_returnsStdKindCharset() throws Throwable {
        FromStringDeserializer.Std d = FromStringDeserializer.findDeserializer(Charset.class);
        assertEquals(FromStringDeserializer.Std.STD_CHARSET, d._kind);
    }

    // covers findDeserializer: TimeZone branch
    @Test
    public void testFindDeserializer_timeZone_returnsStdKindTimeZone() throws Throwable {
        FromStringDeserializer.Std d = FromStringDeserializer.findDeserializer(TimeZone.class);
        assertEquals(FromStringDeserializer.Std.STD_TIME_ZONE, d._kind);
    }

    // covers findDeserializer: InetAddress branch
    @Test
    public void testFindDeserializer_inetAddress_returnsStdKindInetAddress() throws Throwable {
        FromStringDeserializer.Std d = FromStringDeserializer.findDeserializer(InetAddress.class);
        assertEquals(FromStringDeserializer.Std.STD_INET_ADDRESS, d._kind);
    }

    // covers findDeserializer: InetSocketAddress branch
    @Test
    public void testFindDeserializer_inetSocketAddress_returnsStdKindInetSocketAddress() throws Throwable {
        FromStringDeserializer.Std d = FromStringDeserializer.findDeserializer(InetSocketAddress.class);
        assertEquals(FromStringDeserializer.Std.STD_INET_SOCKET_ADDRESS, d._kind);
    }

    // covers findDeserializer: else branch returning null for unsupported type
    @Test
    public void testFindDeserializer_unsupportedType_returnsNull() throws Throwable {
        FromStringDeserializer.Std d = FromStringDeserializer.findDeserializer(String.class);
        assertNull(d);
    }

    // covers deserialize -> _deserialize STD_FILE, normal string
    @Test
    public void testDeserialize_file_validPath_returnsFile() throws Throwable {
        File f = mapper.readValue("\"/tmp/test\"", File.class);
        assertEquals(new File("/tmp/test"), f);
    }

    // covers deserialize -> _deserialize STD_URL, normal string
    @Test
    public void testDeserialize_url_validUrl_returnsUrl() throws Throwable {
        URL u = mapper.readValue("\"http://example.com\"", URL.class);
        assertEquals(new URL("http://example.com"), u);
    }

    // covers deserialize -> _deserialize STD_URI, normal string
    @Test
    public void testDeserialize_uri_validUri_returnsUri() throws Throwable {
        URI u = mapper.readValue("\"http://example.com\"", URI.class);
        assertEquals(URI.create("http://example.com"), u);
    }

    // covers _deserializeFromEmptyString: URI special-case per databind#398
    @Test
    public void testDeserialize_uri_emptyString_returnsEmptyUri() throws Throwable {
        URI u = mapper.readValue("\"\"", URI.class);
        assertEquals(URI.create(""), u);
    }

    // covers _deserializeFromEmptyString: Locale special-case per databind#1123, bug-catching test
    @Test
    public void testDeserialize_locale_emptyString_returnsRootLocale() throws Throwable {
        Locale l = mapper.readValue("\"\"", Locale.class);
        assertEquals(Locale.ROOT, l);
    }

    // covers _deserializeFromEmptyString: default branch returns null (File)
    @Test
    public void testDeserialize_file_emptyString_returnsNull() throws Throwable {
        File f = mapper.readValue("\"\"", File.class);
        assertNull(f);
    }

    // covers whitespace-only string trimmed to empty, triggers empty-string path
    @Test
    public void testDeserialize_whitespaceOnlyString_treatedAsEmpty_returnsNull() throws Throwable {
        File f = mapper.readValue("\"   \"", File.class);
        assertNull(f);
    }

    // covers deserialize -> _deserialize STD_CURRENCY, valid code
    @Test
    public void testDeserialize_currency_validCode_returnsCurrency() throws Throwable {
        Currency c = mapper.readValue("\"USD\"", Currency.class);
        assertEquals(Currency.getInstance("USD"), c);
    }

    // covers IllegalArgumentException catch path wrapped into JsonMappingException (Currency)
    @Test
    public void testDeserialize_currency_invalidCode_throwsJsonMappingException() throws Throwable {
        try {
            mapper.readValue("\"NOT_A_CURRENCY\"", Currency.class);
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
            // expected
        }
    }

    // covers deserialize -> _deserialize STD_PATTERN, valid regex
    @Test
    public void testDeserialize_pattern_validRegex_returnsPattern() throws Throwable {
        Pattern p = mapper.readValue("\"a.*b\"", Pattern.class);
        assertEquals("a.*b", p.pattern());
    }

    // covers IllegalArgumentException (PatternSyntaxException) catch path (Pattern)
    @Test
    public void testDeserialize_pattern_invalidRegex_throwsJsonMappingException() throws Throwable {
        try {
            mapper.readValue("\"[\"", Pattern.class);
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
            // expected
        }
    }

    // covers STD_LOCALE: single-argument branch (no underscore)
    @Test
    public void testDeserialize_locale_singleArgument_returnsLanguageOnlyLocale() throws Throwable {
        Locale l = mapper.readValue("\"en\"", Locale.class);
        assertEquals(new Locale("en"), l);
    }

    // covers STD_LOCALE: two-piece branch (one underscore)
    @Test
    public void testDeserialize_locale_twoArguments_returnsLanguageCountryLocale() throws Throwable {
        Locale l = mapper.readValue("\"en_US\"", Locale.class);
        assertEquals(new Locale("en", "US"), l);
    }

    // covers STD_LOCALE: three-piece branch (two underscores)
    @Test
    public void testDeserialize_locale_threeArguments_returnsLanguageCountryVariantLocale() throws Throwable {
        Locale l = mapper.readValue("\"en_US_WIN\"", Locale.class);
        assertEquals(new Locale("en", "US", "WIN"), l);
    }

    // covers deserialize -> _deserialize STD_CHARSET, valid name
    @Test
    public void testDeserialize_charset_validName_returnsCharset() throws Throwable {
        Charset c = mapper.readValue("\"UTF-8\"", Charset.class);
        assertEquals(Charset.forName("UTF-8"), c);
    }

    // covers deserialize -> _deserialize STD_TIME_ZONE, valid id
    @Test
    public void testDeserialize_timeZone_validId_returnsTimeZone() throws Throwable {
        TimeZone tz = mapper.readValue("\"UTC\"", TimeZone.class);
        assertEquals(TimeZone.getTimeZone("UTC").getID(), tz.getID());
    }

    // covers deserialize -> _deserialize STD_INET_ADDRESS, literal IP (no DNS)
    @Test
    public void testDeserialize_inetAddress_loopback_returnsAddress() throws Throwable {
        InetAddress a = mapper.readValue("\"127.0.0.1\"", InetAddress.class);
        assertEquals("127.0.0.1", a.getHostAddress());
    }

    // covers STD_INET_SOCKET_ADDRESS: host:port branch (single colon)
    @Test
    public void testDeserialize_inetSocketAddress_hostPort_returnsAddressWithPort() throws Throwable {
        InetSocketAddress a = mapper.readValue("\"127.0.0.1:8080\"", InetSocketAddress.class);
        assertEquals(8080, a.getPort());
    }

    // covers STD_INET_SOCKET_ADDRESS: no-colon branch, port defaults to 0
    @Test
    public void testDeserialize_inetSocketAddress_hostOnly_returnsAddressWithPortZero() throws Throwable {
        InetSocketAddress a = mapper.readValue("\"127.0.0.1\"", InetSocketAddress.class);
        assertEquals(0, a.getPort());
    }

    // covers STD_INET_SOCKET_ADDRESS: bracketed IPv6 with port
    @Test
    public void testDeserialize_inetSocketAddress_bracketedIPv6WithPort_returnsCorrectPort() throws Throwable {
        InetSocketAddress a = mapper.readValue("\"[::1]:8080\"", InetSocketAddress.class);
        assertEquals(8080, a.getPort());
    }

    // covers STD_INET_SOCKET_ADDRESS: bracketed IPv6 without port, defaults to 0
    @Test
    public void testDeserialize_inetSocketAddress_bracketedIPv6NoPort_returnsPortZero() throws Throwable {
        InetSocketAddress a = mapper.readValue("\"[::1]\"", InetSocketAddress.class);
        assertEquals(0, a.getPort());
    }

    // covers STD_INET_SOCKET_ADDRESS: missing closing bracket throws InvalidFormatException/JsonMappingException
    @Test
    public void testDeserialize_inetSocketAddress_missingClosingBracket_throwsException() throws Throwable {
        try {
            mapper.readValue("\"[::1\"", InetSocketAddress.class);
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
            // expected
        }
    }

    // covers deserialize -> _deserialize STD_CLASS, valid class name
    @Test
    public void testDeserialize_classType_validClassName_returnsClass() throws Throwable {
        Class<?> c = mapper.readValue("\"java.lang.String\"", Class.class);
        assertEquals(String.class, c);
    }

    // covers STD_CLASS: exception path when class name invalid
    @Test
    public void testDeserialize_classType_invalidClassName_throwsException() throws Throwable {
        try {
            mapper.readValue("\"not.a.real.ClassName123\"", Class.class);
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
            // expected
        }
    }

    // covers START_ARRAY unwrap branch when UNWRAP_SINGLE_VALUE_ARRAYS enabled, single element
    @Test
    public void testDeserialize_arrayUnwrap_singleValueEnabled_returnsValue() throws Throwable {
        ObjectMapper m2 = new ObjectMapper();
        m2.configure(DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS, true);
        Currency c = m2.readValue("[\"USD\"]", Currency.class);
        assertEquals(Currency.getInstance("USD"), c);
    }

    // covers START_ARRAY unwrap branch: more than one value triggers wrongTokenException
    @Test
    public void testDeserialize_arrayUnwrap_multipleValues_throwsException() throws Throwable {
        ObjectMapper m2 = new ObjectMapper();
        m2.configure(DeserializationFeature.UNWRAP_SINGLE_VALUE_ARRAYS, true);
        try {
            m2.readValue("[\"USD\",\"EUR\"]", Currency.class);
            fail("expected JsonMappingException");
        } catch (JsonMappingException expected) {
            // expected
        }
    }
}
