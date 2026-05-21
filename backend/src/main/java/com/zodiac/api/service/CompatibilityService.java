package com.zodiac.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zodiac.api.dto.CompatibilityRequest;
import com.zodiac.api.dto.CompatibilityResponse;
import com.zodiac.api.entity.SoulmateReport;
import com.zodiac.api.repository.SoulmateReportRepository;
import com.zodiac.api.util.ZodiacCalculator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class CompatibilityService {

    private final AiChatService aiChatService;
    private final SoulmateReportRepository repository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SecureRandom rng = new SecureRandom();

    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int MIN_CHAPTERS = 6;
    private static final int MIN_ESSENCE = 6;
    private static final Pattern KEYWORD_COMMA_FIX =
            Pattern.compile("(?<=[\\}\"\\]0-9])\\s*\\n\\s*\"(?=[A-Za-z\\u4e00-\\u9fa5_]+\"\\s*:)");

    public CompatibilityResponse generateReport(CompatibilityRequest request, String ip, String userAgent) {
        var triA = ZodiacCalculator.computeAll(request.getPersonA().getBirthDate(), request.getPersonA().getBirthTime());
        var triB = ZodiacCalculator.computeAll(request.getPersonB().getBirthDate(), request.getPersonB().getBirthTime());

        log.info("Generating compatibility report: {}({}/{}/{}) x {}({}/{}/{})",
                request.getPersonA().getName(), triA.sun(), triA.moon(), triA.rising(),
                request.getPersonB().getName(), triB.sun(), triB.moon(), triB.rising());

        String systemPrompt = buildSystemPrompt();
        String userPrompt = buildUserPrompt(request, triA, triB);
        String selectedModel = request.getModel() != null ? request.getModel() : "deepseek";
        String raw = null;
        CompatibilityResponse response;
        try {
            raw = aiChatService.generate(systemPrompt, userPrompt, selectedModel);
            response = parseResponse(raw, request, triA, triB);
        } catch (AiServiceException e) {
            log.error("AI generation failed, falling back to deterministic report: {}", e.getMessage(), e);
            raw = "{\"fallback\":true,\"reason\":\"" + e.getReason() + "\"}";
            response = buildFallbackResponse(request, triA, triB, raw);
        }

        try {
            SoulmateReport entity = toEntity(request, response, triA, triB, raw, ip, userAgent);
            repository.save(entity);
        } catch (Exception e) {
            log.warn("Saving report failed but response remains usable: {}", e.getMessage());
        }

        return response;
    }

    private String buildSystemPrompt() {
        return """
                你是「小登哥」，要输出一份中文星座合盘报告。

                输出要求非常严格：
                1. 只输出 JSON，不要代码块，不要解释，不要额外前后缀。
                2. JSON 必须合法，所有字符串必须正确转义，字段之间必须有英文逗号。
                3. 所有 chapter.content 都必须是普通字符串，不要嵌套对象，不要列表标记。
                4. 不要在 JSON 末尾补任何总结或署名说明，署名只允许出现在最后一章正文里。

                返回结构：
                {
                  "score": 60-95 的整数,
                  "relationshipType": "4到8个字",
                  "tagline": "一句话总结，不超过30字",
                  "chapters": [
                    {"title": "你们的星座基因", "emoji": "✨", "content": "..." },
                    {"title": "你们在一起的化学反应", "emoji": "💞", "content": "..." },
                    {"title": "你们最容易出问题的地方", "emoji": "⚠️", "content": "..." },
                    {"title": "相处指南", "emoji": "🧭", "content": "..." },
                    {"title": "未来三个月预演", "emoji": "🔮", "content": "..." },
                    {"title": "写给你的悄悄话", "emoji": "🌙", "content": "..." }
                  ],
                  "essence": [
                    "一句可收藏的建议",
                    "一句可收藏的建议",
                    "一句可收藏的建议",
                    "一句可收藏的建议",
                    "一句可收藏的建议",
                    "一句可收藏的建议"
                  ]
                }
                """;
    }

    private String buildUserPrompt(CompatibilityRequest req,
                                   ZodiacCalculator.ZodiacTriplet triA,
                                   ZodiacCalculator.ZodiacTriplet triB) {
        var a = req.getPersonA();
        var b = req.getPersonB();
        StringBuilder sb = new StringBuilder();
        sb.append("请为以下两位用户生成深度合盘报告：\n\n");

        sb.append("【用户A / 报告主角】\n");
        sb.append("姓名: ").append(a.getName()).append("\n");
        sb.append("性别: ").append("male".equals(a.getGender()) ? "男" : "女").append("\n");
        sb.append("生日: ").append(a.getBirthDate()).append("\n");
        if (a.getBirthTime() != null && !a.getBirthTime().isBlank()) {
            sb.append("出生时间: ").append(a.getBirthTime()).append("\n");
        }
        if (a.getBirthPlace() != null && !a.getBirthPlace().isBlank()) {
            sb.append("出生地: ").append(a.getBirthPlace()).append("\n");
        }
        sb.append("太阳: ").append(triA.sun()).append("\n");
        sb.append("月亮: ").append(triA.moon()).append("\n");
        sb.append("上升: ").append(triA.rising()).append("\n\n");

        sb.append("【用户B / TA】\n");
        sb.append("姓名: ").append(b.getName()).append("\n");
        sb.append("性别: ").append("male".equals(b.getGender()) ? "男" : "女").append("\n");
        sb.append("生日: ").append(b.getBirthDate()).append("\n");
        if (b.getBirthTime() != null && !b.getBirthTime().isBlank()) {
            sb.append("出生时间: ").append(b.getBirthTime()).append("\n");
        }
        if (b.getBirthPlace() != null && !b.getBirthPlace().isBlank()) {
            sb.append("出生地: ").append(b.getBirthPlace()).append("\n");
        }
        sb.append("太阳: ").append(triB.sun()).append("\n");
        sb.append("月亮: ").append(triB.moon()).append("\n");
        sb.append("上升: ").append(triB.rising()).append("\n\n");

        sb.append("请用温柔、有洞察的中文写作，但最终只返回合法 JSON。");
        return sb.toString();
    }

    CompatibilityResponse parseResponse(String raw,
                                        CompatibilityRequest request,
                                        ZodiacCalculator.ZodiacTriplet triA,
                                        ZodiacCalculator.ZodiacTriplet triB) {
        String normalized = sanitizeRawJson(raw);
        try {
            JsonNode root = tryParseJson(normalized);
            return buildResponseFromJson(root, request, triA, triB);
        } catch (Exception parseError) {
            log.warn("Primary JSON parse failed, attempting fallback extraction. raw preview: {}",
                    preview(raw), parseError);
            try {
                JsonNode recovered = tryParseJson(repairCommonJsonIssues(normalized));
                return buildResponseFromJson(recovered, request, triA, triB);
            } catch (Exception recoveryError) {
                log.error("AI response recovery failed, falling back to deterministic report. raw preview: {}",
                        preview(raw), recoveryError);
                return buildFallbackResponse(request, triA, triB, raw);
            }
        }
    }

    private CompatibilityResponse buildResponseFromJson(JsonNode root,
                                                        CompatibilityRequest request,
                                                        ZodiacCalculator.ZodiacTriplet triA,
                                                        ZodiacCalculator.ZodiacTriplet triB) {
        List<CompatibilityResponse.Chapter> chapters = extractChapters(root.path("chapters"));
        List<String> essence = extractEssence(root.path("essence"));

        if (chapters.isEmpty()) {
            throw new IllegalArgumentException("AI response did not contain usable chapters.");
        }

        chapters = ensureChapterDefaults(chapters, request, triA, triB);
        essence = ensureEssenceDefaults(essence, request, triA, triB);

        String relationshipType = textOrDefault(root.path("relationshipType"), inferRelationshipType(triA, triB));
        String tagline = textOrDefault(root.path("tagline"),
                request.getPersonA().getName() + "和" + request.getPersonB().getName() + "之间有吸引，也需要耐心磨合。");

        return CompatibilityResponse.builder()
                .score(clampScore(root.path("score").asInt(82)))
                .relationshipType(relationshipType)
                .tagline(tagline)
                .chapters(chapters)
                .essence(essence)
                .reportUid(generateReportUid(triA.sun(), triB.sun()))
                .zodiacA(toZodiacInfo(triA))
                .zodiacB(toZodiacInfo(triB))
                .build();
    }

    private JsonNode tryParseJson(String content) throws Exception {
        return objectMapper.readTree(content);
    }

    private String sanitizeRawJson(String raw) {
        if (raw == null) {
            return "{}";
        }

        String cleaned = raw.trim();
        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }
        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }
        cleaned = cleaned.trim();

        int first = cleaned.indexOf('{');
        int last = cleaned.lastIndexOf('}');
        if (first >= 0 && last > first) {
            cleaned = cleaned.substring(first, last + 1);
        }
        return cleaned.trim();
    }

    private String repairCommonJsonIssues(String content) {
        String fixed = KEYWORD_COMMA_FIX.matcher(content).replaceAll(",\n\"");
        fixed = fixed.replaceAll(",\\s*([}\\]])", "$1");
        fixed = fixed.replace("“", "\"").replace("”", "\"");
        return fixed;
    }

    private List<CompatibilityResponse.Chapter> extractChapters(JsonNode chaptersNode) {
        List<CompatibilityResponse.Chapter> chapters = new ArrayList<>();
        if (chaptersNode.isArray()) {
            for (Iterator<JsonNode> it = chaptersNode.elements(); it.hasNext(); ) {
                JsonNode c = it.next();
                String title = textOrDefault(c.path("title"), "");
                String content = textOrDefault(c.path("content"), "");
                if (title.isBlank() && content.isBlank()) {
                    continue;
                }
                chapters.add(CompatibilityResponse.Chapter.builder()
                        .title(title.isBlank() ? "合盘章节" : title)
                        .emoji(textOrDefault(c.path("emoji"), "✨"))
                        .content(content)
                        .build());
            }
        }
        return chapters;
    }

    private List<String> extractEssence(JsonNode essenceNode) {
        List<String> essence = new ArrayList<>();
        if (essenceNode.isArray()) {
            for (Iterator<JsonNode> it = essenceNode.elements(); it.hasNext(); ) {
                String item = textOrDefault(it.next(), "");
                if (!item.isBlank()) {
                    essence.add(item);
                }
            }
        }
        return essence;
    }

    private List<CompatibilityResponse.Chapter> ensureChapterDefaults(List<CompatibilityResponse.Chapter> chapters,
                                                                      CompatibilityRequest request,
                                                                      ZodiacCalculator.ZodiacTriplet triA,
                                                                      ZodiacCalculator.ZodiacTriplet triB) {
        List<CompatibilityResponse.Chapter> result = new ArrayList<>(chapters);
        List<CompatibilityResponse.Chapter> fallback = fallbackChapters(request, triA, triB);

        for (int i = 0; i < result.size(); i++) {
            CompatibilityResponse.Chapter current = result.get(i);
            CompatibilityResponse.Chapter base = fallback.get(Math.min(i, fallback.size() - 1));
            result.set(i, CompatibilityResponse.Chapter.builder()
                    .title(current.getTitle() == null || current.getTitle().isBlank() ? base.getTitle() : current.getTitle())
                    .emoji(current.getEmoji() == null || current.getEmoji().isBlank() ? base.getEmoji() : current.getEmoji())
                    .content(current.getContent() == null || current.getContent().isBlank() ? base.getContent() : current.getContent())
                    .build());
        }

        int idx = result.size();
        while (result.size() < MIN_CHAPTERS && idx < fallback.size()) {
            result.add(fallback.get(idx));
            idx++;
        }

        return result;
    }

    private List<String> ensureEssenceDefaults(List<String> essence,
                                               CompatibilityRequest request,
                                               ZodiacCalculator.ZodiacTriplet triA,
                                               ZodiacCalculator.ZodiacTriplet triB) {
        List<String> result = new ArrayList<>(essence);
        List<String> fallback = fallbackEssence(request, triA, triB);
        int idx = 0;
        while (result.size() < MIN_ESSENCE && idx < fallback.size()) {
            result.add(fallback.get(idx));
            idx++;
        }
        return result;
    }

    private CompatibilityResponse buildFallbackResponse(CompatibilityRequest request,
                                                        ZodiacCalculator.ZodiacTriplet triA,
                                                        ZodiacCalculator.ZodiacTriplet triB,
                                                        String raw) {
        return CompatibilityResponse.builder()
                .score(clampScore(78 + rng.nextInt(10)))
                .relationshipType(inferRelationshipType(triA, triB))
                .tagline(request.getPersonA().getName() + "和" + request.getPersonB().getName() + "之间有真实吸引，也需要更温柔的表达。")
                .chapters(fallbackChapters(request, triA, triB))
                .essence(fallbackEssence(request, triA, triB))
                .reportUid(generateReportUid(triA.sun(), triB.sun()))
                .zodiacA(toZodiacInfo(triA))
                .zodiacB(toZodiacInfo(triB))
                .build();
    }

    private List<CompatibilityResponse.Chapter> fallbackChapters(CompatibilityRequest request,
                                                                 ZodiacCalculator.ZodiacTriplet triA,
                                                                 ZodiacCalculator.ZodiacTriplet triB) {
        String nameA = request.getPersonA().getName();
        String nameB = request.getPersonB().getName();

        List<CompatibilityResponse.Chapter> chapters = new ArrayList<>();
        chapters.add(chapter("你们的星座基因", "✨",
                nameA + "的太阳" + triA.sun() + "让TA在关系里更重视稳定和投入，月亮" + triA.moon() + "让情绪表达带着主观热度，上升" + triA.rising() + "又会把很多担心藏进细节里。"
                        + nameB + "这边的太阳" + triB.sun() + "更在意被看见的感觉，月亮" + triB.moon() + "决定了内心真正的安全需求，上升" + triB.rising() + "则影响TA在关系里的第一反应。你们不是没有默契，而是默契常常被表达方式拖慢。"));
        chapters.add(chapter("你们在一起的化学反应", "💞",
                nameA + "容易被" + nameB + "身上更鲜明、更直接的情绪吸引，" + nameB + "也会被" + nameA + "带来的稳定感安抚。好的时候，这段关系很容易形成一个人点火、一个人续航的组合。问题在于，一旦其中一方退回自己的舒适区，另一方就会误读成冷淡或不在乎。"));
        chapters.add(chapter("你们最容易出问题的地方", "⚠️",
                "你们最大的摩擦往往不是爱得不够，而是节奏不一致。一个人希望马上回应，另一个人习惯先消化再表达；一个人想确认关系，另一个人先去处理现实细节。矛盾累积后，就会从具体事情升级成“你是不是根本不懂我”。这类关系最怕把情绪拖成沉默。"));
        chapters.add(chapter("相处指南", "🧭",
                "先约定一个固定的沟通动作，比方说遇到分歧时先说明情绪、再说诉求、最后给出具体请求。对" + nameA + "来说，少一点闷着做事、多一点把想法说出来；对" + nameB + "来说，少一点试探式表达、多一点直接说明自己要什么。你们需要的不是更激烈，而是更清楚。"));
        chapters.add(chapter("未来三个月预演", "🔮",
                "接下来三个月，这段关系适合处理现实安排、边界感和期待值。只要把容易误解的事情说清楚，关系会稳得更快；如果继续靠猜，前期的小别扭很容易放大。建议把重要话题放到情绪平稳的时候谈，不要在最上头的时候决定关系走向。"));
        chapters.add(chapter("写给你的悄悄话", "🌙",
                nameA + "，你们之间不是没有缘分，而是这段缘分更考验耐心和表达。真正重要的，不是谁更会爱，而是谁愿意在误解出现时往前走一步。你把心事说出来，TA才有机会真正靠近你。\n\n—— 小登哥 ✨"));
        return chapters;
    }

    private List<String> fallbackEssence(CompatibilityRequest request,
                                         ZodiacCalculator.ZodiacTriplet triA,
                                         ZodiacCalculator.ZodiacTriplet triB) {
        String nameA = request.getPersonA().getName();
        String nameB = request.getPersonB().getName();
        List<String> essence = new ArrayList<>();
        essence.add("别把“我以为你懂”当成沟通。");
        essence.add(nameA + "先讲感受，" + nameB + "再讲需求，效率会高很多。");
        essence.add("稳定感不是沉默，是让对方知道你还在。");
        essence.add("情绪上来的时候先暂停，别急着判关系输赢。");
        essence.add("你们适合把模糊的问题说具体。");
        essence.add("真正拉开差距的，从来不是星座，是愿不愿意认真回应彼此。");
        return essence;
    }

    private CompatibilityResponse.Chapter chapter(String title, String emoji, String content) {
        return CompatibilityResponse.Chapter.builder()
                .title(title)
                .emoji(emoji)
                .content(content)
                .build();
    }

    private String inferRelationshipType(ZodiacCalculator.ZodiacTriplet triA,
                                         ZodiacCalculator.ZodiacTriplet triB) {
        if (triA.sun().equals(triB.sun())) {
            return "同频拉扯型";
        }
        return "互补磨合型";
    }

    private int clampScore(int score) {
        return Math.max(60, Math.min(95, score));
    }

    private String textOrDefault(JsonNode node, String fallback) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return fallback;
        }
        String value = node.asText();
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String preview(String raw) {
        if (raw == null || raw.isBlank()) {
            return "<empty>";
        }
        return raw.substring(0, Math.min(raw.length(), 500));
    }

    private CompatibilityResponse.ZodiacInfo toZodiacInfo(ZodiacCalculator.ZodiacTriplet t) {
        return CompatibilityResponse.ZodiacInfo.builder()
                .sun(t.sun())
                .moon(t.moon())
                .rising(t.rising())
                .build();
    }

    private String generateReportUid(String sunA, String sunB) {
        String codeA = zodiacToCode(sunA);
        String codeB = zodiacToCode(sunB);
        LocalDate d = LocalDate.now();
        String date = String.format("%02d%02d%02d",
                d.getYear() % 100, d.getMonthValue(), d.getDayOfMonth());
        StringBuilder rand = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            rand.append(CHARS.charAt(rng.nextInt(CHARS.length())));
        }
        return String.format("N° %s%s-%s-%s", codeA, codeB, date, rand);
    }

    private String zodiacToCode(String zodiac) {
        return switch (zodiac) {
            case "白羊座" -> "AR";
            case "金牛座" -> "TA";
            case "双子座" -> "GE";
            case "巨蟹座" -> "CA";
            case "狮子座" -> "LE";
            case "处女座" -> "VI";
            case "天秤座" -> "LI";
            case "天蝎座" -> "SC";
            case "射手座" -> "SA";
            case "摩羯座" -> "CP";
            case "水瓶座" -> "AQ";
            case "双鱼座" -> "PI";
            default -> "XX";
        };
    }

    private SoulmateReport toEntity(CompatibilityRequest req, CompatibilityResponse resp,
                                    ZodiacCalculator.ZodiacTriplet triA,
                                    ZodiacCalculator.ZodiacTriplet triB,
                                    String rawJson, String ip, String userAgent) {
        SoulmateReport e = new SoulmateReport();
        e.setReportUid(resp.getReportUid());

        var a = req.getPersonA();
        e.setUserAName(a.getName());
        e.setUserAGender(a.getGender());
        e.setUserABirthDate(a.getBirthDate());
        e.setUserABirthTime(a.getBirthTime());
        e.setUserABirthPlace(a.getBirthPlace());
        e.setZodiacA(triA.sun());
        e.setMoonA(triA.moon());
        e.setRisingA(triA.rising());

        var b = req.getPersonB();
        e.setUserBName(b.getName());
        e.setUserBGender(b.getGender());
        e.setUserBBirthDate(b.getBirthDate());
        e.setUserBBirthTime(b.getBirthTime());
        e.setUserBBirthPlace(b.getBirthPlace());
        e.setZodiacB(triB.sun());
        e.setMoonB(triB.moon());
        e.setRisingB(triB.rising());

        e.setScore(resp.getScore());
        e.setRelationshipType(resp.getRelationshipType());
        e.setTagline(resp.getTagline());
        e.setFullReport(rawJson);

        e.setIpAddress(ip);
        e.setUserAgent(userAgent != null && userAgent.length() > 500
                ? userAgent.substring(0, 500) : userAgent);
        e.setSharedCount(0);
        return e;
    }
}
