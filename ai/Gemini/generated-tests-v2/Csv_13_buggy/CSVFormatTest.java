package org.apache.commons.csv;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.StringReader;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.Arrays;

public class CSVFormatTest {

    @Test
    public void testPredefinedFormats() throws Throwable {
        assertNotNull(CSVFormat.DEFAULT);
        assertNotNull(CSVFormat.RFC4180);
        assertNotNull(CSVFormat.EXCEL);
        assertNotNull(CSVFormat.TDF);
        assertNotNull(CSVFormat.MYSQL);

        assertEquals(CSVFormat.DEFAULT, CSVFormat.valueOf("Default"));
        assertEquals(CSVFormat.EXCEL, CSVFormat.valueOf("Excel"));
        assertEquals(CSVFormat.MYSQL, CSVFormat.valueOf("MySQL"));
        assertEquals(CSVFormat.RFC4180, CSVFormat.valueOf("RFC4180"));
        assertEquals(CSVFormat.TDF, CSVFormat.valueOf("TDF"));
    }

    @Test
    public void testNewFormatValid() throws Throwable {
        CSVFormat format = CSVFormat.newFormat(';');
        assertEquals(';', format.getDelimiter());
        assertFalse(format.isQuoteCharacterSet());
        assertFalse(format.isCommentMarkerSet());
        assertFalse(format.isEscapeCharacterSet());
        assertFalse(format.isNullStringSet());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNewFormatLineBreakDelimiterLF() throws Throwable {
        CSVFormat.newFormat('\n');
    }

    @Test(expected = IllegalArgumentException.class)
    public void testNewFormatLineBreakDelimiterCR() throws Throwable {
        CSVFormat.newFormat('\r');
    }

    @Test
    public void testValidationDuplicateHeader() throws Throwable {
        try {
            CSVFormat.DEFAULT.withHeader("A", "B", "A");
            fail("Should have thrown IllegalArgumentException due to duplicate header");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("duplicate entry"));
        }
    }

    @Test
    public void testValidationSameDelimiterAndQuote() throws Throwable {
        try {
            CSVFormat.DEFAULT.withDelimiter('"').withQuote('"');
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("quoteChar character and the delimiter cannot be the same"));
        }
    }

    @Test
    public void testValidationSameDelimiterAndEscape() throws Throwable {
        try {
            CSVFormat.DEFAULT.withDelimiter('\\').withEscape('\\');
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("escape character and the delimiter cannot be the same"));
        }
    }

    @Test
    public void testValidationSameDelimiterAndComment() throws Throwable {
        try {
            CSVFormat.DEFAULT.withDelimiter('#').withCommentMarker('#');
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("comment start character and the delimiter cannot be the same"));
        }
    }

    @Test
    public void testValidationSameQuoteAndComment() throws Throwable {
        try {
            CSVFormat.DEFAULT.withQuote('#').withCommentMarker('#');
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("comment start character and the quoteChar cannot be the same"));
        }
    }

    @Test
    public void testValidationSameEscapeAndComment() throws Throwable {
        try {
            CSVFormat.DEFAULT.withEscape('#').withCommentMarker('#');
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("comment start and the escape character cannot be the same"));
        }
    }

    @Test
    public void testValidationNoQuotesModeWithoutEscape() throws Throwable {
        try {
            CSVFormat.DEFAULT.withQuoteMode(QuoteMode.NONE).withEscape(null);
            fail("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("No quotes mode set but no escape character is set"));
        }
    }

    @Test
    public void testWithersAndGetters() throws Throwable {
        CSVFormat format = CSVFormat.DEFAULT
            .withCommentMarker('#')
            .withDelimiter(';')
            .withEscape('!')
            .withHeader("col1", "col2")
            .withHeaderComments("Comment1", null)
            .withAllowMissingColumnNames(true)
            .withIgnoreEmptyLines(true)
            .withIgnoreSurroundingSpaces(true)
            .withIgnoreHeaderCase(true)
            .withNullString("NULL")
            .withQuote('\'')
            .withQuoteMode(QuoteMode.ALL)
            .withRecordSeparator("\n")
            .withSkipHeaderRecord(true);

        assertEquals(Character.valueOf('#'), format.getCommentMarker());
        assertEquals(';', format.getDelimiter());
        assertEquals(Character.valueOf('!'), format.getEscapeCharacter());
        assertArrayEquals(new String[]{"col1", "col2"}, format.getHeader());
        assertArrayEquals(new String[]{"Comment1", null}, format.getHeaderComments());
        assertTrue(format.getAllowMissingColumnNames());
        assertTrue(format.getIgnoreEmptyLines());
        assertTrue(format.getIgnoreSurroundingSpaces());
        assertTrue(format.getIgnoreHeaderCase());
        assertEquals("NULL", format.getNullString());
        assertEquals(Character.valueOf('\''), format.getQuoteCharacter());
        assertEquals(QuoteMode.ALL, format.getQuoteMode());
        assertEquals("\n", format.getRecordSeparator());
        assertTrue(format.getSkipHeaderRecord());

        assertTrue(format.isCommentMarkerSet());
        assertTrue(format.isEscapeCharacterSet());
        assertTrue(format.isNullStringSet());
        assertTrue(format.isQuoteCharacterSet());
    }

    @Test
    public void testWithersCharOverloads() throws Throwable {
        CSVFormat format = CSVFormat.DEFAULT
            .withCommentMarker('#')
            .withDelimiter(',')
            .withEscape('\\')
            .withQuote('"')
            .withRecordSeparator('\n');

        assertEquals(Character.valueOf('#'), format.getCommentMarker());
        assertEquals(',', format.getDelimiter());
        assertEquals(Character.valueOf('\\'), format.getEscapeCharacter());
        assertEquals(Character.valueOf('"'), format.getQuoteCharacter());
        assertEquals("\n", format.getRecordSeparator());
    }

    @Test(expected = IllegalArgumentException.class)
    public void testWithCommentMarkerLineBreak() throws Throwable {
        CSVFormat.DEFAULT.withCommentMarker('\n');
    }

    @Test(expected = IllegalArgumentException.class)
    public void testWithDelimiterLineBreak() throws Throwable {
        CSVFormat.DEFAULT.withDelimiter('\r');
    }

    @Test(expected = IllegalArgumentException.class)
    public void testWithEscapeLineBreak() throws Throwable {
        CSVFormat.DEFAULT.withEscape('\n');
    }

    @Test(expected = IllegalArgumentException.class)
    public void testWithQuoteLineBreak() throws Throwable {
        CSVFormat.DEFAULT.withQuote('\r');
    }

    @Test
    public void testWithAllowMissingColumnNamesSimple() throws Throwable {
        CSVFormat format = CSVFormat.DEFAULT.withAllowMissingColumnNames();
        assertTrue(format.getAllowMissingColumnNames());
    }

    @Test
    public void testWithIgnoreEmptyLinesSimple() throws Throwable {
        CSVFormat format = CSVFormat.DEFAULT.withIgnoreEmptyLines();
        assertTrue(format.getIgnoreEmptyLines());
    }

    @Test
    public void testWithIgnoreSurroundingSpacesSimple() throws Throwable {
        CSVFormat format = CSVFormat.DEFAULT.withIgnoreSurroundingSpaces();
        assertTrue(format.getIgnoreSurroundingSpaces());
    }

    @Test
    public void testWithIgnoreHeaderCaseSimple() throws Throwable {
        CSVFormat format = CSVFormat.DEFAULT.withIgnoreHeaderCase();
        assertTrue(format.getIgnoreHeaderCase());
    }

    @Test
    public void testWithSkipHeaderRecordSimple() throws Throwable {
        CSVFormat format = CSVFormat.DEFAULT.withSkipHeaderRecord();
        assertTrue(format.getSkipHeaderRecord());
    }

    @Test
    public void testEqualsAndHashCode() throws Throwable {
        CSVFormat format1 = CSVFormat.DEFAULT;
        CSVFormat format2 = CSVFormat.DEFAULT;
        CSVFormat format3 = CSVFormat.RFC4180;

        assertEquals(format1, format1);
        assertEquals(format1, format2);
        assertEquals(format1.hashCode(), format2.hashCode());
        assertFalse(format1.equals(null));
        assertFalse(format1.equals("SomeString"));
        assertNotEquals(format1, format3);

        CSVFormat fNullQuote = CSVFormat.DEFAULT.withQuote(null);
        CSVFormat fNullQuoteOther = CSVFormat.DEFAULT.withHeader("A").withQuote(null);
        assertNotEquals(fNullQuote, fNullQuoteOther);

        CSVFormat fNullComment = CSVFormat.DEFAULT.withCommentMarker(null);
        CSVFormat fNullCommentOther = CSVFormat.DEFAULT.withCommentMarker('#');
        assertNotEquals(fNullComment, fNullCommentOther);

        CSVFormat fNullEscape = CSVFormat.DEFAULT.withEscape(null);
        CSVFormat fNullEscapeOther = CSVFormat.DEFAULT.withEscape('\\');
        assertNotEquals(fNullEscape, fNullEscapeOther);

        CSVFormat fNullNullStr = CSVFormat.DEFAULT.withNullString(null);
        CSVFormat fNullNullStrOther = CSVFormat.DEFAULT.withNullString("NULL");
        assertNotEquals(fNullNullStr, fNullNullStrOther);

        CSVFormat fNullRecSep = CSVFormat.DEFAULT.withRecordSeparator(null);
        CSVFormat fNullRecSepOther = CSVFormat.DEFAULT.withRecordSeparator("\n");
        assertNotEquals(fNullRecSep, fNullRecSepOther);
    }

    @Test
    public void testToString() throws Throwable {
        String str = CSVFormat.DEFAULT.toString();
        assertNotNull(str);
        assertTrue(str.contains("Delimiter=<,>"));

        CSVFormat custom = CSVFormat.DEFAULT
            .withEscape('\\')
            .withQuote('"')
            .withCommentMarker('#')
            .withNullString("NULL")
            .withRecordSeparator("\r\n")
            .withIgnoreEmptyLines(true)
            .withIgnoreSurroundingSpaces(true)
            .withIgnoreHeaderCase(true)
            .withSkipHeaderRecord(true)
            .withHeaderComments("HeaderComment")
            .withHeader("Col1");

        String customStr = custom.toString();
        assertTrue(customStr.contains("Escape=<\\>"));
        assertTrue(customStr.contains("QuoteChar=<\">"));
        assertTrue(customStr.contains("CommentStart=<#>"));
        assertTrue(customStr.contains("NullString=<NULL>"));
        assertTrue(customStr.contains("RecordSeparator=<\r\n>"));
        assertTrue(customStr.contains("EmptyLines:ignored"));
        assertTrue(customStr.contains("SurroundingSpaces:ignored"));
        assertTrue(customStr.contains("IgnoreHeaderCase:ignored"));
        assertTrue(customStr.contains("SkipHeaderRecord:true"));
        assertTrue(customStr.contains("HeaderComments:[HeaderComment]"));
        assertTrue(customStr.contains("Header:[Col1]"));
    }

    @Test
    public void testFormatAndParseAndPrint() throws Throwable {
        String formatted = CSVFormat.DEFAULT.format("a", "b", "c");
        assertEquals("a,b,c", formatted);

        CSVParser parser = CSVFormat.DEFAULT.parse(new StringReader("a,b,c"));
        assertNotNull(parser);
        parser.close();

        Appendable out = new java.io.StringWriter();
        CSVPrinter printer = CSVFormat.DEFAULT.print(out);
        assertNotNull(printer);
        printer.close();
    }

    @Test
    public void testWithHeaderResultSetAndMetaData() throws Throwable {
        DummyResultSetMetaData metaData = new DummyResultSetMetaData(new String[]{"ID", "Name"});
        DummyResultSet resultSet = new DummyResultSet(metaData);

        CSVFormat format1 = CSVFormat.DEFAULT.withHeader(resultSet);
        assertArrayEquals(new String[]{"ID", "Name"}, format1.getHeader());

        CSVFormat format2 = CSVFormat.DEFAULT.withHeader(metaData);
        assertArrayEquals(new String[]{"ID", "Name"}, format2.getHeader());

        CSVFormat formatNullRs = CSVFormat.DEFAULT.withHeader((ResultSet) null);
        assertNull(formatNullRs.getHeader());

        CSVFormat formatNullMd = CSVFormat.DEFAULT.withHeader((ResultSetMetaData) null);
        assertNull(formatNullMd.getHeader());
    }

    private static class DummyResultSetMetaData implements ResultSetMetaData {
        private final String[] columns;

        public DummyResultSetMetaData(String[] columns) {
            this.columns = columns;
        }

        public int getColumnCount() throws SQLException {
            return columns.length;
        }

        public String getColumnLabel(int column) throws SQLException {
            return columns[column - 1];
        }

        public String getCatalogName(int column) throws SQLException { return null; }
        public String getColumnClassName(int column) throws SQLException { return null; }
        public int getColumnDisplaySize(int column) throws SQLException { return 0; }
        public String getColumnName(int column) throws SQLException { return columns[column - 1]; }
        public int getColumnType(int column) throws SQLException { return 0; }
        public String getColumnTypeName(int column) throws SQLException { return null; }
        public int getPrecision(int column) throws SQLException { return 0; }
        public int getScale(int column) throws SQLException { return 0; }
        public String getSchemaName(int column) throws SQLException { return null; }
        public String getTableName(int column) throws SQLException { return null; }
        public boolean isAutoIncrement(int column) throws SQLException { return false; }
        public boolean isCaseSensitive(int column) throws SQLException { return false; }
        public boolean isCurrency(int column) throws SQLException { return false; }
        public boolean isDefinitelyWritable(int column) throws SQLException { return false; }
        public int isNullable(int column) throws SQLException { return 0; }
        public boolean isReadOnly(int column) throws SQLException { return false; }
        public boolean isSearchable(int column) throws SQLException { return false; }
        public boolean isSigned(int column) throws SQLException { return false; }
        public boolean isWritable(int column) throws SQLException { return false; }
        public boolean isWrapperFor(Class<?> iface) throws SQLException { return false; }
        public <T> T unwrap(Class<T> iface) throws SQLException { return null; }
    }

    private static class DummyResultSet implements ResultSet {
        private final ResultSetMetaData metaData;

        public DummyResultSet(ResultSetMetaData metaData) {
            this.metaData = metaData;
        }

        public ResultSetMetaData getMetaData() throws SQLException {
            return metaData;
        }

        public boolean absolute(int row) throws SQLException { return false; }
        public void afterLast() throws SQLException {}
        public void beforeFirst() throws SQLException {}
        public void cancelRowUpdates() throws SQLException {}
        public void clearWarnings() throws SQLException {}
        public void close() throws SQLException {}
        public void deleteRow() throws SQLException {}
        public int findColumn(String columnLabel) throws SQLException { return 0; }
        public boolean first() throws SQLException { return false; }
        public java.sql.Array getArray(int columnIndex) throws SQLException { return null; }
        public java.sql.Array getArray(String columnLabel) throws SQLException { return null; }
        public java.io.InputStream getAsciiStream(int columnIndex) throws SQLException { return null; }
        public java.io.InputStream getAsciiStream(String columnLabel) throws SQLException { return null; }
        public java.math.BigDecimal getBigDecimal(int columnIndex) throws SQLException { return null; }
        public java.math.BigDecimal getBigDecimal(int columnIndex, int scale) throws SQLException { return null; }
        public java.math.BigDecimal getBigDecimal(String columnLabel) throws SQLException { return null; }
        public java.math.BigDecimal getBigDecimal(String columnLabel, int scale) throws SQLException { return null; }
        public java.io.InputStream getBinaryStream(int columnIndex) throws SQLException { return null; }
        public java.io.InputStream getBinaryStream(String columnLabel) throws SQLException { return null; }
        public java.sql.Blob getBlob(int columnIndex) throws SQLException { return null; }
        public java.sql.Blob getBlob(String columnLabel) throws SQLException { return null; }
        public boolean getBoolean(int columnIndex) throws SQLException { return false; }
        public boolean getBoolean(String columnLabel) throws SQLException { return false; }
        public byte getByte(int columnIndex) throws SQLException { return 0; }
        public byte getByte(String columnLabel) throws SQLException { return 0; }
        public byte[] getBytes(int columnIndex) throws SQLException { return new byte[0]; }
        public byte[] getBytes(String columnLabel) throws SQLException { return new byte[0]; }
        public java.io.Reader getCharacterStream(int columnIndex) throws SQLException { return null; }
        public java.io.Reader getCharacterStream(String columnLabel) throws SQLException { return null; }
        public java.sql.Clob getClob(int columnIndex) throws SQLException { return null; }
        public java.sql.Clob getClob(String columnLabel) throws SQLException { return null; }
        public int getConcurrency() throws SQLException { return 0; }
        public String getCursorName() throws SQLException { return null; }
        public java.sql.Date getDate(int columnIndex) throws SQLException { return null; }
        public java.sql.Date getDate(int columnIndex, java.util.Calendar cal) throws SQLException { return null; }
        public java.sql.Date getDate(String columnLabel) throws SQLException { return null; }
        public java.sql.Date getDate(String columnLabel, java.util.Calendar cal) throws SQLException { return null; }
        public double getDouble(int columnIndex) throws SQLException { return 0; }
        public double getDouble(String columnLabel) throws SQLException { return 0; }
        public int getFetchDirection() throws SQLException { return 0; }
        public int getFetchSize() throws SQLException { return 0; }
        public float getFloat(int columnIndex) throws SQLException { return 0; }
        public float getFloat(String columnLabel) throws SQLException { return 0; }
        public int getInt(int columnIndex) throws SQLException { return 0; }
        public int getInt(String columnLabel) throws SQLException { return 0; }
        public long getLong(int columnIndex) throws SQLException { return 0; }
        public long getLong(String columnLabel) throws SQLException { return 0; }
        public java.io.Reader getNCharacterStream(int columnIndex) throws SQLException { return null; }
        public java.io.Reader getNCharacterStream(String columnLabel) throws SQLException { return null; }
        public java.sql.NClob getNClob(int columnIndex) throws SQLException { return null; }
        public java.sql.NClob getNClob(String columnLabel) throws SQLException { return null; }
        public String getNString(int columnIndex) throws SQLException { return null; }
        public String getNString(String columnLabel) throws SQLException { return null; }
        public Object getObject(int columnIndex) throws SQLException { return null; }
        public Object getObject(int columnIndex, java.util.Map<String, Class<?>> map) throws SQLException { return null; }
        public Object getObject(String columnLabel) throws SQLException { return null; }
        public Object getObject(String columnLabel, java.util.Map<String, Class<?>> map) throws SQLException { return null; }
        public java.sql.Ref getRef(int columnIndex) throws SQLException { return null; }
        public java.sql.Ref getRef(String columnLabel) throws SQLException { return null; }
        public int getRow() throws SQLException { return 0; }
        public int getRowId(int columnIndex) throws SQLException { return null; }
        public int getRowId(String columnLabel) throws SQLException { return null; }
        public short getShort(int columnIndex) throws SQLException { return 0; }
        public short getShort(String columnLabel) throws SQLException { return 0; }
        public java.sql.SQLWarning getWarnings() throws SQLException { return null; }
        public java.sql.SQLXML getSQLXML(int columnIndex) throws SQLException { return null; }
        public java.sql.SQLXML getSQLXML(String columnLabel) throws SQLException { return null; }
        public String getString(int columnIndex) throws SQLException { return null; }
        public String getString(String columnLabel) throws SQLException { return null; }
        public java.sql.Time getTime(int columnIndex) throws SQLException { return null; }
        public java.sql.Time getTime(int columnIndex, java.util.Calendar cal) throws SQLException { return null; }
        public java.sql.Time getTime(String columnLabel) throws SQLException { return null; }
        public java.sql.Time getTime(String columnLabel, java.util.Calendar cal) throws SQLException { return null; }
        public java.sql.Timestamp getTimestamp(int columnIndex) throws SQLException { return null; }
        public java.sql.Timestamp getTimestamp(int columnIndex, java.util.Calendar cal) throws SQLException { return null; }
        public java.sql.Timestamp getTimestamp(String columnLabel) throws SQLException { return null; }
        public java.sql.Timestamp getTimestamp(String columnLabel, java.util.Calendar cal) throws SQLException { return null; }
        public int getType() throws SQLException { return 0; }
        public java.io.InputStream getURL(int columnIndex) throws SQLException { return null; }
        public java.io.InputStream getURL(String columnLabel) throws SQLException { return null; }
        public java.io.InputStream getUnicodeStream(int columnIndex) throws SQLException { return null; }
        public java.io.InputStream getUnicodeStream(String columnLabel) throws SQLException { return null; }
        public void insertRow() throws SQLException {}
        public boolean isAfterLast() throws SQLException { return false; }
        public boolean isBeforeFirst() throws SQLException { return false; }
        public boolean isClosed() throws SQLException { return false; }
        public boolean isFirst() throws SQLException { return false; }
        public boolean isLast() throws SQLException { return false; }
        public boolean last() throws SQLException { return false; }
        public void moveToCurrentRow() throws SQLException {}
        public void moveToInsertRow() throws SQLException {}
        public boolean next() throws SQLException { return false; }
        public boolean previous() throws SQLException { return false; }
        public void refreshRow() throws SQLException {}
        public boolean relative(int rows) throws SQLException { return false; }
        public boolean rowDeleted() throws SQLException { return false; }
        public boolean rowInserted() throws SQLException { return false; }
        public boolean rowUpdated() throws SQLException { return false; }
        public void setFetchDirection(int direction) throws SQLException {}
        public void setFetchSize(int rows) throws SQLException {}
        public void updateArray(int columnIndex, java.sql.Array x) throws SQLException {}
        public void updateArray(String columnLabel, java.sql.Array x) throws SQLException {}
        public void updateAsciiStream(int columnIndex, java.io.InputStream x) throws SQLException {}
        public void updateAsciiStream(int columnIndex, java.io.InputStream x, int length) throws SQLException {}
        public void updateAsciiStream(int columnIndex, java.io.InputStream x, long length) throws SQLException {}
        public void updateAsciiStream(String columnLabel, java.io.InputStream x) throws SQLException {}
        public void updateAsciiStream(String columnLabel, java.io.InputStream x, int length) throws SQLException {}
        public void updateAsciiStream(String columnLabel, java.io.InputStream x, long length) throws SQLException {}
        public void updateBigDecimal(int columnIndex, java.math.BigDecimal x) throws SQLException {}
        public void updateBigDecimal(String columnLabel, java.math.BigDecimal x) throws SQLException {}
        public void updateBinaryStream(int columnIndex, java.io.InputStream x) throws SQLException {}
        public void updateBinaryStream(int columnIndex, java.io.InputStream x, int length) throws SQLException {}
        public void updateBinaryStream(int columnIndex, java.io.InputStream x, long length) throws SQLException {}
        public void updateBinaryStream(String columnLabel, java.io.InputStream x) throws SQLException {}
        public void updateBinaryStream(String columnLabel, java.io.InputStream x, int length) throws SQLException {}
        public void updateBinaryStream(String columnLabel, java.io.InputStream x, long length) throws SQLException {}
        public void updateBlob(int columnIndex, java.sql.Blob x) throws SQLException {}
        public void updateBlob(int columnIndex, java.io.InputStream inputStream) throws SQLException {}
        public void updateBlob(int columnIndex, java.io.InputStream inputStream, long length) throws SQLException {}
        public void updateBlob(String columnLabel, java.sql.Blob x) throws SQLException {}
        public void updateBlob(String columnLabel, java.io.InputStream inputStream) throws SQLException {}
        public void updateBlob(String columnLabel, java.io.InputStream inputStream, long length) throws SQLException {}
        public void updateBoolean(int columnIndex, boolean x) throws SQLException {}
        public void updateBoolean(String columnLabel, boolean x) throws SQLException {}
        public void updateByte(int columnIndex, byte x) throws SQLException {}
        public void updateByte(String columnLabel, byte x) throws SQLException {}
        public void updateBytes(int columnIndex, byte[] x) throws SQLException {}
        public void updateBytes(String columnLabel, byte[] x) throws SQLException {}
        public void updateCharacterStream(int columnIndex, java.io.Reader x) throws SQLException {}
        public void updateCharacterStream(int columnIndex, java.io.Reader x, int length) throws SQLException {}
        public void updateCharacterStream(int columnIndex, java.io.Reader x, long length) throws SQLException {}
        public void updateCharacterStream(String columnLabel, java.io.Reader x) throws SQLException {}
        public void updateCharacterStream(String columnLabel, java.io.Reader x, int length) throws SQLException {}
        public void updateCharacterStream(String columnLabel, java.io.Reader x, long length) throws SQLException {}
        public void updateClob(int columnIndex, java.sql.Clob x) throws SQLException {}
        public void updateClob(int columnIndex, java.io.Reader reader) throws SQLException {}
        public void updateClob(int columnIndex, java.io.Reader reader, long length) throws SQLException {}
        public void updateClob(String columnLabel, java.sql.Clob x) throws SQLException {}
        public void updateClob(String columnLabel, java.io.Reader reader) throws SQLException {}
        public void updateClob(String columnLabel, java.io.Reader reader, long length) throws SQLException {}
        public void updateDate(int columnIndex, java.sql.Date x) throws SQLException {}
        public void updateDate(String columnLabel, java.sql.Date x) throws SQLException {}
        public void updateDouble(int columnIndex, double x) throws SQLException {}
        public void updateDouble(String columnLabel, double x) throws SQLException {}
        public void updateFloat(int columnIndex, float x) throws SQLException {}
        public void updateFloat(String columnLabel, float x) throws SQLException {}
        public void updateInt(int columnIndex, int x) throws SQLException {}
        public void updateInt(String columnLabel, int x) throws SQLException {}
        public void updateLong(int columnIndex, long x) throws SQLException {}
        public void updateLong(String columnLabel, long x) throws SQLException {}
        public void updateNCharacterStream(int columnIndex, java.io.Reader x) throws SQLException {}
        public void updateNCharacterStream(int columnIndex, java.io.Reader x, long length) throws SQLException {}
        public void updateNCharacterStream(String columnLabel, java.io.Reader x) throws SQLException {}
        public void updateNCharacterStream(String columnLabel, java.io.Reader x, long length) throws SQLException {}
        public void updateNClob(int columnIndex, java.sql.NClob x) throws SQLException {}
        public void updateNClob(int columnIndex, java.io.Reader reader) throws SQLException {}
        public void updateNClob(int columnIndex, java.io.Reader reader, long length) throws SQLException {}
        public void updateNClob(String columnLabel, java.sql.NClob x) throws SQLException {}
        public void updateNClob(String columnLabel, java.io.Reader reader) throws SQLException {}
        public void updateNClob(String columnLabel, java.io.Reader reader, long length) throws SQLException {}
        public void updateNString(int columnIndex, String nString) throws SQLException {}
        public void updateNString(String columnLabel, String nString) throws SQLException {}
        public void updateNull(int columnIndex) throws SQLException {}
        public void updateNull(String columnLabel) throws SQLException {}
        public void updateObject(int columnIndex, Object x) throws SQLException {}
        public void updateObject(int columnIndex, Object x, int scaleOrLength) throws SQLException {}
        public void updateObject(String columnLabel, Object x) throws SQLException {}
        public void updateObject(String columnLabel, Object x, int scaleOrLength) throws SQLException {}
        public void updateRef(int columnIndex, java.sql.Ref x) throws SQLException {}
        public void updateRef(String columnLabel, java.sql.Ref x) throws SQLException {}
        public void updateRow() throws SQLException {}
        public void updateRowId(int columnIndex, int x) throws SQLException {}
        public void updateRowId(String columnLabel, int x) throws SQLException {}
        public void updateShort(int columnIndex, short x) throws SQLException {}
        public void updateShort(String columnLabel, short x) throws SQLException {}
        public void updateSQLXML(int columnIndex, java.sql.SQLXML xmlObject) throws SQLException {}
        public void updateSQLXML(String columnLabel, java.sql.SQLXML xmlObject) throws SQLException {}
        public void updateString(int columnIndex, String x) throws SQLException {}
        public void updateString(String columnLabel, String x) throws SQLException {}
        public void updateTime(int columnIndex, java.sql.Time x) throws SQLException {}
        public void updateTime(String columnLabel, java.sql.Time x) throws SQLException {}
        public void updateTimestamp(int columnIndex, java.sql.Timestamp x) throws SQLException {}
        public void updateTimestamp(String columnLabel, java.sql.Timestamp x) throws SQLException {}
        public boolean isWrapperFor(Class<?> iface) throws SQLException { return false; }
        public <T> T unwrap(Class<T> iface) throws SQLException { return null; }
    }
}