package com.ratesentinel.repository;

import com.ratesentinel.model.TierConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TierConfigRepository extends JpaRepository<TierConfig, Long> {

    Optional<TierConfig> findByTierNameAndIsActiveTrue(String tierName);

    boolean existsByTierName(String tierName);

}