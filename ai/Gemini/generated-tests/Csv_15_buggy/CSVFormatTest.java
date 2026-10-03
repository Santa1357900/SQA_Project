package org.apache.commons.csv;

import org.junit.Test;

import java.io.StringReader;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.Arrays;

import static org.junit.Assert.*;

public class CSVFormatTest {

    @Test
    public void testPredefinedFormats() throws Throwable {
        assertNotNull(CSVFormat.DEFAULT);
        assertNotNull(CSVFormat.EXCEL);
        assertNotNull(CSVFormat.INFORMIX_UNLOAD);
        assertNotNull(CSVFormat.INFORMIX_UNLOAD_CSV);
        assertNotNull(CSVFormat.MYSQL);
        assertNotNull(CSVFormat.POSTGRESQL_CSV);
        assertNotNull(CSVFormat.POSTGRESQL_TEXT);
        assertNotNull(CSVFormat.RFC4180);
        assertNotNull(CSVFormat.TDF);

        assertEquals(CSVFormat.DEFAULT, CSVFormat.Predefined.Default.getFormat());
        assertEquals(CSVFormat.EXCEL, CSVFormat.Predefined.Excel.getFormat());
        assertEquals(CSVFormat.INFORMIX_UNLOAD, CSVFormat.Predefined.InformixUnload.getFormat());
        assertEquals(CSVFormat.INFORMIX_UNLOAD_CSV, CSVFormat.Predefined.InformixUnloadCsv.getFormat());
        assertEquals(CSVFormat.MYSQL, CSVFormat.Predefined.MySQL.getFormat());
        assertEquals(CSVFormat.POSTGRESQL_CSV, CSVFormat.Predefined.PostgreSQLCsv.getFormat());
        assertEquals(CSVFormat.POSTGRESQL_TEXT, CSVFormat.Predefined.PostgreSQLText.getFormat());
        assertEquals(CSVFormat.RFC4180, CSVFormat.Predefined.RFC4180.getFormat());
        assertEquals(CSVFormat.TDF, CSVFormat.Predefined.TDF.getFormat());

        assertEquals(CSVFormat.DEFAULT, CSVFormat.valueOf("Default"));
        assertEquals(CSVFormat.EXCEL, CSVFormat.valueOf("Excel"));
    }

    @Test
    public void testNewFormatAndGetters() throws Throwable {
        CSVFormat format = CSVFormat.newFormat(';');
        assertEquals(';', format.getDelimiter());
        assertNull(format.getQuoteCharacter());
        assertNull(format.getCommentMarker());
        assertNull(format.getEscapeCharacter());
        assertNull(format.getNullString());
        assertNull(format.getHeader());
        assertNull(format.getHeaderComments());
        assertNull(format.getRecordSeparator());
        assertNull(format.getQuoteMode());
        assertFalse(format.getAllowMissingColumnNames());
        assertFalse(format.getIgnoreEmptyLines());
        assertFalse(format.getIgnoreHeaderCase());
        assertFalse(format.getIgnoreSurroundingSpaces());
        assertFalse(format.getSkipHeaderRecord());
        assertFalse(format.getTrailingDelimiter());
        assertFalse(format.getTrim());
        assertFalse(format.getAutoFlush());
        assertFalse(format.isCommentMarkerSet());
        assertFalse(format.isEscapeCharacterSet());
        assertFalse(format.isNullStringSet());
        assertFalse(format.isQuoteCharacterSet());
    }

    @Test
    public void testValidationLineBreakDelimiter() throws Throwable {
        boolean thrown = false;
        try {
            CSVFormat.newFormat('\n');
        } catch (IllegalArgumentException e) {
            thrown = true;
        }
        assertTrue(thrown);
    }

    @Test
    public void testValidationSameDelimiterAndQuote() throws Throwable {
        boolean thrown = false;
        try {
            CSVFormat.newFormat(',').withQuote(',');
        } catch (IllegalArgumentException e) {
            thrown = true;
        }
        assertTrue(thrown);
    }

    @Test
    public void testValidationSameDelimiterAndEscape() throws Throwable {
        boolean thrown = false;
        try {
            CSVFormat.newFormat(',').withEscape(',');
        } catch (IllegalArgumentException e) {
            thrown = true;
        }
        assertTrue(thrown);
    }

    @Test
    public void testValidationSameDelimiterAndComment() throws Throwable {
        boolean thrown = false;
        try {
            CSVFormat.newFormat(',').withCommentMarker(',');
        } catch (IllegalArgumentException e) {
            thrown = true;
        }
        assertTrue(thrown);
    }

    @Test
    public void testValidationSameQuoteAndComment() throws Throwable {
        boolean thrown = false;
        try {
            CSVFormat.newFormat(',').withQuote('"').withCommentMarker('"');
        } catch (IllegalArgumentException e) {
            thrown = true;
        }
        assertTrue(thrown);
    }

    @Test
    public void testValidationSameEscapeAndComment() throws Throwable {
        boolean thrown = false;
        try {
            CSVFormat.newFormat(',').withEscape('\\').withCommentMarker('\\');
        } catch (IllegalArgumentException e) {
            thrown = true;
        }
        assertTrue(thrown);
    }

    @Test
    public void testValidationNoneQuoteModeWithoutEscape() throws Throwable {
        boolean thrown = false;
        try {
            CSVFormat.newFormat(',').withQuoteMode(QuoteMode.NONE);
        } catch (IllegalArgumentException e) {
            thrown = true;
        }
        assertTrue(thrown);
    }

    @Test
    public void testValidationDuplicateHeader() throws Throwable {
        boolean thrown = false;
        try {
            CSVFormat.DEFAULT.withHeader("A", "B", "A");
        } catch (IllegalArgumentException e) {
            thrown = true;
        }
        assertTrue(thrown);
    }

    @Test
    public void testWithMethodsAndChaining() throws Throwable {
        CSVFormat format = CSVFormat.DEFAULT
                .withAllowMissingColumnNames(true)
                .withCommentMarker('#')
                .withDelimiter('|')
                .withEscape('\\')
                .withFirstRecordAsHeader()
                .withHeaderComments("Comment1", "Comment2")
                .withIgnoreEmptyLines(true)
                .withIgnoreHeaderCase(true)
                .withIgnoreSurroundingSpaces(true)
                .withNullString("NULL")
                .withQuote('"')
                .withQuoteMode(QuoteMode.ALL)
                .withRecordSeparator("\n")
                .withSkipHeaderRecord(true)
                .withTrailingDelimiter(true)
                .withTrim(true)
                .withAutoFlush(true);

        assertTrue(format.getAllowMissingColumnNames());
        assertEquals(Character.valueOf('#'), format.getCommentMarker());
        assertEquals('|', format.getDelimiter());
        assertEquals(Character.valueOf('\\'), format.getEscapeCharacter());
        assertNotNull(format.getHeader());
        assertNotNull(format.getHeaderComments());
        assertTrue(format.getIgnoreEmptyLines());
        assertTrue(format.getIgnoreHeaderCase());
        assertTrue(format.getIgnoreSurroundingSpaces());
        assertEquals("NULL", format.getNullString());
        assertEquals(Character.valueOf('"'), format.getQuoteCharacter());
        assertEquals(QuoteMode.ALL, format.getQuoteMode());
        assertEquals("\n", format.getRecordSeparator());
        assertTrue(format.getSkipHeaderRecord());
        assertTrue(format.getTrailingDelimiter());
        assertTrue(format.getTrim());
        assertTrue(format.getAutoFlush());
        assertTrue(format.isCommentMarkerSet());
        assertTrue(format.isEscapeCharacterSet());
        assertTrue(format.isNullStringSet());
        assertTrue(format.isQuoteCharacterSet());
    }

    @Test
    public void testWithHeaderEnum() throws Throwable {
        CSVFormat format = CSVFormat.DEFAULT.withHeader(DummyEnum.class);
        assertNotNull(format.getHeader());
        assertEquals(2, format.getHeader().length);
        assertEquals("VAL1", format.getHeader()[0]);
        assertEquals("VAL2", format.getHeader()[1]);
    }

    @Test
    public void testWithHeaderResultSetMetaData() throws Throwable {
        DummyResultSetMetaData metaData = new DummyResultSetMetaData();
        CSVFormat format = CSVFormat.DEFAULT.withHeader((ResultSetMetaData) metaData);
        assertNotNull(format.getHeader());
        assertEquals(2, format.getHeader().length);
        assertEquals("Col1", format.getHeader()[0]);
        assertEquals("Col2", format.getHeader()[1]);

        CSVFormat formatNull = CSVFormat.DEFAULT.withHeader((ResultSetMetaData) null);
        assertNull(formatNull.getHeader());
    }

    @Test
    public void testEqualsAndHashCode() throws Throwable {
        CSVFormat f1 = CSVFormat.DEFAULT.withDelimiter(',');
        CSVFormat f2 = CSVFormat.DEFAULT.withDelimiter(',');
        CSVFormat f3 = CSVFormat.DEFAULT.withDelimiter(';');

        assertEquals(f1, f1);
        assertEquals(f1, f2);
        assertEquals(f1.hashCode(), f2.hashCode());
        assertFalse(f1.equals(f3));
        assertFalse(f1.equals(null));
        assertFalse(f1.equals("NotAFormat"));

        CSVFormat f4 = CSVFormat.DEFAULT.withQuote(null);
        CSVFormat f5 = CSVFormat.DEFAULT.withQuote('"');
        assertFalse(f4.equals(f5));
        assertFalse(f5.equals(f4));

        CSVFormat f6 = CSVFormat.DEFAULT.withCommentMarker(null);
        CSVFormat f7 = CSVFormat.DEFAULT.withCommentMarker('#');
        assertFalse(f6.equals(f7));
        assertFalse(f7.equals(f6));

        CSVFormat f8 = CSVFormat.DEFAULT.withEscape(null);
        CSVFormat f9 = CSVFormat.DEFAULT.withEscape('\\');
        assertFalse(f8.equals(f9));
        assertFalse(f9.equals(f8));

        CSVFormat f10 = CSVFormat.DEFAULT.withNullString(null);
        CSVFormat f11 = CSVFormat.DEFAULT.withNullString("NULL");
        assertFalse(f10.equals(f11));
        assertFalse(f11.equals(f10));

        CSVFormat f12 = CSVFormat.DEFAULT.withRecordSeparator(null);
        CSVFormat f13 = CSVFormat.DEFAULT.withRecordSeparator("\n");
        assertFalse(f12.equals(f13));
        assertFalse(f13.equals(f12));
    }

    @Test
    public void testFormatAndPrint() throws Throwable {
        CSVFormat format = CSVFormat.DEFAULT;
        String formatted = format.format("a", "b", null);
        assertEquals("a,b,", formatted);

        StringBuilder sb = new StringBuilder();
        format.print("val", sb, true);
        assertEquals("val", sb.toString());

        StringBuilder sb2 = new StringBuilder();
        format.print(null, sb2, true);
        assertEquals("", sb2.toString());

        CSVFormat quoteFormat = CSVFormat.DEFAULT.withQuote('"').withQuoteMode(QuoteMode.ALL);
        String quoted = quoteFormat.format("test");
        assertEquals("\"test\"", quoted);

        CSVFormat escapeFormat = CSVFormat.DEFAULT.withEscape('\\').withQuote(null);
        String escaped = escapeFormat.format("test\n,");
        assertEquals("test\\n\\,", escaped);

        String toStringResult = CSVFormat.DEFAULT.toString();
        assertNotNull(toStringResult);

        CSVParser parser = format.parse(new StringReader("a,b\nc,d"));
        assertNotNull(parser);

        assertNotNull(format.print(new StringBuilder()));
        assertNotNull(format.printer());
    }

    @Test
    public void testPrintln() throws Throwable {
        CSVFormat format = CSVFormat.DEFAULT.withTrailingDelimiter(true).withRecordSeparator("\n");
        StringBuilder sb = new StringBuilder();
        format.println(sb);
        assertEquals(",\n", sb.toString());
    }

    @Test
    public void testPrintRecord() throws Throwable {
        CSVFormat format = CSVFormat.DEFAULT;
        StringBuilder sb = new StringBuilder();
        format.printRecord(sb, "a", "b");
        assertEquals("a,b\r\n", sb.toString());
    }

    private enum DummyEnum {
        VAL1, VAL2
    }

    private static class DummyResultSetMetaData implements ResultSetMetaData {
        public int getColumnCount() throws SQLException {
            return 2;
        }
        public String getColumnLabel(int column) throws SQLException {
            return "Col" + column;
        }
        public boolean isAutoIncrement(int column) throws SQLException { return false; }
        public boolean isCaseSensitive(int column) throws SQLException { return false; }
        public boolean isSearchable(int column) throws SQLException { return false; }
        public boolean isCurrency(int column) throws SQLException { return false; }
        public int isNullable(int column) throws SQLException { return 0; }
        public boolean isSigned(int column) throws SQLException { return false; }
        public int getColumnDisplaySize(int column) throws SQLException { return 0; }
        public String getColumnName(int column) throws SQLException { return null; }
        public String getSchemaName(int column) throws SQLException { return null; }
        public int getPrecision(int column) throws SQLException { return 0; }
        public int getScale(int column) throws SQLException { return 0; }
        public String getTableName(int column) throws SQLException { return null; }
        public String getCatalogName(int column) throws SQLException { return null; }
        public int getColumnType(int column) throws SQLException { return 0; }
        public String getColumnTypeName(int column) throws SQLException { return null; }
        public boolean isReadOnly(int column) throws SQLException { return false; }
        public boolean isWritable(int column) throws SQLException { return false; }
        public boolean isDefinitelyWritable(int column) throws SQLException { return false; }
        public String getColumnClassName(int column) throws SQLException { return null; }
        public <T> T unwrap(Class<T> iface) throws SQLException { return null; }
        public boolean isWrapperFor(Class<?> iface) throws SQLException { return false; }
    }
}