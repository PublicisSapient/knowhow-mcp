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

package com.publicissapient.knowhow.knowhow.exception;

import com.publicissapient.knowhow.knowhow.service.ErrorNotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.context.request.WebRequest;

import java.util.HashMap;
import java.util.Map;

@ControllerAdvice
public class GlobalExceptionHandler {

    @Autowired
    private ErrorNotificationService errorNotificationService;

    @ExceptionHandler(LLMServiceException.class)
    public ResponseEntity<Map<String, String>> handleLLMServiceException(LLMServiceException ex, WebRequest request) {
        String context = "Request URI: " + request.getDescription(false);
        errorNotificationService.sendErrorNotification("LLM Service", ex, context);

        Map<String, String> error = new HashMap<>();
        error.put("error", "AI Service Error");
        error.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error);
    }

    @ExceptionHandler(DatabaseServiceException.class)
    public ResponseEntity<Map<String, String>> handleDatabaseServiceException(DatabaseServiceException ex,
            WebRequest request) {
        String context = "Request URI: " + request.getDescription(false);
        errorNotificationService.sendErrorNotification("Database Service", ex, context);

        Map<String, String> error = new HashMap<>();
        error.put("error", "Database Error");
        error.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    @ExceptionHandler(ServiceUnavailableException.class)
    public ResponseEntity<Map<String, String>> handleServiceUnavailableException(ServiceUnavailableException ex,
            WebRequest request) {
        String context = "Request URI: " + request.getDescription(false);
        errorNotificationService.sendErrorNotification("Service Unavailable", ex, context);

        Map<String, String> error = new HashMap<>();
        error.put("error", "Service Unavailable");
        error.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(error);
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, String>> handleRuntimeException(RuntimeException ex, WebRequest request) {
        String context = "Request URI: " + request.getDescription(false);
        errorNotificationService.sendErrorNotification("Runtime Exception", ex, context);

        Map<String, String> error = new HashMap<>();
        error.put("error", "Internal Server Error");
        error.put("message", "An unexpected error occurred. Our team has been notified.");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> handleGenericException(Exception ex, WebRequest request) {
        String context = "Request URI: " + request.getDescription(false);
        errorNotificationService.sendErrorNotification("Unhandled Exception", ex, context);

        Map<String, String> error = new HashMap<>();
        error.put("error", "Internal Server Error");
        error.put("message", "An unexpected error occurred. Our team has been notified.");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }
}
