package net.villagerzock.cloudcore.api.impl;

import net.villagerzock.cloudcore.api.ProxyServerManager;

public class ProxyServerManagerImpl implements ProxyServerManager {
    private static final ProxyServerManagerImpl INSTANCE = new ProxyServerManagerImpl();

    @Override
    public void register(String name, String base, String host, int port) {

    }

    @Override
    public void unregister(String name, String fallback) {

    }
}
