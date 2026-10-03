package org.jsoup.nodes;

import org.jsoup.Connection;
import org.jsoup.helper.HttpConnection;
import org.jsoup.parser.Tag;
import org.jsoup.select.Elements;

import java.util.List;

import org.junit.Test;
import static org.junit.Assert.*;

public class FormElementTest {

    @Test
    public void testConstructorAndElements() throws Throwable {
        Tag tag = Tag.valueOf("form");
        Attributes attributes = new Attributes();
        FormElement form = new FormElement(tag, "http://example.com", attributes);
        
        assertNotNull(form.elements());
        assertEquals(0, form.elements().size());
        
        Element el = new Element(Tag.valueOf("input"), "http://example.com");
        FormElement added = form.addElement(el);
        
        assertSame(form, added);
        assertEquals(1, form.elements().size());
        assertSame(el, form.elements().get(0));
    }

    @Test
    public void testSubmitMissingActionNoBaseUri() throws Throwable {
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
    public void testSubmitGetAndPost() throws Throwable {
        Tag tag = Tag.valueOf("form");
        Attributes attributes = new Attributes();
        attributes.put("action", "http://example.com/submit");
        attributes.put("method", "POST");
        
        FormElement form = new FormElement(tag, "http://example.com", attributes);
        Connection con = form.submit();
        
        assertNotNull(con);
        assertEquals(Connection.Method.POST, con.request().method());
        assertEquals("http://example.com/submit", con.request().url().toString());

        attributes.put("method", "get");
        Connection conGet = form.submit();
        assertNotNull(conGet);
        assertEquals(Connection.Method.GET, conGet.request().method());
    }

    @Test
    public void testSubmitActionAbsoluteVsRelative() throws Throwable {
        Tag tag = Tag.valueOf("form");
        Attributes attributes = new Attributes();
        attributes.put("action", "/relative");
        
        FormElement form = new FormElement(tag, "http://example.com/base/", attributes);
        Connection con = form.submit();
        
        assertNotNull(con);
        assertEquals("http://example.com/relative", con.request().url().toString());
    }

    @Test
    public void testFormDataSubmittableAndNonSubmittable() throws Throwable {
        Tag tag = Tag.valueOf("form");
        FormElement form = new FormElement(tag, "http://example.com", new Attributes());
        
        // Non-submittable element (e.g. div)
        Element div = new Element(Tag.valueOf("div"), "http://example.com");
        div.attr("name", "divName");
        div.attr("value", "divVal");
        form.addElement(div);
        
        // Submittable element without name
        Element unnamedInput = new Element(Tag.valueOf("input"), "http://example.com");
        unnamedInput.attr("value", "val");
        form.addElement(unnamedInput);
        
        // Submittable valid input
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
    public void testFormDataSelectWithOptions() throws Throwable {
        Tag tag = Tag.valueOf("form");
        FormElement form = new FormElement(tag, "http://example.com", new Attributes());
        
        Element select = new Element(Tag.valueOf("select"), "http://example.com");
        select.attr("name", "dropdown");
        
        Element opt1 = new Element(Tag.valueOf("option"), "http://example.com");
        opt1.attr("value", "1");
        Element opt2 = new Element(Tag.valueOf("option"), "http://example.com");
        opt2.attr("value", "2");
        opt2.attr("selected", "selected");
        
        select.appendChild(opt1);
        select.appendChild(opt2);
        form.addElement(select);
        
        List<Connection.KeyVal> data = form.formData();
        assertEquals(1, data.size());
        assertEquals("dropdown", data.get(0).key());
        assertEquals("2", data.get(0).value());
    }

    @Test
    public void testFormDataSelectNoSelectedOption() throws Throwable {
        Tag tag = Tag.valueOf("form");
        FormElement form = new FormElement(tag, "http://example.com", new Attributes());
        
        Element select = new Element(Tag.valueOf("select"), "http://example.com");
        select.attr("name", "dropdown");
        
        Element opt1 = new Element(Tag.valueOf("option"), "http://example.com");
        opt1.attr("value", "first");
        Element opt2 = new Element(Tag.valueOf("option"), "http://example.com");
        opt2.attr("value", "second");
        
        select.appendChild(opt1);
        select.appendChild(opt2);
        form.addElement(select);
        
        List<Connection.KeyVal> data = form.formData();
        assertEquals(1, data.size());
        assertEquals("dropdown", data.get(0).key());
        assertEquals("first", data.get(0).value());
    }

    @Test
    public void testFormDataSelectEmptyOptions() throws Throwable {
        Tag tag = Tag.valueOf("form");
        FormElement form = new FormElement(tag, "http://example.com", new Attributes());
        
        Element select = new Element(Tag.valueOf("select"), "http://example.com");
        select.attr("name", "dropdown");
        form.addElement(select);
        
        List<Connection.KeyVal> data = form.formData();
        assertEquals(0, data.size());
    }

    @Test
    public void testFormDataCheckboxAndRadio() throws Throwable {
        Tag tag = Tag.valueOf("form");
        FormElement form = new FormElement(tag, "http://example.com", new Attributes());
        
        // Unchecked checkbox (should be ignored)
        Element cb1 = new Element(Tag.valueOf("input"), "http://example.com");
        cb1.attr("type", "checkbox");
        cb1.attr("name", "cb1");
        cb1.attr("value", "yes");
        form.addElement(cb1);
        
        // Checked checkbox (should be included)
        Element cb2 = new Element(Tag.valueOf("input"), "http://example.com");
        cb2.attr("type", "checkbox");
        cb2.attr("name", "cb2");
        cb2.attr("value", "yes");
        cb2.attr("checked", "");
        form.addElement(cb2);
        
        // Unchecked radio (should be ignored)
        Element rd1 = new Element(Tag.valueOf("input"), "http://example.com");
        rd1.attr("type", "radio");
        rd1.attr("name", "rd");
        rd1.attr("value", "r1");
        form.addElement(rd1);
        
        // Checked radio (should be included)
        Element rd2 = new Element(Tag.valueOf("input"), "http://example.com");
        rd2.attr("type", "radio");
        rd2.attr("name", "rd");
        rd2.attr("value", "r2");
        rd2.attr("checked", "checked");
        form.addElement(rd2);
        
        List<Connection.KeyVal> data = form.formData();
        assertEquals(2, data.size());
        assertEquals("cb2", data.get(0).key());
        assertEquals("yes", data.get(0).value());
        assertEquals("rd", data.get(1).key());
        assertEquals("r2", data.get(1).value());
    }

    @Test
    public void testEqualsOverride() throws Throwable {
        Tag tag = Tag.valueOf("form");
        FormElement form1 = new FormElement(tag, "http://example.com", new Attributes());
        FormElement form2 = new FormElement(tag, "http://example.com", new Attributes());
        Element el = new Element(tag, "http://example.com", new Attributes());

        assertTrue(form1.equals(form1));
        assertFalse(form1.equals(null));
        assertFalse(form1.equals(el));
    }
}