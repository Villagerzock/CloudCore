package net.villagerzock.cloudcore.core.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.villagerzock.cloudcore.core.server.ProxyServerManager;
import net.villagerzock.cloudcore.core.server.ServerManager;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

public class ApiServer {
    private static HttpServer server;
    public static void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(8008), 0);

        server.createContext("/started", ApiServer::started);
        server.createContext("/start", ApiServer::startServer);

        server.start();
    }

    private static void startServer(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("POST")) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }
        Map<String, String> queryParams = new HashMap<>();

        String query = exchange.getRequestURI().getQuery();
        if (query != null) {
            for (String param : query.split("&")) {
                String[] parts = param.split("=", 2);

                String key = URLDecoder.decode(parts[0], StandardCharsets.UTF_8);
                String value = parts.length > 1
                        ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8)
                        : "";

                queryParams.put(key, value);
            }
        }

        String server = queryParams.get("server");
        int amount = Integer.parseInt(queryParams.getOrDefault("amount", "1"));
        String callback = queryParams.get("callback");

        System.out.println("Server: " + server);
        System.out.println("Amount: " + amount);


        for (int i = 0; i<amount; i++){
            try {
                ServerManager.ServerLaunchResult launchResult = ServerManager.launchServer(server,false).get();
                if (callback != null){
                    launchResult.started().thenAcceptAsync((s)->{
                        ProxyServerManager.getInstance().callback(UUID.fromString(callback), Map.of("server",s.name()));
                    });
                }
            } catch (InterruptedException | ExecutionException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static void started(HttpExchange exchange) throws IOException {
        if (!exchange.getRequestMethod().equals("POST")) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }

        ServerManager.WAIT_FOR_SPRING_START.complete(null);

        exchange.sendResponseHeaders(204, -1);
        exchange.close();
    }

    public static void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }
}
