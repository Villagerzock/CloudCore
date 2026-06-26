package net.villagerzock.backend.service;

import net.villagerzock.backend.dto.CloudCoreServerResponse;
import net.villagerzock.backend.dto.LinkCodeResponse;
import net.villagerzock.backend.entity.CloudCoreNode;
import net.villagerzock.backend.entity.NodeRole;
import net.villagerzock.backend.entity.NodeUser;
import net.villagerzock.backend.entity.ServerLinkCode;
import net.villagerzock.backend.entity.UserAccount;
import net.villagerzock.backend.repository.CloudCoreNodeRepository;
import net.villagerzock.backend.repository.NodeRoleRepository;
import net.villagerzock.backend.repository.NodeUserRepository;
import net.villagerzock.backend.repository.ServerLinkCodeRepository;
import net.villagerzock.backend.repository.UserAccountRepository;
import net.villagerzock.backend.security.NodePermission;
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
    private final CloudCoreNodeRepository nodes;
    private final NodeRoleRepository nodeRoles;
    private final NodeUserRepository nodeUsers;
    private final ServerLinkCodeRepository linkCodes;
    private final LinkAttemptLimiter attemptLimiter;
    private final SecureRandom secureRandom = new SecureRandom();
    private final byte[] linkCodeSecret;

    public CloudCoreServerLinkService(
            UserAccountRepository users,
            CloudCoreNodeRepository nodes,
            NodeRoleRepository nodeRoles,
            NodeUserRepository nodeUsers,
            ServerLinkCodeRepository linkCodes,
            LinkAttemptLimiter attemptLimiter,
            @Value("${cloudcore.link-code-secret}") String linkCodeSecret
    ) {
        if (linkCodeSecret.length() < 32) {
            throw new IllegalArgumentException("LINK_CODE_SECRET must contain at least 32 characters");
        }
        this.users = users;
        this.nodes = nodes;
        this.nodeRoles = nodeRoles;
        this.nodeUsers = nodeUsers;
        this.linkCodes = linkCodes;
        this.attemptLimiter = attemptLimiter;
        this.linkCodeSecret = linkCodeSecret.getBytes(StandardCharsets.UTF_8);
    }

    @Transactional
    public CloudCoreServerResponse createServer(String username, String name, String ipAddress) {
        UserAccount user = requireUser(username);
        String canonicalIp = canonicalizeIp(ipAddress);
        if (nodes.existsByIpAddress(canonicalIp)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A node with this IP address already exists");
        }

        try {
            CloudCoreNode node = nodes.saveAndFlush(new CloudCoreNode(name.trim(), canonicalIp));
            node.setOwner(user);
            NodeRole owner = nodeRoles.save(new NodeRole(node, "Owner", NodePermission.ALL));
            NodeRole defaultUser = new NodeRole(node, "User", NodePermission.DEFAULT_USER);
            defaultUser.setPreviousRole(owner);
            nodeRoles.save(defaultUser);
            nodeUsers.save(new NodeUser(node, user, owner));
            return toResponse(node);
        } catch (DataIntegrityViolationException exception) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "A node with this IP address already exists",
                    exception);
        }
    }

    @Transactional
    public LinkCodeResponse generateCode(String username, UUID nodePublicId) {
        CloudCoreNode node = nodes.findByPublicIdForUpdate(nodePublicId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Node not found"));
        if (!nodes.isMember(node.getId(), username)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Node not found");
        }
        if (node.isLinked()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Node is already linked");
        }

        Instant now = Instant.now();
        Instant expiresAt = now.plus(CODE_LIFETIME);
        linkCodes.consumeActiveCodesForNode(node.getId(), now);
        linkCodes.deleteByExpiresAtBefore(now.minus(CODE_LIFETIME));

        for (int attempt = 0; attempt < MAX_GENERATION_ATTEMPTS; attempt++) {
            String code = String.format(Locale.ROOT, "%06d", secureRandom.nextInt(CODE_SPACE));
            String codeHash = hash(code);
            if (linkCodes.existsByCodeHashAndConsumedAtIsNullAndExpiresAtAfter(codeHash, now)) {
                continue;
            }

            linkCodes.save(new ServerLinkCode(node, codeHash, expiresAt));
            return new LinkCodeResponse(node.getPublicId(), code, expiresAt);
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
        CloudCoreNode node = linkCode.getNode();

        if (!node.getIpAddress().equals(canonicalRequestIp)) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Request IP does not match the registered node IP");
        }
        if (node.isLinked()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Node is already linked");
        }

        linkCode.consume(now);
        node.markLinked(now);
        attemptLimiter.clear(canonicalRequestIp);
        return toResponse(node);
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

    private CloudCoreServerResponse toResponse(CloudCoreNode node) {
        return new CloudCoreServerResponse(
                node.getPublicId(),
                node.getName(),
                node.getIpAddress(),
                node.isLinked(),
                node.getCreatedAt(),
                node.getLinkedAt());
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
