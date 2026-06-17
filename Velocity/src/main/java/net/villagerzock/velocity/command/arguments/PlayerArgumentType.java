package net.villagerzock.velocity.command.arguments;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import com.velocitypowered.api.proxy.Player;
import com.velocitypowered.api.proxy.ProxyServer;

import java.util.concurrent.CompletableFuture;

public class PlayerArgumentType implements ArgumentType<Player> {

    private final ProxyServer proxyServer;

    public PlayerArgumentType(ProxyServer proxyServer) {
        this.proxyServer = proxyServer;
    }

    @Override
    public Player parse(StringReader reader) throws CommandSyntaxException {
        String username = reader.readUnquotedString();

        return proxyServer.getPlayer(username)
                .orElseThrow(() -> CommandSyntaxException.BUILT_IN_EXCEPTIONS.dispatcherUnknownArgument()
                        .create());
    }

    @Override
    public <S> CompletableFuture<Suggestions> listSuggestions(
            CommandContext<S> context,
            SuggestionsBuilder builder
    ) {
        for (Player player : proxyServer.getAllPlayers()) {
            builder.suggest(player.getUsername());
        }

        return builder.buildFuture();
    }
}
