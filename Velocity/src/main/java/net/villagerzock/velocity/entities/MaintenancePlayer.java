package net.villagerzock.velocity.entities;

import io.swagger.v3.oas.annotations.tags.Tags;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "maintenance_user")
public class MaintenancePlayer {
    @Id
    @Getter
    @Setter
    private UUID uuid;

    @Getter
    @Setter
    @Column
    private UUID addedBy;

    @Getter
    @Setter
    @Column
    private String username;

    @Getter
    @Setter
    @Column
    private Instant addedOn;
}
