package org.ease.mvp.support;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Json {
    private Json() {
    }

    public static String stringify(Object value) {
        StringBuilder output = new StringBuilder();
        append(output, value);
        return output.toString();
    }

    public static Object parse(String input) {
        Parser parser = new Parser(input);
        Object value = parser.value();
        parser.whitespace();
        if (!parser.end()) {
            throw new IllegalArgumentException("Unexpected JSON content at offset " + parser.index);
        }
        return value;
    }

    private static void append(StringBuilder output, Object value) {
        if (value == null) {
            output.append("null");
        } else if (value instanceof String string) {
            quote(output, string);
        } else if (value instanceof Number || value instanceof Boolean) {
            output.append(value);
        } else if (value instanceof Enum<?> enumeration) {
            quote(output, enumeration.name());
        } else if (value instanceof Map<?, ?> map) {
            output.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) output.append(',');
                quote(output, String.valueOf(entry.getKey()));
                output.append(':');
                append(output, entry.getValue());
                first = false;
            }
            output.append('}');
        } else if (value instanceof Iterable<?> iterable) {
            output.append('[');
            boolean first = true;
            for (Object item : iterable) {
                if (!first) output.append(',');
                append(output, item);
                first = false;
            }
            output.append(']');
        } else {
            quote(output, String.valueOf(value));
        }
    }

    private static void quote(StringBuilder output, String value) {
        output.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> output.append("\\\"");
                case '\\' -> output.append("\\\\");
                case '\b' -> output.append("\\b");
                case '\f' -> output.append("\\f");
                case '\n' -> output.append("\\n");
                case '\r' -> output.append("\\r");
                case '\t' -> output.append("\\t");
                default -> {
                    if (character < 0x20) output.append(String.format("\\u%04x", (int) character));
                    else output.append(character);
                }
            }
        }
        output.append('"');
    }

    private static final class Parser {
        private final String input;
        private int index;

        private Parser(String input) {
            if (input == null) throw new IllegalArgumentException("JSON input cannot be null");
            this.input = input;
        }

        private Object value() {
            whitespace();
            if (end()) throw error("Expected a JSON value");
            return switch (input.charAt(index)) {
                case '{' -> object();
                case '[' -> array();
                case '"' -> string();
                case 't' -> literal("true", Boolean.TRUE);
                case 'f' -> literal("false", Boolean.FALSE);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }

        private Map<String, Object> object() {
            expect('{');
            Map<String, Object> result = new LinkedHashMap<>();
            whitespace();
            if (consume('}')) return result;
            while (true) {
                whitespace();
                if (end() || input.charAt(index) != '"') throw error("Expected an object key");
                String key = string();
                whitespace();
                expect(':');
                result.put(key, value());
                whitespace();
                if (consume('}')) return result;
                expect(',');
            }
        }

        private List<Object> array() {
            expect('[');
            List<Object> result = new ArrayList<>();
            whitespace();
            if (consume(']')) return result;
            while (true) {
                result.add(value());
                whitespace();
                if (consume(']')) return result;
                expect(',');
            }
        }

        private String string() {
            expect('"');
            StringBuilder result = new StringBuilder();
            while (!end()) {
                char character = input.charAt(index++);
                if (character == '"') return result.toString();
                if (character != '\\') {
                    if (character < 0x20) throw error("Unescaped control character");
                    result.append(character);
                    continue;
                }
                if (end()) throw error("Incomplete escape sequence");
                char escaped = input.charAt(index++);
                switch (escaped) {
                    case '"', '\\', '/' -> result.append(escaped);
                    case 'b' -> result.append('\b');
                    case 'f' -> result.append('\f');
                    case 'n' -> result.append('\n');
                    case 'r' -> result.append('\r');
                    case 't' -> result.append('\t');
                    case 'u' -> result.append(unicode());
                    default -> throw error("Unsupported escape sequence");
                }
            }
            throw error("Unterminated string");
        }

        private char unicode() {
            if (index + 4 > input.length()) throw error("Incomplete unicode escape");
            String digits = input.substring(index, index + 4);
            index += 4;
            try {
                return (char) Integer.parseInt(digits, 16);
            } catch (NumberFormatException exception) {
                throw error("Invalid unicode escape");
            }
        }

        private Number number() {
            int start = index;
            if (consume('-')) {
                if (end()) throw error("Incomplete number");
            }
            digits();
            boolean decimal = false;
            if (consume('.')) {
                decimal = true;
                digits();
            }
            if (!end() && (input.charAt(index) == 'e' || input.charAt(index) == 'E')) {
                decimal = true;
                index++;
                if (!end() && (input.charAt(index) == '+' || input.charAt(index) == '-')) index++;
                digits();
            }
            String raw = input.substring(start, index);
            try {
                return decimal ? Double.parseDouble(raw) : Long.parseLong(raw);
            } catch (NumberFormatException exception) {
                throw error("Invalid number");
            }
        }

        private void digits() {
            int start = index;
            while (!end() && Character.isDigit(input.charAt(index))) index++;
            if (start == index) throw error("Expected a digit");
        }

        private Object literal(String literal, Object value) {
            if (!input.startsWith(literal, index)) throw error("Invalid literal");
            index += literal.length();
            return value;
        }

        private void whitespace() {
            while (!end() && Character.isWhitespace(input.charAt(index))) index++;
        }

        private void expect(char character) {
            if (!consume(character)) throw error("Expected '" + character + "'");
        }

        private boolean consume(char character) {
            if (end() || input.charAt(index) != character) return false;
            index++;
            return true;
        }

        private boolean end() {
            return index >= input.length();
        }

        private IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " at offset " + index);
        }
    }
}
