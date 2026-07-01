package net.villagerzock.backend.controller;

import jakarta.validation.Valid;
import net.villagerzock.backend.dto.AccountResponse;
import net.villagerzock.backend.dto.AddSshKeyRequest;
import net.villagerzock.backend.dto.UpdateAccountPasswordRequest;
import net.villagerzock.backend.dto.UpdateAccountProfileRequest;
import net.villagerzock.backend.dto.UpdateSshKeyRequest;
import net.villagerzock.backend.service.AccountService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/account")
public class AccountController {
    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public AccountResponse getAccount(Authentication authentication) {
        return accountService.getAccount(authentication.getName());
    }

    @PatchMapping
    public AccountResponse updateProfile(
            Authentication authentication,
            @Valid @RequestBody UpdateAccountProfileRequest request
    ) {
        return accountService.updateProfile(authentication.getName(), request);
    }

    @PatchMapping("/password")
    public AccountResponse updatePassword(
            Authentication authentication,
            @Valid @RequestBody UpdateAccountPasswordRequest request
    ) {
        return accountService.updatePassword(authentication.getName(), request);
    }

    @PostMapping("/ssh-keys")
    public AccountResponse addSshKey(
            Authentication authentication,
            @Valid @RequestBody AddSshKeyRequest request
    ) {
        return accountService.addSshKey(authentication.getName(), request);
    }

    @PatchMapping("/ssh-keys/{keyId}")
    public AccountResponse updateSshKey(
            Authentication authentication,
            @PathVariable int keyId,
            @Valid @RequestBody UpdateSshKeyRequest request
    ) {
        return accountService.updateSshKey(authentication.getName(), keyId, request);
    }

    @DeleteMapping("/ssh-keys/{keyId}")
    public AccountResponse deleteSshKey(
            Authentication authentication,
            @PathVariable int keyId
    ) {
        return accountService.deleteSshKey(authentication.getName(), keyId);
    }
}
