package com.S_aman.service;

import dev.langchain4j.model.chat.ChatModel;
import org.springframework.stereotype.Service;

import com.S_aman.model.QuestionResponse;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class GeminiService {

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;

    public GeminiService(ChatModel chatModel) {
        this.chatModel = chatModel;
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    // ADDED difficulty and questionCount parameters
    public QuestionResponse generateContent(String prompt, String context, String difficulty, int questionCount) {
        try {
            String contextInstruction = context.isEmpty() ? "" : 
                "CONTEXT PROVIDED:\n" + context + "\n\nCRITICAL RULE: Generate the quiz STRICTLY based on the context provided above. If the context does not contain enough information about the topic, return an empty JSON array: { \"questions\": [] }. Do NOT use outside knowledge.\n";

            String aiResponse = chatModel.chat(
                 "You are a strict JSON quiz generator.\n" +
                 "RULES:\n" +
                 "1. Return ONLY a valid JSON object.\n" +
                 "2. DO NOT include any introductory text, markdown backticks (```), or explanations.\n" +
                 "3. Generate exactly " + questionCount + " Multiple Choice Questions.\n" + // Dynamic count
                 "4. The difficulty level must be: " + difficulty + ".\n" + // Dynamic difficulty
                 "5. Format:\n" +
                 "{ \"questions\": [ { \"question\": \"The question text\", \"options\": [\"A\", \"B\", \"C\", \"D\"], \"answer\": \"The exact correct string\" } ] }\n" +
                 contextInstruction +
                 "\n\nTopic for quiz: " + prompt
            );

            if (aiResponse == null) return new QuestionResponse();

            String cleanJson = aiResponse
                    .replaceAll("```json", "")
                    .replaceAll("```", "")
                    .trim();

            return objectMapper.readValue(cleanJson, QuestionResponse.class);

        } catch (Exception e) {
            System.err.println("Gemini Service Error: " + e.getMessage());
            QuestionResponse fallback = new QuestionResponse();
            fallback.setQuestions(new java.util.ArrayList<>());
            return fallback;
        }
    }
}