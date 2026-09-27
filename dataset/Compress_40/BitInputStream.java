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
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.commons.compress.utils;

import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteOrder;

/**
 * Reads bits from an InputStream.
 * @since 1.10
 * @NotThreadSafe
 */
public class BitInputStream implements Closeable {
    private static final int MAXIMUM_CACHE_SIZE = 63; // bits in long minus sign bit
    private static final long[] MASKS = new long[MAXIMUM_CACHE_SIZE + 1];

    static {
        for (int i = 1; i <= MAXIMUM_CACHE_SIZE; i++) {
            MASKS[i] = (MASKS[i - 1] << 1) + 1;
        }
    }

    private final InputStream in;
    private final ByteOrder byteOrder;
    private long bitsCached = 0;
    private int bitsCachedSize = 0;

    /**
     * Constructor taking an InputStream and its bit arrangement. 
     * @param in the InputStream
     * @param byteOrder the bit arrangement across byte boundaries,
     *      either BIG_ENDIAN (aaaaabbb bb000000) or LITTLE_ENDIAN (bbbaaaaa 000000bb)
     */
    public BitInputStream(final InputStream in, final ByteOrder byteOrder) {
        this.in = in;
        this.byteOrder = byteOrder;
    }
    
    @Override
    public void close() throws IOException {
        in.close();
    }
    
    /**
     * Clears the cache of bits that have been read from the
     * underlying stream but not yet provided via {@link #readBits}.
     */
    public void clearBitCache() {
        bitsCached = 0;
        bitsCachedSize = 0;
    }
    
    /**
     * Returns at most 63 bits read from the underlying stream.
     *
     * @param count the number of bits to read, must be a positive
     * number not bigger than 63.
     * @return the bits concatenated as a long using the stream's byte order.
     *         -1 if the end of the underlying stream has been reached before reading
     *         the requested number of bits
     * @throws IOException on error
     */
    public long readBits(final int count) throws IOException {
        if (count < 0 || count > MAXIMUM_CACHE_SIZE) {
            throw new IllegalArgumentException("count must not be negative or greater than " + MAXIMUM_CACHE_SIZE);
        }
        while (bitsCachedSize < count) {
            final long nextByte = in.read();
            if (nextByte < 0) {
                return nextByte;
            }
            if (byteOrder == ByteOrder.LITTLE_ENDIAN) {
                bitsCached |= (nextByte << bitsCachedSize);
            } else {
                bitsCached <<= 8;
                bitsCached |= nextByte;
            }
            bitsCachedSize += 8;
        }
            // bitsCachedSize >= 57 and left-shifting it 8 bits would cause an overflow
        
        final long bitsOut;
            if (byteOrder == ByteOrder.LITTLE_ENDIAN) {
                bitsOut = (bitsCached & MASKS[count]);
                bitsCached >>>= count;
            } else {
                bitsOut = (bitsCached >> (bitsCachedSize - count)) & MASKS[count];
            }
            bitsCachedSize -= count;
        return bitsOut;
    }
}