package net.villagerzock.cloudcore.core.server;

import java.io.IOException;
import java.nio.file.Path;

public interface IWorldType {
    void create(Path worldDir, String superflatType, String seed) throws IOException;
}
