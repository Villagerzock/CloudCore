package net.villagerzock.backend.repository;

import net.villagerzock.backend.entity.CloudCoreNode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CloudCoreNodeRepository extends JpaRepository<CloudCoreNode, Long> {
    @Query("""
            select node from CloudCoreNode node
            join fetch node.server server
            join fetch server.user user
            where lower(user.username) = lower(:username)
              and server.linked = true
            order by node.id
            """)
    List<CloudCoreNode> findLinkedByUsername(@Param("username") String username);

    @Query("""
            select count(node) > 0 from CloudCoreNode node
            where node.id = :nodeId
              and lower(node.server.user.username) = lower(:username)
              and node.server.linked = true
            """)
    boolean isAccessibleByUser(
            @Param("nodeId") Long nodeId,
            @Param("username") String username);

    @Query("select node.server.ipAddress from CloudCoreNode node where node.id = :nodeId")
    Optional<String> findIpAddressById(@Param("nodeId") Long nodeId);
}
