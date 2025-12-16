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

import com.publicissapient.knowhow.knowhow.exception.DatabaseServiceException;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import com.publicissapient.knowhow.knowhow.repository.EmbeddingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

@Service
public class IngestionService {

    @Autowired
    private ConfluenceService confluenceService;

    @Autowired
    private ImageOCRService imageOCRService;

    @Autowired
    private ErrorNotificationService errorNotificationService;

    @Autowired
    private EmbeddingRepository embeddingRepository;

    @Value("${spring.datasource.url}")
    private String dbUrl;

    @Autowired
    private EmbeddingStore<TextSegment> embeddingStore;

    @Autowired
    private EmbeddingModel embeddingModel;

    private final ApacheTikaDocumentParser parser = new ApacheTikaDocumentParser();

    @Autowired
    private javax.sql.DataSource dataSource;

    @PostConstruct
    public void init() {
        try {
            // Test database connectivity first
            System.out.println("Testing database connectivity...");
            embeddingRepository.count(); // Test database access
            System.out.println("Database connection successful.");
            System.out.println("Vector store connection verified.");
        } catch (Exception e) {
            System.err.println("Failed to initialize database connection: " + e.getMessage());
            errorNotificationService.sendErrorNotification(
                    "IngestionService Initialization",
                    e,
                    "Failed to initialize vector store connection. Database: " + dbUrl);
            // Do not throw exception here, allow app to start in degraded state
            System.err.println(
                    "WARNING: Application starting without Vector Store. RAG functionality will be unavailable.");
        }
    }

    public String ingestAll() {
        try {
            clearData();
            int totalSegments = 0;
            int limit = 200;

            System.out.println("Starting ingestion of pages...");
            totalSegments += ingestContent("page", limit);

            return "Ingestion complete. Total segments stored: " + totalSegments;
        } catch (Exception e) {
            System.err.println("Error during ingestion: " + e.getMessage());
            errorNotificationService.sendErrorNotification(
                    "Ingestion Process",
                    e,
                    "Failed during full content ingestion. Process was interrupted.");
            throw new RuntimeException("Ingestion failed: " + e.getMessage(), e);
        }
    }

    private void clearData() {
        System.out.println("Clearing existing data...");
        try {
            embeddingRepository.truncateTable();
            System.out.println("Data cleared successfully.");
        } catch (Exception e) {
            System.err.println("Error clearing data: " + e.getMessage());
        }
    }

    private int ingestContent(String type, int limit) {
        int totalSegments = 0;
        int start = 0;
        boolean more = true;
        java.util.Set<String> visitedIds = new java.util.HashSet<>();

        while (more) {
            List<ConfluencePage> pages;
            if ("page".equals(type)) {
                pages = confluenceService.fetchPages(start, limit);
            } else {
                pages = confluenceService.fetchBlogPosts(start, limit);
            }

            if (pages.isEmpty()) {
                more = false;
            } else {
                int newPagesCount = 0;
                List<ConfluencePage> uniquePages = new ArrayList<>();

                for (ConfluencePage page : pages) {
                    if (!visitedIds.contains(page.getId())) {
                        visitedIds.add(page.getId());
                        uniquePages.add(page);
                        newPagesCount++;
                    } else {
                        System.out.println(
                                "DEBUG: Skipping duplicate page ID: " + page.getId() + ", Title: " + page.getTitle());
                    }
                }

                System.out.println("DEBUG: Batch had " + pages.size() + " pages, " + newPagesCount + " were new, "
                        + (pages.size() - newPagesCount) + " were duplicates");

                if (newPagesCount == 0) {
                    System.out.println(
                            "DEBUG: All pages in this batch have already been visited. Stopping to prevent infinite loop.");
                    more = false;
                } else {
                    System.out.println(
                            "Ingesting batch of " + uniquePages.size() + " " + type + "s (start=" + start + ")...");
                    totalSegments += processPages(uniquePages);
                    start += limit;

                    // Safety break for testing/debugging if needed, or if the user was right about
                    // 100 pages
                    if (start > 10000) { // Hard limit to prevent runaway 880k pages if API is weird
                        System.out.println("WARNING: Reached safety limit of 10000 items. Stopping.");
                        more = false;
                    }
                }
            }
        }
        return totalSegments;
    }

    private int processPages(List<ConfluencePage> pages) {
        int count = 0;
        DocumentSplitter splitter = DocumentSplitters.recursive(1000, 200);

        for (ConfluencePage page : pages) {
            // 1. Process Page Content
            if (page.getContent() != null && !page.getContent().isEmpty()) {
                Metadata metadata = Metadata.from("title", page.getTitle())
                        .add("url", page.getUrl())
                        .add("type", "page");

                // Add tags to metadata
                if (page.getTags() != null && !page.getTags().isEmpty()) {
                    String tagsString = String.join(", ", page.getTags());
                    metadata.add("tags", tagsString);
                    System.out.println(
                            "DEBUG: Adding tags to metadata for page '" + page.getTitle() + "': " + tagsString);
                }

                // Enhance content with tags for better search precision
                // Tags are prepended to content and repeated to give them higher weight in
                // embeddings
                String enhancedContent = page.getContent();
                if (page.getTags() != null && !page.getTags().isEmpty()) {
                    // Create a tag prefix that will be included in embeddings
                    // Repeat tags 3 times to increase their weight in the vector space
                    String tagPrefix = "Tags: " + String.join(", ", page.getTags()) + ". ";
                    tagPrefix = tagPrefix + tagPrefix + tagPrefix; // Triple the weight
                    enhancedContent = tagPrefix + "\n\n" + page.getContent();
                    System.out.println("DEBUG: Enhanced content with tags for better search precision");
                }

                Document doc = Document.from(enhancedContent, metadata);
                List<TextSegment> segments = splitter.split(doc);
                List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
                embeddingStore.addAll(embeddings, segments);
                count += segments.size();

                System.out.println(
                        "DEBUG: Processed page '" + page.getTitle() + "' with " + segments.size() + " segments");
            }

            // 2. Process Attachments
            List<ConfluencePage> attachments = confluenceService.fetchAttachments(page.getId());
            for (ConfluencePage attachment : attachments) {
                String mediaType = attachment.getMediaType();

                // Skip video and audio files
                if (mediaType != null && (mediaType.startsWith("video/") || mediaType.startsWith("audio/"))) {
                    System.out.println("Skipping attachment: " + attachment.getTitle() + " (Type: " + mediaType + ")");
                    continue;
                }

                byte[] content = confluenceService.downloadAttachment(attachment.getUrl());
                if (content != null) {
                    try {
                        // Process images with OCR
                        if (mediaType != null && mediaType.startsWith("image/")) {
                            System.out.println("Processing image with OCR: " + attachment.getTitle());
                            String extractedText = imageOCRService.extractTextFromImage(content, mediaType);

                            if (extractedText != null && !extractedText.trim().isEmpty()) {
                                Metadata metadata = Metadata.from("title", attachment.getTitle() + " (Image)")
                                        .add("url", page.getUrl())
                                        .add("type", "image");

                                // Inherit tags from parent page
                                if (page.getTags() != null && !page.getTags().isEmpty()) {
                                    String tagsString = String.join(", ", page.getTags());
                                    metadata.add("tags", tagsString);
                                }

                                Document doc = Document.from(extractedText, metadata);
                                List<TextSegment> segments = splitter.split(doc);
                                if (!segments.isEmpty()) {
                                    List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
                                    embeddingStore.addAll(embeddings, segments);
                                    count += segments.size();
                                    System.out.println("DEBUG: Extracted and indexed text from image '" +
                                            attachment.getTitle() + "': " + segments.size() + " segments");
                                }
                            } else {
                                System.out.println("DEBUG: No text extracted from image: " + attachment.getTitle());
                            }
                        } else {
                            // Process other attachments (PDFs, docs, etc.)
                            Document doc = parser.parse(new ByteArrayInputStream(content));
                            doc.metadata().add("title", attachment.getTitle());
                            doc.metadata().add("url", page.getUrl());
                            doc.metadata().add("type", "attachment");

                            // Inherit tags from parent page for attachments
                            if (page.getTags() != null && !page.getTags().isEmpty()) {
                                String tagsString = String.join(", ", page.getTags());
                                doc.metadata().add("tags", tagsString);
                            }

                            List<TextSegment> segments = splitter.split(doc);
                            if (!segments.isEmpty()) {
                                List<Embedding> embeddings = embeddingModel.embedAll(segments).content();
                                embeddingStore.addAll(embeddings, segments);
                                count += segments.size();
                            }
                        }
                    } catch (Exception e) {
                        System.err.println(
                                "Failed to process attachment: " + attachment.getTitle() + " - " + e.getMessage());
                    }
                }
            }
        }
        return count;
    }

    public EmbeddingStore<TextSegment> getEmbeddingStore() {
        if (embeddingStore == null) {
            throw new DatabaseServiceException(
                    "Vector database is not available. The embedding store was not initialized.");
        }
        return embeddingStore;
    }

    public EmbeddingModel getEmbeddingModel() {
        return embeddingModel;
    }
}
