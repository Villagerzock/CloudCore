package net.villagerzock.corehandshake;

import net.villagerzock.corehandshake.dto.ChartPoint;
import net.villagerzock.corehandshake.dto.AddMaintenancePlayerRequest;
import net.villagerzock.corehandshake.dto.BannedPlayer;
import net.villagerzock.corehandshake.dto.CreateBannedPlayerRequest;
import net.villagerzock.corehandshake.dto.CreateTemplateRequest;
import net.villagerzock.corehandshake.dto.FileDownload;
import net.villagerzock.corehandshake.dto.FileSystemResponse;
import net.villagerzock.corehandshake.dto.MatchmakingConfiguration;
import net.villagerzock.corehandshake.dto.MaintenanceStatus;
import net.villagerzock.corehandshake.dto.NetworkPoint;
import net.villagerzock.corehandshake.dto.NodeMetadata;
import net.villagerzock.corehandshake.dto.SaveTemplateFileRequest;
import net.villagerzock.corehandshake.dto.ServerInfo;
import net.villagerzock.corehandshake.dto.ServerTemplate;
import net.villagerzock.corehandshake.dto.UpdateBannedPlayerRequest;
import net.villagerzock.corehandshake.dto.UploadTemplateFileRequest;

import java.util.List;
import java.util.Optional;

/**
 * Implemented by Core and exposed as a Spring bean when Core starts Spring.
 */
public interface CoreHandshakeProvider {
    List<ServerInfo> getRunningServers();

    Optional<ServerInfo> getServer(String serverName);

    String launchServer(String template, boolean singleton);

    void stopServer(String serverName);

    String restartServer(String serverName);

    void startProxy();

    void stopProxy();

    void restartProxy();

    List<ServerTemplate> getTemplates();

    ServerTemplate createTemplate(CreateTemplateRequest request);

    List<MatchmakingConfiguration> getMatchmakingConfigurations();

    MatchmakingConfiguration saveMatchmakingConfiguration(MatchmakingConfiguration configuration);

    void deleteMatchmakingConfiguration(String name);

    MaintenanceStatus getMaintenanceStatus();

    MaintenanceStatus setMaintenanceActive(boolean active);

    MaintenanceStatus addMaintenancePlayer(AddMaintenancePlayerRequest request);

    MaintenanceStatus removeMaintenancePlayer(String uuid);

    List<BannedPlayer> getBannedPlayers();

    BannedPlayer createBan(CreateBannedPlayerRequest request);

    BannedPlayer updateBan(String uuid, UpdateBannedPlayerRequest request);

    void deleteBan(String uuid);

    FileSystemResponse getTemplateFileSystemPath(String template, String path);

    FileDownload downloadTemplateFile(String template, String path);

    FileDownload getTemplateFileContent(String template, String path);

    FileSystemResponse saveTemplateFile(String template, String path, SaveTemplateFileRequest request);

    FileSystemResponse uploadTemplateFile(String template, String folderPath, UploadTemplateFileRequest request);

    void deleteTemplatePath(String template, String path);

    FileSystemResponse createTemplateFolder(String template, String folderPath, String folderName);

    FileSystemResponse copyTemplatePath(String template, String sourcePath, String destinationFolderPath);

    FileSystemResponse moveTemplatePath(String template, String sourcePath, String destinationFolderPath);

    FileSystemResponse renameTemplatePath(String template, String sourcePath, String newName);

    List<String> getProxyLogs();

    List<String> getServerLogs(String server);

    void executeProxyCommand(String command);

    void executeServerCommand(String server, String command);

    NodeMetadata getMetadata();

    List<ChartPoint> getProxyPlayerCount(MetricRange range);

    List<NetworkPoint> getProxyNetwork(MetricRange range);

    List<ChartPoint> getServerPlayerCount(String serverName);

    List<NetworkPoint> getServerNetwork(String serverName);
}
