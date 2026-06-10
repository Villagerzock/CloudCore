package net.villagerzock.cloudcore.core.server;

public enum ServerType {
    PAPER(true,false),
    FOLIA(true,false),
    FABRIC(false,true),
    VANILLA(false,false)
    ;
    private final boolean hasPlugins;
    private final boolean hasMods;

    ServerType(boolean hasPlugins, boolean hasMods) {
        this.hasPlugins = hasPlugins;
        this.hasMods = hasMods;
    }

    public boolean hasPlugins() {
        return hasPlugins;
    }

    public boolean isModded() {
        return hasMods;
    }
}
