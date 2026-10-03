package org.jsoup.nodes;

import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.jsoup.parser.Tag;
import org.junit.Test;
import static org.junit.Assert.*;

import java.util.List;

public class FormElementClaudeTest {

    private FormElement parseForm(String html, String baseUri) {
        Document doc = Jsoup.parse(html, baseUri);
        return (FormElement) doc.select("form").first();
    }

    // Constructor: standalone FormElement starts with no associated elements
    @Test
    public void testConstructor_standalone_elementsEmptyInitially() throws Throwable {
        FormElement form = new FormElement(Tag.valueOf("form"), "http://example.com/", new Attributes());
        assertEquals(0, form.elements().size());
    }

    // elements(): returns the same underlying list instance on repeated calls
    @Test
    public void testElements_returnsSameReferenceAcrossCalls() throws Throwable {
        FormElement form = new FormElement(Tag.valueOf("form"), "", new Attributes());
        Element e1 = new Element(Tag.valueOf("input"), "");
        form.addElement(e1);
        assertSame(form.elements(), form.elements());
    }

    // elements(): after parsing, all form-associated control tags are present
    @Test
    public void testElements_afterParsingForm_returnsAllFormControls() throws Throwable {
        String html = "<form><input id=1><select id=2><option value='a'></option></select>" +
                "<textarea id=3></textarea><button id=4></button></form>";
        FormElement form = parseForm(html, "http://example.com/");
        assertEquals(4, form.elements().size());
    }

    // addElement(): return value is the same form instance for chaining
    @Test
    public void testAddElement_returnsThisForChaining() throws Throwable {
        FormElement form = new FormElement(Tag.valueOf("form"), "", new Attributes());
        Element e1 = new Element(Tag.valueOf("input"), "");
        FormElement returned = form.addElement(e1);
        assertSame(form, returned);
    }

    // addElement(): single element is added to elements()
    @Test
    public void testAddElement_addsToElementsList() throws Throwable {
        FormElement form = new FormElement(Tag.valueOf("form"), "", new Attributes());
        Element e1 = new Element(Tag.valueOf("input"), "");
        form.addElement(e1);
        assertEquals(1, form.elements().size());
        assertSame(e1, form.elements().get(0));
    }

    // addElement(): multiple calls accumulate elements and preserve insertion order
    @Test
    public void testAddElement_multipleElements_maintainsOrder() throws Throwable {
        FormElement form = new FormElement(Tag.valueOf("form"), "", new Attributes());
        Element e1 = new Element(Tag.valueOf("input"), "");
        Element e2 = new Element(Tag.valueOf("input"), "");
        form.addElement(e1);
        form.addElement(e2);
        assertEquals(2, form.elements().size());
        assertSame(e1, form.elements().get(0));
        assertSame(e2, form.elements().get(1));
    }

    // submit(): relative action attribute resolved against base URI
    @Test
    public void testSubmit_relativeAction_resolvesAgainstBaseUri() throws Throwable {
        FormElement form = parseForm("<form action='/search'><input name='q'></form>", "http://example.com/");
        Connection con = form.submit();
        assertEquals("http://example.com/search", con.request().url().toExternalForm());
    }

    // submit(): absolute action attribute used as-is
    @Test
    public void testSubmit_absoluteAction_usesGivenAbsoluteUrl() throws Throwable {
        FormElement form = parseForm("<form action='http://example.com/search'></form>", "http://example.com/");
        Connection con = form.submit();
        assertEquals("http://example.com/search", con.request().url().toExternalForm());
    }

    // submit(): no action attribute falls back to the document base URI
    @Test
    public void testSubmit_noActionAttribute_fallsBackToBaseUri() throws Throwable {
        FormElement form = parseForm("<form><input name='q'></form>", "http://example.com/");
        Connection con = form.submit();
        assertEquals("http://example.com/", con.request().url().toExternalForm());
    }

    // submit(): no action and no base URI must throw IllegalArgumentException
    @Test
    public void testSubmit_noActionAndNoBaseUri_throwsIllegalArgumentException() throws Throwable {
        Document doc = Jsoup.parse("<form><input name='q'></form>");
        FormElement form = (FormElement) doc.select("form").first();
        try {
            form.submit();
            fail("expected IllegalArgumentException");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("base URI"));
        }
    }

    // submit(): method="post" sets the connection method to POST
    @Test
    public void testSubmit_methodPost_setsPostMethod() throws Throwable {
        FormElement form = parseForm("<form method='post' action='/x'></form>", "http://example.com/");
        Connection con = form.submit();
        assertEquals(Connection.Method.POST, con.request().method());
    }

    // submit(): mixed-case method value is still recognised as POST (case-insensitive)
    @Test
    public void testSubmit_methodMixedCasePost_usesPostMethod() throws Throwable {
        FormElement form = parseForm("<form method='PoSt' action='/x'></form>", "http://example.com/");
        Connection con = form.submit();
        assertEquals(Connection.Method.POST, con.request().method());
    }

    // submit(): missing method attribute defaults to GET
    @Test
    public void testSubmit_methodAbsent_defaultsToGet() throws Throwable {
        FormElement form = parseForm("<form action='/x'></form>", "http://example.com/");
        Connection con = form.submit();
        assertEquals(Connection.Method.GET, con.request().method());
    }

    // submit(): explicit method="get" uses GET
    @Test
    public void testSubmit_methodGetExplicit_usesGetMethod() throws Throwable {
        FormElement form = parseForm("<form method='get' action='/x'></form>", "http://example.com/");
        Connection con = form.submit();
        assertEquals(Connection.Method.GET, con.request().method());
    }

    // submit(): connection data reflects the form's field values
    @Test
    public void testSubmit_dataContainsFormFieldValues() throws Throwable {
        FormElement form = parseForm("<form action='/x'><input name='q' value='jsoup'></form>", "http://example.com/");
        Connection con = form.submit();
        assertEquals(1, con.request().data().size());
        Connection.KeyVal kv = con.request().data().iterator().next();
        assertEquals("q", kv.key());
        assertEquals("jsoup", kv.value());
    }

    // formData(): a form with no controls returns an empty list
    @Test
    public void testFormData_emptyForm_returnsEmptyList() throws Throwable {
        FormElement form = parseForm("<form action='/x'></form>", "http://example.com/");
        assertEquals(0, form.formData().size());
    }

    // formData(): simple text input contributes its name/value pair
    @Test
    public void testFormData_simpleTextInput_includesNameAndValue() throws Throwable {
        FormElement form = parseForm("<form><input name='q' value='jsoup'></form>", "http://example.com/");
        List<Connection.KeyVal> data = form.formData();
        assertEquals(1, data.size());
        assertEquals("q", data.get(0).key());
        assertEquals("jsoup", data.get(0).value());
    }

    // formData(): input without a name attribute is skipped
    @Test
    public void testFormData_inputWithoutName_excluded() throws Throwable {
        FormElement form = parseForm("<form><input value='jsoup'></form>", "http://example.com/");
        assertEquals(0, form.formData().size());
    }

    // formData(): disabled input is skipped
    @Test
    public void testFormData_disabledInput_excluded() throws Throwable {
        FormElement form = parseForm("<form><input name='q' value='jsoup' disabled></form>", "http://example.com/");
        assertEquals(0, form.formData().size());
    }

    // formData(): checked checkbox with a value uses that value
    @Test
    public void testFormData_checkboxCheckedWithValue_includesValue() throws Throwable {
        FormElement form = parseForm("<form><input type='checkbox' name='agree' checked value='yes'></form>", "http://example.com/");
        List<Connection.KeyVal> data = form.formData();
        assertEquals(1, data.size());
        assertEquals("yes", data.get(0).value());
    }

    // formData(): checked checkbox without a value defaults to "on"
    @Test
    public void testFormData_checkboxCheckedWithoutValue_defaultsToOn() throws Throwable {
        FormElement form = parseForm("<form><input type='checkbox' name='agree' checked></form>", "http://example.com/");
        List<Connection.KeyVal> data = form.formData();
        assertEquals(1, data.size());
        assertEquals("on", data.get(0).value());
    }

    // formData(): unchecked checkbox is excluded
    @Test
    public void testFormData_checkboxUnchecked_excluded() throws Throwable {
        FormElement form = parseForm("<form><input type='checkbox' name='agree'></form>", "http://example.com/");
        assertEquals(0, form.formData().size());
    }

    // formData(): checked radio button included with its value
    @Test
    public void testFormData_radioChecked_included() throws Throwable {
        FormElement form = parseForm("<form><input type='radio' name='color' value='red' checked></form>", "http://example.com/");
        List<Connection.KeyVal> data = form.formData();
        assertEquals(1, data.size());
        assertEquals("red", data.get(0).value());
    }

    // formData(): unchecked radio button is excluded
    @Test
    public void testFormData_radioUnchecked_excluded() throws Throwable {
        FormElement form = parseForm("<form><input type='radio' name='color' value='red'></form>", "http://example.com/");
        assertEquals(0, form.formData().size());
    }

    // formData(): select with a single selected option uses that option's value
    @Test
    public void testFormData_selectSingleSelectedOption_includesSelectedValue() throws Throwable {
        String html = "<form><select name='colors'><option value='red' selected>Red</option>" +
                "<option value='blue'>Blue</option></select></form>";
        FormElement form = parseForm(html, "http://example.com/");
        List<Connection.KeyVal> data = form.formData();
        assertEquals(1, data.size());
        assertEquals("red", data.get(0).value());
    }

    // formData(): multi-select with several selected options includes them all, in order
    @Test
    public void testFormData_selectMultipleSelectedOptions_includesAllSelected() throws Throwable {
        String html = "<form><select name='colors' multiple><option value='red' selected>Red</option>" +
                "<option value='blue' selected>Blue</option><option value='green'>Green</option></select></form>";
        FormElement form = parseForm(html, "http://example.com/");
        List<Connection.KeyVal> data = form.formData();
        assertEquals(2, data.size());
        assertEquals("red", data.get(0).value());
        assertEquals("blue", data.get(1).value());
    }

    // formData(): select with no option selected defaults to the first option
    @Test
    public void testFormData_selectNoneSelected_defaultsToFirstOption() throws Throwable {
        String html = "<form><select name='colors'><option value='red'>Red</option>" +
                "<option value='blue'>Blue</option></select></form>";
        FormElement form = parseForm(html, "http://example.com/");
        List<Connection.KeyVal> data = form.formData();
        assertEquals(1, data.size());
        assertEquals("red", data.get(0).value());
    }

    // formData(): select with no options at all adds nothing
    @Test
    public void testFormData_selectNoOptions_noEntryAdded() throws Throwable {
        String html = "<form><select name='colors'></select></form>";
        FormElement form = parseForm(html, "http://example.com/");
        assertEquals(0, form.formData().size());
    }

    // formData(): textarea contributes its text content as the value
    @Test
    public void testFormData_textareaIncluded() throws Throwable {
        String html = "<form><textarea name='comment'>Hello World</textarea></form>";
        FormElement form = parseForm(html, "http://example.com/");
        List<Connection.KeyVal> data = form.formData();
        assertEquals(1, data.size());
        assertEquals("Hello World", data.get(0).value());
    }

    // formData(): hidden input is included like a regular field
    @Test
    public void testFormData_hiddenInputIncluded() throws Throwable {
        String html = "<form><input type='hidden' name='token' value='abc123'></form>";
        FormElement form = parseForm(html, "http://example.com/");
        List<Connection.KeyVal> data = form.formData();
        assertEquals(1, data.size());
        assertEquals("abc123", data.get(0).value());
    }

    // formData(): elements whose tag is not form-submittable are skipped
    @Test
    public void testFormData_nonFormSubmittableElement_excluded() throws Throwable {
        FormElement form = new FormElement(Tag.valueOf("form"), "http://example.com/", new Attributes());
        Element div = new Element(Tag.valueOf("div"), "http://example.com/");
        div.attr("name", "test");
        form.addElement(div);
        assertEquals(0, form.formData().size());
    }

    // formData(): the returned list is a copy; mutating it does not affect subsequent calls
    @Test
    public void testFormData_returnedListIsCopy_modifyingDoesNotAffectForm() throws Throwable {
        FormElement form = parseForm("<form><input name='q' value='jsoup'></form>", "http://example.com/");
        List<Connection.KeyVal> data1 = form.formData();
        data1.clear();
        List<Connection.KeyVal> data2 = form.formData();
        assertEquals(1, data2.size());
    }
}
