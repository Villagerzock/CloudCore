package net.villagerzock.backend.repository;

import net.villagerzock.backend.entity.NodeRole;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface NodeRoleRepository extends JpaRepository<NodeRole, Long> {
    @Query("""
            select role from NodeRole role
            left join fetch role.previousRole
            where role.node.id = :nodeId
            """)
    List<NodeRole> findByNodeId(@Param("nodeId") long nodeId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            select role from NodeRole role
            left join fetch role.previousRole
            where role.node.id = :nodeId
            """)
    List<NodeRole> findByNodeIdForUpdate(@Param("nodeId") long nodeId);

    @Query("""
            select role from NodeRole role
            left join fetch role.previousRole
            where role.id = :roleId
              and role.node.id = :nodeId
            """)
    Optional<NodeRole> findByIdAndNodeId(
            @Param("roleId") long roleId,
            @Param("nodeId") long nodeId);
}
