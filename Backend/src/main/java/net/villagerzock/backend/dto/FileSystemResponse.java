package net.villagerzock.backend.dto;

public sealed interface FileSystemResponse
        permits FileResponse, FolderResponse {
    boolean isFile();
}
