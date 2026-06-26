package net.villagerzock.backend.repository;

import net.villagerzock.backend.entity.NodeUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface NodeUserRepository extends JpaRepository<NodeUser, Long> {
    boolean existsByNodeIdAndUserId(long nodeId, long userId);

    @Query("""
            select nodeUser.role.permissions from NodeUser nodeUser
            join nodeUser.user user
            where nodeUser.node.id = :nodeId
              and lower(user.username) = lower(:username)
            """)
    List<Integer> findPermissionValues(
            @Param("nodeId") long nodeId,
            @Param("username") String username);

    @Query("""
            select nodeUser.role.id from NodeUser nodeUser
            join nodeUser.user user
            where nodeUser.node.id = :nodeId
              and lower(user.username) = lower(:username)
            """)
    List<Long> findRoleIds(
            @Param("nodeId") long nodeId,
            @Param("username") String username);

    @Query("""
            select nodeUser from NodeUser nodeUser
            join fetch nodeUser.user
            join fetch nodeUser.role
            join fetch nodeUser.node node
            left join fetch node.owner
            where nodeUser.node.id = :nodeId
            order by nodeUser.user.username
            """)
    List<NodeUser> findByNodeId(@Param("nodeId") long nodeId);

    @Query("""
            select nodeUser from NodeUser nodeUser
            join fetch nodeUser.user
            join fetch nodeUser.role
            join fetch nodeUser.node node
            left join fetch node.owner
            where nodeUser.node.id = :nodeId
              and nodeUser.id = :userId
            """)
    Optional<NodeUser> findByIdAndNodeId(
            @Param("userId") long userId,
            @Param("nodeId") long nodeId);

    @Query("""
            select nodeUser from NodeUser nodeUser
            join fetch nodeUser.user user
            join fetch nodeUser.role
            join fetch nodeUser.node node
            left join fetch node.owner
            where nodeUser.node.id = :nodeId
              and lower(user.username) = lower(:username)
            """)
    Optional<NodeUser> findByNodeIdAndUsername(
            @Param("nodeId") long nodeId,
            @Param("username") String username);
}
