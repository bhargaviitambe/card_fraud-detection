// ============================================================
// JsonParser.java
// A minimal, self-contained JSON parser — no external libraries.
// Supports: objects, arrays, strings, numbers, booleans, null.
// ============================================================

import java.util.*;

/**
 * Parses a JSON string and returns a hierarchy of:
 *   JsonObject  — {"key": value, ...}
 *   JsonArray   — [value, ...]
 *   String, Double, Integer, Boolean, null
 */
class JsonParser {

    private final String src;
    private int pos;

    public JsonParser(String source) {
        this.src = source;
        this.pos = 0;
    }

    // ---- Public entry point ----
    public static Object parse(String json) {
        return new JsonParser(json.trim()).parseValue();
    }

    // ---- Value dispatcher ----
    private Object parseValue() {
        skipWhitespace();
        char c = peek();
        if (c == '{') return parseObject();
        if (c == '[') return parseArray();
        if (c == '"') return parseString();
        if (c == 't' || c == 'f') return parseBoolean();
        if (c == 'n') return parseNull();
        return parseNumber();
    }

    // ---- Object: { "key": value, ... } ----
    private JsonObject parseObject() {
        JsonObject obj = new JsonObject();
        consume('{');
        skipWhitespace();
        if (peek() == '}') { consume('}'); return obj; }
        while (true) {
            skipWhitespace();
            String key = parseString();
            skipWhitespace();
            consume(':');
            skipWhitespace();
            Object val = parseValue();
            obj.put(key, val);
            skipWhitespace();
            if (peek() == '}') { consume('}'); break; }
            consume(',');
        }
        return obj;
    }

    // ---- Array: [ value, ... ] ----
    private JsonArray parseArray() {
        JsonArray arr = new JsonArray();
        consume('[');
        skipWhitespace();
        if (peek() == ']') { consume(']'); return arr; }
        while (true) {
            skipWhitespace();
            arr.add(parseValue());
            skipWhitespace();
            if (peek() == ']') { consume(']'); break; }
            consume(',');
        }
        return arr;
    }

    // ---- String: "..." with basic escape handling ----
    private String parseString() {
        consume('"');
        StringBuilder sb = new StringBuilder();
        while (pos < src.length()) {
            char c = src.charAt(pos++);
            if (c == '"') break;
            if (c == '\\') {
                char esc = src.charAt(pos++);
                switch (esc) {
                    case '"': sb.append('"');  break;
                    case '\\': sb.append('\\'); break;
                    case '/':  sb.append('/');  break;
                    case 'n':  sb.append('\n'); break;
                    case 'r':  sb.append('\r'); break;
                    case 't':  sb.append('\t'); break;
                    default:   sb.append(esc);
                }
            } else {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    // ---- Number: integer or double ----
    private Object parseNumber() {
        int start = pos;
        boolean isDouble = false;
        if (peek() == '-') pos++;
        while (pos < src.length() && (Character.isDigit(src.charAt(pos))
               || src.charAt(pos) == '.' || src.charAt(pos) == 'e'
               || src.charAt(pos) == 'E' || src.charAt(pos) == '+'
               || src.charAt(pos) == '-')) {
            if (src.charAt(pos) == '.' || src.charAt(pos) == 'e'
                    || src.charAt(pos) == 'E') isDouble = true;
            pos++;
        }
        String num = src.substring(start, pos);
        if (isDouble) return Double.parseDouble(num);
        return Long.parseLong(num);      // use Long to avoid overflow
    }

    private Boolean parseBoolean() {
        if (src.startsWith("true",  pos)) { pos += 4; return Boolean.TRUE; }
        if (src.startsWith("false", pos)) { pos += 5; return Boolean.FALSE; }
        throw new RuntimeException("Expected boolean at pos " + pos);
    }

    private Object parseNull() {
        if (src.startsWith("null", pos)) { pos += 4; return null; }
        throw new RuntimeException("Expected null at pos " + pos);
    }

    // ---- Helpers ----
    private char peek() { skipWhitespace(); return src.charAt(pos); }
    private void consume(char expected) {
        if (src.charAt(pos) != expected)
            throw new RuntimeException(
                "Expected '" + expected + "' but got '" + src.charAt(pos)
                + "' at pos " + pos);
        pos++;
    }
    private void skipWhitespace() {
        while (pos < src.length() && Character.isWhitespace(src.charAt(pos))) pos++;
    }
}

// ============================================================
// JsonObject — Map<String, Object> with typed accessors
// ============================================================
class JsonObject extends LinkedHashMap<String, Object> {

    public String getString(String key) {
        Object v = get(key);
        if (v == null) throw new NoSuchElementException("Key not found: " + key);
        return v.toString();
    }

    public int getInt(String key) {
        Object v = get(key);
        if (v instanceof Long)   return ((Long)   v).intValue();
        if (v instanceof Double) return ((Double) v).intValue();
        throw new RuntimeException("Not a number: " + key);
    }

    public long getLong(String key) {
        Object v = get(key);
        if (v instanceof Long)   return (Long)   v;
        if (v instanceof Double) return ((Double) v).longValue();
        throw new RuntimeException("Not a number: " + key);
    }

    public double getDouble(String key) {
        Object v = get(key);
        if (v instanceof Double) return (Double) v;
        if (v instanceof Long)   return ((Long) v).doubleValue();
        throw new RuntimeException("Not a number: " + key);
    }

    public boolean getBoolean(String key) {
        Object v = get(key);
        if (v instanceof Boolean) return (Boolean) v;
        throw new RuntimeException("Not a boolean: " + key);
    }

    public JsonObject getObject(String key) {
        Object v = get(key);
        if (v instanceof JsonObject) return (JsonObject) v;
        throw new RuntimeException("Not a JsonObject: " + key);
    }

    public JsonArray getArray(String key) {
        Object v = get(key);
        if (v instanceof JsonArray) return (JsonArray) v;
        throw new RuntimeException("Not a JsonArray: " + key);
    }

    public boolean has(String key) { return containsKey(key) && get(key) != null; }
}

// ============================================================
// JsonArray — List<Object> with typed accessors
// ============================================================
class JsonArray extends ArrayList<Object> {

    public JsonObject getObject(int i) {
        Object v = get(i);
        if (v instanceof JsonObject) return (JsonObject) v;
        throw new RuntimeException("Element " + i + " is not a JsonObject");
    }

    public String getString(int i)  { return get(i).toString(); }
    public int    getInt(int i)     { return ((Number) get(i)).intValue(); }
    public double getDouble(int i)  { return ((Number) get(i)).doubleValue(); }
}
