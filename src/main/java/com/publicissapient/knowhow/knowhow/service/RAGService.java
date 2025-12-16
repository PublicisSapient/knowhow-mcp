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

import com.publicissapient.knowhow.knowhow.controller.ChatController;
import com.publicissapient.knowhow.knowhow.exception.LLMServiceException;
import com.publicissapient.knowhow.knowhow.model.Feedback;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class RAGService {

    @Autowired
    private IngestionService ingestionService;

    @Autowired
    private dev.langchain4j.store.embedding.EmbeddingStore<TextSegment> embeddingStore;

    @Autowired
    private dev.langchain4j.model.embedding.EmbeddingModel embeddingModel;

    @Autowired
    private FeedbackService feedbackService;

    @Value("${langchain4j.open-ai.chat-model.api-key}")
    private String openAiApiKey;

    @Value("${langchain4j.open-ai.chat-model.model-name}")
    private String modelName;

    @Value("${langchain4j.open-ai.chat-model.fast-model-name}")
    private String fastModelName;

    @Value("${langchain4j.open-ai.chat-model.base-url}")
    private String baseUrl;

    /**
     * Answer a user question using vector search over ingested content.
     */
    public ChatController.ChatResponse askQuestion(String question, boolean includeWebContent, List<String> tags,
            List<ChatController.ConversationMessage> conversationHistory) {

        System.out.println("\\n========================================");
        System.out.println("DEBUG: NEW QUESTION RECEIVED");
        System.out.println("DEBUG: Question: " + question);

        // 1. Perform vector search
        String searchQuery = question;

        // Rewrite query if there's conversation history
        if (conversationHistory != null && !conversationHistory.isEmpty()) {
            System.out.println("DEBUG: Conversation History Size: " + conversationHistory.size());
            searchQuery = rewriteQuery(question, conversationHistory);
            System.out.println("DEBUG: Rewritten query: " + searchQuery);
        }

        // Embed the question
        Embedding questionEmbedding = embeddingModel.embed(searchQuery).content();

        // Fetch more results initially (50) so we can filter by tags
        List<EmbeddingMatch<TextSegment>> relevant = embeddingStore.findRelevant(questionEmbedding, 50);

        // If tags are specified, filter by tags
        if (tags != null && !tags.isEmpty()) {
            System.out.println("DEBUG: Filtering by tags: " + tags);
            relevant = relevant.stream()
                    .filter(match -> {
                        String segmentTags = match.embedded().metadata("tags");
                        if (segmentTags == null || segmentTags.isEmpty()) {
                            return false; // Strict filtering: if no tags, exclude (or return true to be lenient)
                        }
                        // Check if any of the requested tags are contained in the segment tags
                        for (String tag : tags) {
                            if (segmentTags.toLowerCase().contains(tag.toLowerCase())) {
                                return true;
                            }
                        }
                        return false;
                    })
                    .collect(Collectors.toList());

            // Limit back to 15 after filtering
            if (relevant.size() > 15) {
                relevant = relevant.subList(0, 15);
            }
        } else {
            // If no tags, just take top 15
            if (relevant.size() > 15) {
                relevant = relevant.subList(0, 15);
            }
        }

        System.out.println("DEBUG: Found " + relevant.size() + " relevant segments.");

        // IF NO RELEVANT CONTENT FOUND -> Continue to LLM but Context will be empty
        if (relevant.isEmpty()) {
            System.out.println(
                    "DEBUG: No relevant segments found. Proceeding with empty context to use general knowledge.");
        }

        for (EmbeddingMatch<TextSegment> match : relevant) {
            System.out.println("DEBUG: Match Score: " + match.score());
            System.out.println("DEBUG: Match Content Preview: "
                    + match.embedded().text().substring(0, Math.min(100, match.embedded().text().length())));
        }

        String context = relevant.stream()
                .map(match -> {
                    TextSegment segment = match.embedded();
                    String segmentTags = segment.metadata("tags");
                    String contextEntry = "Title: " + segment.metadata("title") + "\\n"
                            + "Source: " + segment.metadata("url") + "\\n";

                    // Include tags if available
                    if (segmentTags != null && !segmentTags.isEmpty()) {
                        contextEntry += "Tags: " + segmentTags + "\\n";
                    }

                    contextEntry += "Content: " + segment.text();
                    return contextEntry;
                })
                .collect(Collectors.joining("\\n\\n"));

        // 1.5 Query feedback for similar questions
        String feedbackExamples = "";
        try {
            List<Feedback> likedFeedback = feedbackService.findSimilarLikedQuestions(searchQuery);
            List<Feedback> dislikedFeedback = feedbackService.findSimilarDislikedQuestions(searchQuery);

            if (!likedFeedback.isEmpty() || !dislikedFeedback.isEmpty()) {
                feedbackExamples = "\\n\\n--- Previous Feedback for Similar Questions ---\\n";
                if (!likedFeedback.isEmpty()) {
                    feedbackExamples += "Examples of GOOD responses (liked by users):\\n";
                    for (int i = 0; i < Math.min(2, likedFeedback.size()); i++) {
                        Feedback fb = likedFeedback.get(i);
                        feedbackExamples += "Q: " + fb.getQuestion() + "\\nA: " + fb.getAnswer() + "\\n\\n";
                    }
                }
                if (!dislikedFeedback.isEmpty()) {
                    feedbackExamples += "Examples of BAD responses (disliked by users - avoid similar approaches):\\n";
                    for (int j = 0; j < Math.min(2, dislikedFeedback.size()); j++) {
                        Feedback fb = dislikedFeedback.get(j);
                        feedbackExamples += "Q: " + fb.getQuestion() + "\\nA: " + fb.getAnswer() + "\\n\\n";
                    }
                }
                System.out.println("DEBUG: Found feedback examples for similar questions");
            }
        } catch (Exception e) {
            System.err.println("DEBUG: Error querying feedback: " + e.getMessage());
        }

        // 1.6 Build conversation history context
        String conversationContext = "";
        if (conversationHistory != null && !conversationHistory.isEmpty()) {
            conversationContext = "\\n\\n--- Previous Conversation ---\\n";
            int startIndex = Math.max(0, conversationHistory.size() - 4);
            for (int i = startIndex; i < conversationHistory.size(); i++) {
                ChatController.ConversationMessage msg = conversationHistory.get(i);
                conversationContext += (msg.getRole().equals("user") ? "User: " : "Assistant: ") + msg.getContent()
                        + "\n";
            }
            System.out.println("DEBUG: Including conversation history in prompt");
        }

        // 2. Construct Prompt
        String systemPrompt = "You are a helpful assistant. Your task is to answer questions based on the information provided in the Context section below. "
                + "The context contains relevant excerpts from internal documentation. " + "Guidelines:\n"
                + "- If the context directly answers the question, provide a clear and comprehensive answer.\n"
                + "- If the context contains partial information that helps answer the question, use that information and clearly explain what you found.\n"
                + "- If the context contains related or similar information (e.g., user asks about 'DSR' but context has 'DSI' or 'DRR'), explain what information is available and suggest the user may have meant one of those terms.\n"
                + "- If the context is empty or has no relevant information at all, answer the question using your general knowledge (full context).\n"
                + "- CRITICAL: If you answer using your general knowledge (and not the provided context), you MUST explicitly append the following citation at the end of your response: 'Source: this information is provided from internet'.\n"
                + "- CRITICAL: When explaining formulas, calculations, or technical definitions from the context, preserve the EXACT wording and meaning from the source. Do not paraphrase technical terms or change the definition. Quote the formula exactly as written.\n"
                + "- Format formulas in plain text, NOT in LaTeX. Use simple text like 'DSR = (defects in UAT) / (defects in UAT + defects in QA)' instead of LaTeX notation.\n"
                + "- IMPORTANT: Always include the source URL(s) at the end of your answer in the format: 'Source: [URL]'. If multiple sources are used, list all of them.\n"
                + "- Cite the source titles when providing information.\n"
                + "- If there is previous conversation history, use it to understand the context of the current question. For example, if the user asks 'what about DSI?' after asking about DSR, understand they want information about DSI.";

        if (includeWebContent) {
            systemPrompt = "You are a helpful assistant. " +
                    "Answer the user's question based on the following context from Confluence. " +
                    "If the answer is not in the context, you may use your general knowledge to answer, but explicitly state that the information comes from outside Confluence. "
                    +
                    "If you use general knowledge, append 'Source: this information is provided from internet' at the end. "
                    +
                    "Always include source URLs when using information from the context.";
        }

        String fullPrompt = systemPrompt + conversationContext + "\n\n--- Context from Documentation ---\n" + context
                + feedbackExamples + "\n\n--- Question ---\n" + searchQuery + "\n\n--- Answer ---";

        System.out.println(
                "DEBUG: Context being sent to LLM:\n" + context.substring(0, Math.min(500, context.length())) + "...");

        // 3. Call LLM
        if ("demo".equals(openAiApiKey)) {
            return new ChatController.ChatResponse("Mock LLM Response: Based on Confluence, "
                    + (context.isEmpty() ? "no info found." : "found relevant info."));
        }

        try {
            System.out.println("DEBUG: Full prompt being sent to LLM:");
            System.out.println("---START PROMPT---");
            System.out.println(fullPrompt);
            System.out.println("---END PROMPT---\\n");

            ChatLanguageModel model = OpenAiChatModel.builder()
                    .apiKey(openAiApiKey)
                    .modelName(modelName)
                    .baseUrl(baseUrl)
                    .timeout(java.time.Duration.ofSeconds(60))
                    .logRequests(true)
                    .logResponses(true)
                    .build();

            String response = model.generate(fullPrompt);
            System.out.println("\\nDEBUG: LLM Response received:");
            System.out.println("---START RESPONSE---");
            System.out.println(response);
            System.out.println("---END RESPONSE---");
            System.out.println("========================================\\n");
            return new ChatController.ChatResponse(response);
        } catch (Exception e) {
            System.err.println("DEBUG: Error calling LLM: " + e.getMessage());
            e.printStackTrace();

            // Check for network connectivity issues by examining the exception cause chain
            Throwable cause = e;
            while (cause != null) {
                if (cause instanceof java.net.ConnectException ||
                        cause instanceof java.net.UnknownHostException) {
                    throw new LLMServiceException(
                            "Unable to connect to the AI service. The service may be down or unreachable.", e);
                }
                if (cause instanceof java.net.SocketTimeoutException) {
                    throw new LLMServiceException(
                            "The AI service is taking too long to respond. Please try again later.", e);
                }
                cause = cause.getCause();
            }

            // Check if it's an authentication/authorization error
            String errorMsg = e.getMessage();
            if (errorMsg != null &&
                    (errorMsg.contains("401") || errorMsg.contains("403") ||
                            errorMsg.contains("Unauthorized") || errorMsg.contains("Forbidden"))) {
                throw new LLMServiceException(
                        "Authentication failed with the AI service. Please check API credentials.", e);
            }

            // Generic LLM error
            throw new LLMServiceException(
                    "The AI service encountered an error while processing your request.", e);
        }
    }

    private List<String> generateSuggestedQuestions(String originalQuestion) {
        try {
            String prompt = "The user asked: \\\"" + originalQuestion + "\\\". " +
                    "We could not find any relevant information in our documentation. " +
                    "Please generate 3 relevant, alternative questions that the user might have intended to ask, related to software development, KPIs, or project management. "
                    +
                    "Return ONLY the 3 questions, each on a new line, without numbering or bullets.";

            ChatLanguageModel model = OpenAiChatModel.builder()
                    .apiKey(openAiApiKey)
                    .modelName(fastModelName)
                    .baseUrl(baseUrl)
                    .timeout(java.time.Duration.ofSeconds(30))
                    .build();

            String response = model.generate(prompt);
            return java.util.Arrays.stream(response.split("\\n"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        } catch (Exception e) {
            System.err.println("Error generating suggestions: " + e.getMessage());
            return java.util.Collections.emptyList();
        }
    }

    private String rewriteQuery(String originalQuestion, List<ChatController.ConversationMessage> history) {
        try {
            String historyText = history.stream()
                    .map(msg -> (msg.getRole().equals("user") ? "User: " : "Assistant: ") + msg.getContent())
                    .collect(Collectors.joining("\n"));

            String prompt = "Given the following conversation history and a new follow-up question, rephrase the follow-up question to be a standalone query that contains all necessary context from the history.\n"
                    + "If the follow-up question is already standalone, return it exactly as is.\n"
                    + "Do NOT answer the question. Return ONLY the rewritten question text. Do not add quotes or prefixes like 'Rewritten Question:'.\n\n"
                    +
                    "--- History ---\n" +
                    historyText + "\n\n" +
                    "--- Follow-up Question ---\n" +
                    originalQuestion + "\n\n" +
                    "--- Rewritten Question ---";

            ChatLanguageModel model = OpenAiChatModel.builder()
                    .apiKey(openAiApiKey)
                    .modelName(fastModelName) // Use faster model for rewriting
                    .baseUrl(baseUrl)
                    .timeout(java.time.Duration.ofSeconds(30))
                    .build();

            return model.generate(prompt);
        } catch (Exception e) {
            System.err.println("Error rewriting query: " + e.getMessage());
            return originalQuestion; // Fallback to original
        }
    }
}
