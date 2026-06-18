package net.villagerzock.backend.service;

import net.villagerzock.backend.dto.LinkCodeResponse;
import net.villagerzock.backend.dto.LinkedInstanceResponse;
import net.villagerzock.backend.entity.CloudCoreInstance;
import net.villagerzock.backend.entity.LinkCode;
import net.villagerzock.backend.entity.UserAccount;
import net.villagerzock.backend.repository.CloudCoreInstanceRepository;
import net.villagerzock.backend.repository.LinkCodeRepository;
import net.villagerzock.backend.repository.UserAccountRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;

@Service
public class InstanceLinkService {
    private static final Duration CODE_LIFETIME = Duration.ofMinutes(5);
    private static final int CODE_SPACE = 1_000_000;
    private static final int MAX_GENERATION_ATTEMPTS = 20;

    private final UserAccountRepository users;
    private final LinkCodeRepository linkCodes;
    private final CloudCoreInstanceRepository instances;
    private final LinkAttemptLimiter attemptLimiter;
    private final SecureRandom secureRandom = new SecureRandom();
    private final byte[] linkCodeSecret;

    public InstanceLinkService(
            UserAccountRepository users,
            LinkCodeRepository linkCodes,
            CloudCoreInstanceRepository instances,
            LinkAttemptLimiter attemptLimiter,
            @Value("${cloudcore.link-code-secret}") String linkCodeSecret
    ) {
        if (linkCodeSecret.length() < 32) {
            throw new IllegalArgumentException("LINK_CODE_SECRET must contain at least 32 characters");
        }
        this.users = users;
        this.linkCodes = linkCodes;
        this.instances = instances;
        this.attemptLimiter = attemptLimiter;
        this.linkCodeSecret = linkCodeSecret.getBytes(StandardCharsets.UTF_8);
    }

    @Transactional
    public LinkCodeResponse generateCode(String username) {
        UserAccount user = users.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        Instant now = Instant.now();
        Instant expiresAt = now.plus(CODE_LIFETIME);
        linkCodes.consumeActiveCodesForUser(user.getId(), now);
        linkCodes.deleteByExpiresAtBefore(now.minus(CODE_LIFETIME));

        for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
            String code = String.format(Locale.ROOT, "%06d", secureRandom.nextInt(CODE_SPACE));
            String codeHash = hash(code);
            if (linkCodes.existsByCodeHashAndConsumedAtIsNullAndExpiresAtAfter(codeHash, now)) {
                continue;
            }

            linkCodes.save(new LinkCode(user, codeHash, expiresAt));
            return new LinkCodeResponse(code, expiresAt);
        }

        throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Could not allocate a link code");
    }

    @Transactional
    public LinkedInstanceResponse linkInstance(String code, String name, String ipAddress) {
        attemptLimiter.check(ipAddress);
        Instant now = Instant.now();
        LinkCode linkCode = linkCodes.findActiveForUpdate(hash(code), now)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Invalid or expired link code"));

        linkCode.consume(now);
        CloudCoreInstance instance = instances.saveAndFlush(new CloudCoreInstance(
                linkCode.getUser(),
                name.trim(),
                ipAddress));
        attemptLimiter.clear(ipAddress);
        return new LinkedInstanceResponse(instance.getId(), instance.getName(), instance.getLinkedAt());
    }

    private String hash(String code) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(linkCodeSecret, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(code.getBytes(StandardCharsets.US_ASCII)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("HMAC-SHA-256 is unavailable", exception);
        }
    }
}
