package com.ratesentinel.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ratesentinel.model.WhiteListEntry;

@Repository
public interface WhitelistRepository extends JpaRepository<WhiteListEntry, Long> {

	// Check if IP exists in whitelist
    Optional<WhiteListEntry> findByIpAddress(String ipAddress);

    // Check existence directly — faster than fetching full object
    boolean existsByIpAddress(String ipAddress);
}
