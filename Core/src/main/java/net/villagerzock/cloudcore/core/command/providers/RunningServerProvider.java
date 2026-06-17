package net.villagerzock.cloudcore.core.command.providers;

import net.villagerzock.cloudcore.core.command.SuggestionProvider;
import net.villagerzock.cloudcore.core.server.ServerManager;

import java.util.ArrayList;
import java.util.List;

public class RunningServerProvider implements SuggestionProvider {
    @Override
    public List<String> suggestions() {
        return new ArrayList<>(ServerManager.getRunningServers().keySet());
    }
}
