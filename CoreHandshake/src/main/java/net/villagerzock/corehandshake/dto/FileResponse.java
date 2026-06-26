package net.villagerzock.corehandshake.dto;

public record FileResponse(
        boolean isFile,
        String type,
        boolean binary,
        String contentUrl,
        boolean tooLarge,
        long sizeBytes,
        String downloadUrl
) implements FileSystemResponse {

    public FileResponse(String type, boolean binary, boolean tooLarge, long sizeBytes, String contentUrl, String downloadUrl) {
        this(true, type, binary, contentUrl, tooLarge, sizeBytes, downloadUrl);
    }
}
