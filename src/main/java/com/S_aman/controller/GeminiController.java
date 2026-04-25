package com.S_aman.controller;

import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import com.S_aman.model.Question;
import com.S_aman.model.QuestionResponse;
import com.S_aman.service.GeminiService;
import com.S_aman.service.RagService;

import jakarta.servlet.http.HttpSession;

@Controller
@RequestMapping("/api")
public class GeminiController {

    @Autowired
    private GeminiService geminiService;

    @Autowired
    private RagService ragService;

    @GetMapping("/")
    public String home() {
        return "index";
    }

    @PostMapping("/generate")
    public String generate(
            @RequestParam String prompt, 
            @RequestParam(defaultValue = "Medium") String difficulty,
            @RequestParam(defaultValue = "5") int questionCount,
            Model model, HttpSession session) {

        QuestionResponse response = geminiService.generateContent(prompt, "", difficulty, questionCount);

        if (response == null || response.getQuestions() == null || response.getQuestions().isEmpty()) {
            model.addAttribute("error", "The AI was unable to generate questions.");
            return "index";
        }

        session.setAttribute("questions", response.getQuestions());
        session.setAttribute("currentIndex", 0);
        session.setAttribute("userAnswers", new ArrayList<String>());

        model.addAttribute("currentQuestion", response.getQuestions().get(0));
        model.addAttribute("currentIndex", 0);
        model.addAttribute("totalQuestions", response.getQuestions().size());

        return "quiz";
    }

    @PostMapping("/upload-pdf")
    public String uploadPdf(
            @RequestParam("file") MultipartFile file, 
            @RequestParam String prompt,
            @RequestParam(defaultValue = "Medium") String difficulty,
            @RequestParam(defaultValue = "5") int questionCount,
            Model model, HttpSession session) {
        
        if (file.isEmpty()) {
            model.addAttribute("error", "Please select a PDF file to upload.");
            return "index";
        }

        try {
            // 1. Process PDF
            ragService.processPdf(file);

            // 2. Handle Empty Prompt (General Quiz)
            String searchQuery;
            String topicForAI;

            if (prompt == null || prompt.trim().isEmpty()) {
                // If empty, search for broad terms to get a wide variety of chunks
                searchQuery = "main concepts, key topics, summary, overview, important definitions";
                topicForAI = "a general overview of all the main topics";
            } else {
                // If specific, use their exact prompt
                searchQuery = prompt;
                topicForAI = prompt;
            }

            // 3. Retrieve context using the determined search query
            String context = ragService.retrieveContext(searchQuery);

            if (context == null || context.trim().isEmpty()) {
                model.addAttribute("error", "The uploaded PDF does not contain enough information to generate a quiz.");
                return "index";
            }

            // 4. Generate quiz using the topicForAI and the retrieved context
            QuestionResponse response = geminiService.generateContent(topicForAI, context, difficulty, questionCount);

            if (response == null || response.getQuestions() == null || response.getQuestions().isEmpty()) {
                model.addAttribute("error", "The AI was unable to generate questions from the PDF.");
                return "index";
            }

            session.setAttribute("questions", response.getQuestions());
            session.setAttribute("currentIndex", 0);
            session.setAttribute("userAnswers", new ArrayList<String>());

            model.addAttribute("currentQuestion", response.getQuestions().get(0));
            model.addAttribute("currentIndex", 0);
            model.addAttribute("totalQuestions", response.getQuestions().size());

            return "quiz";

        } catch (Exception e) {
            model.addAttribute("error", "Error processing PDF: " + e.getMessage());
            return "index";
        }
    }

    @PostMapping("/next")
    public String nextQuestion(@RequestParam String answer, Model model, HttpSession session) {
        List<Question> questions = (List<Question>) session.getAttribute("questions");
        List<String> userAnswers = (List<String>) session.getAttribute("userAnswers");
        Integer currentIndex = (Integer) session.getAttribute("currentIndex");

        if (questions == null || questions.isEmpty()) return "redirect:/api/";

        userAnswers.add(answer);
        currentIndex++;

        session.setAttribute("currentIndex", currentIndex);
        session.setAttribute("userAnswers", userAnswers);

        if (currentIndex >= questions.size()) {
            // Quiz finished! Save data to session and redirect
            int score = calculateScore(questions, userAnswers);
            session.setAttribute("score", score);
            session.setAttribute("total", questions.size());
            session.setAttribute("reviewQuestions", questions);
            session.setAttribute("reviewUserAnswers", userAnswers);
            
            // Use standard Spring redirect instead of a fake template name
            return "redirect:/api/result";
        }
        model.addAttribute("currentQuestion", questions.get(currentIndex));
        model.addAttribute("currentIndex", currentIndex);
        model.addAttribute("totalQuestions", questions.size());
        return "fragments/question :: questionFragment";
    }

    // NEW: A dedicated URL to load the final result page properly
    @GetMapping("/result")
    public String showResult(Model model, HttpSession session) {
        model.addAttribute("score", session.getAttribute("score"));
        model.addAttribute("total", session.getAttribute("total"));
        model.addAttribute("questions", session.getAttribute("reviewQuestions"));
        model.addAttribute("userAnswers", session.getAttribute("reviewUserAnswers"));
        return "result";
    }

    private int calculateScore(List<Question> questions, List<String> userAnswers) {
        int score = 0;
        for (int i = 0; i < userAnswers.size(); i++) {
            String correctAnswer = questions.get(i).getAnswer();
            String userSelected = userAnswers.get(i);
            if (correctAnswer != null && userSelected != null &&
                    correctAnswer.trim().equalsIgnoreCase(userSelected.trim())) {
                score++;
            }
        }
        return score;
    }
}