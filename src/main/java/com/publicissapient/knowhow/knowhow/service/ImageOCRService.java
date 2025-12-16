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

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import java.util.Base64;
import java.util.List;
import java.util.Map;

@Service
public class ImageOCRService {

    @Value("${langchain4j.open-ai.chat-model.api-key}")
    private String openAiApiKey;

    @Value("${langchain4j.open-ai.chat-model.base-url}")
    private String baseUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * Extract text from an image using OpenAI Vision API
     */
    public String extractTextFromImage(byte[] imageData, String imageType) {
        try {
            // Convert image to base64
            String base64Image = Base64.getEncoder().encodeToString(imageData);

            // Determine the media type
            String mimeType = "image/png"; // default
            if (imageType != null) {
                if (imageType.contains("jpeg") || imageType.contains("jpg")) {
                    mimeType = "image/jpeg";
                } else if (imageType.contains("png")) {
                    mimeType = "image/png";
                } else if (imageType.contains("gif")) {
                    mimeType = "image/gif";
                } else if (imageType.contains("webp")) {
                    mimeType = "image/webp";
                }
            }

            // Build the request
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(openAiApiKey);

            Map<String, Object> requestBody = Map.of(
                    "model", "gpt-4-turbo",
                    "messages", List.of(
                            Map.of(
                                    "role", "user",
                                    "content", List.of(
                                            Map.of(
                                                    "type", "text",
                                                    "text",
                                                    "Extract all text visible in this image. Include labels, titles, captions, annotations, and any embedded text. Return only the extracted text without any additional commentary."),
                                            Map.of(
                                                    "type", "image_url",
                                                    "image_url", Map.of(
                                                            "url", "data:" + mimeType + ";base64," + base64Image))))),
                    "max_tokens", 1000);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(requestBody, headers);

            // Call OpenAI Vision API
            String url = baseUrl + "/chat/completions";
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restTemplate.postForObject(url, request, Map.class);

            if (response != null && response.containsKey("choices")) {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
                if (!choices.isEmpty()) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
                    String extractedText = (String) message.get("content");

                    System.out.println("DEBUG: Extracted text from image: " +
                            extractedText.substring(0, Math.min(100, extractedText.length())) + "...");

                    return extractedText;
                }
            }

            return "";
        } catch (Exception e) {
            System.err.println("Error extracting text from image: " + e.getMessage());
            e.printStackTrace();
            return "";
        }
    }
}
