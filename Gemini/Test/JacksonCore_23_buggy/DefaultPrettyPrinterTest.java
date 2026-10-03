package com.fasterxml.jackson.core.util;

import org.junit.Test;
import static org.junit.Assert.*;

import java.io.IOException;
import java.io.StringWriter;
import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.SerializableString;
import com.fasterxml.jackson.core.Separators;
import com.fasterxml.jackson.core.io.SerializedString;

public class DefaultPrettyPrinterTest {

    @Test
    public void test_DefaultPrettyPrinter_constructors_and_cloning_behavior_attempt_2() throws Throwable {
        DefaultPrettyPrinter printer1 = new DefaultPrettyPrinter();
        assertNotNull(printer1);

        DefaultPrettyPrinter printer2 = new DefaultPrettyPrinter((String) null);
        assertNotNull(printer2);

        DefaultPrettyPrinter printer3 = new DefaultPrettyPrinter("CUSTOM_ROOT");
        assertNotNull(printer3);

        SerializableString serStr = new SerializedString("SERIALIZED_ROOT");
        DefaultPrettyPrinter printer4 = new DefaultPrettyPrinter(serStr);
        assertNotNull(printer4);

        DefaultPrettyPrinter clone1 = new DefaultPrettyPrinter(printer1);
        assertNotNull(clone1);

        DefaultPrettyPrinter clone2 = new DefaultPrettyPrinter(printer1, serStr);
        assertNotNull(clone2);

        DefaultPrettyPrinter instanceCreated = printer1.createInstance();
        assertNotNull(instanceCreated);
    }

    @Test
    public void test_DefaultPrettyPrinter_rootSeparator_mutants_behavior_attempt_2() throws Throwable {
        DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
        
        DefaultPrettyPrinter printerSame = printer.withRootSeparator(DefaultPrettyPrinter.DEFAULT_ROOT_VALUE_SEPARATOR);
        assertSame(printer, printerSame);

        DefaultPrettyPrinter printerNew = printer.withRootSeparator("NEW_ROOT");
        assertNotSame(printer, printerNew);

        DefaultPrettyPrinter printerNull = printer.withRootSeparator((String) null);
        assertNotSame(printer, printerNull);

        SerializableString customSer = new SerializedString("CUSTOM_SER");
        DefaultPrettyPrinter printerCustomSer = printer.withRootSeparator(customSer);
        assertNotSame(printer, printerCustomSer);
    }

    @Test
    public void test_DefaultPrettyPrinter_indenterConfiguration_behavior_attempt_2() throws Throwable {
        DefaultPrettyPrinter printer = new DefaultPrettyPrinter();

        printer.indentArraysWith(null);
        printer.indentObjectsWith(null);

        DefaultPrettyPrinter.Indenter customIndenter = new DefaultPrettyPrinter.NopIndenter();
        printer.indentArraysWith(customIndenter);
        printer.indentObjectsWith(customIndenter);

        DefaultPrettyPrinter printerWithArray = printer.withArrayIndenter(null);
        assertNotNull(printerWithArray);

        DefaultPrettyPrinter printerWithSameArray = printerWithArray.withArrayIndenter(printerWithArray._arrayIndenter);
        assertSame(printerWithArray, printerWithSameArray);

        DefaultPrettyPrinter printerWithObj = printer.withObjectIndenter(null);
        assertNotNull(printerWithObj);

        DefaultPrettyPrinter printerWithSameObj = printerWithObj.withObjectIndenter(printerWithObj._objectIndenter);
        assertSame(printerWithObj, printerWithSameObj);
    }

    @Test
    public void test_DefaultPrettyPrinter_spacesInObjectEntries_behavior_attempt_2() throws Throwable {
        DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
        
        DefaultPrettyPrinter withSpaces = printer.withSpacesInObjectEntries();
        assertSame(printer, withSpaces);

        DefaultPrettyPrinter withoutSpaces = printer.withoutSpacesInObjectEntries();
        assertNotSame(printer, withoutSpaces);

        DefaultPrettyPrinter withoutSpacesAgain = withoutSpaces.withoutSpacesInObjectEntries();
        assertSame(withoutSpaces, withoutSpacesAgain);

        DefaultPrettyPrinter withSpacesBack = withoutSpaces.withSpacesInObjectEntries();
        assertNotSame(withoutSpaces, withSpacesBack);
    }

    @Test
    public void test_DefaultPrettyPrinter_separators_behavior_attempt_2() throws Throwable {
        DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
        Separators separators = Separators.createDefaultInstance();
        DefaultPrettyPrinter configured = printer.withSeparators(separators);
        assertSame(printer, configured);
    }

    @Test
    public void test_DefaultPrettyPrinter_jsonGenerationFlow_behavior_attempt_2() throws Throwable {
        StringWriter sw = new StringWriter();
        JsonFactory f = new JsonFactory();
        JsonGenerator g = f.createGenerator(sw);

        DefaultPrettyPrinter printer = new DefaultPrettyPrinter();
        printer.withSeparators(Separators.createDefaultInstance());

        printer.writeRootValueSeparator(g);

        printer.writeStartObject(g);
        printer.beforeObjectEntries(g);
        printer.writeObjectFieldValueSeparator(g);
        printer.writeObjectEntrySeparator(g);
        printer.writeEndObject(g, 1);

        printer.writeStartObject(g);
        printer.writeEndObject(g, 0);

        printer.writeStartArray(g);
        printer.beforeArrayValues(g);
        printer.writeArrayValueSeparator(g);
        printer.writeEndArray(g, 1);

        printer.writeStartArray(g);
        printer.writeEndArray(g, 0);

        g.close();
    }

    @Test
    public void test_DefaultPrettyPrinter_withoutSpacesGenerationFlow_behavior_attempt_2() throws Throwable {
        StringWriter sw = new StringWriter();
        JsonFactory f = new JsonFactory();
        JsonGenerator g = f.createGenerator(sw);

        DefaultPrettyPrinter printer = new DefaultPrettyPrinter().withoutSpacesInObjectEntries();
        
        printer.writeStartObject(g);
        printer.writeObjectFieldValueSeparator(g);
        printer.writeEndObject(g, 1);

        g.close();
    }

    @Test
    public void test_FixedSpaceIndenter_behavior_attempt_2() throws Throwable {
        StringWriter sw = new StringWriter();
        JsonFactory f = new JsonFactory();
        JsonGenerator g = f.createGenerator(sw);

        DefaultPrettyPrinter.FixedSpaceIndenter indenter = DefaultPrettyPrinter.FixedSpaceIndenter.instance;
        assertTrue(indenter.isInline());
        indenter.writeIndentation(g, 1);

        g.close();
    }

    @Test
    public void test_NopIndenter_behavior_attempt_2() throws Throwable {
        StringWriter sw = new StringWriter();
        JsonFactory f = new JsonFactory();
        JsonGenerator g = f.createGenerator(sw);

        DefaultPrettyPrinter.NopIndenter indenter = DefaultPrettyPrinter.NopIndenter.instance;
        assertTrue(indenter.isInline());
        indenter.writeIndentation(g, 1);

        g.close();
    }
}