package org.jsoup.nodes;

import org.jsoup.Connection;
import org.jsoup.parser.Tag;
import org.jsoup.select.Elements;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class FormElementTest {

    @Test
    public void testFormElementCreationAndElements() throws Throwable {
        Tag tag = Tag.valueOf("form");
        Attributes attributes = new Attributes();
        FormElement form = new FormElement(tag, "http://example.com", attributes);

        assertNotNull(form.elements());
        assertEquals(0, form.elements().size());

        Element input = new Element(Tag.valueOf("input"), "http://example.com");
        form.addElement(input);
        assertEquals(1, form.elements().size());
        assertEquals(input, form.elements().get(0));

        form.removeChild(input);
        assertEquals(0, form.elements().size());
    }

    @Test
    public void testSubmitWithoutBaseUriAndAction() throws Throwable {
        Tag tag = Tag.valueOf("form");
        Attributes attributes = new Attributes();
        FormElement form = new FormElement(tag, "", attributes);

        boolean thrown = false;
        try {
            form.submit();
        } catch (IllegalArgumentException e) {
            thrown = true;
            assertTrue(e.getMessage().contains("Could not determine a form action URL"));
        }
        assertTrue(thrown);
    }

    @Test
    public void testSubmitWithAction() throws Throwable {
        Tag tag = Tag.valueOf("form");
        Attributes attributes = new Attributes();
        attributes.put("action", "http://example.com/search");
        attributes.put("method", "POST");
        FormElement form = new FormElement(tag, "http://example.com", attributes);

        Connection con = form.submit();
        assertNotNull(con);
        assertEquals(Connection.Method.POST, con.request().method());
        assertEquals("http://example.com/search", con.request().url().toString());
    }

    @Test
    public void testSubmitGetMethod() throws Throwable {
        Tag tag = Tag.valueOf("form");
        Attributes attributes = new Attributes();
        attributes.put("action", "http://example.com/get");
        attributes.put("method", "GET");
        FormElement form = new FormElement(tag, "http://example.com", attributes);

        Connection con = form.submit();
        assertNotNull(con);
        assertEquals(Connection.Method.GET, con.request().method());
    }

    @Test
    public void testFormDataFiltering() throws Throwable {
        Tag tag = Tag.valueOf("form");
        FormElement form = new FormElement(tag, "http://example.com", new Attributes());

        // Non-submittable element
        Element div = new Element(Tag.valueOf("div"), "http://example.com");
        div.attr("name", "divName");
        div.attr("value", "divVal");
        form.addElement(div);

        // Disabled element
        Element disabledInput = new Element(Tag.valueOf("input"), "http://example.com");
        disabledInput.attr("name", "disabledName");
        disabledInput.attr("value", "val");
        disabledInput.attr("disabled", "disabled");
        form.addElement(disabledInput);

        // Empty name element
        Element emptyNameInput = new Element(Tag.valueOf("input"), "http://example.com");
        emptyNameInput.attr("name", "");
        emptyNameInput.attr("value", "val");
        form.addElement(emptyNameInput);

        // Valid input element
        Element validInput = new Element(Tag.valueOf("input"), "http://example.com");
        validInput.attr("name", "username");
        validInput.attr("value", "john");
        form.addElement(validInput);

        List<Connection.KeyVal> data = form.formData();
        assertEquals(1, data.size());
        assertEquals("username", data.get(0).key());
        assertEquals("john", data.get(0).value());
    }

    @Test
    public void testFormDataCheckboxAndRadio() throws Throwable {
        Tag tag = Tag.valueOf("form");
        FormElement form = new FormElement(tag, "http://example.com", new Attributes());

        // Checkbox unchecked (should be skipped)
        Element cbUnchecked = new Element(Tag.valueOf("input"), "http://example.com");
        cbUnchecked.attr("type", "checkbox");
        cbUnchecked.attr("name", "cb1");
        cbUnchecked.attr("value", "yes");
        form.addElement(cbUnchecked);

        // Checkbox checked without value (should default to "on")
        Element cbCheckedNoVal = new Element(Tag.valueOf("input"), "http://example.com");
        cbCheckedNoVal.attr("type", "checkbox");
        cbCheckedNoVal.attr("name", "cb2");
        cbCheckedNoVal.attr("checked", "");
        form.addElement(cbCheckedNoVal);

        // Radio checked with value
        Element radioChecked = new Element(Tag.valueOf("input"), "http://example.com");
        radioChecked.attr("type", "radio");
        radioChecked.attr("name", "rad");
        radioChecked.attr("value", "A");
        radioChecked.attr("checked", "checked");
        form.addElement(radioChecked);

        List<Connection.KeyVal> data = form.formData();
        assertEquals(2, data.size());
        assertEquals("cb2", data.get(0).key());
        assertEquals("on", data.get(0).value());
        assertEquals("rad", data.get(1).key());
        assertEquals("A", data.get(1).value());
    }

    @Test
    public void testFormDataSelectElement() throws Throwable {
        Tag tag = Tag.valueOf("form");
        FormElement form = new FormElement(tag, "http://example.com", new Attributes());

        // Select with selected option
        Element select = new Element(Tag.valueOf("select"), "http://example.com");
        select.attr("name", "sel1");

        Element opt1 = new Element(Tag.valueOf("option"), "http://example.com");
        opt1.attr("value", "1");
        select.appendChild(opt1);

        Element opt2 = new Element(Tag.valueOf("option"), "http://example.com");
        opt2.attr("value", "2");
        opt2.attr("selected", "");
        select.appendChild(opt2);

        form.addElement(select);

        List<Connection.KeyVal> data = form.formData();
        assertEquals(1, data.size());
        assertEquals("sel1", data.get(0).key());
        assertEquals("2", data.get(0).value());
    }

    @Test
    public void testFormDataSelectElementFallbackToFirst() throws Throwable {
        Tag tag = Tag.valueOf("form");
        FormElement form = new FormElement(tag, "http://example.com", new Attributes());

        // Select without any selected option, should fallback to first option
        Element select = new Element(Tag.valueOf("select"), "http://example.com");
        select.attr("name", "sel2");

        Element opt1 = new Element(Tag.valueOf("option"), "http://example.com");
        opt1.attr("value", "first");
        select.appendChild(opt1);

        Element opt2 = new Element(Tag.valueOf("option"), "http://example.com");
        opt2.attr("value", "second");
        select.appendChild(opt2);

        form.addElement(select);

        List<Connection.KeyVal> data = form.formData();
        assertEquals(1, data.size());
        assertEquals("sel2", data.get(0).key());
        assertEquals("first", data.get(0).value());
    }
}