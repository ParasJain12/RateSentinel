package com.ratesentinel.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class DashboardWebSocketService {

    private final SimpMessagingTemplate messagingTemplate;

    // Topic clients subscribe to
    private static final String RATE_LIMIT_TOPIC = "/topic/rate-limit-events";
    private static final String STATS_TOPIC = "/topic/stats";

    /**
     * Push rate limit event to all connected dashboard clients
     * Called every time a request is allowed or blocked
     * Async so it never slows down main request
     */
    @Async
    public void pushRateLimitEvent(RateLimitEvent event) {
        try {
            messagingTemplate.convertAndSend(RATE_LIMIT_TOPIC, event);
            log.debug("WebSocket event pushed - Type: {}, Identifier: {}",
                    event.getEventType(), event.getIdentifier());
        } catch (Exception e) {
            log.error("Failed to push WebSocket event: {}", e.getMessage());
        }
    }

    /**
     * Push updated stats to all connected clients
     * Called after every request to update live counters
     */
    @Async
    public void pushStats(long totalAllowed, long totalBlocked) {
        try {
            double blockRate = (totalAllowed + totalBlocked) > 0
                    ? (double) totalBlocked / (totalAllowed + totalBlocked) * 100
                    : 0;

            messagingTemplate.convertAndSend(STATS_TOPIC, new java.util.HashMap<>() {{
                put("totalAllowed", totalAllowed);
                put("totalBlocked", totalBlocked);
                put("blockRate", Math.round(blockRate * 100.0) / 100.0);
                put("timestamp", java.time.LocalDateTime.now().toString());
            }});
        } catch (Exception e) {
            log.error("Failed to push stats: {}", e.getMessage());
        }
    }

}