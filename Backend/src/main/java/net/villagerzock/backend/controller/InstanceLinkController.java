package net.villagerzock.backend.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import net.villagerzock.backend.dto.LinkCodeResponse;
import net.villagerzock.backend.dto.LinkInstanceRequest;
import net.villagerzock.backend.dto.LinkedInstanceResponse;
import net.villagerzock.backend.service.InstanceLinkService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class InstanceLinkController {
    private final InstanceLinkService instanceLinkService;

    public InstanceLinkController(InstanceLinkService instanceLinkService) {
        this.instanceLinkService = instanceLinkService;
    }

    @PostMapping("/link-codes")
    @ResponseStatus(HttpStatus.CREATED)
    public LinkCodeResponse generateCode(Authentication authentication) {
        return instanceLinkService.generateCode(authentication.getName());
    }

    @PostMapping("/instances/link")
    @ResponseStatus(HttpStatus.CREATED)
    public LinkedInstanceResponse linkInstance(
            @Valid @RequestBody LinkInstanceRequest request,
            HttpServletRequest servletRequest
    ) {
        return instanceLinkService.linkInstance(
                request.code(),
                request.name(),
                servletRequest.getRemoteAddr());
    }
}
