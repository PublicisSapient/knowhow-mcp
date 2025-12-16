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

package com.publicissapient.knowhow.knowhow.mcp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.publicissapient.knowhow.knowhow.controller.ChatController;
import com.publicissapient.knowhow.knowhow.service.ConfluencePage;
import com.publicissapient.knowhow.knowhow.service.ConfluenceService;
import com.publicissapient.knowhow.knowhow.service.RAGService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class McpService {

    @Autowired
    private ConfluenceService confluenceService;

    @Autowired
    private RAGService ragService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public JsonRpcResponse handleRequest(JsonRpcRequest request) {
        try {
            switch (request.getMethod()) {
                case "initialize":
                    return handleInitialize(request);
                case "resources/list":
                    return handleListResources(request);
                case "resources/read":
                    return handleReadResource(request);
                case "tools/list":
                    return handleListTools(request);
                case "tools/call":
                    return handleCallTool(request);
                case "ping":
                    return createResponse(request.getId(), "pong");
                default:
                    return createErrorResponse(request.getId(), -32601, "Method not found");
            }
        } catch (Exception e) {
            e.printStackTrace();
            return createErrorResponse(request.getId(), -32000, "Internal error: " + e.getMessage());
        }
    }

    private JsonRpcResponse handleInitialize(JsonRpcRequest request) {
        Map<String, Object> result = new HashMap<>();
        result.put("protocolVersion", "2024-11-05");

        Map<String, Object> capabilities = new HashMap<>();
        capabilities.put("resources", new HashMap<>());
        capabilities.put("tools", new HashMap<>());
        result.put("capabilities", capabilities);

        Map<String, Object> serverInfo = new HashMap<>();
        serverInfo.put("name", "gravity-bot-mcp");
        serverInfo.put("version", "1.0.0");
        result.put("server", serverInfo);

        return createResponse(request.getId(), result);
    }

    private JsonRpcResponse handleListResources(JsonRpcRequest request) {
        // Fetch recent pages (limit 50 for now)
        List<ConfluencePage> pages = confluenceService.fetchPages(0, 50);
        List<McpResource> resources = new ArrayList<>();

        for (ConfluencePage page : pages) {
            resources.add(McpResource.builder()
                    .uri("confluence://page/" + page.getId())
                    .name(page.getTitle())
                    .mimeType("text/plain")
                    .description("Confluence Page: " + page.getTitle())
                    .build());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("resources", resources);
        return createResponse(request.getId(), result);
    }

    private JsonRpcResponse handleReadResource(JsonRpcRequest request) {
        Map<String, Object> params = request.getParams();
        if (params == null || !params.containsKey("uri")) {
            return createErrorResponse(request.getId(), -32602, "Missing 'uri' parameter");
        }

        String uri = (String) params.get("uri");
        if (!uri.startsWith("confluence://page/")) {
            return createErrorResponse(request.getId(), -32602, "Invalid URI format. Expected confluence://page/{id}");
        }

        String pageId = uri.substring("confluence://page/".length());
        ConfluencePage page = confluenceService.fetchPage(pageId);

        if (page == null) {
            return createErrorResponse(request.getId(), -32001, "Page not found: " + pageId);
        }

        List<Content> contents = new ArrayList<>();
        contents.add(Content.builder()
                .type("text")
                .text(page.getContent())
                .mimeType("text/plain")
                .build());

        Map<String, Object> result = new HashMap<>();
        result.put("contents", contents);
        return createResponse(request.getId(), result);
    }

    private JsonRpcResponse handleListTools(JsonRpcRequest request) {
        List<McpTool> tools = new ArrayList<>();

        // Tool: ask_gravity_bot
        Map<String, Object> inputSchema = new HashMap<>();
        inputSchema.put("type", "object");
        Map<String, Object> properties = new HashMap<>();
        properties.put("question", Map.of("type", "string", "description", "The question to ask the bot"));
        inputSchema.put("properties", properties);
        inputSchema.put("required", List.of("question"));

        tools.add(McpTool.builder()
                .name("ask_gravity_bot")
                .description("Ask a question about internal documentation (KPIs, DSR, etc.) using RAG.")
                .inputSchema(inputSchema)
                .build());

        Map<String, Object> result = new HashMap<>();
        result.put("tools", tools);
        return createResponse(request.getId(), result);
    }

    private JsonRpcResponse handleCallTool(JsonRpcRequest request) {
        Map<String, Object> params = request.getParams();
        if (params == null || !params.containsKey("name") || !params.containsKey("arguments")) {
            return createErrorResponse(request.getId(), -32602, "Missing 'name' or 'arguments' parameter");
        }

        String name = (String) params.get("name");
        Map<String, Object> arguments = (Map<String, Object>) params.get("arguments");

        if ("ask_gravity_bot".equals(name)) {
            String question = (String) arguments.get("question");
            if (question == null) {
                return createErrorResponse(request.getId(), -32602, "Missing 'question' argument");
            }

            // Call RAG Service
            // Note: We don't have conversation history here easily, so passing empty list
            // for now.
            // In a real MCP client, the client manages history.
            ChatController.ChatResponse response = ragService.askQuestion(question, false, null, new ArrayList<>());

            List<Content> content = new ArrayList<>();
            content.add(Content.builder()
                    .type("text")
                    .text(response.getAnswer())
                    .build());

            CallToolResult result = CallToolResult.builder()
                    .content(content)
                    .isError(false)
                    .build();

            return createResponse(request.getId(), result);
        }

        return createErrorResponse(request.getId(), -32601, "Tool not found: " + name);
    }

    private JsonRpcResponse createResponse(Object id, Object result) {
        return JsonRpcResponse.builder()
                .id(id)
                .result(result)
                .build();
    }

    private JsonRpcResponse createErrorResponse(Object id, int code, String message) {
        return JsonRpcResponse.builder()
                .id(id)
                .error(JsonRpcError.builder()
                        .code(code)
                        .message(message)
                        .build())
                .build();
    }
}
