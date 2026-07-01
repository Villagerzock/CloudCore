package net.villagerzock.backend.service;

import net.villagerzock.backend.dto.AccountResponse;
import net.villagerzock.backend.dto.AddSshKeyRequest;
import net.villagerzock.backend.dto.UpdateAccountPasswordRequest;
import net.villagerzock.backend.dto.UpdateAccountProfileRequest;
import net.villagerzock.backend.dto.UpdateSshKeyRequest;
import net.villagerzock.backend.entity.UserAccount;
import net.villagerzock.backend.repository.UserAccountRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Base64;
import java.util.Locale;

@Service
public class AccountService {
    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;

    public AccountService(UserAccountRepository users, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public AccountResponse getAccount(String username) {
        return toResponse(requireUser(username));
    }

    @Transactional
    public AccountResponse updateProfile(String currentUsername, UpdateAccountProfileRequest request) {
        UserAccount user = requireUser(currentUsername);
        String username = request.username().trim();
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        users.findByUsernameIgnoreCase(username)
                .filter(candidate -> !candidate.getId().equals(user.getId()))
                .ifPresent(candidate -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Username is already in use");
                });
        users.findByEmailIgnoreCase(email)
                .filter(candidate -> !candidate.getId().equals(user.getId()))
                .ifPresent(candidate -> {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already in use");
                });

        user.setUsername(username);
        user.setEmail(email);
        return save(user);
    }

    @Transactional
    public AccountResponse updatePassword(String username, UpdateAccountPasswordRequest request) {
        UserAccount user = requireUser(username);
        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Current password is incorrect");
        }
        user.setPasswordHash(passwordEncoder.encode(request.newPassword()));
        return toResponse(user);
    }

    @Transactional
    public AccountResponse addSshKey(String username, AddSshKeyRequest request) {
        UserAccount user = requireUser(username);
        String key = normalizeSshKey(request.key());
        if (user.getSshKeys().stream().map(this::comparableSshKey).anyMatch(comparableSshKey(key)::equals)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "SSH key already exists");
        }
        user.getSshKeys().add(key);
        return toResponse(user);
    }

    @Transactional
    public AccountResponse updateSshKey(String username, int keyId, UpdateSshKeyRequest request) {
        UserAccount user = requireUser(username);
        if (keyId < 0 || keyId >= user.getSshKeys().size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "SSH key not found");
        }
        String key = normalizeSshKey(request.key());
        String comparable = comparableSshKey(key);
        for (int index = 0; index < user.getSshKeys().size(); index++) {
            if (index != keyId && comparableSshKey(user.getSshKeys().get(index)).equals(comparable)) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "SSH key already exists");
            }
        }
        user.getSshKeys().set(keyId, key);
        return toResponse(user);
    }

    @Transactional
    public AccountResponse deleteSshKey(String username, int keyId) {
        UserAccount user = requireUser(username);
        if (keyId < 0 || keyId >= user.getSshKeys().size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "SSH key not found");
        }
        user.getSshKeys().remove(keyId);
        return toResponse(user);
    }

    private UserAccount requireUser(String username) {
        return users.findByUsernameIgnoreCase(username)
                .filter(UserAccount::isEnabled)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
    }

    private AccountResponse save(UserAccount user) {
        try {
            return toResponse(users.saveAndFlush(user));
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Username or email is already in use",
                    exception);
        }
    }

    private AccountResponse toResponse(UserAccount user) {
        return new AccountResponse(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                java.util.stream.IntStream.range(0, user.getSshKeys().size())
                        .mapToObj(index -> new AccountResponse.SshKeyResponse(index, user.getSshKeys().get(index)))
                        .toList());
    }

    private String normalizeSshKey(String key) {
        String normalized = key.trim().replaceAll("\\s+", " ");
        String[] parts = normalized.split(" ", 3);
        if (parts.length < 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid SSH public key");
        }
        try {
            Base64.getDecoder().decode(parts[1]);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid SSH public key", exception);
        }
        return normalized;
    }

    private String comparableSshKey(String key) {
        String[] parts = key.trim().split("\\s+");
        if (parts.length < 2) {
            return key.trim();
        }
        return parts[0] + " " + parts[1];
    }
}
