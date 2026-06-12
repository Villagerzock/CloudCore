package net.villagerzock.velocity.dto;

import java.util.List;

public record LobbyResponseDto(String type, List<String> servers) {
}
