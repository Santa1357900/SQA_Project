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

package org.mockito.internal.invocation;

import java.io.Serializable;
import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.*;

import org.hamcrest.Matcher;
import org.mockito.internal.matchers.CapturesArguments;
import org.mockito.internal.matchers.MatcherDecorator;
import org.mockito.internal.reporting.PrintSettings;
import org.mockito.invocation.DescribedInvocation;
import org.mockito.invocation.Invocation;
import org.mockito.invocation.Location;

@SuppressWarnings("unchecked")
public class InvocationMatcher implements DescribedInvocation, CapturesArgumensFromInvocation, Serializable {

    private static final long serialVersionUID = -3047126096857467610L;
    private final Invocation invocation;
    private final List<Matcher> matchers;

    public InvocationMatcher(Invocation invocation, List<Matcher> matchers) {
        this.invocation = invocation;
        if (matchers.isEmpty()) {
            this.matchers = ArgumentsProcessor.argumentsToMatchers(invocation.getArguments());
        } else {
            this.matchers = matchers;
        }
    }
    
    public InvocationMatcher(Invocation invocation) {
        this(invocation, Collections.<Matcher>emptyList());
    }

    public Method getMethod() {
        return invocation.getMethod();
    }
    
    public Invocation getInvocation() {
        return this.invocation;
    }
    
    public List<Matcher> getMatchers() {
        return this.matchers;
    }
    
    public String toString() {
        return new PrintSettings().print(matchers, invocation);
    }

    public boolean matches(Invocation actual) {
        return invocation.getMock().equals(actual.getMock())
                && hasSameMethod(actual)
                && new ArgumentsComparator().argumentsMatch(this, actual);
    }

    private boolean safelyArgumentsMatch(Object[] actualArgs) {
        try {
            return new ArgumentsComparator().argumentsMatch(this, actualArgs);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * similar means the same method name, same mock, unverified 
     * and: if arguments are the same cannot be overloaded
     */
    public boolean hasSimilarMethod(Invocation candidate) {
        String wantedMethodName = getMethod().getName();
        String currentMethodName = candidate.getMethod().getName();
        
        final boolean methodNameEquals = wantedMethodName.equals(currentMethodName);
        final boolean isUnverified = !candidate.isVerified();
        final boolean mockIsTheSame = getInvocation().getMock() == candidate.getMock();
        final boolean methodEquals = hasSameMethod(candidate);

        if (!methodNameEquals || !isUnverified || !mockIsTheSame) {
            return false;
        }

        final boolean overloadedButSameArgs = !methodEquals && safelyArgumentsMatch(candidate.getArguments());

        return !overloadedButSameArgs;
    }

    public boolean hasSameMethod(Invocation candidate) {
        //not using method.equals() for 1 good reason:
        //sometimes java generates forwarding methods when generics are in play see JavaGenericsForwardingMethodsTest
        Method m1 = invocation.getMethod();
        Method m2 = candidate.getMethod();
        
        if (m1.getName() != null && m1.getName().equals(m2.getName())) {
        	/* Avoid unnecessary cloning */
        	Class[] params1 = m1.getParameterTypes();
        	Class[] params2 = m2.getParameterTypes();
        	if (params1.length == params2.length) {
        	    for (int i = 0; i < params1.length; i++) {
        		if (params1[i] != params2[i])
        		    return false;
        	    }
        	    return true;
        	}
        }
        return false;
    }
    
    public Location getLocation() {
        return invocation.getLocation();
    }

    public void captureArgumentsFrom(Invocation invocation) {
        if (invocation.getMethod().isVarArgs()) {
            int indexOfVararg = invocation.getRawArguments().length - 1;
            for (int position = 0; position < indexOfVararg; position++) {
                Matcher m = matchers.get(position);
                if (m instanceof CapturesArguments) {
                    ((CapturesArguments) m).captureFrom(invocation.getArgumentAt(position, Object.class));
                }
            }
            for (int position = indexOfVararg; position < matchers.size(); position++) {
                Matcher m = matchers.get(position);
                if (m instanceof CapturesArguments) {
                    ((CapturesArguments) m).captureFrom(invocation.getRawArguments()[position - indexOfVararg]);
                }
            }
        } else {
            for (int position = 0; position < matchers.size(); position++) {
                Matcher m = matchers.get(position);
                if (m instanceof CapturesArguments) {
                    ((CapturesArguments) m).captureFrom(invocation.getArgumentAt(position, Object.class));
                }
            }
        }
    }


    public static List<InvocationMatcher> createFrom(List<Invocation> invocations) {
        LinkedList<InvocationMatcher> out = new LinkedList<InvocationMatcher>();
        for (Invocation i : invocations) {
            out.add(new InvocationMatcher(i));
        }
        return out;
    }
}