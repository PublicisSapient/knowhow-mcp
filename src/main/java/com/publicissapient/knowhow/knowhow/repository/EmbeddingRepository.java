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

package com.publicissapient.knowhow.knowhow.repository;

import com.publicissapient.knowhow.knowhow.model.EmbeddingEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Repository for managing vector embeddings stored in the database.
 * Provides CRUD operations and custom queries for the embeddings table.
 */
@Repository
public interface EmbeddingRepository extends JpaRepository<EmbeddingEntity, UUID> {

    /**
     * Truncate the embeddings table to clear all data.
     * This is used during re-ingestion to start fresh.
     */
    @Modifying
    @Transactional
    @Query(value = "TRUNCATE TABLE vector_store", nativeQuery = true)
    void truncateTable();

    /**
     * Count total number of embeddings in the database.
     */
    long count();

    /**
     * Delete all embeddings (alternative to truncate for smaller datasets).
     */
    @Modifying
    @Transactional
    void deleteAll();
}
