package com.ratesentinel.service;

import com.ratesentinel.repository.RateLimitLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class AlertService {

    private final RateLimitLogRepository logRepository;
    private final JavaMailSender mailSender;

    @Value("${rate-sentinel.alert-threshold:50}")
    private int alertThreshold;

    @Value("${spring.mail.username}")
    private String alertEmail;

    /**
     * Check if an identifier has been blocked too many times
     * If yes, send an alert email
     */
    @Async
    public void checkAndAlert(String identifier, String endpoint) {
        try {
            LocalDateTime since = LocalDateTime.now().minusMinutes(10);
            Long blockedCount = logRepository
                    .countBlockedRequestsSince(identifier, since);

            if (blockedCount != null && blockedCount >= alertThreshold) {
                log.warn("ALERT THRESHOLD REACHED - Identifier: {}, " +
                        "Blocked {} times in last 10 minutes", identifier, blockedCount);
                sendAlertEmail(identifier, endpoint, blockedCount);
            }
        } catch (Exception e) {
            log.error("Failed to check alert threshold: {}", e.getMessage());
        }
    }

    private void sendAlertEmail(String identifier, String endpoint, Long count) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setTo(alertEmail);
            message.setSubject("RateSentinel Alert - Threshold Exceeded");
            message.setText(String.format(
                    "Rate limit alert!\n\n" +
                            "Identifier: %s\n" +
                            "Endpoint: %s\n" +
                            "Blocked requests in last 10 minutes: %d\n" +
                            "Threshold: %d\n\n" +
                            "Time: %s\n\n" +
                            "Please investigate immediately.",
                    identifier, endpoint, count, alertThreshold,
                    LocalDateTime.now()
            ));
            mailSender.send(message);
            log.info("Alert email sent for identifier: {}", identifier);
        } catch (Exception e) {
            log.error("Failed to send alert email: {}", e.getMessage());
        }
    }

}