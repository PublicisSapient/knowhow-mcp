/*
 *  Copyright 2024 <Sapient Corporation>
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and limitations under the
 *  License.
 */

package com.publicissapient.knowhow.knowhow.controller;

import com.publicissapient.knowhow.knowhow.repository.EmbeddingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Health check endpoint to monitor service availability
 */
@RestController
@RequestMapping("/health")
@CrossOrigin(origins = "*")
public class HealthController {

    @Autowired
    private EmbeddingRepository embeddingRepository;

    @GetMapping
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", System.currentTimeMillis());

        Map<String, String> services = new HashMap<>();

        // Check database connectivity
        try {
            embeddingRepository.count();
            services.put("database", "UP");
        } catch (Exception e) {
            services.put("database", "DOWN");
            health.put("status", "DEGRADED");
        }

        // Note: We don't check LLM here as it requires API calls
        // LLM availability is checked on-demand during actual requests
        services.put("llm", "UNKNOWN");

        health.put("services", services);

        HttpStatus status = "UP".equals(health.get("status")) ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return new ResponseEntity<>(health, status);
    }

    @GetMapping("/database")
    public ResponseEntity<Map<String, Object>> databaseHealth() {
        Map<String, Object> health = new HashMap<>();
        health.put("service", "database");
        health.put("timestamp", System.currentTimeMillis());

        try {
            embeddingRepository.count();
            health.put("status", "UP");
            health.put("message", "Database connection is healthy");
            return new ResponseEntity<>(health, HttpStatus.OK);
        } catch (Exception e) {
            health.put("status", "DOWN");
            health.put("message", "Database connection failed");
            health.put("error", e.getMessage());
            return new ResponseEntity<>(health, HttpStatus.SERVICE_UNAVAILABLE);
        }
    }
}
