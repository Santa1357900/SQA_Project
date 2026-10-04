package acogen;

import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Runs one generated test case, written as a small text spec, through reflection
 * and returns what happened as a string. The same class is used while searching,
 * while recording the expected result and inside the generated JUnit tests.
 *
 * Spec grammar:
 *   _                         null
 *   i:5 j:5 s:5 b:5 c:65 z:1  int, long, short, byte, char (code), boolean
 *   f:bits d:bits             float / double as raw bits
 *   t:0041...                 string, UTF-16 code units in hex
 *   e(cls,NAME)               enum constant
 *   k(cls)                    class literal
 *   a(componentCls,v,...)     array
 *   n(cls,paramTypes,v,...)   constructor call, paramTypes joined with |
 *   m(cls,name,paramTypes,receiver,v,...)   method call, receiver _ when static
 *   p(key)                    ready-made JDK object, see prefab()
 *
 * Written in Java 6 syntax on purpose: it is compiled together with the tests.
 */
final class AcoReplay {

    static final int MAX_TEXT = 160;
    static final int MAX_ITEMS = 30;

    private final String src;
    private int pos;
    private Object receiver;
    private boolean topLevel = true;

    private AcoReplay(String src) {
        this.src = src;
    }

    /** Outcome of the spec: "R:<value>|S:<receiver state>" or "E:<exception>". */
    static String run(String spec) {
        AcoReplay r = new AcoReplay(spec);
        try {
            Object value = r.value();
            String out = "R:" + show(value, 0);
            if (r.receiver != null) {
                out = out + "|S:" + show(r.receiver, 0);
            }
            return out;
        } catch (InvocationTargetException e) {
            return "E:" + describe(e.getCause() == null ? e : e.getCause());
        } catch (Throwable e) {
            return "E:" + describe(e);
        }
    }

    private static String describe(Throwable t) {
        String msg;
        try {
            msg = t.getMessage();
        } catch (Throwable ignored) {
            msg = null;
        }
        String name = t.getClass().getName();
        if (t instanceof StackOverflowError || t instanceof OutOfMemoryError || msg == null) {
            return name;
        }
        return name + ":" + clean(msg);
    }

    // ---- parsing and evaluation

    private Object value() throws Throwable {
        char c = src.charAt(pos);
        if (c == '_') {
            pos++;
            return null;
        }
        if (src.charAt(pos + 1) == ':') {
            pos += 2;
            String raw = token();
            switch (c) {
                case 'i': return Integer.valueOf(raw);
                case 'j': return Long.valueOf(raw);
                case 's': return Short.valueOf(raw);
                case 'b': return Byte.valueOf(raw);
                case 'c': return Character.valueOf((char) Integer.parseInt(raw));
                case 'z': return Boolean.valueOf(raw.equals("1"));
                case 'f': return Float.valueOf(Float.intBitsToFloat(Integer.parseInt(raw)));
                case 'd': return Double.valueOf(Double.longBitsToDouble(Long.parseLong(raw)));
                case 't': return unhex(raw);
                default: throw new IllegalArgumentException("bad literal " + c);
            }
        }
        pos += 2; // letter and '('
        Object result;
        switch (c) {
            case 'e': {
                Class cls = type(token());
                pos++;
                String name = token();
                result = Enum.valueOf(cls, name);
                break;
            }
            case 'k':
                result = type(token());
                break;
            case 'p':
                result = prefab(token());
                break;
            case 'a': {
                Class component = type(token());
                List items = rest();
                Object array = Array.newInstance(component, items.size());
                for (int i = 0; i < items.size(); i++) {
                    Array.set(array, i, items.get(i));
                }
                result = array;
                break;
            }
            case 'n': {
                Class cls = type(token());
                pos++;
                Class[] params = types(token());
                boolean top = topLevel;
                topLevel = false;
                List args = rest();
                Constructor ctor = cls.getDeclaredConstructor(params);
                ctor.setAccessible(true);
                result = ctor.newInstance(args.toArray());
                if (top) {
                    receiver = null;
                }
                break;
            }
            case 'm': {
                Class cls = type(token());
                pos++;
                String name = token();
                pos++;
                Class[] params = types(token());
                boolean top = topLevel;
                topLevel = false;
                pos++;
                Object target = value();
                List args = rest();
                Method method = cls.getDeclaredMethod(name, params);
                method.setAccessible(true);
                result = method.invoke(target, args.toArray());
                if (top) {
                    receiver = target;
                }
                break;
            }
            default:
                throw new IllegalArgumentException("bad spec at " + pos);
        }
        pos++; // ')'
        return result;
    }

    /** Remaining ",value" items up to the closing parenthesis (left unconsumed). */
    private List rest() throws Throwable {
        List items = new ArrayList();
        while (src.charAt(pos) == ',') {
            pos++;
            items.add(value());
        }
        return items;
    }

    private String token() {
        int start = pos;
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == ',' || c == ')') {
                break;
            }
            pos++;
        }
        return src.substring(start, pos);
    }

    private static Class[] types(String joined) throws ClassNotFoundException {
        if (joined.length() == 0) {
            return new Class[0];
        }
        String[] names = joined.split("\\|");
        Class[] out = new Class[names.length];
        for (int i = 0; i < names.length; i++) {
            out[i] = type(names[i]);
        }
        return out;
    }

    static Class type(String name) throws ClassNotFoundException {
        if (name.equals("int")) return int.class;
        if (name.equals("long")) return long.class;
        if (name.equals("double")) return double.class;
        if (name.equals("boolean")) return boolean.class;
        if (name.equals("char")) return char.class;
        if (name.equals("float")) return float.class;
        if (name.equals("short")) return short.class;
        if (name.equals("byte")) return byte.class;
        if (name.equals("void")) return void.class;
        ClassLoader loader = Thread.currentThread().getContextClassLoader();
        if (loader == null) {
            loader = AcoReplay.class.getClassLoader();
        }
        return Class.forName(name, true, loader);
    }

    static String unhex(String hex) {
        StringBuilder sb = new StringBuilder(hex.length() / 4);
        for (int i = 0; i + 4 <= hex.length(); i += 4) {
            sb.append((char) Integer.parseInt(hex.substring(i, i + 4), 16));
        }
        return sb.toString();
    }

    /** Small fixed set of JDK objects used for parameters that are not simple values. */
    static Object prefab(String key) throws Exception {
        if (key.equals("object")) return new Object();
        if (key.equals("list0")) return new ArrayList();
        if (key.equals("listS")) return list(new Object[] {"a", "b", "c"});
        if (key.equals("listI")) return list(new Object[] {Integer.valueOf(1), Integer.valueOf(2), Integer.valueOf(3)});
        if (key.equals("listN")) return list(new Object[] {"a", null});
        if (key.equals("linked")) return new java.util.LinkedList(list(new Object[] {"x", "y"}));
        if (key.equals("set0")) return new java.util.LinkedHashSet();
        if (key.equals("setS")) return new java.util.LinkedHashSet(list(new Object[] {"a", "b"}));
        if (key.equals("tree")) return new java.util.TreeSet(list(new Object[] {"b", "a"}));
        if (key.equals("map0")) return new java.util.LinkedHashMap();
        if (key.equals("mapS")) {
            Map m = new java.util.LinkedHashMap();
            m.put("k1", "v1");
            m.put("k2", "v2");
            return m;
        }
        if (key.equals("treemap")) {
            Map m = new java.util.TreeMap();
            m.put("a", Integer.valueOf(1));
            return m;
        }
        if (key.equals("props")) {
            java.util.Properties p = new java.util.Properties();
            p.setProperty("key", "value");
            return p;
        }
        if (key.equals("iter")) return list(new Object[] {"a", "b"}).iterator();
        if (key.equals("sb")) return new StringBuilder("abc");
        if (key.equals("sbuf")) return new StringBuffer("abc");
        if (key.equals("reader")) return new java.io.StringReader("a,b\n1,2\n");
        if (key.equals("reader0")) return new java.io.StringReader("");
        if (key.equals("writer")) return new java.io.StringWriter();
        if (key.equals("in")) return new java.io.ByteArrayInputStream(new byte[] {1, 2, 3, 65, 66, 10});
        if (key.equals("in0")) return new java.io.ByteArrayInputStream(new byte[0]);
        if (key.equals("out")) return new java.io.ByteArrayOutputStream();
        if (key.equals("bigint")) return new java.math.BigInteger("12345678901234567890");
        if (key.equals("bigdec")) return new java.math.BigDecimal("1.50");
        if (key.equals("localeUS")) return java.util.Locale.US;
        if (key.equals("localeDE")) return java.util.Locale.GERMANY;
        if (key.equals("utc")) return java.util.TimeZone.getTimeZone("UTC");
        if (key.equals("tzParis")) return java.util.TimeZone.getTimeZone("Europe/Paris");
        if (key.equals("date")) return new java.util.Date(86400000L);
        if (key.equals("calendar")) {
            java.util.Calendar c = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"), java.util.Locale.US);
            c.setTimeInMillis(86400000L);
            return c;
        }
        if (key.equals("random")) return new java.util.Random(7L);
        if (key.equals("comparator")) return String.CASE_INSENSITIVE_ORDER;
        if (key.equals("charset")) return java.nio.charset.Charset.forName("UTF-8");
        if (key.equals("exception")) return new RuntimeException("x");
        if (key.equals("thread")) return Thread.currentThread();
        throw new IllegalArgumentException("unknown prefab " + key);
    }

    private static List list(Object[] items) {
        List l = new ArrayList();
        for (int i = 0; i < items.length; i++) {
            l.add(items[i]);
        }
        return l;
    }

    // ---- turning results into stable text

    static String show(Object o, int depth) {
        if (o == null) {
            return "null";
        }
        if (o instanceof String) {
            return "\"" + clean((String) o) + "\"";
        }
        if (o instanceof Character) {
            return "'" + (int) ((Character) o).charValue() + "'";
        }
        if (o instanceof Number || o instanceof Boolean) {
            return o.getClass().getSimpleName() + ":" + o;
        }
        if (o instanceof Enum) {
            return ((Enum) o).getDeclaringClass().getName() + "." + ((Enum) o).name();
        }
        if (o instanceof Class) {
            return "class " + ((Class) o).getName();
        }
        if (o instanceof Throwable) {
            return "throwable " + o.getClass().getName();
        }
        if (depth > 2) {
            return o.getClass().getName();
        }
        if (o.getClass().isArray()) {
            int n = Array.getLength(o);
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < n && i < MAX_ITEMS; i++) {
                if (i > 0) sb.append(',');
                sb.append(show(Array.get(o, i), depth + 1));
            }
            return sb.append(n > MAX_ITEMS ? ",..]" : "]").append("#").append(n).toString();
        }
        try {
            if (o instanceof Collection) {
                Collection col = (Collection) o;
                List parts = new ArrayList();
                Iterator it = col.iterator();
                while (it.hasNext() && parts.size() < MAX_ITEMS) {
                    parts.add(show(it.next(), depth + 1));
                }
                boolean ordered = o instanceof List || o instanceof java.util.SortedSet
                        || o instanceof java.util.LinkedHashSet || o instanceof java.util.Queue;
                if (!ordered) {
                    Collections.sort(parts);
                }
                return o.getClass().getName() + parts + "#" + col.size();
            }
            if (o instanceof Map) {
                Map map = (Map) o;
                List parts = new ArrayList();
                Iterator it = map.entrySet().iterator();
                while (it.hasNext() && parts.size() < MAX_ITEMS) {
                    Map.Entry en = (Map.Entry) it.next();
                    parts.add(show(en.getKey(), depth + 1) + "=" + show(en.getValue(), depth + 1));
                }
                if (!(o instanceof java.util.SortedMap) && !(o instanceof java.util.LinkedHashMap)) {
                    Collections.sort(parts);
                }
                return o.getClass().getName() + parts + "#" + map.size();
            }
            if (o instanceof CharSequence) {
                return o.getClass().getName() + "{" + clean(o.toString()) + "}";
            }
            Method toString = o.getClass().getMethod("toString", new Class[0]);
            if (toString.getDeclaringClass() != Object.class) {
                return o.getClass().getName() + "{" + clean(String.valueOf(o)) + "}";
            }
        } catch (Throwable e) {
            return o.getClass().getName() + "{!" + e.getClass().getName() + "}";
        }
        return o.getClass().getName();
    }

    /** Printable, bounded text with identity hash codes removed. */
    static String clean(String s) {
        StringBuilder sb = new StringBuilder();
        int n = Math.min(s.length(), MAX_TEXT);
        for (int i = 0; i < n; i++) {
            char c = s.charAt(i);
            if (c == '@') {
                int j = i + 1;
                while (j < s.length() && Character.digit(s.charAt(j), 16) >= 0) {
                    j++;
                }
                if (j - i > 4) {
                    sb.append("@#");
                    i = j - 1;
                    continue;
                }
            }
            if (c >= 32 && c < 127 && c != '\\') {
                sb.append(c);
            } else {
                sb.append("\\u");
                String h = Integer.toHexString(c);
                for (int k = h.length(); k < 4; k++) {
                    sb.append('0');
                }
                sb.append(h);
            }
        }
        if (s.length() > MAX_TEXT) {
            sb.append("..#").append(s.length());
        }
        return sb.toString();
    }
}
