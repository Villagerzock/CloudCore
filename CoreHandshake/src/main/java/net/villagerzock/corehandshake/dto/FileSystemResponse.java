package net.villagerzock.corehandshake.dto;

public sealed interface FileSystemResponse
        permits FileResponse, FolderResponse {
    boolean isFile();
}
