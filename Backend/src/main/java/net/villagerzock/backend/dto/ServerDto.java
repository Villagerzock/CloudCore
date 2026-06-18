package net.villagerzock.backend.dto;

public record ServerDto(long id, String name, String template, int online, int max) {
}
