package net.villagerzock.backend.service;

import net.villagerzock.backend.dto.ChartPointDto;
import net.villagerzock.backend.dto.CommandRequest;
import net.villagerzock.backend.dto.NetworkPointDto;
import net.villagerzock.backend.dto.NodeMetadataDto;
import net.villagerzock.backend.dto.ServerDto;
import net.villagerzock.backend.dto.ServerTemplateDto;
import net.villagerzock.backend.repository.CloudCoreNodeRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;

@Service
public class NodeHandshakeClient {
    private static final ParameterizedTypeReference<List<ServerDto>> SERVER_LIST = new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<ServerTemplateDto>> TEMPLATE_LIST = new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<String>> STRING_LIST = new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<ChartPointDto>> CHART_LIST = new ParameterizedTypeReference<>() {};
    private static final ParameterizedTypeReference<List<NetworkPointDto>> NETWORK_LIST = new ParameterizedTypeReference<>() {};

    private final CloudCoreNodeRepository nodes;
    private final RestClient.Builder restClientBuilder;
    private final int handshakePort;

    public NodeHandshakeClient(
            CloudCoreNodeRepository nodes,
            RestClient.Builder restClientBuilder,
            @Value("${cloudcore.handshake-port:8081}") int handshakePort
    ) {
        this.nodes = nodes;
        this.restClientBuilder = restClientBuilder;
        this.handshakePort = handshakePort;
    }

    public List<ServerDto> getServers(long nodeId) {
        return get(nodeId, "/servers", SERVER_LIST);
    }

    public ServerDto getServer(long nodeId, long serverId) {
        return get(nodeId, "/servers/" + serverId, ServerDto.class);
    }

    public List<ServerTemplateDto> getTemplates(long nodeId) {
        return get(nodeId, "/templates", TEMPLATE_LIST);
    }

    public List<String> getProxyLogs(long nodeId) {
        return get(nodeId, "/proxy/logs", STRING_LIST);
    }

    public List<String> getServerLogs(long nodeId, String server) {
        return get(nodeId, "/servers/{server}/logs", server, STRING_LIST);
    }

    public List<String> executeProxyCommand(long nodeId, String command) {
        return post(nodeId, "/proxy/commands", command, STRING_LIST);
    }

    public List<String> executeServerCommand(long nodeId, String server, String command) {
        return post(nodeId, "/servers/{server}/commands", server, command, STRING_LIST);
    }

    public NodeMetadataDto getMetadata(long nodeId) {
        return get(nodeId, "/metadata", NodeMetadataDto.class);
    }

    public List<ChartPointDto> getProxyPlayerCount(long nodeId) {
        return get(nodeId, "/proxy/metrics/player-count", CHART_LIST);
    }

    public List<NetworkPointDto> getProxyNetwork(long nodeId) {
        return get(nodeId, "/proxy/metrics/network", NETWORK_LIST);
    }

    public List<ChartPointDto> getServerPlayerCount(long nodeId, long serverId) {
        return get(nodeId, "/servers/" + serverId + "/metrics/player-count", CHART_LIST);
    }

    public List<NetworkPointDto> getServerNetwork(long nodeId, long serverId) {
        return get(nodeId, "/servers/" + serverId + "/metrics/network", NETWORK_LIST);
    }

    private <T> T get(long nodeId, String path, Class<T> responseType) {
        try {
            return client(nodeId).get().uri(path).retrieve().body(responseType);
        } catch (RestClientException exception) {
            throw unavailable(nodeId, exception);
        }
    }

    private <T> T get(long nodeId, String path, ParameterizedTypeReference<T> responseType) {
        try {
            return client(nodeId).get().uri(path).retrieve().body(responseType);
        } catch (RestClientException exception) {
            throw unavailable(nodeId, exception);
        }
    }

    private <T> T get(
            long nodeId,
            String path,
            Object uriVariable,
            ParameterizedTypeReference<T> responseType
    ) {
        try {
            return client(nodeId).get().uri(path, uriVariable).retrieve().body(responseType);
        } catch (RestClientException exception) {
            throw unavailable(nodeId, exception);
        }
    }

    private <T> T post(long nodeId, String path, String command, ParameterizedTypeReference<T> responseType) {
        try {
            return client(nodeId).post()
                    .uri(path)
                    .body(new CommandRequest(command))
                    .retrieve()
                    .body(responseType);
        } catch (RestClientException exception) {
            throw unavailable(nodeId, exception);
        }
    }

    private <T> T post(
            long nodeId,
            String path,
            Object uriVariable,
            String command,
            ParameterizedTypeReference<T> responseType
    ) {
        try {
            return client(nodeId).post()
                    .uri(path, uriVariable)
                    .body(new CommandRequest(command))
                    .retrieve()
                    .body(responseType);
        } catch (RestClientException exception) {
            throw unavailable(nodeId, exception);
        }
    }

    private RestClient client(long nodeId) {
        String ipAddress = nodes.findIpAddressById(nodeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Node not found"));
        String baseUrl = UriComponentsBuilder.newInstance()
                .scheme("http")
                .host(ipAddress)
                .port(handshakePort)
                .path("/core-handshake/v1")
                .build()
                .toUriString();
        return restClientBuilder.clone().baseUrl(baseUrl).build();
    }

    private ResponseStatusException unavailable(long nodeId, RestClientException cause) {
        return new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "CoreHandshake on node " + nodeId + " is unavailable",
                cause);
    }
}
