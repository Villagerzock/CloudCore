package net.villagerzock.backend.dto;

import java.util.List;

public record AccountResponse(
        long id,
        String username,
        String email,
        List<SshKeyResponse> sshKeys
) {
    public record SshKeyResponse(int id, String key) {
    }
}
