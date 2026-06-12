package net.villagerzock.velocity.service;

import com.fasterxml.jackson.core.ObjectCodec;
import lombok.Setter;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

@Service
public class CallbackService {
    public record Callback<T>(CompletableFuture<T> callback, Function<Map<String, Object>,T> value){
        public void call(Map<String, Object> data){
            callback.complete(value.apply(data));
        }
    }
    private final Map<UUID, Callback<?>> CALLBACKS = new HashMap<>();

    public <T> UUID createCallback(CompletableFuture<T> callback, Function<Map<String, Object>,T> value){
        UUID uuid = UUID.randomUUID();
        CALLBACKS.put(uuid,new Callback<>(callback,value));
        return uuid;
    }

    public void callback(UUID uuid, Map<String, Object> data){
        CALLBACKS.get(uuid).call(data);
    }
}
