package com.ratesentinel.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("RateSentinel API")
                        .version("1.0.0")
                        .description("""
                    ## Distributed Rate Limiting System
                    
                    RateSentinel is a production-grade distributed rate limiting 
                    system built with Spring Boot and Redis.
                    
                    ### Features
                    - **5 Algorithms** — Fixed Window, Sliding Window Log,
                      Sliding Window Counter, Token Bucket, Leaky Bucket
                    - **Per User Tier Limiting** — FREE, PRO, ENTERPRISE, INTERNAL
                    - **Circuit Breaker** — Graceful degradation when Redis is down
                    - **Custom @RateLimit Annotation** — Method level rate limiting
                    - **Real Time Dashboard** — Live traffic monitoring
                    - **Whitelist / Blacklist** — IP based access control
                    
                    ### How to Test Rate Limiting
                    Add these headers to your requests:
                    - `X-User-Id` — User identifier for per-user limiting
                    - `X-User-Tier` — FREE / PRO / ENTERPRISE / INTERNAL
                    - `X-Api-Key` — API key for key-based limiting
                    
                    ### Rate Limit Response Headers
                    - `X-RateLimit-Limit` — Maximum requests allowed
                    - `X-RateLimit-Remaining` — Requests remaining in window
                    - `X-RateLimit-Reset` — Unix timestamp when window resets
                    - `Retry-After` — Seconds to wait before retrying
                    """)
                        .contact(new Contact()
                                .name("Paras Jain")
                                .email("parasjain8103@gmail.com")
                                .url("https://github.com/ParasJain12"))
                        .license(new License()
                                .name("MIT License")))
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8080")
                                .description("Local Development Server")))
                .tags(List.of(
                        new Tag()
                                .name("Rate Limit Rules")
                                .description("Create, update, toggle and delete rate limit rules"),
                        new Tag()
                                .name("Analytics")
                                .description("Traffic analytics and dashboard data"),
                        new Tag()
                                .name("Tier Management")
                                .description("User tier configurations and limits"),
                        new Tag()
                                .name("Test Endpoints")
                                .description("Test endpoints to demo rate limiting"),
                        new Tag()
                                .name("Health")
                                .description("System health and circuit breaker status")
                ));
    }

}