package dev.grip.controller.route;

import dev.grip.controller.GripControllerProperties;
import dev.grip.controller.connect.AgentConnection;
import dev.grip.controller.connect.AgentRegistry;
import dev.grip.controller.connect.FrameSink;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ExternalRequestFilterTest {

    private final AgentRegistry registry = new AgentRegistry();
    private final HostRouter router = new HostRouter(registry,
            new GripControllerProperties("grip.example.com", null));
    private final RequestProxy proxy = mock(RequestProxy.class);
    private final ExternalRequestFilter filter = new ExternalRequestFilter(router, proxy);

    private MockHttpServletResponse run(String host) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/status");
        request.addHeader("Host", host);
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, mock(FilterChain.class));
        return response;
    }

    private void connect(String id) {
        registry.register(id, new AgentConnection("test", new FrameSink() {
            public void send(String line) { }
            public void close() { }
        }));
    }

    @Test
    void passesNonAgentHostsDownTheChain() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/actuator/health");
        request.addHeader("Host", "controller.internal");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void unknownAgentIs404() throws Exception {
        MockHttpServletResponse response = run("ghost.grip.example.com");
        assertThat(response.getStatus()).isEqualTo(404);
        assertThat(response.getContentType()).isEqualTo("application/problem+json");
        assertThat(response.getContentAsString()).contains("ghost");
    }

    @Test
    void disconnectedAgentIs503WithRetryAfter() throws Exception {
        connect("beta");
        registry.get("beta").orElseThrow().close("gone");

        MockHttpServletResponse response = run("beta.grip.example.com");

        assertThat(response.getStatus()).isEqualTo(503);
        assertThat(response.getHeader("Retry-After")).isEqualTo("5");
    }

    @Test
    void aResolvedRouteIsHandedToTheProxy() throws Exception {
        connect("alpha");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/status");
        request.addHeader("Host", "alpha.grip.example.com");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, mock(FilterChain.class));

        verify(proxy).proxy(eq(request), eq(response), eq("alpha"), any(AgentConnection.class));
    }
}
