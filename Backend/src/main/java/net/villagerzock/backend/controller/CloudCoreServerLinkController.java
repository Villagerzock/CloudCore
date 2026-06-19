package net.villagerzock.backend.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import net.villagerzock.backend.dto.CloudCoreServerResponse;
import net.villagerzock.backend.dto.CreateCloudCoreServerRequest;
import net.villagerzock.backend.dto.LinkCloudCoreServerRequest;
import net.villagerzock.backend.dto.LinkCodeResponse;
import net.villagerzock.backend.service.CloudCoreServerLinkService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/cloudcore-servers")
public class CloudCoreServerLinkController {
    private final CloudCoreServerLinkService linkService;

    public CloudCoreServerLinkController(CloudCoreServerLinkService linkService) {
        this.linkService = linkService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CloudCoreServerResponse createServer(
            Authentication authentication,
            @Valid @RequestBody CreateCloudCoreServerRequest request
    ) {
        return linkService.createServer(
                authentication.getName(),
                request.name(),
                request.ipAddress());
    }

    @PostMapping("/{serverId}/link-code")
    @ResponseStatus(HttpStatus.CREATED)
    public LinkCodeResponse generateCode(
            Authentication authentication,
            @PathVariable UUID serverId
    ) {
        return linkService.generateCode(authentication.getName(), serverId);
    }

    @PostMapping("/link")
    public CloudCoreServerResponse linkServer(
            @Valid @RequestBody LinkCloudCoreServerRequest request,
            HttpServletRequest servletRequest
    ) {
        return linkService.linkServer(request.code(), servletRequest.getRemoteAddr());
    }
}
