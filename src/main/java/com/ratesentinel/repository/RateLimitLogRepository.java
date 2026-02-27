package com.ratesentinel.repository;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.ratesentinel.model.RateLimitLog;

@Repository
public interface RateLimitLogRepository extends JpaRepository<RateLimitLog, Long> {

    // Find logs by identifier (user/IP)
    List<RateLimitLog> findByIdentifierOrderByCreatedAtDesc(String identifier);

    // Find logs by decision (ALLOW or BLOCK)
    List<RateLimitLog> findByDecision(String decision);

    // Find logs by endpoint
    List<RateLimitLog> findByEndpoint(String endpoint);

    // Count blocked requests for an identifier in last N minutes
    // Used for alerting — if someone is blocked 50+ times, send alert
    @Query("SELECT COUNT(l) FROM RateLimitLog l WHERE l.identifier = :identifier " +
           "AND l.decision = 'BLOCK' AND l.createdAt >= :since")
    Long countBlockedRequestsSince(
        @Param("identifier") String identifier,
        @Param("since") LocalDateTime since);

    // Get top blocked identifiers for dashboard
    @Query("SELECT l.identifier, COUNT(l) as blockCount FROM RateLimitLog l " +
           "WHERE l.decision = 'BLOCK' AND l.createdAt >= :since " +
           "GROUP BY l.identifier ORDER BY blockCount DESC")
    List<Object[]> findTopBlockedIdentifiers(@Param("since") LocalDateTime since);

    // Get hourly traffic stats for dashboard graph
    @Query("SELECT HOUR(l.createdAt), l.decision, COUNT(l) FROM RateLimitLog l " +
           "WHERE l.createdAt >= :since GROUP BY HOUR(l.createdAt), l.decision")
    List<Object[]> getHourlyStats(@Param("since") LocalDateTime since);

    // Find logs between two timestamps
    List<RateLimitLog> findByCreatedAtBetweenOrderByCreatedAtDesc(
        LocalDateTime start, LocalDateTime end);

    // Count total allowed vs blocked today
    @Query("SELECT l.decision, COUNT(l) FROM RateLimitLog l " +
           "WHERE l.createdAt >= :since GROUP BY l.decision")
    List<Object[]> countByDecisionSince(@Param("since") LocalDateTime since);
}
