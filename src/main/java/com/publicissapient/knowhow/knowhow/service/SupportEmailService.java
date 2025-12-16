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

@Service
public class SupportEmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Value("${gravity.support.email.to}")
    private String supportEmailTo;

    @Value("${gravity.support.email.from}")
    private String supportEmailFrom;

    public void sendSupportEmail(String userName, String userEmail, String project, String issueDescription) {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(supportEmailFrom);
            message.setTo(supportEmailTo);
            message.setSubject("KnowHOW Assistant - Support Request from " + userName);

            String emailBody = "Support Request Details:\n\n" +
                    "Name: " + userName + "\n" +
                    "Email: " + userEmail + "\n" +
                    "Project: " + project + "\n\n" +
                    "Issue Description:\n" + issueDescription + "\n\n" +
                    "---\n" +
                    "Sent from KnowHOW Assistant";

            message.setText(emailBody);

            mailSender.send(message);

            System.out.println("Support email sent successfully to: " + supportEmailTo);
        } catch (Exception e) {
            System.err.println("Error sending support email: " + e.getMessage());
            throw new RuntimeException("Failed to send support email", e);
        }
    }
}
