package net.villagerzock.cloudcore.api;

public interface ProxyServerManager {
    void register(String name, String base, String host, int port);
    void unregister(String name, String fallback);

    static ProxyServerManager getInstance(){
        return null;
    }
}
