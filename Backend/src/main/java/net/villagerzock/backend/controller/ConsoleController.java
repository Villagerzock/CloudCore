package net.villagerzock.backend.controller;

import jakarta.validation.Valid;
import net.villagerzock.backend.dto.CommandRequest;
import net.villagerzock.backend.service.ConsoleService;
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

    public ConsoleController(ConsoleService consoleService) {
        this.consoleService = consoleService;
    }

    @GetMapping("/{console}/logs")
    public List<String> getLogs(
            @RequestAttribute("cloudcore.nodeId") long nodeId,
            @PathVariable String console
    ) {
        return consoleService.getLogs(nodeId, console);
    }

    @PostMapping("/{console}/commands")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void execute(
            @RequestAttribute("cloudcore.nodeId") long nodeId,
            @PathVariable String console,
            @Valid @RequestBody CommandRequest request
    ) {
        consoleService.execute(nodeId, console, request.command().trim());
    }
}
