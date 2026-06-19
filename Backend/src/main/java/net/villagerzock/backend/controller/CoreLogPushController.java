package net.villagerzock.backend.controller;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import net.villagerzock.backend.dto.ConsoleLogPushRequest;
import net.villagerzock.backend.service.ConsoleLogPushService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/core/logs")
public class CoreLogPushController {
    private final ConsoleLogPushService pushService;

    public CoreLogPushController(ConsoleLogPushService pushService) {
        this.pushService = pushService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void push(
            HttpServletRequest servletRequest,
            @Valid @RequestBody ConsoleLogPushRequest request
    ) {
        pushService.push(servletRequest.getRemoteAddr(), request);
    }
}
