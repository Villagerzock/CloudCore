package net.villagerzock.cloudcore.core.api;

import net.villagerzock.cloudcore.core.server.ServerManager;
import net.villagerzock.corehandshake.CoreHandshakeProvider;
import net.villagerzock.corehandshake.dto.*;

import java.util.List;
import java.util.Optional;

public class CoreHandshakeProviderImpl implements CoreHandshakeProvider {
    @Override
    public List<ServerInfo> getRunningServers() {
        return ServerManager.getRunningServers().values().stream().map(CoreHandshakeMapper::runningToInfo).toList();
    }

    @Override
    public Optional<ServerInfo> getServer(long serverId) {
        return Optional.empty();
    }

    @Override
    public List<ServerTemplate> getTemplates() {
        return List.of();
    }

    @Override
    public List<String> getProxyLogs() {
        return List.of();
    }

    @Override
    public List<String> getServerLogs(String server) {
        return List.of();
    }

    @Override
    public List<String> executeProxyCommand(String command) {
        return List.of();
    }

    @Override
    public List<String> executeServerCommand(String server, String command) {
        return List.of();
    }

    @Override
    public NodeMetadata getMetadata() {
        return null;
    }

    @Override
    public List<ChartPoint> getProxyPlayerCount() {
        return List.of();
    }

    @Override
    public List<NetworkPoint> getProxyNetwork() {
        return List.of();
    }

    @Override
    public List<ChartPoint> getServerPlayerCount(long serverId) {
        return List.of();
    }

    @Override
    public List<NetworkPoint> getServerNetwork(long serverId) {
        return List.of();
    }
}
