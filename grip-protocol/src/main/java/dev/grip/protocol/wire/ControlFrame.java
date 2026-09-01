package dev.grip.protocol.wire;

import java.util.List;

/**
 * <strong>Provisional</strong> line-oriented framing used until the real binary
 * codec lands in Stage 3. One frame per line, {@code \n}-terminated, tokens
 * separated by single spaces:
 *
 * <pre>
 *   REGISTER &lt;agentId&gt; &lt;protocolVersion&gt;
 *   REGISTER_OK
 *   REGISTER_REJECTED &lt;reason&gt;
 *   PING
 *   PONG
 *   BYE
 * </pre>
 *
 * This carries only Stage 1 connection-lifecycle traffic. It is deliberately
 * not extended to request/response proxying — that waits for the framed
 * protocol. See {@code docs/protocol.md}.
 *
 * @param verb the frame keyword, upper-case
 * @param args the remaining space-separated tokens
 */
public record ControlFrame(String verb, List<String> args) {

    public ControlFrame {
        if (verb == null || verb.isBlank()) {
            throw new IllegalArgumentException("verb required");
        }
        verb = verb.trim();
        args = args == null ? List.of() : List.copyOf(args);
    }

    public static ControlFrame of(String verb, String... args) {
        return new ControlFrame(verb, List.of(args));
    }

    /** Parses one line (without the trailing newline). */
    public static ControlFrame parse(String line) {
        String trimmed = line.strip();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("empty control frame");
        }
        String[] parts = trimmed.split(" ");
        return new ControlFrame(parts[0], List.of(parts).subList(1, parts.length));
    }

    /** The wire form, including the trailing newline. */
    public String encode() {
        StringBuilder sb = new StringBuilder(verb);
        for (String a : args) {
            if (a.contains(" ") || a.contains("\n")) {
                throw new IllegalArgumentException("control-frame arg may not contain space or newline: " + a);
            }
            sb.append(' ').append(a);
        }
        return sb.append('\n').toString();
    }

    public String arg(int index) {
        return index < args.size() ? args.get(index) : null;
    }

    public boolean is(String candidate) {
        return verb.equalsIgnoreCase(candidate);
    }
}
