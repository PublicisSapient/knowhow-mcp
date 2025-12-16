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

import com.publicissapient.knowhow.knowhow.model.Feedback;
import com.publicissapient.knowhow.knowhow.repository.FeedbackRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class FeedbackService {

    @Autowired
    private FeedbackRepository feedbackRepository;

    public Feedback saveFeedback(String question, String answer, Boolean isLiked) {
        Feedback feedback = new Feedback(question, answer, isLiked);
        return feedbackRepository.save(feedback);
    }

    public List<Feedback> getAllFeedback() {
        return feedbackRepository.findAll();
    }

    public List<Feedback> getLikedFeedback() {
        return feedbackRepository.findByIsLiked(true);
    }

    public List<Feedback> getDislikedFeedback() {
        return feedbackRepository.findByIsLiked(false);
    }

    // Find similar questions for few-shot learning
    public List<Feedback> findSimilarLikedQuestions(String question) {
        // Extract key words from the question (simple approach)
        String[] words = question.toLowerCase().split("\\s+");

        // Try to find feedback with the longest word (likely the most specific term)
        String longestWord = "";
        for (String word : words) {
            if (word.length() > longestWord.length() && word.length() > 3) {
                longestWord = word;
            }
        }

        if (longestWord.isEmpty()) {
            return List.of();
        }

        return feedbackRepository.findSimilarLikedQuestions(longestWord);
    }

    public List<Feedback> findSimilarDislikedQuestions(String question) {
        String[] words = question.toLowerCase().split("\\s+");

        String longestWord = "";
        for (String word : words) {
            if (word.length() > longestWord.length() && word.length() > 3) {
                longestWord = word;
            }
        }

        if (longestWord.isEmpty()) {
            return List.of();
        }

        return feedbackRepository.findSimilarDislikedQuestions(longestWord);
    }
}
