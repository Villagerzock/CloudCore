package net.villagerzock.backend.repository;

import net.villagerzock.backend.entity.CloudCoreInstance;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface CloudCoreInstanceRepository extends JpaRepository<CloudCoreInstance, UUID> {
    List<CloudCoreInstance> findAllByUserIdOrderByLinkedAtDesc(Long userId);
}
