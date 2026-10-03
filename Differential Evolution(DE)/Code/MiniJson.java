import java.lang.reflect.Array;
import java.util.Map;

/** JSON output only; no project dependencies are needed by the generator. */
final class MiniJson {
    static String encode(Object value) {
        if (value == null) return "null";
        if (value instanceof Number || value instanceof Boolean) return value.toString();
        if (value instanceof Map) {
            StringBuilder s = new StringBuilder("{");
            for (Object item : ((Map<?, ?>) value).entrySet()) {
                Map.Entry<?, ?> e = (Map.Entry<?, ?>) item;
                if (s.length() > 1) s.append(',');
                s.append(encode(e.getKey().toString())).append(':').append(encode(e.getValue()));
            }
            return s.append('}').toString();
        }
        if (value instanceof Iterable || value.getClass().isArray()) {
            StringBuilder s = new StringBuilder("[");
            if (value instanceof Iterable) {
                for (Object e : (Iterable<?>) value) {
                    if (s.length() > 1) s.append(',');
                    s.append(encode(e));
                }
            } else for (int i = 0; i < Array.getLength(value); i++) {
                if (i > 0) s.append(',');
                s.append(encode(Array.get(value, i)));
            }
            return s.append(']').toString();
        }
        StringBuilder s = new StringBuilder("\"");
        for (char c : value.toString().toCharArray()) {
            if (c == '"' || c == '\\') s.append('\\').append(c);
            else if (c < 32 || c > 126) s.append(String.format("\\u%04x", (int)c));
            else s.append(c);
        }
        return s.append('"').toString();
    }
}
