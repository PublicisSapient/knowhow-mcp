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

import com.publicissapient.knowhow.knowhow.model.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FeedbackRepository extends JpaRepository<Feedback, Long> {

    // Find all liked responses
    List<Feedback> findByIsLiked(Boolean isLiked);

    // Find feedback for questions containing specific text
    List<Feedback> findByQuestionContainingIgnoreCase(String questionText);

    // Find similar questions using simple text matching (for few-shot learning)
    @Query("SELECT f FROM Feedback f WHERE LOWER(f.question) LIKE LOWER(CONCAT('%', :keyword, '%')) AND f.isLiked = true ORDER BY f.timestamp DESC")
    List<Feedback> findSimilarLikedQuestions(@Param("keyword") String keyword);

    @Query("SELECT f FROM Feedback f WHERE LOWER(f.question) LIKE LOWER(CONCAT('%', :keyword, '%')) AND f.isLiked = false ORDER BY f.timestamp DESC")
    List<Feedback> findSimilarDislikedQuestions(@Param("keyword") String keyword);
}
