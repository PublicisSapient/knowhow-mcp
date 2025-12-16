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

import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;

import java.util.ArrayList;
import java.util.List;

@Service
public class ConfluenceService {

    @Value("${gravity.confluence.url}")
    private String confluenceUrl;

    @Value("${gravity.confluence.username:}")
    private String username;

    @Value("${gravity.confluence.api-token:}")
    private String apiToken;

    @Value("${gravity.confluence.space-key}")
    private String spaceKey;

    private final RestTemplate restTemplate;

    public ConfluenceService(RestTemplateBuilder restTemplateBuilder) {
        this.restTemplate = restTemplateBuilder.build();
    }

    /**
     * Fetches pages from Confluence.
     */
    public List<ConfluencePage> searchPages(String query) {
        List<ConfluencePage> pages = searchPagesInternal(query);

        // If no results, try to sanitize the query (remove stop words, punctuation)
        if (pages.isEmpty()) {
            String sanitizedQuery = sanitizeQuery(query);
            if (!sanitizedQuery.equals(query) && !sanitizedQuery.isEmpty()) {
                System.out.println(
                        "DEBUG: No results for original query. Retrying with sanitized query: " + sanitizedQuery);
                pages = searchPagesInternal(sanitizedQuery);
            }
        }

        return pages;
    }

    public List<ConfluencePage> fetchPages(int start, int limit) {
        String cql = String.format("space=\"%s\" AND type=\"page\" ORDER BY created", spaceKey);
        return fetchContent(cql, start, limit);
    }

    public List<ConfluencePage> fetchBlogPosts(int start, int limit) {
        String cql = String.format("space=\"%s\" AND type=\"blogpost\" ORDER BY id", spaceKey);
        return fetchContent(cql, start, limit);
    }

    public ConfluencePage fetchPage(String pageId) {
        String url = String.format("%s/rest/api/content/%s?expand=body.storage,version,metadata.labels", confluenceUrl,
                pageId);
        try {
            ConfluenceResponse.Result result = restTemplate.getForObject(url, ConfluenceResponse.Result.class);
            if (result != null) {
                ConfluencePage page = new ConfluencePage();
                page.setId(result.getId());
                page.setTitle(result.getTitle());

                // Extract labels/tags
                if (result.getMetadata() != null && result.getMetadata().getLabels() != null
                        && result.getMetadata().getLabels().getResults() != null) {
                    List<String> tags = new ArrayList<>();
                    for (ConfluenceResponse.Label label : result.getMetadata().getLabels().getResults()) {
                        if (label.getName() != null && !label.getName().isEmpty()) {
                            tags.add(label.getName());
                        }
                    }
                    page.setTags(tags);
                }

                if (result.getBody() != null && result.getBody().getStorage() != null) {
                    page.setContent(cleanContent(result.getBody().getStorage().getValue()));
                    page.setUrl(confluenceUrl + result.getLinks().getWebui());
                }
                return page;
            }
        } catch (Exception e) {
            System.err.println("Error fetching page " + pageId + ": " + e.getMessage());
        }
        return null;
    }

    public List<ConfluencePage> fetchContent(String cql, int start, int limit) {
        URI uri = UriComponentsBuilder.fromHttpUrl(confluenceUrl)
                .path("/rest/api/content/search")
                .queryParam("cql", cql)
                .queryParam("expand", "body.storage,version,metadata.labels")
                .queryParam("start", start)
                .queryParam("limit", limit)
                .build()
                .toUri();

        System.out.println("DEBUG: Fetching content from URL: " + uri);

        try {
            ConfluenceResponse response = restTemplate.getForObject(uri, ConfluenceResponse.class);

            if (response == null || response.getResults() == null || response.getResults().isEmpty()) {
                return new ArrayList<>();
            }

            List<ConfluencePage> pages = new ArrayList<>();
            for (ConfluenceResponse.Result result : response.getResults()) {
                ConfluencePage page = new ConfluencePage();
                page.setId(result.getId());
                page.setTitle(result.getTitle());

                // Extract labels/tags
                if (result.getMetadata() != null && result.getMetadata().getLabels() != null
                        && result.getMetadata().getLabels().getResults() != null) {
                    List<String> tags = new ArrayList<>();
                    for (ConfluenceResponse.Label label : result.getMetadata().getLabels().getResults()) {
                        if (label.getName() != null && !label.getName().isEmpty()) {
                            tags.add(label.getName());
                        }
                    }
                    page.setTags(tags);
                    System.out.println("DEBUG: Page '" + page.getTitle() + "' has tags: " + tags);
                }

                if (result.getBody() != null && result.getBody().getStorage() != null) {
                    page.setContent(cleanContent(result.getBody().getStorage().getValue()));
                    page.setUrl(confluenceUrl + result.getLinks().getWebui());
                    System.out.println("DEBUG: Fetched page ID: " + page.getId() + ", Title: " + page.getTitle());
                    pages.add(page);
                }
            }
            return pages;
        } catch (Exception e) {
            System.err.println("Error fetching content: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    public List<ConfluencePage> fetchAttachments(String contentId) {
        String url = String.format("%s/rest/api/content/%s/child/attachment", confluenceUrl, contentId);
        List<ConfluencePage> attachments = new ArrayList<>();

        try {
            ConfluenceResponse response = restTemplate.getForObject(url, ConfluenceResponse.class);
            if (response != null && response.getResults() != null) {
                for (ConfluenceResponse.Result result : response.getResults()) {
                    ConfluencePage attachment = new ConfluencePage();
                    attachment.setId(result.getId());
                    attachment.setTitle(result.getTitle());
                    attachment.setUrl(confluenceUrl + result.getLinks().getDownload()); // Download URL
                    if (result.getMetadata() != null) {
                        attachment.setMediaType(result.getMetadata().getMediaType());
                    }
                    attachments.add(attachment);
                }
            }
        } catch (Exception e) {
            System.err.println("Error fetching attachments for content " + contentId + ": " + e.getMessage());
        }
        return attachments;
    }

    public byte[] downloadAttachment(String downloadUrl) {
        try {
            return restTemplate.getForObject(downloadUrl, byte[].class);
        } catch (Exception e) {
            System.err.println("Error downloading attachment: " + e.getMessage());
            return null;
        }
    }

    private String sanitizeQuery(String query) {
        // Remove common question words and stop words
        String[] stopWords = { "what", "whats", "what's", "is", "are", "how", "to", "the", "a", "an", "in", "on", "for",
                "of", "do", "does", "did" };
        String cleaned = query.toLowerCase().replaceAll("[^a-zA-Z0-9\\s]", ""); // Remove punctuation

        for (String word : stopWords) {
            cleaned = cleaned.replaceAll("\\b" + word + "\\b", "");
        }

        return cleaned.trim().replaceAll("\\s+", " "); // Normalize whitespace
    }

    private List<ConfluencePage> searchPagesInternal(String query) {
        String cql = String.format("space=\"%s\" AND text~\"%s\"", spaceKey, query);

        URI uri = UriComponentsBuilder.fromHttpUrl(confluenceUrl)
                .path("/rest/api/content/search")
                .queryParam("cql", cql)
                .queryParam("expand", "body.storage,version,metadata.labels")
                .queryParam("limit", 10)
                .build()
                .toUri();

        System.out.println("DEBUG: Fetching from URL: " + uri);

        try {
            ConfluenceResponse response = restTemplate.getForObject(uri, ConfluenceResponse.class);

            if (response == null) {
                System.out.println("DEBUG: Response is null");
                return new ArrayList<>();
            }

            if (response.getResults() == null) {
                System.out.println("DEBUG: Response results list is null");
                return new ArrayList<>();
            }

            System.out.println("DEBUG: Found " + response.getResults().size() + " pages.");

            List<ConfluencePage> pages = new ArrayList<>();
            for (ConfluenceResponse.Result result : response.getResults()) {
                System.out.println("DEBUG: Processing page: " + result.getTitle());
                ConfluencePage page = new ConfluencePage();
                page.setId(result.getId());
                page.setTitle(result.getTitle());

                // Extract labels/tags
                if (result.getMetadata() != null && result.getMetadata().getLabels() != null
                        && result.getMetadata().getLabels().getResults() != null) {
                    List<String> tags = new ArrayList<>();
                    for (ConfluenceResponse.Label label : result.getMetadata().getLabels().getResults()) {
                        if (label.getName() != null && !label.getName().isEmpty()) {
                            tags.add(label.getName());
                        }
                    }
                    page.setTags(tags);
                    System.out.println("DEBUG: Page '" + page.getTitle() + "' has tags: " + tags);
                }

                if (result.getBody() != null && result.getBody().getStorage() != null) {
                    page.setContent(cleanContent(result.getBody().getStorage().getValue()));
                    page.setUrl(confluenceUrl + result.getLinks().getWebui());
                }

                pages.add(page);
            }
            return pages;

        } catch (Exception e) {
            System.err.println("Error fetching from Confluence: " + e.getMessage());
            e.printStackTrace();
            return new ArrayList<>();
        }
    }

    private String cleanContent(String rawContent) {
        if (rawContent == null || rawContent.isEmpty()) {
            return "";
        }
        // Use Jsoup to strip HTML and get plain text
        return Jsoup.parse(rawContent).text();
    }
}
