package net.villagerzock.backend.service;

import net.villagerzock.backend.dto.ConsoleLogPushRequest;
import net.villagerzock.backend.repository.CloudCoreNodeRepository;
import net.villagerzock.backend.websocket.ConsoleWebSocketHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class ConsoleLogPushService {
    private final CloudCoreNodeRepository nodes;
    private final ConsoleWebSocketHandler webSocketHandler;
    private final String loopbackHost;

    public ConsoleLogPushService(
            CloudCoreNodeRepository nodes,
            ConsoleWebSocketHandler webSocketHandler,
            @Value("${cloudcore.loopback-host:127.0.0.1}") String loopbackHost
    ) {
        this.nodes = nodes;
        this.webSocketHandler = webSocketHandler;
        this.loopbackHost = loopbackHost;
    }

    public void push(String remoteAddress, ConsoleLogPushRequest request) {
        if (!"proxy".equals(request.console())
                && (!request.console().startsWith("server-")
                || request.console().length() == "server-".length())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Console must be 'proxy' or 'server-{server-name}'");
        }
        long nodeId = resolveNode(remoteAddress);
        webSocketHandler.broadcast(nodeId, request.console(), List.copyOf(request.lines()));
    }

    private long resolveNode(String remoteAddress) {
        String canonicalAddress = canonicalize(remoteAddress);
        List<Long> exactMatches = nodes.findLinkedNodeIdsByIpAddress(canonicalAddress);
        if (exactMatches.size() == 1) {
            return exactMatches.getFirst();
        }
        if (exactMatches.size() > 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Request IP matches multiple nodes");
        }

        if (isLocalHostSource(canonicalAddress)) {
            List<Long> loopbackMatches = nodes.findLinkedLoopbackNodeIds();
            if (loopbackMatches.size() == 1) {
                return loopbackMatches.getFirst();
            }
            if (loopbackMatches.size() > 1) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Host gateway matches multiple loopback nodes");
            }
        }

        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No linked node matches request IP");
    }

    private boolean isLocalHostSource(String canonicalAddress) {
        if (canonicalAddress.equals(canonicalize(loopbackHost))) {
            return true;
        }
        return defaultIpv4Gateways().contains(canonicalAddress);
    }

    private Set<String> defaultIpv4Gateways() {
        Set<String> gateways = new HashSet<>();
        Path routeTable = Path.of("/proc/net/route");
        if (!Files.isReadable(routeTable)) {
            return gateways;
        }
        try {
            for (String line : Files.readAllLines(routeTable)) {
                String[] columns = line.trim().split("\\s+");
                if (columns.length < 3 || !"00000000".equals(columns[1])) {
                    continue;
                }
                long gateway = Long.parseUnsignedLong(columns[2], 16);
                byte[] address = {
                        (byte) gateway,
                        (byte) (gateway >>> 8),
                        (byte) (gateway >>> 16),
                        (byte) (gateway >>> 24)
                };
                gateways.add(InetAddress.getByAddress(address).getHostAddress());
            }
        } catch (IOException | NumberFormatException ignored) {
            return Set.of();
        }
        return gateways;
    }

    private String canonicalize(String address) {
        try {
            return InetAddress.getByName(address).getHostAddress();
        } catch (UnknownHostException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid request IP", exception);
        }
    }
}
