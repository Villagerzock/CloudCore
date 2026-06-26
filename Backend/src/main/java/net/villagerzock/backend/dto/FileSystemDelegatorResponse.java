package net.villagerzock.backend.dto;

import java.util.List;

public record FileSystemDelegatorResponse(
        boolean isFile,
        String type,
        Boolean binary,
        String contentUrl,
        Boolean tooLarge,
        Long sizeBytes,
        String downloadUrl,
        List<FolderResponse.FileInFolder> files
) {
    public FileSystemResponse toResponse(String contentUrl, String downloadUrl) {
        if (isFile) {
            return new FileResponse(
                    type,
                    Boolean.TRUE.equals(binary),
                    contentUrl,
                    Boolean.TRUE.equals(tooLarge),
                    sizeBytes == null ? 0 : sizeBytes,
                    downloadUrl);
        }
        return new FolderResponse(files == null ? List.of() : files);
    }
}
