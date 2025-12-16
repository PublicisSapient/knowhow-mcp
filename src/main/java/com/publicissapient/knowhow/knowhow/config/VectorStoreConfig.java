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

package com.publicissapient.knowhow.knowhow.config;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.pgvector.PgVectorEmbeddingStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Configuration
public class VectorStoreConfig {

    @Value("${gravity.vector-store.table-name}")
    private String tableName;

    @Value("${gravity.vector-store.dimension}")
    private int dimension;

    @Value("${spring.datasource.url}")
    private String dbUrl;

    @Value("${spring.datasource.username}")
    private String dbUser;

    @Value("${spring.datasource.password}")
    private String dbPassword;

    @Bean
    public EmbeddingModel embeddingModel() {
        return new dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel();
    }

    @Bean
    public EmbeddingStore<TextSegment> embeddingStore() {
        // Parse database connection details from JDBC URL
        // Expected format: jdbc:postgresql://host:port/database
        String host = "localhost";
        int port = 5432;
        String database = "chatbot_db";

        try {
            // Remove "jdbc:postgresql://" prefix
            String urlWithoutPrefix = dbUrl.replace("jdbc:postgresql://", "");

            // Split by "/" to separate host:port from database
            String[] parts = urlWithoutPrefix.split("/");
            if (parts.length >= 2) {
                // Extract database name (remove any query parameters)
                database = parts[1].split("\\?")[0];

                // Extract host and port
                String hostPort = parts[0];
                if (hostPort.contains(":")) {
                    String[] hostPortParts = hostPort.split(":");
                    host = hostPortParts[0];
                    port = Integer.parseInt(hostPortParts[1]);
                } else {
                    host = hostPort;
                }
            }
        } catch (Exception e) {
            // Fallback to defaults or log error
            System.err.println("Error parsing DB URL in VectorStoreConfig: " + e.getMessage());
        }

        return PgVectorEmbeddingStore.builder()
                .host(host)
                .port(port)
                .database(database)
                .user(dbUser)
                .password(dbPassword)
                .table(tableName)
                .dimension(dimension)
                .build();
    }
}
