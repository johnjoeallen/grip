/**
 * Shared GRIP protocol definitions.
 *
 * <p>This module is intentionally tiny. It holds only the vocabulary that both
 * the Controller and the Agent must agree on — frame kinds, channel identity,
 * a few limits — and deliberately <em>not</em> a wire format, a codec, or any
 * transport code. Those are decided incrementally in the staged implementation
 * (see {@code docs/protocol.md}).
 *
 * <p>Nothing here may depend on Spring, on an HTTP client/server library, or on
 * Controller- or Agent-specific types.
 */
package dev.grip.protocol;
