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
 * Copyright (C) 2008 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.gson;

import java.io.IOException;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import com.google.gson.internal.bind.util.ISO8601Utils;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

/**
 * This type adapter supports three subclasses of date: Date, Timestamp, and
 * java.sql.Date.
 *
 * @author Inderjeet Singh
 * @author Joel Leitch
 */
final class DefaultDateTypeAdapter extends TypeAdapter<Date> {

  private static final String SIMPLE_NAME = "DefaultDateTypeAdapter";

  private final Class<? extends Date> dateType;
  private final DateFormat enUsFormat;
  private final DateFormat localFormat;
  
  DefaultDateTypeAdapter(Class<? extends Date> dateType) {
    this(dateType,
        DateFormat.getDateTimeInstance(DateFormat.DEFAULT, DateFormat.DEFAULT, Locale.US),
        DateFormat.getDateTimeInstance(DateFormat.DEFAULT, DateFormat.DEFAULT));
  }

  DefaultDateTypeAdapter(Class<? extends Date> dateType, String datePattern) {
    this(dateType, new SimpleDateFormat(datePattern, Locale.US), new SimpleDateFormat(datePattern));
  }

  DefaultDateTypeAdapter(Class<? extends Date> dateType, int style) {
    this(dateType, DateFormat.getDateInstance(style, Locale.US), DateFormat.getDateInstance(style));
  }

  public DefaultDateTypeAdapter(int dateStyle, int timeStyle) {
    this(Date.class,
        DateFormat.getDateTimeInstance(dateStyle, timeStyle, Locale.US),
        DateFormat.getDateTimeInstance(dateStyle, timeStyle));
  }

  public DefaultDateTypeAdapter(Class<? extends Date> dateType, int dateStyle, int timeStyle) {
    this(dateType,
        DateFormat.getDateTimeInstance(dateStyle, timeStyle, Locale.US),
        DateFormat.getDateTimeInstance(dateStyle, timeStyle));
  }

  DefaultDateTypeAdapter(final Class<? extends Date> dateType, DateFormat enUsFormat, DateFormat localFormat) {
    if ( dateType != Date.class && dateType != java.sql.Date.class && dateType != Timestamp.class ) {
      throw new IllegalArgumentException("Date type must be one of " + Date.class + ", " + Timestamp.class + ", or " + java.sql.Date.class + " but was " + dateType);
    }
    this.dateType = dateType;
    this.enUsFormat = enUsFormat;
    this.localFormat = localFormat;
  }

  // These methods need to be synchronized since JDK DateFormat classes are not thread-safe
  // See issue 162
  @Override
  public void write(JsonWriter out, Date value) throws IOException {
    if (value == null) {
      out.nullValue();
      return;
    }
    synchronized (localFormat) {
      String dateFormatAsString = enUsFormat.format(value);
      out.value(dateFormatAsString);
    }
  }

  @Override
  public Date read(JsonReader in) throws IOException {
    if (in.peek() != JsonToken.STRING) {
      throw new JsonParseException("The date should be a string value");
    }
    Date date = deserializeToDate(in.nextString());
    if (dateType == Date.class) {
      return date;
    } else if (dateType == Timestamp.class) {
      return new Timestamp(date.getTime());
    } else if (dateType == java.sql.Date.class) {
      return new java.sql.Date(date.getTime());
    } else {
      // This must never happen: dateType is guarded in the primary constructor
      throw new AssertionError();
    }
  }

  private Date deserializeToDate(String s) {
    synchronized (localFormat) {
      try {
        return localFormat.parse(s);
      } catch (ParseException ignored) {}
      try {
        return enUsFormat.parse(s);
      } catch (ParseException ignored) {}
      try {
        return ISO8601Utils.parse(s, new ParsePosition(0));
      } catch (ParseException e) {
        throw new JsonSyntaxException(s, e);
      }
    }
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(SIMPLE_NAME);
    sb.append('(').append(localFormat.getClass().getSimpleName()).append(')');
    return sb.toString();
  }
}