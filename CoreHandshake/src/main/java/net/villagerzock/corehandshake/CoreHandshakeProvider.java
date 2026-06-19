package net.villagerzock.corehandshake;

import net.villagerzock.corehandshake.dto.ChartPoint;
import net.villagerzock.corehandshake.dto.NetworkPoint;
import net.villagerzock.corehandshake.dto.NodeMetadata;
import net.villagerzock.corehandshake.dto.ServerInfo;
import net.villagerzock.corehandshake.dto.ServerTemplate;

import java.util.List;
import java.util.Optional;

/**
 * Implemented by Core and exposed as a Spring bean when Core starts Spring.
 */
public interface CoreHandshakeProvider {
    List<ServerInfo> getRunningServers();

    Optional<ServerInfo> getServer(long serverId);

    List<ServerTemplate> getTemplates();

    List<String> getProxyLogs();

    List<String> getServerLogs(String server);

    List<String> executeProxyCommand(String command);

    List<String> executeServerCommand(String server, String command);

    NodeMetadata getMetadata();

    List<ChartPoint> getProxyPlayerCount();

    List<NetworkPoint> getProxyNetwork();

    List<ChartPoint> getServerPlayerCount(long serverId);

    List<NetworkPoint> getServerNetwork(long serverId);
}
