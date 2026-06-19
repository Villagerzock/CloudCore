package net.villagerzock.backend.service;

import net.villagerzock.backend.dto.CloudCoreServerResponse;
import net.villagerzock.backend.dto.LinkCodeResponse;
import net.villagerzock.backend.entity.CloudCoreServer;
import net.villagerzock.backend.entity.CloudCoreNode;
import net.villagerzock.backend.entity.ServerLinkCode;
import net.villagerzock.backend.entity.UserAccount;
import net.villagerzock.backend.repository.CloudCoreServerRepository;
import net.villagerzock.backend.repository.CloudCoreNodeRepository;
import net.villagerzock.backend.repository.ServerLinkCodeRepository;
import net.villagerzock.backend.repository.UserAccountRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;

@Service
public class CloudCoreServerLinkService {
    private static final Duration CODE_LIFETIME = Duration.ofMinutes(5);
    private static final int CODE_SPACE = 1_000_000;
    private static final int MAX_GENERATION_ATTEMPTS = 20;

    private final UserAccountRepository users;
    private final CloudCoreServerRepository servers;
    private final ServerLinkCodeRepository linkCodes;
    private final CloudCoreNodeRepository nodes;
    private final LinkAttemptLimiter attemptLimiter;
    private final SecureRandom secureRandom = new SecureRandom();
    private final byte[] linkCodeSecret;

    public CloudCoreServerLinkService(
            UserAccountRepository users,
            CloudCoreServerRepository servers,
            ServerLinkCodeRepository linkCodes,
            CloudCoreNodeRepository nodes,
            LinkAttemptLimiter attemptLimiter,
            @Value("${cloudcore.link-code-secret}") String linkCodeSecret
    ) {
        if (linkCodeSecret.length() < 32) {
            throw new IllegalArgumentException("LINK_CODE_SECRET must contain at least 32 characters");
        }
        this.users = users;
        this.servers = servers;
        this.linkCodes = linkCodes;
        this.nodes = nodes;
        this.attemptLimiter = attemptLimiter;
        this.linkCodeSecret = linkCodeSecret.getBytes(StandardCharsets.UTF_8);
    }

    @Transactional
    public CloudCoreServerResponse createServer(String username, String name, String ipAddress) {
        UserAccount user = requireUser(username);
        String canonicalIp = canonicalizeIp(ipAddress);
        if (servers.existsByIpAddress(canonicalIp)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A server with this IP address already exists");
        }

        try {
            CloudCoreServer server = servers.saveAndFlush(new CloudCoreServer(
                    user,
                    name.trim(),
                    canonicalIp));
            return toResponse(server);
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A server with this IP address already exists",
                    exception);
        }
    }

    @Transactional
    public LinkCodeResponse generateCode(String username, UUID serverId) {
        CloudCoreServer server = servers.findByIdForUpdate(serverId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Server not found"));
        if (!server.getUser().getUsername().equalsIgnoreCase(username)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Server not found");
        }
        if (server.isLinked()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Server is already linked");
        }

        Instant now = Instant.now();
        Instant expiresAt = now.plus(CODE_LIFETIME);
        linkCodes.consumeActiveCodesForServer(serverId, now);
        linkCodes.deleteByExpiresAtBefore(now.minus(CODE_LIFETIME));

        for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
            String code = String.format(Locale.ROOT, "%06d", secureRandom.nextInt(CODE_SPACE));
            String codeHash = hash(code);
            if (linkCodes.existsByCodeHashAndConsumedAtIsNullAndExpiresAtAfter(codeHash, now)) {
                continue;
            }

            linkCodes.save(new ServerLinkCode(server, codeHash, expiresAt));
            return new LinkCodeResponse(serverId, code, expiresAt);
        }

        throw new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Could not allocate a link code");
    }

    @Transactional
    public CloudCoreServerResponse linkServer(String code, String requestIpAddress) {
        String canonicalRequestIp = canonicalizeIp(requestIpAddress);
        attemptLimiter.check(canonicalRequestIp);
        Instant now = Instant.now();
        ServerLinkCode linkCode = linkCodes.findActiveForUpdate(hash(code), now)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.BAD_REQUEST,
                        "Invalid or expired link code"));
        CloudCoreServer server = linkCode.getServer();

        if (!server.getIpAddress().equals(canonicalRequestIp)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Request IP does not match the registered server IP");
        }
        if (server.isLinked()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Server is already linked");
        }

        linkCode.consume(now);
        server.markLinked(now);
        nodes.save(new CloudCoreNode(server));
        attemptLimiter.clear(canonicalRequestIp);
        return toResponse(server);
    }

    private UserAccount requireUser(String username) {
        return users.findByUsernameIgnoreCase(username)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private String canonicalizeIp(String ipAddress) {
        String candidate = ipAddress == null ? "" : ipAddress.trim();
        if (candidate.isEmpty() || !candidate.matches("[0-9a-fA-F:.]+")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid IP address");
        }
        try {
            return InetAddress.getByName(candidate).getHostAddress();
        } catch (UnknownHostException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid IP address");
        }
    }

    private CloudCoreServerResponse toResponse(CloudCoreServer server) {
        return new CloudCoreServerResponse(
                server.getId(),
                server.getName(),
                server.getIpAddress(),
                server.isLinked(),
                server.getCreatedAt(),
                server.getLinkedAt());
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
