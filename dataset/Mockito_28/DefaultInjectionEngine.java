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
 * Copyright (c) 2007 Mockito contributors
 * This program is made available under the terms of the MIT License.
 */
package org.mockito.internal.configuration;

import org.mockito.exceptions.Reporter;
import org.mockito.exceptions.base.MockitoException;
import org.mockito.internal.configuration.injection.FinalMockCandidateFilter;
import org.mockito.internal.configuration.injection.MockCandidateFilter;
import org.mockito.internal.configuration.injection.NameBasedCandidateFilter;
import org.mockito.internal.configuration.injection.TypeBasedCandidateFilter;
import org.mockito.internal.util.reflection.FieldInitializer;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

/**
 * Initializes mock/spies dependencies for fields annotated with &#064;InjectMocks
 * <p/>
 * See {@link org.mockito.MockitoAnnotations}
 */
public class DefaultInjectionEngine {

    private final MockCandidateFilter mockCandidateFilter = new TypeBasedCandidateFilter(new NameBasedCandidateFilter(new FinalMockCandidateFilter()));
    private Comparator<Field> supertypesLast = new Comparator<Field>() {
        public int compare(Field field1, Field field2) {
            Class<?> field1Type = field1.getType();
            Class<?> field2Type = field2.getType();

            if(field1Type.isAssignableFrom(field2Type)) {
                return 1;
            }
            if(field2Type.isAssignableFrom(field1Type)) {
                return -1;
            }
            return 0;
        }
    };

    /**
     * Inject mocks in injectMocksFields, and initialize them if needed.
     *
     * <p>
     * <u>Algorithm :<br></u>
     * for each field annotated by @InjectMocks
     *   <ul>
     *   <li>copy mocks set
     *   <li>initialize field annotated by @InjectMocks
     *   <li>for each field in @InjectMocks type ordered from sub-type to super-type
     *     <ul>
     *     <li>find mock candidate by type
     *     <li>if more than *one* candidate find mock candidate on name
     *     <li>if one mock candidate then
     *       <ul>
     *       <li>set mock by property setter if possible
     *       <li>else set mock by field injection
     *       </ul>
     *     <li>remove mock from mocks copy (mocks are just injected once)
     *     <li>else don't fail, user will then provide dependencies
     *     </ul>
     *   </ul>
     * </p>
     *
     * @param injectMocksFields Fields annotated by @InjectMocks
     * @param mocks Mocks
     * @param testClassInstance The test class instance
     */
    public void injectMocksOnFields(Set<Field> injectMocksFields, Set<Object> mocks, Object testClassInstance) {
        for (Field field : injectMocksFields) {
            Set<Object> mocksToBeInjected = new HashSet<Object>(mocks);
            Object injectMocksFieldInstance = null;
            try {
                injectMocksFieldInstance = new FieldInitializer(testClassInstance, field).initialize();
            } catch (MockitoException e) {
                new Reporter().cannotInitializeForInjectMocksAnnotation(field.getName(), e);
            }

            // for each field in the class hierarchy
            Class<?> fieldClass = injectMocksFieldInstance.getClass();
            while (fieldClass != Object.class) {
                injectMockCandidate(fieldClass, mocksToBeInjected, injectMocksFieldInstance);
                fieldClass = fieldClass.getSuperclass();
            }
        }
    }

    private void injectMockCandidate(Class<?> awaitingInjectionClazz, Set<Object> mocks, Object fieldInstance) {
        for(Field field : orderedInstanceFieldsFrom(awaitingInjectionClazz)) {
            mockCandidateFilter.filterCandidate(mocks, field, fieldInstance).thenInject();
        }
    }

    private Field[] orderedInstanceFieldsFrom(Class<?> awaitingInjectionClazz) {
        Field[] declaredFields = awaitingInjectionClazz.getDeclaredFields();
        Arrays.sort(declaredFields, supertypesLast);
        return declaredFields;
    }

}