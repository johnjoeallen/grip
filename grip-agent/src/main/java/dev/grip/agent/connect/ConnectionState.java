package dev.grip.agent.connect;

/** Where the Agent's single outbound connection currently is. */
public enum ConnectionState {

    /** Not connected; no attempt in progress. */
    IDLE,

    /** A connect attempt is in flight (TLS, HTTP, REGISTER). */
    CONNECTING,

    /** Connected and REGISTER_OK received. */
    REGISTERED,

    /** The last attempt failed or the connection dropped. */
    DISCONNECTED
}
