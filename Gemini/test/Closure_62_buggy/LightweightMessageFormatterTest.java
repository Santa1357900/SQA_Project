package com.google.javascript.jscomp;

import static com.google.javascript.jscomp.SourceExcerptProvider.SourceExcerpt.LINE;
import static com.google.javascript.jscomp.SourceExcerptProvider.SourceExcerpt.REGION;

import org.junit.Test;
import static org.junit.Assert.*;

public class LightweightMessageFormatterTest {

  // Dummy SourceExcerptProvider implementing interface safely for testing
  private static class DummySourceExcerptProvider implements SourceExcerptProvider {
    private final String excerptText;

    public DummySourceExcerptProvider(String excerptText) {
      this.excerptText = excerptText;
    }

    public StringgetSource(String sourceName, int lineNumber) {
      return excerptText;
    }

    public Region REGION(String sourceName, int lineNumber, int charno, boolean excerpt) {
      return null;
    }
  }

  @Test
  public void testWithoutSourceConstructorAndFormatting() throws Throwable {
    LightweightMessageFormatter formatter = LightweightMessageFormatter.withoutSource();
    assertNotNull(formatter);

    DiagnosticType diagnosticType = DiagnosticType.error("TEST_ERROR", "An error occurred");
    JSError error = JSError.make("test.js", 10, 5, diagnosticType);

    String result = formatter.formatError(error);
    assertNotNull(result);
    assertTrue(result.contains("test.js:10"));
    assertTrue(result.contains("ERROR"));
    assertTrue(result.contains("An error occurred"));
  }

  @Test
  public void testWarningFormattingWithoutLineNumber() throws Throwable {
    LightweightMessageFormatter formatter = LightweightMessageFormatter.withoutSource();
    DiagnosticType diagnosticType = DiagnosticType.warning("TEST_WARNING", "A warning occurred");
    JSError warning = JSError.make("test.js", 0, 0, diagnosticType);

    String result = formatter.formatWarning(warning);
    assertNotNull(result);
    assertTrue(result.contains("test.js"));
    assertFalse(result.contains("test.js:0"));
    assertTrue(result.contains("WARNING"));
    assertTrue(result.contains("A warning occurred"));
  }

  @Test
  public void testFormatterWithSourceExcerptLine() throws Throwable {
    SourceExcerptProvider provider = new SourceExcerptProvider() {
      public String getSource(String sourceName, int lineNumber) {
        return "var x = 10;";
      }
      public Region REGION(String sourceName, int lineNumber, int charno, boolean excerpt) {
        return null;
      }
    };

    LightweightMessageFormatter formatter = new LightweightMessageFormatter(provider, LINE);
    DiagnosticType diagnosticType = DiagnosticType.error("TEST_ERROR", "Error message");
    JSError error = JSError.make("my_script.js", 1, 4, diagnosticType);

    String result = formatter.formatError(error);
    assertNotNull(result);
    assertTrue(result.contains("my_script.js:1"));
    assertTrue(result.contains("var x = 10;"));
    assertTrue(result.contains("^\n"));
  }

  @Test
  public void testFormatterWithSourceExcerptRegion() throws Throwable {
    SourceExcerptProvider provider = new SourceExcerptProvider() {
      public String getSource(String sourceName, int lineNumber) {
        return "line1\nline2";
      }
      public Region REGION(String sourceName, int lineNumber, int charno, boolean excerpt) {
        return null;
      }
    };

    LightweightMessageFormatter formatter = new LightweightMessageFormatter(provider, REGION);
    DiagnosticType diagnosticType = DiagnosticType.error("TEST_ERROR", "Region error");
    JSError error = JSError.make("region.js", 1, 1, diagnosticType);

    String result = formatter.formatError(error);
    assertNotNull(result);
    assertTrue(result.contains("region.js:1"));
    assertTrue(result.contains("Region error"));
  }

  @Test(expected = NullPointerException.class)
  public void testConstructorNullSourceCheck() throws Throwable {
    new LightweightMessageFormatter(null);
  }

  @Test
  public void testLineNumberingFormatterFormatLine() throws Throwable {
    LightweightMessageFormatter.LineNumberingFormatter numberingFormatter =
        new LightweightMessageFormatter.LineNumberingFormatter();
    String formatted = numberingFormatter.formatLine("sample line", 5);
    assertEquals("sample line", formatted);
  }

  @Test
  public void testLineNumberingFormatterFormatRegionNull() throws Throwable {
    LightweightMessageFormatter.LineNumberingFormatter numberingFormatter =
        new LightweightMessageFormatter.LineNumberingFormatter();
    String formatted = numberingFormatter.formatRegion(null);
    assertNull(formatted);
  }

  @Test
  public void testLineNumberingFormatterFormatRegionEmptyCode() throws Throwable {
    LightweightMessageFormatter.LineNumberingFormatter numberingFormatter =
        new LightweightMessageFormatter.LineNumberingFormatter();
    Region region = new SimpleRegion(1, 1, "");
    String formatted = numberingFormatter.formatRegion(region);
    assertNull(formatted);
  }

  @Test
  public void testLineNumberingFormatterFormatRegionValid() throws Throwable {
    LightweightMessageFormatter.LineNumberingFormatter numberingFormatter =
        new LightweightMessageFormatter.LineNumberingFormatter();
    Region region = new SimpleRegion(10, 11, "first line\nsecond line");
    String formatted = numberingFormatter.formatRegion(region);
    assertNotNull(formatted);
    assertTrue(formatted.contains("10| first line"));
    assertTrue(formatted.contains("11| second line"));
  }

  @Test
  public void testLineNumberingFormatterFormatRegionSingleLine() throws Throwable {
    LightweightMessageFormatter.LineNumberingFormatter numberingFormatter =
        new LightweightMessageFormatter.LineNumberingFormatter();
    Region region = new SimpleRegion(5, 5, "single line code");
    String formatted = numberingFormatter.formatRegion(region);
    assertNotNull(formatted);
    assertTrue(formatted.contains("5| single line code"));
  }

  @Test
  public void testCharnoWhitespacePadding() throws Throwable {
    SourceExcerptProvider provider = new SourceExcerptProvider() {
      public String getSource(String sourceName, int lineNumber) {
        return "\tvar y = 20;";
      }
      public Region REGION(String sourceName, int lineNumber, int charno, boolean excerpt) {
        return null;
      }
    };

    LightweightMessageFormatter formatter = new LightweightMessageFormatter(provider, LINE);
    DiagnosticType diagnosticType = DiagnosticType.error("TEST_ERROR", "Tab test");
    JSError error = JSError.make("tab.js", 1, 5, diagnosticType);

    String result = formatter.formatError(error);
    assertNotNull(result);
    assertTrue(result.contains("^\n"));
  }
}