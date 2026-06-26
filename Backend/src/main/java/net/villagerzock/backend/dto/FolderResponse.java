package net.villagerzock.backend.dto;

import java.util.List;

public record FolderResponse(
        boolean isFile,
        List<FileInFolder> files
) implements FileSystemResponse {

    public record FileInFolder(
            String name,
            boolean isFile
    ) {}

    public FolderResponse(List<FileInFolder> files) {
        this(false, files);
    }
}
