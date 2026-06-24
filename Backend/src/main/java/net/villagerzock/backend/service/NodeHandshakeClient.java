package net.villagerzock.backend.service;

import net.villagerzock.backend.dto.ChartPointDto;
import net.villagerzock.backend.dto.CommandRequest;
import net.villagerzock.backend.dto.NetworkPointDto;
import net.villagerzock.backend.dto.LaunchServerRequest;
import net.villagerzock.backend.dto.LaunchServerResponse;
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
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;

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
    private final String loopbackHost;

    public NodeHandshakeClient(
            CloudCoreNodeRepository nodes,
            RestClient.Builder restClientBuilder,
            @Value("${cloudcore.handshake-port:8081}") int handshakePort,
            @Value("${cloudcore.loopback-host:127.0.0.1}") String loopbackHost
    ) {
        this.nodes = nodes;
        this.restClientBuilder = restClientBuilder;
        this.handshakePort = handshakePort;
        this.loopbackHost = loopbackHost;
    }

    public List<ServerDto> getServers(long nodeId) {
        return get(nodeId, "/servers", SERVER_LIST);
    }

    public LaunchServerResponse launchServer(long nodeId, LaunchServerRequest request) {
        try {
            return client(nodeId, Duration.ofSeconds(40)).post()
                    .uri("/servers")
                    .body(request)
                    .retrieve()
                    .body(LaunchServerResponse.class);
        } catch (RestClientException exception) {
            throw unavailable(nodeId, exception);
        }
    }

    public ServerDto getServer(long nodeId, String serverName) {
        return get(nodeId, "/servers/{serverName}", serverName, ServerDto.class);
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

    public void executeProxyCommand(long nodeId, String command) {
        post(nodeId, "/proxy/commands", command);
    }

    public void executeServerCommand(long nodeId, String server, String command) {
        post(nodeId, "/servers/{server}/commands", server, command);
    }

    public NodeMetadataDto getMetadata(long nodeId) {
        return get(nodeId, "/metadata", NodeMetadataDto.class);
    }

    public List<ChartPointDto> getProxyPlayerCount(long nodeId, String range) {
        return get(nodeId, "/proxy/metrics/player-count?range=" + range, CHART_LIST);
    }

    public List<NetworkPointDto> getProxyNetwork(long nodeId, String range) {
        return get(nodeId, "/proxy/metrics/network?range=" + range, NETWORK_LIST);
    }

    public List<ChartPointDto> getServerPlayerCount(long nodeId, String serverName) {
        return get(nodeId, "/servers/{serverName}/metrics/player-count", serverName, CHART_LIST);
    }

    public List<NetworkPointDto> getServerNetwork(long nodeId, String serverName) {
        return get(nodeId, "/servers/{serverName}/metrics/network", serverName, NETWORK_LIST);
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

    private <T> T get(long nodeId, String path, Object uriVariable, Class<T> responseType) {
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

    private void post(long nodeId, String path, String command) {
        try {
            client(nodeId).post()
                    .uri(path)
                    .body(new CommandRequest(command))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            throw unavailable(nodeId, exception);
        }
    }

    private void post(long nodeId, String path, Object uriVariable, String command) {
        try {
            client(nodeId).post()
                    .uri(path, uriVariable)
                    .body(new CommandRequest(command))
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException exception) {
            throw unavailable(nodeId, exception);
        }
    }

    private RestClient client(long nodeId) {
        return client(nodeId, Duration.ofSeconds(5));
    }

    private RestClient client(long nodeId, Duration readTimeout) {
        String ipAddress = nodes.findIpAddressById(nodeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Node not found"));
        String baseUrl = UriComponentsBuilder.newInstance()
                .scheme("http")
                .host(resolveHost(ipAddress))
                .port(handshakePort)
                .path("/core-handshake/v1")
                .build()
                .toUriString();
        java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(readTimeout);
        return restClientBuilder.clone()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    private String resolveHost(String ipAddress) {
        try {
            return InetAddress.getByName(ipAddress).isLoopbackAddress() ? loopbackHost : ipAddress;
        } catch (UnknownHostException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Node has an invalid IP address", exception);
        }
    }

    private ResponseStatusException unavailable(long nodeId, RestClientException cause) {
        return new ResponseStatusException(
                HttpStatus.BAD_GATEWAY,
                "CoreHandshake on node " + nodeId + " is unavailable",
                cause);
    }
}
