package dev.grip.controller.route;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Splits incoming requests: a {@code Host} of {@code <agent>.<base-domain>} is
 * an external request to be proxied; anything else (the Controller's own
 * hostname — actuator, the Agent WebSocket) passes straight through.
 *
 * <p>Stage 2a wires the routing decision and its error responses. Actual
 * proxying of a resolved route arrives with the next issue; until then a
 * resolved route returns {@code 502} with an explanatory detail.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class ExternalRequestFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ExternalRequestFilter.class);

    private final HostRouter router;
    private final RequestProxy proxy;

    public ExternalRequestFilter(HostRouter router, RequestProxy proxy) {
        this.router = router;
        this.proxy = proxy;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String host = request.getHeader("Host");
        if (!router.isAgentHost(host)) {
            chain.doFilter(request, response);
            return;
        }

        HostRouter.Route route = router.resolve(host);
        switch (route) {
            case HostRouter.Route.Resolved resolved -> {
                log.debug("{} {} -> agent {}", request.getMethod(), request.getRequestURI(), resolved.agentId());
                proxy.proxy(request, response, resolved.agentId(), resolved.connection());
            }
            case HostRouter.Route.UnknownAgent unknown -> ProblemResponse.write(response, 404, "Unknown agent",
                    "No agent named '" + unknown.agentId() + "' is registered.");
            case HostRouter.Route.AgentDisconnected disconnected -> {
                response.setHeader("Retry-After", "5");
                ProblemResponse.write(response, 503, "Agent disconnected",
                        "Agent '" + disconnected.agentId() + "' is not currently connected.");
            }
            case HostRouter.Route.NotAnAgentHost notAgent -> ProblemResponse.write(response, 404, "Unknown host",
                    "Host '" + notAgent.host() + "' does not map to an agent.");
        }
    }
}
