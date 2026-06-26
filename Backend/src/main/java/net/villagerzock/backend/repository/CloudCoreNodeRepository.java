package net.villagerzock.backend.repository;

import net.villagerzock.backend.entity.CloudCoreNode;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CloudCoreNodeRepository extends JpaRepository<CloudCoreNode, Long> {
    boolean existsByIpAddress(String ipAddress);

    @Query("select node from CloudCoreNode node where node.publicId = :publicId")
    Optional<CloudCoreNode> findByPublicId(@Param("publicId") UUID publicId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select node from CloudCoreNode node where node.publicId = :publicId")
    Optional<CloudCoreNode> findByPublicIdForUpdate(@Param("publicId") UUID publicId);

    @Query("""
            select distinct node from CloudCoreNode node
            left join node.owner owner
            left join NodeUser nodeUser on nodeUser.node = node
            left join nodeUser.user user
            where (lower(user.username) = lower(:username)
                or lower(owner.username) = lower(:username))
              and node.linked = true
            order by node.id
            """)
    List<CloudCoreNode> findLinkedByUsername(@Param("username") String username);

    @Query("""
            select count(node) > 0 from CloudCoreNode node
            left join node.owner owner
            left join NodeUser nodeUser on nodeUser.node = node
            left join nodeUser.user user
            where node.id = :nodeId
              and (lower(user.username) = lower(:username)
                or lower(owner.username) = lower(:username))
              and node.linked = true
            """)
    boolean isAccessibleByUser(
            @Param("nodeId") Long nodeId,
            @Param("username") String username);

    @Query("""
            select count(node) > 0 from CloudCoreNode node
            left join node.owner owner
            left join NodeUser nodeUser on nodeUser.node = node
            left join nodeUser.user user
            where node.id = :nodeId
              and (lower(user.username) = lower(:username)
                or lower(owner.username) = lower(:username))
            """)
    boolean isMember(
            @Param("nodeId") Long nodeId,
            @Param("username") String username);

    @Query("""
            select count(node) > 0 from CloudCoreNode node
            join node.owner owner
            where node.id = :nodeId
              and lower(owner.username) = lower(:username)
            """)
    boolean isOwnedByUser(
            @Param("nodeId") Long nodeId,
            @Param("username") String username);

    @Query("select node.ipAddress from CloudCoreNode node where node.id = :nodeId")
    Optional<String> findIpAddressById(@Param("nodeId") Long nodeId);

    @Query("""
            select node.id from CloudCoreNode node
            where node.ipAddress = :ipAddress
              and node.linked = true
            """)
    List<Long> findLinkedNodeIdsByIpAddress(@Param("ipAddress") String ipAddress);

    @Query("""
            select node.id from CloudCoreNode node
            where node.ipAddress in ('127.0.0.1', '0:0:0:0:0:0:0:1', '::1')
              and node.linked = true
            """)
    List<Long> findLinkedLoopbackNodeIds();
}
