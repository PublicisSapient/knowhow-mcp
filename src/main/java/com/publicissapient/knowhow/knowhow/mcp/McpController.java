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

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/mcp")
@CrossOrigin(origins = "*")
public class McpController {

    @Autowired
    private McpService mcpService;

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    @GetMapping("/sse")
    public SseEmitter handleSse() {
        String sessionId = UUID.randomUUID().toString();
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        emitters.put(sessionId, emitter);

        emitter.onCompletion(() -> emitters.remove(sessionId));
        emitter.onTimeout(() -> emitters.remove(sessionId));
        emitter.onError((e) -> emitters.remove(sessionId));

        try {
            // Send the endpoint event as per MCP spec
            // The client should POST messages to this endpoint
            String endpoint = "/mcp/messages?sessionId=" + sessionId;
            emitter.send(SseEmitter.event().name("endpoint").data(endpoint));
            System.out.println("MCP Client connected. Session ID: " + sessionId);
        } catch (IOException e) {
            emitters.remove(sessionId);
        }

        return emitter;
    }

    @PostMapping("/messages")
    public ResponseEntity<Void> handleMessage(
            @RequestParam String sessionId,
            @RequestBody JsonRpcRequest request) {

        SseEmitter emitter = emitters.get(sessionId);
        if (emitter == null) {
            return ResponseEntity.notFound().build();
        }

        // Process request asynchronously to avoid blocking the HTTP thread
        // (In a real app, use a thread pool. Here we just run it.)
        new Thread(() -> {
            JsonRpcResponse response = mcpService.handleRequest(request);
            try {
                // Send response via SSE
                emitter.send(SseEmitter.event().name("message").data(response));
            } catch (IOException e) {
                System.err.println("Error sending MCP response: " + e.getMessage());
                emitters.remove(sessionId);
            }
        }).start();

        return ResponseEntity.accepted().build();
    }
}
