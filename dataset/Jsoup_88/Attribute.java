ว่าคลาสมี Constructor อะไรบ้าง
   - ❌ ห้ามส่ง `null` ให้ parameter ที่เป็น primitive type (`int`, `double`, `boolean`, `char`, `long`) เด็ดขาด เพราะ javac จะฟ้อง type incompatible
   - ❌ ห้ามเรียกใช้ `private` method / constructor หรือ private field หรือ private inner class (เช่น `Assign` ใน `RemoveUnusedVars`) โดยตรง
   - ❌ ห้ามใช้ `assertEquals(null, val)` ให้ใช้ `assertNull(val)` แทนเสมอ
   - ❌ ห้ามใช้ `assertEquals(Double.NaN, val)` เพราะ ambiguous ใน JUnit 4 ให้ใช้ `assertEquals(Double.valueOf(Double.NaN), val)` หรือ `assertTrue(Double.isNaN(val))`
5. Full Imports Required:
   - ต้อง Import ทุกคลาสที่เรียกใช้อย่างครบถ้วน ไม่ละเลย Utility หรือ Annotation package (เช่น `com.fasterxml.jackson.databind.util.ClassUtil`, `com.fasterxml.jackson.annotation.*`, `org.jsoup.parser.*`, `org.jsoup.nodes.*`)

[TEST DESIGN & FAULT DETECTION DIRECTIVES (เพิ่มโอกาส REVEALING และลด INCONCLUSIVE)]
1. Oracle Precision (ลด Inconclusive จากการ Assert เปราะบาง):
   - ❌ อย่า Assert ข้อความ Exception หรือ toString() แบบ Exact Match (เพราะข้อความอาจเปลี่ยนตามเวอร์ชัน) ให้เน้น Assert ชนิดของ Exception หรือใช้ `assertTrue(e.getMessage().contains(...))` แทน
   - สำหรับกรณีทดสอบที่คาดว่าจะเกิด Exception ให้ใช้โครงสร้าง `try { method(); fail("Should throw exception"); } catch (SpecificException e) { /* Expected */ }` หรือ `@Test(expected = SpecificException.class)`
2. Bug-Hunting Edge Cases (กระตุ้นจุดบกพร่องที่ซ่อนอยู่):
   - ค่าขอบเขตวิกฤต: ค่าศูนย์ (0), ค่าลบ (-1), Maximum/Minimum values, Integer overflow/underflow
   - ปัญหา Null Safety: ส่ง `null` เข้าไปยัง Public method ที่รับ Object เพื่อดูว่ามี NullPointerException โดยไม่ได้จัดการหรือไม่
   - กรณี Collection/Array: ทดสอบ Empty, Single element, Duplicate elements, Null elements, Unmodifiable collection
   - กรณี String: Empty string `""`, Whitespace only `"   "`, String containing delimiters, Escape sequences, Special Unicode characters
   - Boundary & Loop conditions: จุดเปลี่ยนเงื่อนไข `<` vs `<=`, Off-by-one errors, Empty input streams

[TARGET SOURCE CODE]
package org.jsoup.nodes;

import org.jsoup.SerializationException;
import org.jsoup.internal.StringUtil;
import org.jsoup.helper.Validate;

import java.io.IOException;
import java.util.Arrays;
import java.util.Map;

/**
 A single key + value attribute. (Only used for presentation.)
 */
public class Attribute implements Map.Entry<String, String>, Cloneable  {
    private static final String[] booleanAttributes = {
            "allowfullscreen", "async", "autofocus", "checked", "compact", "declare", "default", "defer", "disabled",
            "formnovalidate", "hidden", "inert", "ismap", "itemscope", "multiple", "muted", "nohref", "noresize",
            "noshade", "novalidate", "nowrap", "open", "readonly", "required", "reversed", "seamless", "selected",
            "sortable", "truespeed", "typemustmatch"
    };

    private String key;
    private String val;
    Attributes parent; // used to update the holding Attributes when the key / value is changed via this interface

    /**
     * Create a new attribute from unencoded (raw) key and value.
     * @param key attribute key; case is preserved.
     * @param value attribute value
     * @see #createFromEncoded
     */
    public Attribute(String key, String value) {
        this(key, value, null);
    }

    /**
     * Create a new attribute from unencoded (raw) key and value.
     * @param key attribute key; case is preserved.
     * @param val attribute value
     * @param parent the containing Attributes (this Attribute is not automatically added to said Attributes)
     * @see #createFromEncoded*/
    public Attribute(String key, String val, Attributes parent) {
        Validate.notNull(key);
        key = key.trim();
        Validate.notEmpty(key); // trimming could potentially make empty, so validate here
        this.key = key;
        this.val = val;
        this.parent = parent;
    }

    /**
     Get the attribute key.
     @return the attribute key
     */
    public String getKey() {
        return key;
    }

    /**
     Set the attribute key; case is preserved.
     @param key the new key; must not be null
     */
    public void setKey(String key) {
        Validate.notNull(key);
        key = key.trim();
        Validate.notEmpty(key); // trimming could potentially make empty, so validate here
        if (parent != null) {
            int i = parent.indexOfKey(this.key);
            if (i != Attributes.NotFound)
                parent.keys[i] = key;
        }
        this.key = key;
    }

    /**
     Get the attribute value.
     @return the attribute value
     */
    public String getValue() {
        return val;
    }

    /**
     Set the attribute value.
     @param val the new attribute value; must not be null
     */
    public String setValue(String val) {
        String oldVal = parent.get(this.key);
        if (parent != null) {
            int i = parent.indexOfKey(this.key);
            if (i != Attributes.NotFound)
                parent.vals[i] = val;
        }
        this.val = val;
        return oldVal;
    }

    /**
     Get the HTML representation of this attribute; e.g. {@code href="index.html"}.
     @return HTML
     */
    public String html() {
        StringBuilder sb = StringUtil.borrowBuilder();
        
        try {
        	html(sb, (new Document("")).outputSettings());
        } catch(IOException exception) {
        	throw new SerializationException(exception);
        }
        return StringUtil.releaseBuilder(sb);
    }

    protected static void html(String key, String val, Appendable accum, Document.OutputSettings out) throws IOException {
        accum.append(key);
        if (!shouldCollapseAttribute(key, val, out)) {
            accum.append("=\"");
            Entities.escape(accum, Attributes.checkNotNull(val) , out, true, false, false);
            accum.append('"');
        }
    }
    
    protected void html(Appendable accum, Document.OutputSettings out) throws IOException {
        html(key, val, accum, out);
    }

    /**
     Get the string representation of this attribute, implemented as {@link #html()}.
     @return string
     */
    @Override
    public String toString() {
        return html();
    }

    /**
     * Create a new Attribute from an unencoded key and a HTML attribute encoded value.
     * @param unencodedKey assumes the key is not encoded, as can be only run of simple \w chars.
     * @param encodedValue HTML attribute encoded value
     * @return attribute
     */
    public static Attribute createFromEncoded(String unencodedKey, String encodedValue) {
        String value = Entities.unescape(encodedValue, true);
        return new Attribute(unencodedKey, value, null); // parent will get set when Put
    }

    protected boolean isDataAttribute() {
        return isDataAttribute(key);
    }

    protected static boolean isDataAttribute(String key) {
        return key.startsWith(Attributes.dataPrefix) && key.length() > Attributes.dataPrefix.length();
    }

    /**
     * Collapsible if it's a boolean attribute and value is empty or same as name
     * 
     * @param out output settings
     * @return  Returns whether collapsible or not
     */
    protected final boolean shouldCollapseAttribute(Document.OutputSettings out) {
        return shouldCollapseAttribute(key, val, out);
    }

    protected static boolean shouldCollapseAttribute(final String key, final String val, final Document.OutputSettings out) {
        return (
            out.syntax() == Document.OutputSettings.Syntax.html &&
                (val == null || ("".equals(val) || val.equalsIgnoreCase(key)) && Attribute.isBooleanAttribute(key)));
    }

    /**
     * @deprecated
     */
    protected boolean isBooleanAttribute() {
        return Arrays.binarySearch(booleanAttributes, key) >= 0 || val == null;
    }

    /**
     * Checks if this attribute name is defined as a boolean attribute in HTML5
     */
    protected static boolean isBooleanAttribute(final String key) {
        return Arrays.binarySearch(booleanAttributes, key) >= 0;
    }

    @Override
    public boolean equals(Object o) { // note parent not considered
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Attribute attribute = (Attribute) o;
        if (key != null ? !key.equals(attribute.key) : attribute.key != null) return false;
        return val != null ? val.equals(attribute.val) : attribute.val == null;
    }

    @Override
    public int hashCode() { // note parent not considered
        int result = key != null ? key.hashCode() : 0;
        result = 31 * result + (val != null ? val.hashCode() : 0);
        return result;
    }

    @Override
    public Attribute clone() {
        try {
            return (Attribute) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }
}