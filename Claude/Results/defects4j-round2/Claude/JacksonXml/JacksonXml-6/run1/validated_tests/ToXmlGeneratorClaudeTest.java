package com.fasterxml.jackson.dataformat.xml.ser;

import java.io.IOException;
import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.BigInteger;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLStreamWriter;

import org.codehaus.stax2.XMLStreamWriter2;

import org.junit.Test;
import static org.junit.Assert.*;

import com.fasterxml.jackson.core.Base64Variant;
import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.fasterxml.jackson.dataformat.xml.util.DefaultXmlPrettyPrinter;

public class ToXmlGeneratorClaudeTest
{
    private StringWriter out;

    private ToXmlGenerator newGenerator() throws IOException
    {
        out = new StringWriter();
        XmlMapper mapper = new XmlMapper();
        JsonGenerator g = mapper.getFactory().createGenerator(out);
        return (ToXmlGenerator) g;
    }

    // getFormatFeatures(): default state has neither Feature enabled -> 0
    @Test
    public void testGetFormatFeatures_defaultAllDisabled() throws Throwable {
        ToXmlGenerator gen = newGenerator();
        assertEquals(0, gen.getFormatFeatures());
    }

    // writeStartObject(): _handleStartObject throws when _nextName is null
    @Test
    public void testWriteStartObject_withoutNextName_throwsIllegalStateException() throws Throwable {
        ToXmlGenerator gen = newGenerator();
        try {
            gen.writeStartObject();
            fail("expected IllegalStateException");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("name"));
        }
    }



    // writeFieldName()+writeString(): value wrapped in field-named element
    @Test
    public void testWriteFieldName_and_writeString_wrapsValueInFieldElement() throws Throwable {
        ToXmlGenerator gen = newGenerator();
        gen.setNextName(new QName("root"));
        gen.writeStartObject();
        gen.writeFieldName("field");
        gen.writeString("value");
        gen.writeEndObject();
        gen.close();
        assertTrue(out.toString().contains("<field>value</field>"));
    }

    // writeFieldName() called twice without a value in between: STATUS_EXPECT_VALUE -> error
    @Test
    public void testWriteFieldName_calledTwiceWithoutValue_throwsJsonGenerationException() throws Throwable {
        ToXmlGenerator gen = newGenerator();
        gen.setNextName(new QName("root"));
        gen.writeStartObject();
        gen.writeFieldName("f1");
        try {
            gen.writeFieldName("f2");
            fail("expected JsonGenerationException");
        } catch (JsonGenerationException expected) {
        }
    }

    // writeStringField(): combines writeFieldName + writeString
    @Test
    public void testWriteStringField_combinesFieldNameAndStringValue() throws Throwable {
        ToXmlGenerator gen = newGenerator();
        gen.setNextName(new QName("root"));
        gen.writeStartObject();
        gen.writeStringField("name", "Alice");
        gen.writeEndObject();
        gen.close();
        assertTrue(out.toString().contains("<name>Alice</name>"));
    }

    // setNextNameIfMissing(): does nothing when a name is already set, returns false
    @Test
    public void testSetNextNameIfMissing_whenNameAlreadySet_returnsFalseAndKeepsOriginal() throws Throwable {
        ToXmlGenerator gen = newGenerator();
        gen.setNextName(new QName("first"));
        boolean result = gen.setNextNameIfMissing(new QName("second"));
        assertFalse(result);
        assertEquals("first", gen._nextName.getLocalPart());
    }

    // setNextNameIfMissing(): sets the name when missing, returns true
    @Test
    public void testSetNextNameIfMissing_whenNameMissing_returnsTrueAndSetsName() throws Throwable {
        ToXmlGenerator gen = newGenerator();
        boolean result = gen.setNextNameIfMissing(new QName("only"));
        assertTrue(result);
        assertEquals("only", gen._nextName.getLocalPart());
    }

    // enable()/disable(): toggles isEnabled() mask bit
    @Test
    public void testEnableDisableFeature_togglesIsEnabled() throws Throwable {
        ToXmlGenerator gen = newGenerator();
        assertFalse(gen.isEnabled(ToXmlGenerator.Feature.WRITE_XML_DECLARATION));
        gen.enable(ToXmlGenerator.Feature.WRITE_XML_DECLARATION);
        assertTrue(gen.isEnabled(ToXmlGenerator.Feature.WRITE_XML_DECLARATION));
        gen.disable(ToXmlGenerator.Feature.WRITE_XML_DECLARATION);
        assertFalse(gen.isEnabled(ToXmlGenerator.Feature.WRITE_XML_DECLARATION));
    }

    // configure(Feature, boolean): true enables, false disables
    @Test
    public void testConfigureFeature_trueEnablesFalseDisables() throws Throwable {
        ToXmlGenerator gen = newGenerator();
        gen.configure(ToXmlGenerator.Feature.WRITE_XML_1_1, true);
        assertTrue(gen.isEnabled(ToXmlGenerator.Feature.WRITE_XML_1_1));
        gen.configure(ToXmlGenerator.Feature.WRITE_XML_1_1, false);
        assertFalse(gen.isEnabled(ToXmlGenerator.Feature.WRITE_XML_1_1));
    }

    // overrideFormatFeatures(): only masked bits are changed
    @Test
    public void testOverrideFormatFeatures_updatesOnlyMaskedBits() throws Throwable {
        ToXmlGenerator gen = newGenerator();
        int declMask = ToXmlGenerator.Feature.WRITE_XML_DECLARATION.getMask();
        gen.overrideFormatFeatures(declMask, declMask);
        assertTrue(gen.isEnabled(ToXmlGenerator.Feature.WRITE_XML_DECLARATION));
        assertFalse(gen.isEnabled(ToXmlGenerator.Feature.WRITE_XML_1_1));
    }

    // canWriteFormattedNumbers(): always true per contract
    @Test
    public void testCanWriteFormattedNumbers_returnsTrue() throws Throwable {
        ToXmlGenerator gen = newGenerator();
        assertTrue(gen.canWriteFormattedNumbers());
    }

    // getOutputBuffered(): always -1 since Stax2 doesn't expose buffered amount
    @Test
    public void testGetOutputBuffered_returnsMinusOne() throws Throwable {
        ToXmlGenerator gen = newGenerator();
        assertEquals(-1, gen.getOutputBuffered());
    }

    // getOutputTarget(): returns a non-null XMLStreamWriter
    @Test
    public void testGetOutputTarget_returnsNonNullXmlStreamWriter() throws Throwable {
        ToXmlGenerator gen = newGenerator();
        Object target = gen.getOutputTarget();
        assertNotNull(target);
        assertTrue(target instanceof XMLStreamWriter);
    }

    // getStaxWriter(): per javadoc always of type XMLStreamWriter2
    @Test
    public void testGetStaxWriter_returnsXmlStreamWriter2Instance() throws Throwable {
        ToXmlGenerator gen = newGenerator();
        XMLStreamWriter writer = gen.getStaxWriter();
        assertNotNull(writer);
        assertTrue(writer instanceof XMLStreamWriter2);
    }

    // inRoot(): true before any structural writes
    @Test
    public void testInRoot_trueBeforeAnyWrites() throws Throwable {
        ToXmlGenerator gen = newGenerator();
        assertTrue(gen.inRoot());
    }

    // inRoot(): false once inside an object context
    @Test
    public void testInRoot_falseInsideObjectContext() throws Throwable {
        ToXmlGenerator gen = newGenerator();
        gen.setNextName(new QName("root"));
        gen.writeStartObject();
        assertFalse(gen.inRoot());
        gen.writeEndObject();
        gen.close();
    }

    // initGenerator(): with default features, nothing is written and flag is set
    @Test
    public void testInitGenerator_defaultFeatures_noDeclarationWritten() throws Throwable {
        ToXmlGenerator gen = newGenerator();
        gen.initGenerator();
        assertTrue(gen._initialized);
        assertEquals("", out.toString());
    }





    // setNextIsAttribute(true): writeStringField writes an XML attribute, not an element
    @Test
    public void testSetNextIsAttribute_writesXmlAttribute() throws Throwable {
        ToXmlGenerator gen = newGenerator();
        gen.setNextName(new QName("root"));
        gen.writeStartObject();
        gen.setNextIsAttribute(true);
        gen.writeStringField("attr", "val");
        gen.setNextIsAttribute(false);
        gen.writeEndObject();
        gen.close();
        String xml = out.toString();
        assertTrue(xml.contains("attr=\"val\""));
    }

    // setNextIsCData(true): writeString outputs a CDATA section
    @Test
    public void testSetNextIsCData_writesCDataSection() throws Throwable {
        ToXmlGenerator gen = newGenerator();
        gen.setNextName(new QName("root"));
        gen.writeStartObject();
        gen.writeFieldName("field");
        gen.setNextIsCData(true);
        gen.writeString("data");
        gen.writeEndObject();
        gen.close();
        String xml = out.toString();
        assertTrue(xml.contains("<![CDATA[data]]>"));
    }

    // setNextIsUnwrapped(true): value written without its own wrapper element
    @Test
    public void testSetNextIsUnwrapped_omitsWrapperElement() throws Throwable {
        ToXmlGenerator gen = newGenerator();
        gen.setNextName(new QName("root"));
        gen.writeStartObject();
        gen.writeFieldName("field");
        gen.setNextIsUnwrapped(true);
        gen.writeString("text");
        gen.writeEndObject();
        gen.close();
        String xml = out.toString();
        assertFalse(xml.contains("<field>"));
        assertTrue(xml.contains("text"));
    }

    // writeBoolean(): wraps value in named element
    @Test
    public void testWriteBoolean_wrapsInElement() throws Throwable {
        ToXmlGenerator gen = newGenerator();
        gen.setNextName(new QName("root"));
        gen.writeStartObject();
        gen.writeFieldName("flag");
        gen.writeBoolean(true);
        gen.writeEndObject();
        gen.close();
        assertTrue(out.toString().contains("<flag>true</flag>"));
    }

    // writeNull(): produces an empty element
    @Test
    public void testWriteNull_writesEmptyElement() throws Throwable {
        ToXmlGenerator gen = newGenerator();
        gen.setNextName(new QName("root"));
        gen.writeStartObject();
        gen.writeFieldName("field");
        gen.writeNull();
        gen.writeEndObject();
        gen.close();
        assertTrue(out.toString().contains("<field/>"));
    }

    // writeNumber(int): wraps value in named element
    @Test
    public void testWriteNumberInt_wrapsInElement() throws Throwable {
        ToXmlGenerator gen = newGenerator();
        gen.setNextName(new QName("root"));
        gen.writeStartObject();
        gen.writeFieldName("num");
        gen.writeNumber(42);
        gen.writeEndObject();
        gen.close();
        assertTrue(out.toString().contains("<num>42</num>"));
    }

    // writeNumber(long): wraps value in named element
    @Test
    public void testWriteNumberLong_wrapsInElement() throws Throwable {
        ToXmlGenerator gen = newGenerator();
        gen.setNextName(new QName("root"));
        gen.writeStartObject();
        gen.writeFieldName("num");
        gen.writeNumber(9999999999L);
        gen.writeEndObject();
        gen.close();
        assertTrue(out.toString().contains("<num>9999999999</num>"));
    }

    // writeNumber(double): wraps value in named element
    @Test
    public void testWriteNumberDouble_wrapsInElement() throws Throwable {
        ToXmlGenerator gen = newGenerator();
        gen.setNextName(new QName("root"));
        gen.writeStartObject();
        gen.writeFieldName("num");
        gen.writeNumber(3.5);
        gen.writeEndObject();
        gen.close();
        assertTrue(out.toString().contains("<num>3.5</num>"));
    }

    // writeNumber(float): wraps value in named element
    @Test
    public void testWriteNumberFloat_wrapsInElement() throws Throwable {
        ToXmlGenerator gen = newGenerator();
        gen.setNextName(new QName("root"));
        gen.writeStartObject();
        gen.writeFieldName("num");
        gen.writeNumber(2.5f);
        gen.writeEndObject();
        gen.close();
        assertTrue(out.toString().contains("<num>2.5</num>"));
    }

    // writeNumber(BigDecimal): WRITE_BIGDECIMAL_AS_PLAIN uses toPlainString() per BigDecimal contract
    @Test
    public void testWriteNumberBigDecimal_plainFeatureEnabled_usesPlainString() throws Throwable {
        ToXmlGenerator gen = newGenerator();
        gen.configure(JsonGenerator.Feature.WRITE_BIGDECIMAL_AS_PLAIN, true);
        gen.setNextName(new QName("root"));
        gen.writeStartObject();
        gen.writeFieldName("num");
        gen.writeNumber(new BigDecimal("1E+2"));
        gen.writeEndObject();
        gen.close();
        assertTrue(out.toString().contains("<num>100</num>"));
    }

    // writeNumber(BigDecimal): default (non-plain) writes a simple decimal correctly
    @Test
    public void testWriteNumberBigDecimal_defaultNotation_writesToString() throws Throwable {
        ToXmlGenerator gen = newGenerator();
        gen.setNextName(new QName("root"));
        gen.writeStartObject();
        gen.writeFieldName("num");
        gen.writeNumber(new BigDecimal("3.14"));
        gen.writeEndObject();
        gen.close();
        assertTrue(out.toString().contains("<num>3.14</num>"));
    }

    // writeNumber(BigDecimal null): delegates to writeNull()
    @Test
    public void testWriteNumberBigDecimal_nullValue_writesNullElement() throws Throwable {
        ToXmlGenerator gen = newGenerator();
        gen.setNextName(new QName("root"));
        gen.writeStartObject();
        gen.writeFieldName("num");
        gen.writeNumber((BigDecimal) null);
        gen.writeEndObject();
        gen.close();
        assertTrue(out.toString().contains("<num/>"));
    }

    // writeNumber(BigInteger): wraps value in named element
    @Test
    public void testWriteNumberBigInteger_wrapsInElement() throws Throwable {
        ToXmlGenerator gen = newGenerator();
        gen.setNextName(new QName("root"));
        gen.writeStartObject();
        gen.writeFieldName("num");
        gen.writeNumber(BigInteger.valueOf(123456789L));
        gen.writeEndObject();
        gen.close();
        assertTrue(out.toString().contains("<num>123456789</num>"));
    }

    // writeNumber(BigInteger null): delegates to writeNull()
    @Test
    public void testWriteNumberBigInteger_nullValue_writesNullElement() throws Throwable {
        ToXmlGenerator gen = newGenerator();
        gen.setNextName(new QName("root"));
        gen.writeStartObject();
        gen.writeFieldName("num");
        gen.writeNumber((BigInteger) null);
        gen.writeEndObject();
        gen.close();
        assertTrue(out.toString().contains("<num/>"));
    }

    // writeNumber(String): delegates to writeString(), value written as text
    @Test
    public void testWriteNumberEncodedString_wrapsAsText() throws Throwable {
        ToXmlGenerator gen = newGenerator();
        gen.setNextName(new QName("root"));
        gen.writeStartObject();
        gen.writeFieldName("num");
        gen.writeNumber("12345");
        gen.writeEndObject();
        gen.close();
        assertTrue(out.toString().contains("<num>12345</num>"));
    }

    // writeBinary(): null data delegates to writeNull()
    @Test
    public void testWriteBinary_nullData_writesNullElement() throws Throwable {
        ToXmlGenerator gen = newGenerator();
        gen.setNextName(new QName("root"));
        gen.writeStartObject();
        gen.writeFieldName("bin");
        gen.writeBinary((Base64Variant) null, null, 0, 0);
        gen.writeEndObject();
        gen.close();
        assertTrue(out.toString().contains("<bin/>"));
    }

    // writeEndArray(): reports error when current context is not an array
    @Test
    public void testWriteEndArray_notInArrayContext_throwsJsonGenerationException() throws Throwable {
        ToXmlGenerator gen = newGenerator();
        try {
            gen.writeEndArray();
            fail("expected JsonGenerationException");
        } catch (JsonGenerationException expected) {
        }
    }

    // writeEndObject(): reports error when current context is not an object
    @Test
    public void testWriteEndObject_notInObjectContext_throwsJsonGenerationException() throws Throwable {
        ToXmlGenerator gen = newGenerator();
        try {
            gen.writeEndObject();
            fail("expected JsonGenerationException");
        } catch (JsonGenerationException expected) {
        }
    }

    // writeStartArray()/writeEndArray(): each array item repeats the field element name
    @Test
    public void testWriteStartArray_writeEndArray_repeatsElementNameForEachItem() throws Throwable {
        ToXmlGenerator gen = newGenerator();
        gen.setNextName(new QName("root"));
        gen.writeStartObject();
        gen.writeFieldName("item");
        gen.writeStartArray();
        gen.writeString("a");
        gen.writeString("b");
        gen.writeEndArray();
        gen.writeEndObject();
        gen.close();
        String xml = out.toString();
        assertTrue(xml.contains("<item>a</item>"));
        assertTrue(xml.contains("<item>b</item>"));
    }

    // writeRepeatedFieldName(): re-registers the same field name for another value
    @Test
    public void testWriteRepeatedFieldName_writesSameFieldNameAgain() throws Throwable {
        ToXmlGenerator gen = newGenerator();
        gen.setNextName(new QName("root"));
        gen.writeStartObject();
        gen.writeFieldName("f");
        gen.writeString("v1");
        gen.writeRepeatedFieldName();
        gen.writeString("v2");
        gen.writeEndObject();
        gen.close();
        String xml = out.toString();
        assertTrue(xml.contains("<f>v1</f>"));
        assertTrue(xml.contains("<f>v2</f>"));
    }

    // startWrappedValue()/finishWrappedValue(): non-null wrapper is written around items
    @Test
    public void testStartWrappedValue_withWrapper_writesWrapperElement() throws Throwable {
        ToXmlGenerator gen = newGenerator();
        gen.setNextName(new QName("root"));
        gen.writeStartObject();
        gen.writeFieldName("list");
        gen.startWrappedValue(new QName("items"), new QName("item"));
        gen.writeString("x");
        gen.finishWrappedValue(new QName("items"), new QName("item"));
        gen.writeEndObject();
        gen.close();
        String xml = out.toString();
        assertTrue(xml.contains("<items>"));
        assertTrue(xml.contains("<item>x</item>"));
        assertTrue(xml.contains("</items>"));
    }

    // startWrappedValue()/finishWrappedValue(): null wrapper writes no wrapper element
    @Test
    public void testStartWrappedValue_withoutWrapper_noWrapperElementWritten() throws Throwable {
        ToXmlGenerator gen = newGenerator();
        gen.setNextName(new QName("root"));
        gen.writeStartObject();
        gen.writeFieldName("list");
        gen.startWrappedValue(null, new QName("item"));
        gen.writeString("y");
        gen.finishWrappedValue(null, new QName("item"));
        gen.writeEndObject();
        gen.close();
        String xml = out.toString();
        assertTrue(xml.contains("<item>y</item>"));
        assertFalse(xml.contains("<items>"));
    }



    // setPrettyPrinter(): fluent return value and internal XmlPrettyPrinter field update
    @Test
    public void testSetPrettyPrinter_returnsSameGeneratorAndUpdatesXmlPrettyPrinterField() throws Throwable {
        ToXmlGenerator gen = newGenerator();
        DefaultXmlPrettyPrinter pp = new DefaultXmlPrettyPrinter();
        JsonGenerator result = gen.setPrettyPrinter(pp);
        assertSame(gen, result);
        assertSame(pp, gen._xmlPrettyPrinter);
    }

    // setPrettyPrinter(null): clears the XmlPrettyPrinter-specific field
    @Test
    public void testSetPrettyPrinter_withNull_clearsXmlPrettyPrinterField() throws Throwable {
        ToXmlGenerator gen = newGenerator();
        gen.setPrettyPrinter(new DefaultXmlPrettyPrinter());
        gen.setPrettyPrinter(null);
        assertNull(gen._xmlPrettyPrinter);
    }
}
