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
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.commons.jxpath.ri;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.apache.commons.jxpath.Pointer;
import org.apache.commons.jxpath.ri.model.NodeIterator;
import org.apache.commons.jxpath.ri.model.NodePointer;

/**
 * Namespace resolver for JXPathContextReferenceImpl.
 *
 * @author Dmitri Plotnikov
 * @version $Revision$ $Date$
 */
public class NamespaceResolver implements Cloneable {
    final protected NamespaceResolver parent;
    protected HashMap namespaceMap = new HashMap();
    protected HashMap reverseMap;
    protected NodePointer pointer;
    private boolean sealed;

    /**
     * Find the namespace prefix for the specified namespace URI and NodePointer.
     * @param pointer
     * @param namespaceURI
     * @return prefix if found
     * @since JXPath 1.3
     */

    /**
     * Create a new NamespaceResolver.
     */
    public NamespaceResolver() {
        this(null);
    }

    /**
     * Create a new NamespaceResolver.
     * @param parent
     */
    public NamespaceResolver(NamespaceResolver parent) {
        this.parent = parent;
    }

    /**
     * Registers a namespace prefix.
     * 
     * @param prefix A namespace prefix
     * @param namespaceURI A URI for that prefix
     */
    public synchronized void registerNamespace(String prefix, String namespaceURI) {
        if (isSealed()) {
            throw new IllegalStateException(
                    "Cannot register namespaces on a sealed NamespaceResolver");
        }
        namespaceMap.put(prefix, namespaceURI);
        reverseMap = null;
    }
    
    /**
     * Register a namespace for the expression context.
     * @param pointer the Pointer to set.
     */
    public void setNamespaceContextPointer(NodePointer pointer) {
        this.pointer = pointer;
    }

    /**
     * Get the namespace context pointer.
     * @return Pointer
     */
    public Pointer getNamespaceContextPointer() {
        if (pointer == null && parent != null) {
            return parent.getNamespaceContextPointer();
        }
        return pointer;
    }
    
    /**
     * Given a prefix, returns a registered namespace URI. If the requested
     * prefix was not defined explicitly using the registerNamespace method,
     * JXPathContext will then check the context node to see if the prefix is
     * defined there. See
     * {@link #setNamespaceContextPointer(NodePointer) setNamespaceContextPointer}.
     * 
     * @param prefix The namespace prefix to look up
     * @return namespace URI or null if the prefix is undefined.
     */
    public synchronized String getNamespaceURI(String prefix) {

    /**
     * Given a prefix, returns an externally registered namespace URI.
     * 
     * @param prefix The namespace prefix to look up
     * @return namespace URI or null if the prefix is undefined.
     * @since JXPath 1.3
     */
        String uri = (String) namespaceMap.get(prefix);
        if (uri == null && pointer != null) {
            uri = pointer.getNamespaceURI(prefix);
        }
        if (uri == null && parent != null) {
            return parent.getNamespaceURI(prefix);
        }
        return uri;
    }

    /**
     * Get the prefix associated with the specifed namespace URI.
     * @param namespaceURI the ns URI to check.
     * @return String prefix
     */
    public synchronized String getPrefix(String namespaceURI) {

    /**
     * Get the nearest prefix found that matches an externally-registered namespace. 
     * @param namespaceURI
     * @return String prefix if found.
     * @since JXPath 1.3
     */
        if (reverseMap == null) {
            reverseMap = new HashMap();
            NodeIterator ni = pointer.namespaceIterator();
            if (ni != null) {
                for (int position = 1; ni.setPosition(position); position++) {
                    NodePointer nsPointer = ni.getNodePointer();
                    String uri = nsPointer.getNamespaceURI();                    
                    String prefix = nsPointer.getName().getName();
                    if (!"".equals(prefix)) {
                        reverseMap.put(uri, prefix);
                    }
                }
            }
            Iterator it = namespaceMap.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry entry = (Map.Entry) it.next();
                reverseMap.put(entry.getValue(), entry.getKey());
            }
        }
        String prefix = (String) reverseMap.get(namespaceURI);
        if (prefix == null && parent != null) {
            return parent.getPrefix(namespaceURI);
        }
        return prefix;
    }

    /**
     * Learn whether this NamespaceResolver has been sealed.
     * @return
     */
    public boolean isSealed() {
        return sealed;
    }

    /**
     * Seal this {@link NamespaceResolver}.
     */
    public void seal() {
        sealed = true;
        if (parent != null) {
            parent.seal();
        }
    }

    /**
     * {@inheritDoc}
     * @see java.lang.Object#clone()
     */
    public Object clone() {
        try {
            NamespaceResolver result = (NamespaceResolver) super.clone();
            result.sealed = false;
            return result;
        }
        catch (CloneNotSupportedException e) {
            // Of course, it's supported.
            e.printStackTrace();
            return null;
        }
    }

}