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

import com.publicissapient.knowhow.knowhow.service.RAGService;
import com.publicissapient.knowhow.knowhow.service.IngestionService;
import com.publicissapient.knowhow.knowhow.service.FeedbackService;
import com.publicissapient.knowhow.knowhow.service.SupportEmailService;
import lombok.Data;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/chat")
@CrossOrigin(origins = "http://localhost:4200") // Restrict to Angular frontend
public class ChatController {

    @Autowired
    private RAGService ragService;

    @Autowired
    private IngestionService ingestionService;

    @Autowired
    private FeedbackService feedbackService;

    @Autowired
    private SupportEmailService supportEmailService;

    @PostMapping
    public ChatResponse ask(@RequestBody ChatRequest request) {
        return ragService.askQuestion(
                request.getQuestion(),
                request.isIncludeWebContent(),
                request.getTags(),
                request.getConversationHistory());
    }

    @GetMapping("/ingest")
    public String ingest() {
        return ingestionService.ingestAll();
    }

    @PostMapping("/feedback")
    public FeedbackResponse submitFeedback(@RequestBody FeedbackRequest request) {
        feedbackService.saveFeedback(request.getQuestion(), request.getAnswer(), request.getIsLiked());
        return new FeedbackResponse("Feedback saved successfully");
    }

    @PostMapping("/support")
    public SupportResponse submitSupport(@RequestBody SupportRequest request) {
        supportEmailService.sendSupportEmail(
                request.getName(),
                request.getEmail(),
                request.getProject(),
                request.getIssueDescription());
        return new SupportResponse("Support request sent successfully");
    }

    @GetMapping("/debug-search")
    public java.util.List<String> debugSearch(@RequestParam String query) {
        dev.langchain4j.data.embedding.Embedding embedding = ingestionService.getEmbeddingModel().embed(query)
                .content();
        java.util.List<dev.langchain4j.store.embedding.EmbeddingMatch<dev.langchain4j.data.segment.TextSegment>> matches = ingestionService
                .getEmbeddingStore().findRelevant(embedding, 20, 0.0);
        return matches.stream().map(m -> "Score: " + m.score() + " | Content: " + m.embedded().text())
                .collect(java.util.stream.Collectors.toList());
    }

    @Data
    public static class ChatRequest {
        private String question;
        private boolean includeWebContent;
        private java.util.List<String> tags;
        private java.util.List<ConversationMessage> conversationHistory;
    }

    @Data
    public static class ConversationMessage {
        private String role;
        private String content;
    }

    @Data
    public static class ChatResponse {
        private String answer;
        private java.util.List<String> suggestedQuestions;

        public ChatResponse(String answer) {
            this.answer = answer;
        }

        public ChatResponse(String answer, java.util.List<String> suggestedQuestions) {
            this.answer = answer;
            this.suggestedQuestions = suggestedQuestions;
        }
    }

    @Data
    public static class FeedbackRequest {
        private String question;
        private String answer;
        private Boolean isLiked;
    }

    @Data
    public static class FeedbackResponse {
        private String message;

        public FeedbackResponse(String message) {
            this.message = message;
        }
    }

    @Data
    public static class SupportRequest {
        private String name;
        private String email;
        private String project;
        private String issueDescription;
    }

    @Data
    public static class SupportResponse {
        private String message;

        public SupportResponse(String message) {
            this.message = message;
        }
    }
}
