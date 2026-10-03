package org.jsoup.nodes;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.parser.Tag;

import java.util.List;

import org.junit.Test;
import static org.junit.Assert.*;

public class FormElementClaudeTest {

    // constructor: creates instance with given baseUri and empty elements list
    @Test
    public void testConstructor_createsFormElementWithGivenTagAndBaseUri() throws Throwable {
        FormElement form = new FormElement(Tag.valueOf("form"), "http://example.com/", new Attributes());
        assertEquals("http://example.com/", form.baseUri());
        assertEquals(0, form.elements().size());
    }

    // elements(): new form has no associated controls
    @Test
    public void testElements_newForm_isEmpty() throws Throwable {
        Document doc = Jsoup.parse("<form></form>");
        FormElement form = (FormElement) doc.select("form").first();
        assertEquals(0, form.elements().size());
    }

    // elements(): reflects control added via addElement
    @Test
    public void testElements_reflectsElementsAddedViaAddElement() throws Throwable {
        Document doc = Jsoup.parse("<form></form>");
        FormElement form = (FormElement) doc.select("form").first();
        Element div = Jsoup.parse("<div></div>").select("div").first();
        form.addElement(div);
        assertEquals(1, form.elements().size());
        assertSame(div, form.elements().get(0));
    }

    // addElement(): returns the same FormElement instance for chaining
    @Test
    public void testAddElement_returnsSameInstanceForChaining() throws Throwable {
        Document doc = Jsoup.parse("<form></form>");
        FormElement form = (FormElement) doc.select("form").first();
        Element div = Jsoup.parse("<div></div>").select("div").first();
        FormElement returned = form.addElement(div);
        assertSame(form, returned);
    }

    // addElement(): multiple elements preserved in insertion order
    @Test
    public void testAddElement_addsMultipleElementsInOrder() throws Throwable {
        Document doc = Jsoup.parse("<form></form>");
        FormElement form = (FormElement) doc.select("form").first();
        Element e1 = Jsoup.parse("<div></div>").select("div").first();
        Element e2 = Jsoup.parse("<span></span>").select("span").first();
        form.addElement(e1);
        form.addElement(e2);
        assertEquals(2, form.elements().size());
        assertSame(e1, form.elements().get(0));
        assertSame(e2, form.elements().get(1));
    }

    // removeChild(): removes an associated control from elements() list
    @Test
    public void testRemoveChild_removesElementFromElementsList() throws Throwable {
        Document doc = Jsoup.parse("<form><input type='text' name='q'/></form>");
        FormElement form = (FormElement) doc.select("form").first();
        assertEquals(1, form.elements().size());
        Element input = form.elements().get(0);
        form.removeChild(input);
        assertEquals(0, form.elements().size());
    }

    // submit(): empty action attribute and empty baseUri -> IllegalArgumentException per Javadoc
    @Test
    public void testSubmit_emptyActionAndEmptyBaseUri_throwsIllegalArgumentException() throws Throwable {
        FormElement form = new FormElement(Tag.valueOf("form"), "", new Attributes());
        try {
            form.submit();
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) { }
    }

    // submit(): absolute action attribute present -> connection is built successfully
    @Test
    public void testSubmit_withAbsoluteActionAttribute_returnsConnection() throws Throwable {
        Document doc = Jsoup.parse("<form action='http://example.com/submit'></form>");
        FormElement form = (FormElement) doc.select("form").first();
        Connection connection = form.submit();
        assertNotNull(connection);
    }

    // submit(): method=post attribute -> POST branch of ternary taken, connection still built
    @Test
    public void testSubmit_methodPostAttribute_returnsConnection() throws Throwable {
        Document doc = Jsoup.parse("<form action='http://example.com/submit' method='post'></form>");
        FormElement form = (FormElement) doc.select("form").first();
        Connection connection = form.submit();
        assertNotNull(connection);
    }

    // submit(): method attribute missing -> defaults to GET branch, connection still built
    @Test
    public void testSubmit_methodMissingDefaultsToGet_returnsConnection() throws Throwable {
        Document doc = Jsoup.parse("<form action='http://example.com/submit'></form>");
        FormElement form = (FormElement) doc.select("form").first();
        Connection connection = form.submit();
        assertNotNull(connection);
    }

    // formData(): no controls associated -> empty list (0 loop iterations)
    @Test
    public void testFormData_noElements_returnsEmptyList() throws Throwable {
        Document doc = Jsoup.parse("<form></form>");
        FormElement form = (FormElement) doc.select("form").first();
        List<Connection.KeyVal> data = form.formData();
        assertEquals(0, data.size());
    }

    // formData(): element whose tag is not form submittable is skipped
    @Test
    public void testFormData_nonSubmittableElement_isExcluded() throws Throwable {
        Document doc = Jsoup.parse("<form></form>");
        FormElement form = (FormElement) doc.select("form").first();
        Element div = Jsoup.parse("<div name='x'>text</div>").select("div").first();
        form.addElement(div);
        assertEquals(0, form.formData().size());
    }

    // formData(): element with disabled attribute is skipped
    @Test
    public void testFormData_disabledElement_isExcluded() throws Throwable {
        Document doc = Jsoup.parse("<form></form>");
        FormElement form = (FormElement) doc.select("form").first();
        Element input = Jsoup.parse("<input type='text' name='a' value='v' disabled>").select("input").first();
        form.addElement(input);
        assertEquals(0, form.formData().size());
    }

    // formData(): element with empty name attribute is skipped
    @Test
    public void testFormData_elementWithoutName_isExcluded() throws Throwable {
        Document doc = Jsoup.parse("<form></form>");
        FormElement form = (FormElement) doc.select("form").first();
        Element input = Jsoup.parse("<input type='text' value='v'>").select("input").first();
        form.addElement(input);
        assertEquals(0, form.formData().size());
    }

    // formData(): plain text input with name is included (else branch)
    @Test
    public void testFormData_textInput_isIncluded() throws Throwable {
        Document doc = Jsoup.parse("<form></form>");
        FormElement form = (FormElement) doc.select("form").first();
        Element input = Jsoup.parse("<input type='text' name='q' value='v'>").select("input").first();
        form.addElement(input);
        assertEquals(1, form.formData().size());
    }

    // formData(): checked checkbox is included
    @Test
    public void testFormData_checkboxChecked_isIncluded() throws Throwable {
        Document doc = Jsoup.parse("<form></form>");
        FormElement form = (FormElement) doc.select("form").first();
        Element box = Jsoup.parse("<input type='checkbox' name='c' checked>").select("input").first();
        form.addElement(box);
        assertEquals(1, form.formData().size());
    }

    // formData(): unchecked checkbox is excluded
    @Test
    public void testFormData_checkboxUnchecked_isExcluded() throws Throwable {
        Document doc = Jsoup.parse("<form></form>");
        FormElement form = (FormElement) doc.select("form").first();
        Element box = Jsoup.parse("<input type='checkbox' name='c'>").select("input").first();
        form.addElement(box);
        assertEquals(0, form.formData().size());
    }

    // formData(): checked radio is included
    @Test
    public void testFormData_radioChecked_isIncluded() throws Throwable {
        Document doc = Jsoup.parse("<form></form>");
        FormElement form = (FormElement) doc.select("form").first();
        Element radio = Jsoup.parse("<input type='radio' name='r' value='yes' checked>").select("input").first();
        form.addElement(radio);
        assertEquals(1, form.formData().size());
    }

    // formData(): unchecked radio is excluded
    @Test
    public void testFormData_radioUnchecked_isExcluded() throws Throwable {
        Document doc = Jsoup.parse("<form></form>");
        FormElement form = (FormElement) doc.select("form").first();
        Element radio = Jsoup.parse("<input type='radio' name='r' value='yes'>").select("input").first();
        form.addElement(radio);
        assertEquals(0, form.formData().size());
    }

    // formData(): single select with an explicitly selected option includes that one entry
    @Test
    public void testFormData_selectSingleWithSelectedOption_includesOneEntry() throws Throwable {
        Document doc = Jsoup.parse("<form></form>");
        FormElement form = (FormElement) doc.select("form").first();
        Element select = Jsoup.parse("<select name='s'><option value='a'>A</option>"
                + "<option value='b' selected>B</option></select>").select("select").first();
        form.addElement(select);
        assertEquals(1, form.formData().size());
    }

    // formData(): single (non-multiple) select with no selection defaults to its first option per HTML spec
    @Test
    public void testFormData_selectSingleNoSelection_defaultsToFirstOption() throws Throwable {
        Document doc = Jsoup.parse("<form></form>");
        FormElement form = (FormElement) doc.select("form").first();
        Element select = Jsoup.parse("<select name='s'><option value='a'>A</option>"
                + "<option value='b'>B</option></select>").select("select").first();
        form.addElement(select);
        assertEquals(1, form.formData().size());
    }



    // formData(): multiple select with several selected options includes each selected one
    @Test
    public void testFormData_selectMultipleWithMultipleSelected_includesAllEntries() throws Throwable {
        Document doc = Jsoup.parse("<form></form>");
        FormElement form = (FormElement) doc.select("form").first();
        Element select = Jsoup.parse("<select name='s' multiple><option value='a' selected>A</option>"
                + "<option value='b' selected>B</option><option value='c'>C</option></select>")
                .select("select").first();
        form.addElement(select);
        assertEquals(2, form.formData().size());
    }

    // formData(): select with no option children at all contributes no value
    @Test
    public void testFormData_selectWithNoOptions_excludesEntry() throws Throwable {
        Document doc = Jsoup.parse("<form></form>");
        FormElement form = (FormElement) doc.select("form").first();
        Element select = Jsoup.parse("<select name='s'></select>").select("select").first();
        form.addElement(select);
        assertEquals(0, form.formData().size());
    }

    // formData(): mixed controls over several loop iterations - only eligible ones are counted
    @Test
    public void testFormData_mixedElements_countsOnlyIncludedOnes() throws Throwable {
        Document doc = Jsoup.parse("<form></form>");
        FormElement form = (FormElement) doc.select("form").first();
        Element disabledInput = Jsoup.parse("<input type='text' name='a' value='x' disabled>").select("input").first();
        Element noNameInput = Jsoup.parse("<input type='text' value='y'>").select("input").first();
        Element textInput = Jsoup.parse("<input type='text' name='b' value='z'>").select("input").first();
        Element checkbox = Jsoup.parse("<input type='checkbox' name='c' checked>").select("input").first();
        Element select = Jsoup.parse("<select name='d'><option value='1'>One</option></select>").select("select").first();
        form.addElement(disabledInput);
        form.addElement(noNameInput);
        form.addElement(textInput);
        form.addElement(checkbox);
        form.addElement(select);
        assertEquals(3, form.formData().size());
    }
}
