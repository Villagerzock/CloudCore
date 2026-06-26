package net.villagerzock.backend.entity;

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
@Table(name = "node_users", indexes = {
        @Index(name = "idx_node_users_node_user", columnList = "node_id,user_id", unique = true),
        @Index(name = "idx_node_users_user", columnList = "user_id"),
        @Index(name = "idx_node_users_role", columnList = "role_id")
})
public class NodeUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "node_id", nullable = false)
    private CloudCoreNode node;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private NodeRole role;

    protected NodeUser() {
    }

    public NodeUser(CloudCoreNode node, UserAccount user, NodeRole role) {
        this.node = node;
        this.user = user;
        this.role = role;
    }

    public Long getId() {
        return id;
    }

    public CloudCoreNode getNode() {
        return node;
    }

    public UserAccount getUser() {
        return user;
    }

    public NodeRole getRole() {
        return role;
    }

    public void setRole(NodeRole role) {
        this.role = role;
    }
}
