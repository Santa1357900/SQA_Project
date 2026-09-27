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

import org.mockito.InOrder;
import org.mockito.MockSettings;
import org.mockito.exceptions.Reporter;
import org.mockito.exceptions.misusing.NotAMockException;
import org.mockito.internal.creation.MockSettingsImpl;
import org.mockito.internal.invocation.Invocation;
import org.mockito.internal.progress.IOngoingStubbing;
import org.mockito.internal.progress.MockingProgress;
import org.mockito.internal.progress.ThreadSafeMockingProgress;
import org.mockito.internal.stubbing.OngoingStubbingImpl;
import org.mockito.internal.stubbing.StubberImpl;
import org.mockito.internal.util.MockUtil;
import org.mockito.internal.verification.api.VerificationMode;
import org.mockito.stubbing.*;

import java.util.Arrays;
import java.util.List;

@SuppressWarnings("unchecked")
public class MockitoCore {

    private final Reporter reporter = new Reporter();
    private final MockUtil mockUtil = new MockUtil();
    private final MockingProgress mockingProgress = new ThreadSafeMockingProgress();
    
    public <T> T mock(Class<T> classToMock, MockSettings mockSettings, boolean shouldResetOngoingStubbing) { return mock(classToMock, mockSettings); }
    public <T> T mock(Class<T> classToMock, MockSettings mockSettings) {
        mockingProgress.validateState();
            mockingProgress.resetOngoingStubbing();
        return mockUtil.createMock(classToMock, (MockSettingsImpl) mockSettings);
    }
    
    public IOngoingStubbing stub() {
        IOngoingStubbing stubbing = mockingProgress.pullOngoingStubbing();
        if (stubbing == null) {
            mockingProgress.reset();
            reporter.missingMethodInvocation();
        }
        return stubbing;
    }

    @Deprecated
    public <T> DeprecatedOngoingStubbing<T> stub(T methodCall) {
        mockingProgress.stubbingStarted();
        return (DeprecatedOngoingStubbing) stub();
    }

    public <T> OngoingStubbing<T> when(T methodCall) {
        mockingProgress.stubbingStarted();
        return (OngoingStubbing) stub();
    }
    
    
    public <T> T verify(T mock, VerificationMode mode) {
        if (mock == null) {
            reporter.nullPassedToVerify();
        } else if (!mockUtil.isMock(mock)) {
            reporter.notAMockPassedToVerify();
        }
        mockingProgress.verificationStarted(mode);
        return mock;
    }
    
    public <T> void reset(T ... mocks) {
        mockingProgress.validateState();
        mockingProgress.reset();
        mockingProgress.resetOngoingStubbing();
        
        for (T m : mocks) {
            mockUtil.resetMock(m);
        }
    }
    
    public void verifyNoMoreInteractions(Object... mocks) {
        assertMocksNotEmpty(mocks);
        mockingProgress.validateState();
        for (Object mock : mocks) {
            try {
                if (mock == null) {
                    reporter.nullPassedToVerifyNoMoreInteractions();
                }
                mockUtil.getMockHandler(mock).verifyNoMoreInteractions();
            } catch (NotAMockException e) {
                reporter.notAMockPassedToVerifyNoMoreInteractions();
            }
        }
    }
    
    private void assertMocksNotEmpty(Object[] mocks) {
        if (mocks == null || mocks.length == 0) {
            reporter.mocksHaveToBePassedToVerifyNoMoreInteractions();
        }
    }
    
    public InOrder inOrder(Object... mocks) {
        if (mocks == null || mocks.length == 0) {
            reporter.mocksHaveToBePassedWhenCreatingInOrder();
        }
        for (Object mock : mocks) {
            if (mock == null) {
                reporter.nullPassedWhenCreatingInOrder();
            } else if (!mockUtil.isMock(mock)) {
                reporter.notAMockPassedWhenCreatingInOrder();
            }
        }
        return new InOrderImpl(Arrays.asList(mocks));
    }
    
    public Stubber doAnswer(Answer answer) {
        mockingProgress.stubbingStarted();
        mockingProgress.resetOngoingStubbing();
        return new StubberImpl().doAnswer(answer);
    }
    
    public <T> VoidMethodStubbable<T> stubVoid(T mock) {
        MockHandlerInterface<T> handler = mockUtil.getMockHandler(mock);
        mockingProgress.stubbingStarted();
        return handler.voidMethodStubbable(mock);
    }

    public void validateMockitoUsage() {
        mockingProgress.validateState();
    }

    /**
     * For testing purposes only. Is not the part of main API.
     * @return last invocation
     */
    public Invocation getLastInvocation() {
        OngoingStubbingImpl ongoingStubbing = ((OngoingStubbingImpl) mockingProgress.pullOngoingStubbing());
        List<Invocation> allInvocations = ongoingStubbing.getRegisteredInvocations();
        return allInvocations.get(allInvocations.size()-1);
    }
}