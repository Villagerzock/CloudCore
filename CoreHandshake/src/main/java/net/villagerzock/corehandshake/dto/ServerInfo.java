package net.villagerzock.corehandshake.dto;

public record ServerInfo(String name, String template, int online, int max, boolean singleton) {
}
