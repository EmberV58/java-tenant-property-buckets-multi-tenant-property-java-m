import java.util.*;

/** Minimal JSON decoder for the dependency-free example. */
final class Json {
    private final String text;
    private int pos;
    private Json(String text) { this.text = text; }
    static Object parse(String text) {
        Json parser = new Json(text);
        Object value = parser.value();
        parser.space();
        if (parser.pos != text.length()) throw new IllegalArgumentException("Trailing JSON content");
        return value;
    }
    private void space() { while (pos < text.length() && " \r\n\t".indexOf(text.charAt(pos)) >= 0) pos++; }
    private char take() {
        if (pos == text.length()) throw new IllegalArgumentException("Unexpected end of JSON");
        return text.charAt(pos++);
    }
    private void require(char c) { space(); if (take() != c) throw new IllegalArgumentException("Invalid JSON delimiter"); }
    private Object value() {
        space();
        if (pos == text.length()) throw new IllegalArgumentException("Missing JSON value");
        char c = text.charAt(pos);
        if (c == '"') return string();
        if (c == '{') {
            pos++; Map<String, Object> result = new LinkedHashMap<>(); space();
            if (pos < text.length() && text.charAt(pos) == '}') { pos++; return result; }
            do { space(); String key = string(); require(':'); result.put(key, value()); space(); c = take(); }
            while (c == ',');
            if (c != '}') throw new IllegalArgumentException("Invalid JSON object");
            return result;
        }
        if (c == '[') {
            pos++; List<Object> result = new ArrayList<>(); space();
            if (pos < text.length() && text.charAt(pos) == ']') { pos++; return result; }
            do { result.add(value()); space(); c = take(); } while (c == ',');
            if (c != ']') throw new IllegalArgumentException("Invalid JSON array");
            return result;
        }
        for (String literal : new String[]{"true", "false", "null"}) {
            if (text.startsWith(literal, pos)) {
                pos += literal.length();
                return literal.equals("null") ? null : Boolean.valueOf(literal);
            }
        }
        var number = java.util.regex.Pattern.compile("-?(?:0|[1-9][0-9]*)(?:\\.[0-9]+)?(?:[eE][+-]?[0-9]+)?").matcher(text);
        number.region(pos, text.length());
        if (!number.lookingAt()) throw new IllegalArgumentException("Invalid JSON value");
        pos = number.end(); return new java.math.BigDecimal(number.group());
    }
    private String string() {
        if (take() != '"') throw new IllegalArgumentException("Expected JSON string");
        StringBuilder result = new StringBuilder();
        for (;;) {
            char c = take();
            if (c == '"') return result.toString();
            if (c < 32) throw new IllegalArgumentException("Invalid JSON string");
            if (c == '\\') {
                c = take();
                switch (c) {
                    case '"', '\\', '/' -> result.append(c);
                    case 'b' -> result.append('\b');
                    case 'f' -> result.append('\f');
                    case 'n' -> result.append('\n');
                    case 'r' -> result.append('\r');
                    case 't' -> result.append('\t');
                    case 'u' -> { int n = 0; for (int i = 0; i < 4; i++) { int d = Character.digit(take(), 16); if (d < 0) throw new IllegalArgumentException("Invalid Unicode escape"); n = n * 16 + d; } result.append((char)n); }
                    default -> throw new IllegalArgumentException("Invalid JSON escape");
                }
            } else result.append(c);
        }
    }
    static Map<?, ?> object(String text) {
        if (parse(text) instanceof Map<?, ?> map) return map;
        throw new IllegalArgumentException("Expected JSON object");
    }
    static Map<?, ?> data(String text) {
        Map<?, ?> root = object(text);
        if (root.containsKey("ok")) {
            if (!Boolean.TRUE.equals(root.get("ok")) || !(root.get("data") instanceof Map<?, ?>))
                throw new IllegalArgumentException("Expected successful object envelope");
            return (Map<?, ?>) root.get("data");
        }
        return root;
    }
}
