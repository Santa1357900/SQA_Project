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
package org.mockito.internal;

import java.util.List;

import org.mockito.internal.creation.MockSettingsImpl;
import org.mockito.internal.invocation.Invocation;
import org.mockito.internal.invocation.InvocationMatcher;
import org.mockito.internal.invocation.MatchersBinder;
import org.mockito.internal.progress.MockingProgress;
import org.mockito.internal.progress.ThreadSafeMockingProgress;
import org.mockito.internal.stubbing.InvocationContainer;
import org.mockito.internal.stubbing.InvocationContainerImpl;
import org.mockito.internal.stubbing.OngoingStubbingImpl;
import org.mockito.internal.stubbing.StubbedInvocationMatcher;
import org.mockito.internal.stubbing.VoidMethodStubbableImpl;
import org.mockito.internal.verification.VerificationDataImpl;
import org.mockito.stubbing.Answer;
import org.mockito.stubbing.VoidMethodStubbable;
import org.mockito.verification.VerificationMode;

/**
 * Invocation handler set on mock objects.
 * 
 * @param <T>
 *            type of mock object to handle
 */
public class MockHandler<T> implements MockitoInvocationHandler, MockHandlerInterface<T> {

    private static final long serialVersionUID = -2917871070982574165L;

    InvocationContainerImpl invocationContainerImpl;
    MatchersBinder matchersBinder = new MatchersBinder();
    MockingProgress mockingProgress = new ThreadSafeMockingProgress();

    private final MockSettingsImpl mockSettings;

    public MockHandler(MockSettingsImpl mockSettings) {
        this.mockSettings = mockSettings;
        this.mockingProgress = new ThreadSafeMockingProgress();
        this.matchersBinder = new MatchersBinder();
        this.invocationContainerImpl = new InvocationContainerImpl(mockingProgress);
    }

    // for tests
    MockHandler() {
        this(new MockSettingsImpl());
    }

    public MockHandler(MockHandlerInterface<T> oldMockHandler) {
        this(oldMockHandler.getMockSettings());
    }

    public Object handle(Invocation invocation) throws Throwable {
        if (invocationContainerImpl.hasAnswersForStubbing()) {
            // stubbing voids with stubVoid() or doAnswer() style
            InvocationMatcher invocationMatcher = matchersBinder.bindMatchers(mockingProgress
                            .getArgumentMatcherStorage(), invocation);
            invocationContainerImpl.setMethodForStubbing(invocationMatcher);
            return null;
        }
        VerificationMode verificationMode = mockingProgress.pullVerificationMode();

        InvocationMatcher invocationMatcher = matchersBinder.bindMatchers(mockingProgress.getArgumentMatcherStorage(),
                        invocation);

        mockingProgress.validateState();

        //if verificationMode is not null then someone is doing verify()        
        if (verificationMode != null) {
            //We need to check if verification was started on the correct mock 
            // - see VerifyingWithAnExtraCallToADifferentMockTest
                VerificationDataImpl data = new VerificationDataImpl(invocationContainerImpl.getInvocations(), invocationMatcher);            
                verificationMode.verify(data);
                return null;
        }
        
        invocationContainerImpl.setInvocationForPotentialStubbing(invocationMatcher);
        OngoingStubbingImpl<T> ongoingStubbing = new OngoingStubbingImpl<T>(invocationContainerImpl);
        mockingProgress.reportOngoingStubbing(ongoingStubbing);

        StubbedInvocationMatcher stubbedInvocation = invocationContainerImpl.findAnswerFor(invocation);

        if (stubbedInvocation != null) {
            stubbedInvocation.captureArgumentsFrom(invocation);
            return stubbedInvocation.answer(invocation);
        } else {
            Object ret = mockSettings.getDefaultAnswer().answer(invocation);

            // redo setting invocation for potential stubbing in case of partial
            // mocks / spies.
            // Without it, the real method inside 'when' might have delegated
            // to other self method and overwrite the intended stubbed method
            // with a different one.
            invocationContainerImpl.resetInvocationForPotentialStubbing(invocationMatcher);
            return ret;
        }
    }

    public VoidMethodStubbable<T> voidMethodStubbable(T mock) {
        return new VoidMethodStubbableImpl<T>(mock, invocationContainerImpl);
    }

    public MockSettingsImpl getMockSettings() {
        return mockSettings;
    }

    @SuppressWarnings("unchecked")
    public void setAnswersForStubbing(List<Answer> answers) {
        invocationContainerImpl.setAnswersForStubbing(answers);
    }

    public InvocationContainer getInvocationContainer() {
        return invocationContainerImpl;
    }
}