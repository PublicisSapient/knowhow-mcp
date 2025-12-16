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

package com.publicissapient.knowhow.knowhow.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Service
public class ErrorNotificationService {

    @Autowired
    private JavaMailSender mailSender;

    @Value("${gravity.error.email.enabled:true}")
    private boolean errorEmailEnabled;

    @Value("${gravity.error.email.to}")
    private String errorEmailTo;

    @Value("${gravity.error.email.from}")
    private String errorEmailFrom;

    @Value("${gravity.error.email.environment:development}")
    private String environment;

    /**
     * Send error notification email with exception details
     */
    public void sendErrorNotification(String source, Exception exception, String additionalContext) {
        if (!errorEmailEnabled) {
            System.out.println("Error email notifications are disabled. Skipping email for: " + source);
            return;
        }

        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(errorEmailFrom);
            message.setTo(errorEmailTo);
            message.setSubject(String.format("[%s] KnowHOW Error: %s - %s",
                    environment.toUpperCase(),
                    source,
                    exception.getClass().getSimpleName()));

            String emailBody = buildErrorEmailBody(source, exception, additionalContext);
            message.setText(emailBody);

            mailSender.send(message);

            System.out.println("Error notification email sent to: " + errorEmailTo);
        } catch (Exception e) {
            // Log but don't throw - we don't want email failures to cause more errors
            System.err.println("Failed to send error notification email: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Overloaded method without additional context
     */
    public void sendErrorNotification(String source, Exception exception) {
        sendErrorNotification(source, exception, null);
    }

    private String buildErrorEmailBody(String source, Exception exception, String additionalContext) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        String timestamp = LocalDateTime.now().format(formatter);

        StringBuilder body = new StringBuilder();
        body.append("=".repeat(80)).append("\n");
        body.append("KnowHOW Assistant - Error Notification\n");
        body.append("=".repeat(80)).append("\n\n");

        body.append("Environment: ").append(environment.toUpperCase()).append("\n");
        body.append("Timestamp: ").append(timestamp).append("\n");
        body.append("Error Source: ").append(source).append("\n");
        body.append("Exception Type: ").append(exception.getClass().getName()).append("\n");
        body.append("Error Message: ").append(exception.getMessage()).append("\n\n");

        if (additionalContext != null && !additionalContext.trim().isEmpty()) {
            body.append("-".repeat(80)).append("\n");
            body.append("Additional Context:\n");
            body.append("-".repeat(80)).append("\n");
            body.append(additionalContext).append("\n\n");
        }

        body.append("-".repeat(80)).append("\n");
        body.append("Stack Trace:\n");
        body.append("-".repeat(80)).append("\n");
        body.append(getStackTraceAsString(exception)).append("\n");

        if (exception.getCause() != null) {
            body.append("\n").append("-".repeat(80)).append("\n");
            body.append("Root Cause:\n");
            body.append("-".repeat(80)).append("\n");
            body.append(getStackTraceAsString(exception.getCause())).append("\n");
        }

        body.append("\n").append("=".repeat(80)).append("\n");
        body.append("End of Error Report\n");
        body.append("=".repeat(80)).append("\n");

        return body.toString();
    }

    private String getStackTraceAsString(Throwable throwable) {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString();
    }
}
