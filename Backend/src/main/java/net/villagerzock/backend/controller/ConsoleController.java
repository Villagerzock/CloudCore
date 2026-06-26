package net.villagerzock.backend.controller;

import jakarta.validation.Valid;
import net.villagerzock.backend.dto.CommandRequest;
import net.villagerzock.backend.security.NodePermission;
import net.villagerzock.backend.service.ConsoleService;
import net.villagerzock.backend.service.NodePermissionService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.http.HttpStatus;

import java.util.List;

@RestController
@RequestMapping("/api/console")
public class ConsoleController {
    private final ConsoleService consoleService;
    private final NodePermissionService permissions;

    public ConsoleController(ConsoleService consoleService, NodePermissionService permissions) {
        this.consoleService = consoleService;
        this.permissions = permissions;
    }

    @GetMapping("/{console}/logs")
    public List<String> getLogs(
            @RequestAttribute("cloudcore.nodeId") long nodeId,
            Authentication authentication,
            @PathVariable String console
    ) {
        permissions.require(authentication, nodeId, readPermission(console));
        return consoleService.getLogs(nodeId, console);
    }

    @PostMapping("/{console}/commands")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void execute(
            @RequestAttribute("cloudcore.nodeId") long nodeId,
            Authentication authentication,
            @PathVariable String console,
            @Valid @RequestBody CommandRequest request
    ) {
        permissions.require(authentication, nodeId, statusPermission(console));
        consoleService.execute(nodeId, console, request.command().trim());
    }

    private NodePermission readPermission(String console) {
        return "proxy".equalsIgnoreCase(console)
                ? NodePermission.PROXY_PAGE
                : NodePermission.SERVER_CONSOLE;
    }

    private NodePermission statusPermission(String console) {
        return "proxy".equalsIgnoreCase(console)
                ? NodePermission.PROXY_STATUS
                : NodePermission.SERVER_STATUS;
    }
}
