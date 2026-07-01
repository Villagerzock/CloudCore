package net.villagerzock.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "uuid_to_username")
public class Username {
    @Id
    @Getter
    @Setter
    private UUID uuid;

    @Column
    @Getter
    @Setter
    private String username;


    @Column
    @Getter
    @Setter
    private Instant created;
}
