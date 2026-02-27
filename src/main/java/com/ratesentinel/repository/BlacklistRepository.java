package com.ratesentinel.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ratesentinel.model.BlackListEntry;

@Repository
public interface BlacklistRepository extends JpaRepository<BlackListEntry, Long> {

    // Check if IP exists in blacklist
    Optional<BlackListEntry> findByIpAddress(String ipAddress);

    // Check existence directly — faster than fetching full object
    boolean existsByIpAddress(String ipAddress);
}
