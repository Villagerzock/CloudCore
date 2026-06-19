package net.villagerzock.backend.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import net.villagerzock.backend.repository.CloudCoreNodeRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;

@Component
public class NodeSelectionInterceptor implements HandlerInterceptor {
    private final CloudCoreNodeRepository nodes;

    public NodeSelectionInterceptor(CloudCoreNodeRepository nodes) {
        this.nodes = nodes;
    }

    @Override
    public boolean preHandle(
            HttpServletRequest request,
            HttpServletResponse response,
            Object handler
    ) throws IOException {
        String rawNodeId = request.getParameter("node");
        if (rawNodeId == null || rawNodeId.isBlank()) {
            response.sendError(HttpStatus.BAD_REQUEST.value(), "Query parameter 'node' is required");
            return false;
        }

        long nodeId;
        try {
            nodeId = Long.parseLong(rawNodeId);
        } catch (NumberFormatException exception) {
            response.sendError(HttpStatus.BAD_REQUEST.value(), "Query parameter 'node' must be a number");
            return false;
        }
        if (nodeId <= 0) {
            response.sendError(HttpStatus.BAD_REQUEST.value(), "Query parameter 'node' must be positive");
            return false;
        }

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            response.sendError(HttpStatus.UNAUTHORIZED.value());
            return false;
        }
        if (!nodes.isAccessibleByUser(nodeId, authentication.getName())) {
            response.sendError(HttpStatus.FORBIDDEN.value(), "Node is not accessible");
            return false;
        }

        request.setAttribute("cloudcore.nodeId", nodeId);
        return true;
    }
}
