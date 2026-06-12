package net.villagerzock.velocity.controller;

import net.villagerzock.velocity.dto.CallbackDto;
import net.villagerzock.velocity.service.CallbackService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
public class CallbackController {
    private final CallbackService callbackService;

    public CallbackController(CallbackService callbackService) {
        this.callbackService = callbackService;
    }

    @PostMapping("/api/callback")
    public ResponseEntity<String> callback(@RequestBody CallbackDto callbackDto){
        callbackService.callback(callbackDto.uuid(),callbackDto.data());

        return ResponseEntity.ok("Callback Called");
    }
}
