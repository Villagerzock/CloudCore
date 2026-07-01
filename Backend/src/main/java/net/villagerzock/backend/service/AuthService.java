package net.villagerzock.backend.service;

import net.villagerzock.backend.dto.AuthResponse;
import net.villagerzock.backend.dto.LoginRequest;
import net.villagerzock.backend.dto.RegisterRequest;
import net.villagerzock.backend.entity.AuthToken;
import net.villagerzock.backend.entity.UserAccount;
import net.villagerzock.backend.repository.AuthTokenRepository;
import net.villagerzock.backend.repository.UserAccountRepository;
import org.apache.sshd.common.config.keys.AuthorizedKeyEntry;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.config.keys.PublicKeyEntryResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class AuthService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthService.class);
    private static final Duration TOKEN_LIFETIME = Duration.ofDays(30);

    private final UserAccountRepository users;
    private final AuthTokenRepository tokens;
    private final PasswordEncoder passwordEncoder;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(
            UserAccountRepository users,
            AuthTokenRepository tokens,
            PasswordEncoder passwordEncoder
    ) {
        this.users = users;
        this.tokens = tokens;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String username = request.username().trim();
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByUsernameIgnoreCase(username)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Username is already in use");
        }
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is already in use");
        }

        try {
            UserAccount user = users.saveAndFlush(new UserAccount(
                    username,
                    email,
                    passwordEncoder.encode(request.password())));
            return issueToken(user);
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Username or email is already in use",
                    exception);
        }
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        UserAccount user = users.findByUsernameIgnoreCase(request.username().trim())
                .filter(UserAccount::isEnabled)
                .filter(account -> passwordEncoder.matches(request.password(), account.getPasswordHash()))
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Invalid username or password"));
        return issueToken(user);
    }

    @Transactional
    public void logout(String rawToken) {
        tokens.deleteByTokenHash(hashToken(rawToken));
    }

    private AuthResponse issueToken(UserAccount user) {
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        Instant expiresAt = Instant.now().plus(TOKEN_LIFETIME);
        tokens.deleteByExpiresAtBefore(Instant.now());
        tokens.save(new AuthToken(user, hashToken(rawToken), expiresAt));
        return new AuthResponse(rawToken, expiresAt, user.getUsername());
    }

    public static String hashToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.US_ASCII)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    public boolean checkPassword(String username, String password) {
        return users.findByUsernameIgnoreCase(username.trim())
                .filter(UserAccount::isEnabled)
                .filter(account -> passwordEncoder.matches(password, account.getPasswordHash()))
                .isPresent();
    }

    public Optional<UserAccount> findByUsername(String username){
        return users.findByUsernameIgnoreCase(username.trim());
    }
    @Transactional(readOnly = true)
    public boolean checkSshKey(String username, PublicKey incomingKey) {
        Optional<UserAccount> user = users.findByUsernameIgnoreCase(username);
        if (user.isEmpty() || !user.get().isEnabled()) {
            LOGGER.warn("Rejected SSH key for unknown or disabled user '{}'", username);
            return false;
        }

        boolean result = user.get().getSshKeys().stream()
                .anyMatch(storedKey -> matchesAuthorizedKey(storedKey, incomingKey));
        if (!result) {
            LOGGER.warn(
                    "No stored SSH key matched for user '{}' and incoming {} key fingerprint {}",
                    username,
                    incomingKey.getAlgorithm(),
                    KeyUtils.getFingerPrint(incomingKey));
        }
        return result;
    }

    private boolean matchesAuthorizedKey(String storedAuthorizedKey, PublicKey incomingKey) {
        if (storedAuthorizedKey == null || incomingKey == null) {
            return false;
        }

        try {
            AuthorizedKeyEntry entry = AuthorizedKeyEntry.parseAuthorizedKeyEntry(storedAuthorizedKey);
            if (entry == null) {
                return false;
            }

            PublicKey storedKey = entry.resolvePublicKey(null, PublicKeyEntryResolver.FAILING);
            return KeyUtils.compareKeys(storedKey, incomingKey);
        } catch (Exception exception) {
            LOGGER.warn("Failed to parse stored SSH key '{}': {}", storedAuthorizedKey, exception.getMessage());
            return false;
        }
    }
}
