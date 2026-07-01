package net.villagerzock.backend.dto;

public record ServerDto(String name, String template, int online, int max, boolean singleton) {
}
