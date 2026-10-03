package com.google.gson.internal;

import org.junit.Test;
import static org.junit.Assert.*;

public class UnsafeAllocatorTest {

  @Test
  public void testCreateReturnsValidAllocator() throws Throwable {
    UnsafeAllocator allocator = UnsafeAllocator.create();
    assertNotNull(allocator);
  }

  @Test
  public void testAllocateConcreteClass() throws Throwable {
    UnsafeAllocator allocator = UnsafeAllocator.create();
    try {
      SampleClass instance = allocator.newInstance(SampleClass.class);
      assertNotNull(instance);
      assertEquals(0, instance.value);
    } catch (Exception e) {
      // If the JVM/environment does not support any Unsafe/Dalvik allocation fallback,
      // it throws UnsupportedOperationException. We verify that either it succeeds
      // or throws UnsupportedOperationException.
      assertTrue(e instanceof UnsupportedOperationException);
    }
  }

  @Test
  public void testAllocateUnsupportedClass() throws Throwable {
    UnsafeAllocator allocator = UnsafeAllocator.create();
    try {
      allocator.newInstance(null);
      fail("Should have thrown an exception");
    } catch (Exception e) {
      assertNotNull(e);
    }
  }

  public static class SampleClass {
    public int value = 42;
    public SampleClass() {
      // Intentionally throw an exception in constructor to prove constructor is bypassed
      throw new RuntimeException("Constructor should not be called");
    }
  }
}