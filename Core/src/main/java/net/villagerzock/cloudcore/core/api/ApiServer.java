package net.villagerzock.cloudcore.core.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import net.villagerzock.cloudcore.core.server.ServerManager;

import java.io.IOException;
import java.net.InetSocketAddress;

public class ApiServer {

    public static void start() throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(8008), 0);

        server.createContext("/started", ApiServer::started);
        //server.createContext("/unregister", ApiServer::unregister);

        server.start();
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

}
