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

import org.jsoup.Jsoup;
import org.jsoup.parser.Parser;

import java.io.IOException;

/**
 A comment node.

 @author Jonathan Hedley, jonathan@hedley.net */
public class Comment extends LeafNode {
    private static final String COMMENT_KEY = "comment";

    /**
     Create a new comment node.
     @param data The contents of the comment
     */
    public Comment(String data) {
        value = data;
    }

    /**
     Create a new comment node.
     @param data The contents of the comment
     @param baseUri base URI not used. This is a leaf node.
     @deprecated
     */
    public Comment(String data, String baseUri) {
        this(data);
    }

    public String nodeName() {
        return "#comment";
    }

    /**
     Get the contents of the comment.
     @return comment content
     */
    public String getData() {
        return coreValue();
    }

	void outerHtmlHead(Appendable accum, int depth, Document.OutputSettings out) throws IOException {
        if (out.prettyPrint())
            indent(accum, depth, out);
        accum
                .append("<!--")
                .append(getData())
                .append("-->");
    }

	void outerHtmlTail(Appendable accum, int depth, Document.OutputSettings out) {}

    @Override
    public String toString() {
        return outerHtml();
    }

    /**
     * Check if this comment looks like an XML Declaration.
     * @return true if it looks like, maybe, it's an XML Declaration.
     */
    public boolean isXmlDeclaration() {
        String data = getData();
        return (data.length() > 1 && (data.startsWith("!") || data.startsWith("?")));
    }

    /**
     * Attempt to cast this comment to an XML Declaration note.
     * @return an XML declaration if it could be parsed as one, null otherwise.
     */
    public XmlDeclaration asXmlDeclaration() {
        String data = getData();
        Document doc = Jsoup.parse("<" + data.substring(1, data.length() -1) + ">", baseUri(), Parser.xmlParser());
        XmlDeclaration decl = null;
        if (doc.childNodeSize() > 0) {
            Element el = doc.child(0);
            decl = new XmlDeclaration(NodeUtils.parser(doc).settings().normalizeTag(el.tagName()), data.startsWith("!"));
            decl.attributes().addAll(el.attributes());
        }
        return decl;
    }
}