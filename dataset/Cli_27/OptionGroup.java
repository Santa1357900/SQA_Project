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
/**
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

package org.apache.commons.cli;

import java.io.Serializable;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * A group of mutually exclusive options.
 *
 * @author John Keyes ( john at integralsource.com )
 * @version $Revision$, $Date$
 */
public class OptionGroup implements Serializable
{
    private static final long serialVersionUID = 1L;
    
    /** hold the options */
    private Map optionMap = new HashMap();

    /** the name of the selected option */
    private String selected;

    /** specified whether this group is required */
    private boolean required;

    /**
     * Add the specified <code>Option</code> to this group.
     *
     * @param option the option to add to this group
     * @return this option group with the option added
     */
    public OptionGroup addOption(Option option)
    {
        // key   - option name
        // value - the option
        optionMap.put(option.getKey(), option);

        return this;
    }

    /**
     * @return the names of the options in this group as a 
     * <code>Collection</code>
     */
    public Collection getNames()
    {
        // the key set is the collection of names
        return optionMap.keySet();
    }

    /**
     * @return the options in this group as a <code>Collection</code>
     */
    public Collection getOptions()
    {
        // the values are the collection of options
        return optionMap.values();
    }

    /**
     * Set the selected option of this group to <code>name</code>.
     *
     * @param option the option that is selected
     * @throws AlreadySelectedException if an option from this group has 
     * already been selected.
     */
    public void setSelected(Option option) throws AlreadySelectedException
    {
        if (option == null)
        {
            // reset the option previously selected
            selected = null;
            return;
        }
        
        // if no option has already been selected or the 
        // same option is being reselected then set the
        // selected member variable
        if (selected == null || selected.equals(option.getOpt()))
        {
            selected = option.getOpt();
        }
        else
        {
            throw new AlreadySelectedException(this, option);
        }
    }

    /**
     * @return the selected option name
     */
    public String getSelected()
    {
        return selected;
    }

    /**
     * @param required specifies if this group is required
     */
    public void setRequired(boolean required)
    {
        this.required = required;
    }

    /**
     * Returns whether this option group is required.
     *
     * @return whether this option group is required
     */
    public boolean isRequired()
    {
        return required;
    }

    /**
     * Returns the stringified version of this OptionGroup.
     * 
     * @return the stringified representation of this group
     */
    public String toString()
    {
        StringBuffer buff = new StringBuffer();

        Iterator iter = getOptions().iterator();

        buff.append("[");

        while (iter.hasNext())
        {
            Option option = (Option) iter.next();

            if (option.getOpt() != null)
            {
                buff.append("-");
                buff.append(option.getOpt());
            }
            else
            {
                buff.append("--");
                buff.append(option.getLongOpt());
            }

            buff.append(" ");
            buff.append(option.getDescription());

            if (iter.hasNext())
            {
                buff.append(", ");
            }
        }

        buff.append("]");

        return buff.toString();
    }
}