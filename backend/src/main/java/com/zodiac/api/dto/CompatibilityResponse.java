package com.zodiac.api.dto;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class CompatibilityResponse {
    private Integer score;
    private String relationshipType;
    private String tagline;
    private List<Chapter> chapters;
    private List<String> essence;
    private String reportUid;
    private ZodiacInfo zodiacA;
    private ZodiacInfo zodiacB;

    @Data
    @Builder
    public static class Chapter {
        private String title;
        private String emoji;
        private String content;
    }

    @Data
    @Builder
    public static class ZodiacInfo {
        private String sun;
        private String moon;
        private String rising;
    }
}
