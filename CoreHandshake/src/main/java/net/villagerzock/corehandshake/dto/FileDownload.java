package net.villagerzock.corehandshake.dto;

public record FileDownload(
        String name,
        String type,
        byte[] content
) {
}
