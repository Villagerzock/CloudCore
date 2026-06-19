package net.villagerzock.backend.service;

import net.villagerzock.backend.dto.AuthResponse;
import net.villagerzock.backend.dto.LoginRequest;
import net.villagerzock.backend.dto.RegisterRequest;
import net.villagerzock.backend.entity.AuthToken;
import net.villagerzock.backend.entity.UserAccount;
import net.villagerzock.backend.repository.AuthTokenRepository;
import net.villagerzock.backend.repository.UserAccountRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

@Service
public class AuthService {
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
}
