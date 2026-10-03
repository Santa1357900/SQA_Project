package org.jsoup.nodes;

import org.jsoup.Connection;
import org.jsoup.parser.Tag;
import org.jsoup.select.Elements;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class FormElementTest {

    @Test
    public void testConstructorAndElements() throws Throwable {
        Tag tag = Tag.valueOf("form");
        Attributes attributes = new Attributes();
        FormElement form = new FormElement(tag, "http://example.com", attributes);

        assertNotNull(form.elements());
        assertEquals(0, form.elements().size());

        Element input = new Element(Tag.valueOf("input"), "http://example.com");
        form.addElement(input);

        assertEquals(1, form.elements().size());
        assertEquals(input, form.elements().get(0));
    }

    @Test
    public void testSubmitWithoutBaseUriAndAction() throws Throwable {
        Tag tag = Tag.valueOf("form");
        Attributes attributes = new Attributes();
        FormElement form = new FormElement(tag, "", attributes);

        try {
            form.submit();
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            assertTrue(e.getMessage().contains("Could not determine a form action URL"));
        }
    }

    @Test
    public void testSubmitWithBaseUri() throws Throwable {
        Tag tag = Tag.valueOf("form");
        Attributes attributes = new Attributes();
        FormElement form = new FormElement(tag, "http://example.com/path", attributes);
        form.attr("method", "POST");

        Connection conn = form.submit();
        assertNotNull(conn);
        assertEquals(Connection.Method.POST, conn.request().method());
        assertEquals("http://example.com/path", conn.request().url().toString());
    }

    @Test
    public void testSubmitWithActionAttr() throws Throwable {
        Tag tag = Tag.valueOf("form");
        Attributes attributes = new Attributes();
        FormElement form = new FormElement(tag, "http://example.com/base", attributes);
        form.attr("action", "http://example.com/action");
        form.attr("method", "GET");

        Connection conn = form.submit();
        assertNotNull(conn);
        assertEquals(Connection.Method.GET, conn.request().method());
        assertEquals("http://example.com/action", conn.request().url().toString());
    }

    @Test
    public void testFormDataBasicInputs() throws Throwable {
        Tag tag = Tag.valueOf("form");
        FormElement form = new FormElement(tag, "http://example.com", new Attributes());

        Element input1 = new Element(Tag.valueOf("input"), "http://example.com");
        input1.attr("name", "user");
        input1.attr("value", "john");
        form.addElement(input1);

        Element inputNoName = new Element(Tag.valueOf("input"), "http://example.com");
        inputNoName.attr("value", "noname");
        form.addElement(inputNoName);

        Element inputDisabled = new Element(Tag.valueOf("input"), "http://example.com");
        inputDisabled.attr("name", "disabled");
        inputDisabled.attr("value", "val");
        inputDisabled.attr("disabled", "disabled");
        form.addElement(inputDisabled);

        Element div = new Element(Tag.valueOf("div"), "http://example.com");
        div.attr("name", "divname");
        form.addElement(div);

        List<Connection.KeyVal> data = form.formData();
        assertEquals(1, data.size());
        assertEquals("user", data.get(0).key());
        assertEquals("john", data.get(0).value());
    }

    @Test
    public void testFormDataCheckboxesAndRadios() throws Throwable {
        Tag tag = Tag.valueOf("form");
        FormElement form = new FormElement(tag, "http://example.com", new Attributes());

        Element cbUnchecked = new Element(Tag.valueOf("input"), "http://example.com");
        cbUnchecked.attr("type", "checkbox");
        cbUnchecked.attr("name", "cb1");
        cbUnchecked.attr("value", "val1");
        form.addElement(cbUnchecked);

        Element cbChecked = new Element(Tag.valueOf("input"), "http://example.com");
        cbChecked.attr("type", "checkbox");
        cbChecked.attr("name", "cb2");
        cbChecked.attr("value", "val2");
        cbChecked.attr("checked", "");
        form.addElement(cbChecked);

        Element cbCheckedNoVal = new Element(Tag.valueOf("input"), "http://example.com");
        cbCheckedNoVal.attr("type", "checkbox");
        cbCheckedNoVal.attr("name", "cb3");
        cbCheckedNoVal.attr("checked", "");
        form.addElement(cbCheckedNoVal);

        Element radioChecked = new Element(Tag.valueOf("input"), "http://example.com");
        radioChecked.attr("type", "radio");
        radioChecked.attr("name", "rad1");
        radioChecked.attr("value", "rval");
        radioChecked.attr("checked", "checked");
        form.addElement(radioChecked);

        List<Connection.KeyVal> data = form.formData();
        assertEquals(3, data.size());
        assertEquals("cb2", data.get(0).key());
        assertEquals("val2", data.get(0).value());

        assertEquals("cb3", data.get(1).key());
        assertEquals("on", data.get(1).value());

        assertEquals("rad1", data.get(2).key());
        assertEquals("rval", data.get(2).value());
    }

    @Test
    public void testFormDataSelectOptions() throws Throwable {
        Tag tag = Tag.valueOf("form");
        FormElement form = new FormElement(tag, "http://example.com", new Attributes());

        Element select = new Element(Tag.valueOf("select"), "http://example.com");
        select.attr("name", "sel");

        Element opt1 = new Element(Tag.valueOf("option"), "http://example.com");
        opt1.attr("value", "1");
        opt1.text("One");
        select.appendChild(opt1);

        Element opt2 = new Element(Tag.valueOf("option"), "http://example.com");
        opt2.attr("value", "2");
        opt2.attr("selected", "");
        opt2.text("Two");
        select.appendChild(opt2);

        Element opt3 = new Element(Tag.valueOf("option"), "http://example.com");
        opt3.attr("value", "3");
        opt3.attr("selected", "");
        opt3.text("Three");
        select.appendChild(opt3);

        form.addElement(select);

        List<Connection.KeyVal> data = form.formData();
        assertEquals(2, data.size());
        assertEquals("2", data.get(0).value());
        assertEquals("3", data.get(1).value());
    }

    @Test
    public void testFormDataSelectNoSelectedOptionFirstFallback() throws Throwable {
        Tag tag = Tag.valueOf("form");
        FormElement form = new FormElement(tag, "http://example.com", new Attributes());

        Element select = new Element(Tag.valueOf("select"), "http://example.com");
        select.attr("name", "sel");

        Element opt1 = new Element(Tag.valueOf("option"), "http://example.com");
        opt1.attr("value", "fallback");
        select.appendChild(opt1);

        form.addElement(select);

        List<Connection.KeyVal> data = form.formData();
        assertEquals(1, data.size());
        assertEquals("sel", data.get(0).key());
        assertEquals("fallback", data.get(0).value());
    }

    @Test
    public void testFormDataSelectEmptyOptions() throws Throwable {
        Tag tag = Tag.valueOf("form");
        FormElement form = new FormElement(tag, "http://example.com", new Attributes());

        Element select = new Element(Tag.valueOf("select"), "http://example.com");
        select.attr("name", "sel");
        form.addElement(select);

        List<Connection.KeyVal> data = form.formData();
        assertEquals(0, data.size());
    }
}