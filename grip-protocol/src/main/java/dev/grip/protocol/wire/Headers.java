package dev.grip.protocol.wire;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.BiConsumer;

/**
 * An ordered list of header fields, allowing repeats. Names are compared
 * case-insensitively; order is preserved on the wire.
 */
public final class Headers {

    /** One header name/value pair. */
    public record Field(String name, String value) { }

    private final List<Field> fields;

    public Headers() {
        this.fields = new ArrayList<>();
    }

    public Headers(List<Field> fields) {
        this.fields = new ArrayList<>(fields);
    }

    public static Headers of() {
        return new Headers();
    }

    public Headers add(String name, String value) {
        fields.add(new Field(name, value));
        return this;
    }

    public List<Field> fields() {
        return List.copyOf(fields);
    }

    public int size() {
        return fields.size();
    }

    public List<String> values(String name) {
        List<String> out = new ArrayList<>();
        for (Field f : fields) {
            if (f.name().equalsIgnoreCase(name)) {
                out.add(f.value());
            }
        }
        return out;
    }

    public String first(String name) {
        for (Field f : fields) {
            if (f.name().equalsIgnoreCase(name)) {
                return f.value();
            }
        }
        return null;
    }

    public void forEach(BiConsumer<String, String> consumer) {
        for (Field f : fields) {
            consumer.accept(f.name(), f.value());
        }
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Headers h && fields.equals(h.fields);
    }

    @Override
    public int hashCode() {
        return fields.hashCode();
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Headers[");
        for (int i = 0; i < fields.size(); i++) {
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(fields.get(i).name().toLowerCase(Locale.ROOT)).append('=').append(fields.get(i).value());
        }
        return sb.append(']').toString();
    }
}
