package com.S_aman.service;

import java.io.InputStream;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentParser;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.TokenCountEstimator;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiTokenCountEstimator;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;

@Service
public class RagService {

    private final EmbeddingStore<TextSegment> embeddingStore;
    private final EmbeddingModel embeddingModel;

    public RagService(EmbeddingStore<TextSegment> embeddingStore, EmbeddingModel embeddingModel) {
        this.embeddingStore = embeddingStore;
        this.embeddingModel = embeddingModel;
    }

    public void processPdf(MultipartFile file) throws Exception {
        System.out.println("=== Starting Ingestion for PDF ===");

        // 1. Read the PDF using LangChain4j Apache PDFBox parser
        InputStream inputStream = file.getInputStream();
        DocumentParser parser = new ApachePdfBoxDocumentParser();
        Document document = parser.parse(inputStream);

        // 2. Setup Token Estimator and Splitter (Exactly like your RepoLens code)
        TokenCountEstimator estimator = new OpenAiTokenCountEstimator("gpt-4o");
        DocumentSplitter splitter = DocumentSplitters.recursive(500, 50, estimator);

        List<TextSegment> chunks = splitter.split(document);
        System.out.println("1. Document split into " + chunks.size() + " chunks.");

        // 3. Embed all chunks
        System.out.println("2. Sending chunks for embedding...");
        List<Embedding> embeddings = embeddingModel.embedAll(chunks).content();
        System.out.println("3. Successfully received embeddings.");

        // 4. Store embeddings
        System.out.println("4. Saving to InMemory Store...");
        embeddingStore.addAll(embeddings, chunks);
        System.out.println("=== SUCCESS: Ingestion Completed ===");
    }

    public String retrieveContext(String prompt) {
        // 1. Embed the user's prompt
        Embedding queryEmbedding = embeddingModel.embed(prompt).content();

        // 2. Build the Search Request (New 1.x API)
        EmbeddingSearchRequest searchRequest = EmbeddingSearchRequest.builder()
                .queryEmbedding(queryEmbedding)
                .maxResults(5) // Replaces the old integer parameter
                .minScore(0.5) // Optional: Only return matches with 70%+ similarity to prevent bad context
                .build();

        // 3. Execute the search
        EmbeddingSearchResult<TextSegment> searchResult = embeddingStore.search(searchRequest);

        // 4. Combine matched text chunks into a single string
        return searchResult.matches().stream()
                .map(match -> match.embedded().text())
                .collect(Collectors.joining("\n\n"));
    }
}