package com.fasterxml.jackson.dataformat.xml.ser;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.StringWriter;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.LinkedList;

import javax.xml.namespace.QName;
import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.io.IOContext;
import com.fasterxml.jackson.core.util.BufferRecycler;
import com.fasterxml.jackson.dataformat.xml.XmlPrettyPrinter;
import com.fasterxml.jackson.dataformat.xml.util.DefaultXmlPrettyPrinter;

public class ToXmlGeneratorTest {

    private ToXmlGenerator createGenerator() throws Throwable {
        BufferRecycler recycler = new BufferRecycler();
        IOContext ctxt = new IOContext(recycler, new Object(), false);
        StringWriter sw = new StringWriter();
        XMLOutputFactory xmlOutF = XMLOutputFactory.newInstance();
        XMLStreamWriter xmlWriter = xmlOutF.createXMLStreamWriter(sw);
        return new ToXmlGenerator(ctxt, 0, 0, null, xmlWriter);
    }

    @Test
    public void testFeatureCollectDefaults() throws Throwable {
        int defaults = ToXmlGenerator.Feature.collectDefaults();
        assertEquals(0, defaults);
        assertFalse(ToXmlGenerator.Feature.WRITE_XML_DECLARATION.enabledByDefault());
        assertFalse(ToXmlGenerator.Feature.WRITE_XML_1_1.enabledByDefault());
        assertEquals(1, ToXmlGenerator.Feature.WRITE_XML_DECLARATION.getMask());
        assertEquals(2, ToXmlGenerator.Feature.WRITE_XML_1_1.getMask());
        assertTrue(ToXmlGenerator.Feature.WRITE_XML_DECLaration_enabledIn(1)); // via enabledIn check
    }

    private boolean checkEnabledIn(ToXmlGenerator.Feature f, int flags) {
        return f.enabledIn(flags);
    }

    @Test
    public void testFeatureMethods() throws Throwable {
        assertTrue(checkEnabledIn(ToXmlGenerator.Feature.WRITE_XML_DECLARATION, 1));
        assertFalse(checkEnabledIn(ToXmlGenerator.Feature.WRITE_XML_DECLARATION, 2));
    }

    @Test
    public void testLifeCycleAndConfiguration() throws Throwable {
        ToXmlGenerator gen = createGenerator();
        
        assertNotNull(gen.getStaxWriter());
        assertNotNull(gen.getOutputTarget());
        assertEquals(-1, gen.getOutputBuffered());
        assertEquals(0, gen.getFormatFeatures());
        assertTrue(gen.canWriteFormattedNumbers());
        
        gen.enable(ToXmlGenerator.Feature.WRITE_XML_DECLARATION);
        assertTrue(gen.isEnabled(ToXmlGenerator.Feature.WRITE_XML_DECLARATION));
        
        gen.disable(ToXmlGenerator.Feature.WRITE_XML_DECLARATION);
        assertFalse(gen.isEnabled(ToXmlGenerator.Feature.WRITE_XML_DECLARATION));
        
        gen.configure(ToXmlGenerator.Feature.WRITE_XML_DECLARATION, true);
        assertTrue(gen.isEnabled(ToXmlGenerator.Feature.WRITE_XML_DECLARATION));
        
        gen.overrideFormatFeatures(2, 2);
        assertEquals(2, gen.getFormatFeatures());

        XmlPrettyPrinter pp = new DefaultXmlPrettyPrinter();
        gen.setPrettyPrinter(pp);
        
        gen.setNextIsAttribute(true);
        gen.setNextIsUnwrapped(true);
        gen.setNextIsCData(true);
        
        QName name = new QName("urn:test", "local");
        gen.setNextName(name);
        
        boolean missing1 = gen.setNextNameIfMissing(new QName("urn:other", "other"));
        assertFalse(missing1);
        
        gen = createGenerator();
        boolean missing2 = gen.setNextNameIfMissing(name);
        assertTrue(missing2);
    }

    @Test
    public void testInitGenerator() throws Throwable {
        ToXmlGenerator gen = createGenerator();
        gen.initGenerator();
        // second call should just return
        gen.initGenerator();

        ToXmlGenerator gen11 = createGenerator();
        gen11.enable(ToXmlGenerator.Feature.WRITE_XML_1_1);
        gen11.initGenerator();

        ToXmlGenerator genDecl = createGenerator();
        genDecl.enable(ToXmlGenerator.Feature.WRITE_XML_DECLARATION);
        genDecl.initGenerator();
        
        ToXmlGenerator genPretty = createGenerator();
        genPretty.enable(ToXmlGenerator.Feature.WRITE_XML_DECLARATION);
        genPretty.setPrettyPrinter(new DefaultXmlPrettyPrinter());
        genPretty.initGenerator();
    }

    @Test
    public void testWrappedValues() throws Throwable {
        ToXmlGenerator gen = createGenerator();
        QName wrapper = new QName("urn:test", "wrapper");
        QName wrapped = new QName("urn:test", "wrapped");
        
        gen.startWrappedValue(wrapper, wrapped);
        gen.finishWrappedValue(wrapper, wrapped);

        gen.startWrappedValue(null, wrapped);
        gen.finishWrappedValue(null, wrapped);

        XmlPrettyPrinter pp = new DefaultXmlPrettyPrinter();
        gen.setPrettyPrinter(pp);
        gen.startWrappedValue(wrapper, wrapped);
        gen.finishWrappedValue(wrapper, wrapped);
    }

    @Test
    public void testWriteFieldNameAndErrors() throws Throwable {
        ToXmlGenerator gen = createGenerator();
        gen.writeStartObject();
        gen.writeFieldName("testField");
        gen.writeString("val");
        gen.writeEndObject();
        gen.close();
    }

    @Test(expected = com.fasterxml.jackson.core.JsonGenerationException.class)
    public void testEndObjectWithoutStart() throws Throwable {
        ToXmlGenerator gen = createGenerator();
        gen._handleEndObject();
    }

    @Test
    public void testWriteScalars() throws Throwable {
        ToXmlGenerator gen = createGenerator();
        gen.writeStartObject();
        
        gen.setNextName(new QName("b"));
        gen.writeBoolean(true);

        gen.setNextName(new QName("s"));
        gen.writeString("hello");

        gen.setNextName(new QName("chars"));
        char[] buf = new char[] {'a', 'b', 'c'};
        gen.writeString(buf, 0, 3);

        gen.setNextName(new QName("nullElem"));
        gen.writeNull();

        gen.setNextName(new QName("i"));
        gen.writeNumber(123);

        gen.setNextName(new QName("l"));
        gen.writeNumber(123L);

        gen.setNextName(new QName("d"));
        gen.writeNumber(1.23);

        gen.setNextName(new QName("f"));
        gen.writeNumber(1.23f);

        gen.setNextName(new QName("dec"));
        gen.writeNumber(new BigDecimal("10.5"));

        gen.setNextName(new QName("big"));
        gen.writeNumber(new BigInteger("100"));

        gen.setNextName(new QName("encoded"));
        gen.writeNumber("456");

        gen.setNextName(new QName("bin"));
        gen.writeBinary(null, new byte[] {1, 2, 3}, 0, 3);

        gen.setNextName(new QName("binAttr"));
        gen.setNextIsAttribute(true);
        gen.writeBinary(com.fasterxml.jackson.core.Base64Variants.MIME, new byte[] {4, 5, 6}, 0, 3);

        gen.writeEndObject();
        gen.close();
    }

    @Test
    public void testRawWrites() throws Throwable {
        ToXmlGenerator gen = createGenerator();
        gen.writeStartObject();
        gen.setNextName(new QName("raw1"));
        gen.writeRawValue("data");

        gen.setNextName(new QName("raw2"));
        gen.writeRawValue("data", 0, 4);

        gen.setNextName(new QName("raw3"));
        char[] c = new char[] {'d', 'a', 't', 'a'};
        gen.writeRawValue(c, 0, 4);

        gen.writeRaw("rawStr");
        gen.writeRaw("rawStr", 0, 3);
        gen.writeRaw(c, 0, 2);
        gen.writeRaw('x');

        gen.writeEndObject();
        gen.close();
    }

    @Test
    public void testArrayContext() throws Throwable {
        ToXmlGenerator gen = createGenerator();
        gen.writeStartArray();
        gen.writeEndArray();
        gen.close();
    }

    @Test
    public void testFlushAndClose() throws Throwable {
        ToXmlGenerator gen = createGenerator();
        gen.enable(JsonGenerator.Feature.FLUSH_PASSED_TO_STREAM);
        gen.flush();
        gen.close();
    }
}