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
package org.mockito.internal.stubbing.defaultanswers;

import org.mockito.internal.InternalMockHandler;
import org.mockito.internal.creation.settings.CreationSettings;
import org.mockito.internal.stubbing.InvocationContainerImpl;
import org.mockito.internal.stubbing.StubbedInvocationMatcher;
import org.mockito.internal.util.MockCreationValidator;
import org.mockito.internal.util.MockUtil;
import org.mockito.internal.util.reflection.GenericMetadataSupport;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import java.io.Serializable;


/**
 * Returning deep stub implementation.
 *
 * Will return previously created mock if the invocation matches.
 *
 * <p>Supports nested generic information, with this answer you can write code like this :
 *
 * <pre class="code"><code class="java">
 *     interface GenericsNest&lt;K extends Comparable&lt;K&gt; & Cloneable&gt; extends Map&lt;K, Set&lt;Number&gt;&gt; {}
 *
 *     GenericsNest&lt;?&gt; mock = mock(GenericsNest.class, new ReturnsGenericDeepStubs());
 *     Number number = mock.entrySet().iterator().next().getValue().iterator().next();
 * </code></pre>
 * </p>
 *
 * @see org.mockito.Mockito#RETURNS_DEEP_STUBS
 * @see org.mockito.Answers#RETURNS_DEEP_STUBS
 */
public class ReturnsDeepStubs implements Answer<Object>, Serializable {
    
    private static final long serialVersionUID = -7105341425736035847L;

    private ReturnsEmptyValues delegate = new ReturnsEmptyValues();

    public Object answer(InvocationOnMock invocation) throws Throwable {
        GenericMetadataSupport returnTypeGenericMetadata =
                actualParameterizedType(invocation.getMock()).resolveGenericReturnType(invocation.getMethod());

        Class<?> rawType = returnTypeGenericMetadata.rawType();
        if (!new MockCreationValidator().isTypeMockable(rawType)) {
            return delegate.returnValueFor(rawType);
        }

        return getMock(invocation);
    }

    private Object getMock(InvocationOnMock invocation) throws Throwable {
    	InternalMockHandler<Object> handler = new MockUtil().getMockHandler(invocation.getMock());
    	InvocationContainerImpl container = (InvocationContainerImpl) handler.getInvocationContainer();

        // matches invocation for verification
        for (StubbedInvocationMatcher stubbedInvocationMatcher : container.getStubbedInvocations()) {
    		if(container.getInvocationForStubbing().matches(stubbedInvocationMatcher.getInvocation())) {
    			return stubbedInvocationMatcher.answer(invocation);
    		}
		}

        // deep stub
        return recordDeepStubMock(invocation, container);
    }

    /**
     * Creates a mock using the Generics Metadata.
     *
     * @param returnTypeGenericMetadata The metadata to use to create the new mock.
     * @return The mock
     */




    private Object recordDeepStubMock(InvocationOnMock invocation, InvocationContainerImpl container) {
        Class<?> clz = invocation.getMethod().getReturnType();
        final Object mock = org.mockito.Mockito.mock(clz, this);

        container.addAnswer(new Answer<Object>() {
            public Object answer(InvocationOnMock invocation) throws Throwable {
                return mock;
            }
        }, false);

        return mock;
    }

    protected GenericMetadataSupport actualParameterizedType(Object mock) {
        CreationSettings mockSettings = (CreationSettings) new MockUtil().getMockHandler(mock).getMockSettings();
        return GenericMetadataSupport.inferFrom(mockSettings.getTypeToMock());
    }
}