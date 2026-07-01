package net.villagerzock.velocity.controller;

import jakarta.validation.Valid;
import net.villagerzock.velocity.dto.BannedPlayerDto;
import net.villagerzock.velocity.dto.ResolvedBanRequestDto;
import net.villagerzock.velocity.dto.UpdateBanRequestDto;
import net.villagerzock.velocity.service.BanService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/bans")
public class BanController {
    private final BanService banService;

    public BanController(BanService banService) {
        this.banService = banService;
    }

    @GetMapping
    public List<BannedPlayerDto> getBans() {
        return banService.getActiveBans();
    }

    @PostMapping
    public BannedPlayerDto createBan(@Valid @RequestBody ResolvedBanRequestDto request) {
        return banService.createBan(request);
    }

    @PatchMapping("/{uuid}")
    public BannedPlayerDto updateBan(
            @PathVariable UUID uuid,
            @Valid @RequestBody UpdateBanRequestDto request
    ) {
        return banService.updateBan(uuid, request);
    }

    @DeleteMapping("/{uuid}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBan(@PathVariable UUID uuid) {
        banService.deleteBan(uuid);
    }
}
