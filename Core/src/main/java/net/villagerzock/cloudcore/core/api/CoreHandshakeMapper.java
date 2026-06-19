package net.villagerzock.cloudcore.core.api;

import net.villagerzock.cloudcore.core.server.ServerManager;
import net.villagerzock.corehandshake.dto.ServerInfo;

public class CoreHandshakeMapper {
    public static ServerInfo runningToInfo(ServerManager.RunningServer server){
        if (server == null) return null;
        return new ServerInfo(server.name(),server.templateName(),0,0);
    }
}
