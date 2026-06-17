package net.villagerzock.cloudcore.core.command.providers;

import net.villagerzock.cloudcore.core.command.SuggestionProvider;
import net.villagerzock.cloudcore.core.server.ServerManager;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ServerTemplateProvider implements SuggestionProvider {
    @Override
    public List<String> suggestions() {
        List<String> templates = new ArrayList<>();
        File templatesFolder = ServerManager.BASE_DIR.resolve("templates").toFile();
        for (File templateFolder : Objects.requireNonNull(templatesFolder.listFiles(File::isDirectory))){
            templates.add(templateFolder.getName());
        }
        return templates;
    }
}
