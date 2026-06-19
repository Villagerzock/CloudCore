package net.villagerzock.backend.controller;

import net.villagerzock.backend.dto.ServerTemplateDto;
import net.villagerzock.backend.service.ServerService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/templates")
public class TemplateController {
    private final ServerService serverService;

    public TemplateController(ServerService serverService) {
        this.serverService = serverService;
    }

    @GetMapping
    public List<ServerTemplateDto> getTemplates(@RequestAttribute("cloudcore.nodeId") long nodeId) {
        return serverService.getTemplates(nodeId);
    }
}
