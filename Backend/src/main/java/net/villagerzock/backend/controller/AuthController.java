package net.villagerzock.backend.controller;

import jakarta.validation.Valid;
import net.villagerzock.backend.dto.AuthResponse;
import net.villagerzock.backend.dto.LoginRequest;
import net.villagerzock.backend.dto.RegisterRequest;
import net.villagerzock.backend.service.AuthService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.http.HttpHeaders;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public AuthResponse register(@Valid @RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        if (!authorization.startsWith("Bearer ") || authorization.length() <= "Bearer ".length()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Bearer token is required");
        }
        authService.logout(authorization.substring("Bearer ".length()).trim());
    }
}
