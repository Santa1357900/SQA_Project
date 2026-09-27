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
package org.apache.commons.cli2;

import java.util.Comparator;
import java.util.List;
import java.util.ListIterator;
import java.util.Set;

/**
 * The super type of all options representing a particular element of the
 * command line interface.
 */
public interface Option {

    /**
     * Processes String arguments into a CommandLine.
     *
     * The iterator will initially point at the first argument to be processed
     * and at the end of the method should point to the first argument not
     * processed. This method MUST process at least one argument from the
     * ListIterator.
     *
     * @param commandLine
     *            The CommandLine object to store results in
     * @param args
     *            The arguments to process
     * @throws OptionException
     *             if any problems occur
     */
    void process(
        final WriteableCommandLine commandLine,
        final ListIterator args)
        throws OptionException;

    /**
     * Adds defaults to a CommandLine.
     *
     * Any defaults for this option are applied as well as the defaults for
     * any contained options
     *
     * @param commandLine
     *            The CommandLine object to store defaults in
     */
    void defaults(final WriteableCommandLine commandLine);

    /**
     * Indicates whether this Option will be able to process the particular
     * argument.
     *
     * @param argument
     *            The argument to be tested
     * @return true if the argument can be processed by this Option
     */
    boolean canProcess(final WriteableCommandLine commandLine, final String argument);

    /**
     * Indicates whether this Option will be able to process the particular
     * argument. The ListIterator must be restored to the initial state before
     * returning the boolean.
     *
     * @see #canProcess(WriteableCommandLine,String)
     * @param arguments
     *            the ListIterator over String arguments
     * @return true if the argument can be processed by this Option
     */
    boolean canProcess(final WriteableCommandLine commandLine, final ListIterator arguments);

    /**
     * Identifies the argument prefixes that should trigger this option. This
     * is used to decide which of many Options should be tried when processing
     * a given argument string.
     *
     * The returned Set must not be null.
     *
     * @return The set of triggers for this Option
     */
    Set getTriggers();

    /**
     * Identifies the argument prefixes that should be considered options. This
     * is used to identify whether a given string looks like an option or an
     * argument value. Typically an option would return the set [--,-] while
     * switches might offer [-,+].
     *
     * The returned Set must not be null.
     *
     * @return The set of prefixes for this Option
     */
    Set getPrefixes();

    /**
     * Checks that the supplied CommandLine is valid with respect to this
     * option.
     *
     * @param commandLine
     *            The CommandLine to check.
     * @throws OptionException
     *             if the CommandLine is not valid.
     */
    void validate(final WriteableCommandLine commandLine)
        throws OptionException;

    /**
     * Builds up a list of HelpLineImpl instances to be presented by HelpFormatter.
     *
     * @see HelpLine
     * @see org.apache.commons.cli2.util.HelpFormatter
     * @param depth
     *            the initial indent depth
     * @param helpSettings
     *            the HelpSettings that should be applied
     * @param comp
     *            a comparator used to sort options when applicable.
     * @return a List of HelpLineImpl objects
     */
    List helpLines(
        final int depth,
        final Set helpSettings,
        final Comparator comp);

    /**
     * Appends usage information to the specified StringBuffer
     *
     * @param buffer the buffer to append to
     * @param helpSettings a set of display settings @see DisplaySetting
     * @param comp a comparator used to sort the Options
     */
    void appendUsage(
        final StringBuffer buffer,
        final Set helpSettings,
        final Comparator comp);

    /**
     * The preferred name of an option is used for generating help and usage
     * information.
     *
     * @return The preferred name of the option
     */
    String getPreferredName();

    /**
     * Returns a description of the option. This string is used to build help
     * messages as in the HelpFormatter.
     *
     * @see org.apache.commons.cli2.util.HelpFormatter
     * @return a description of the option.
     */
    String getDescription();

    /**
     * Returns the id of the option.  This can be used in a loop and switch
     * construct:
     *
     * <code>
     * for(Option o : cmd.getOptions()){
     *     switch(o.getId()){
     *         case POTENTIAL_OPTION:
     *             ...
     *     }
     * }
     * </code>
     *
     * The returned value is not guarenteed to be unique.
     *
     * @return the id of the option.
     */
    int getId();

    /**
     * Recursively searches for an option with the supplied trigger.
     *
     * @param trigger the trigger to search for.
     * @return the matching option or null.
     */
    Option findOption(final String trigger);

    /**
     * Indicates whether this option is required to be present.
     * @return true iff the CommandLine will be invalid without this Option
     */
    boolean isRequired();

    /**
     * Returns the parent of this option. Options can be organized in a
     * hierarchical manner if they are added to groups. This method can be used
     * for obtaining the parent option of this option. The result may be
     * <b>null</b> if this option does not have a parent.
     *
     * @return the parent of this option
     */

    /**
     * Sets the parent of this option. This method is called when the option is
     * added to a group. Storing the parent of an option makes it possible to
     * keep track of hierarchical relations between options. For instance, if an
     * option is identified while parsing a command line, the group this option
     * belongs to can also be added to the command line.
     *
     * @param parent the parent option
     */
}