package net.villagerzock.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "node_roles", indexes = {
        @Index(name = "idx_node_roles_node_name", columnList = "node_id,name", unique = true)
})
public class NodeRole {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "node_id", nullable = false)
    private CloudCoreNode node;

    @Column(nullable = false, length = 50)
    private String name;

    @Column(nullable = false)
    private int permissions;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "previous_role_id")
    private NodeRole previousRole;

    protected NodeRole() {
    }

    public NodeRole(CloudCoreNode node, String name, int permissions) {
        this.node = node;
        this.name = name;
        this.permissions = permissions;
    }

    public Long getId() {
        return id;
    }

    public CloudCoreNode getNode() {
        return node;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getPermissions() {
        return permissions;
    }

    public void setPermissions(int permissions) {
        this.permissions = permissions;
    }

    public NodeRole getPreviousRole() {
        return previousRole;
    }

    public void setPreviousRole(NodeRole previousRole) {
        this.previousRole = previousRole;
    }

    public boolean hasPermission(int permission) {
        return (permissions & permission) == permission;
    }
}
