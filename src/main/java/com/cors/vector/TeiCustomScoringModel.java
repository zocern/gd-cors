package com.cors.vector;

import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.scoring.ScoringModel;
import dev.langchain4j.model.output.Response;
import org.springframework.web.client.RestTemplate;
import java.util.List;
import java.util.stream.Collectors;

public class TeiCustomScoringModel implements ScoringModel {

    private final String baseUrl;
    private final RestTemplate restTemplate = new RestTemplate();

    public TeiCustomScoringModel(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
    }

    @Override
    public Response<List<Double>> scoreAll(List<dev.langchain4j.data.segment.TextSegment> segments, String query) {
        if (segments == null || segments.isEmpty()) {
            return Response.from(java.util.Collections.emptyList());
        }
        TeiRequest request = new TeiRequest(
                query,
                segments.stream().map(TextSegment::text).collect(Collectors.toList())
        );

        try {
            TeiResponse[] responses = restTemplate.postForObject(baseUrl + "rerank", request, TeiResponse[].class);
            if (responses == null) {
                throw new RuntimeException("TEI reranker returned empty response");
            }
            Double[] scoreArray = new Double[segments.size()];
            for (TeiResponse res : responses) {
                if (res.index < scoreArray.length) {
                    scoreArray[res.index] = res.score;
                }
            }
            return Response.from(java.util.Arrays.asList(scoreArray));
        } catch (Exception e) {
            throw new RuntimeException("Error during reranking: " + e.getMessage(), e);
        }
    }

    static class TeiRequest {
        public String query;
        public List<String> texts;
        public TeiRequest(String query, List<String> texts) { this.query = query; this.texts = texts; }
    }

    static class TeiResponse {
        public int index;
        public double score;
    }
}