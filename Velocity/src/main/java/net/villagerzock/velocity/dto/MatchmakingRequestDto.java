package net.villagerzock.velocity.dto;

import java.util.List;
import java.util.UUID;

public record MatchmakingRequestDto(List<UUID> partyOfPlayers, String serverType, int matchmakingValue) {
}
