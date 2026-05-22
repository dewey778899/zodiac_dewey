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
    private final ZodiacScoringService scoringService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SecureRandom rng = new SecureRandom();

    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int MIN_CHAPTERS = 6;
    private static final int MIN_ESSENCE = 6;
    private static final int PREMIUM_MIN_CHAPTERS = 8;
    private static final int PREMIUM_MIN_ESSENCE = 10;
    private static final Pattern KEYWORD_COMMA_FIX =
            Pattern.compile("(?<=[\\}\"\\]0-9])\\s*\\n\\s*\"(?=[A-Za-z\\u4e00-\\u9fa5_]+\"\\s*:)");

    public CompatibilityResponse generateReport(CompatibilityRequest request, String ip, String userAgent) {
        var triA = ZodiacCalculator.computeAll(request.getPersonA().getBirthDate(), request.getPersonA().getBirthTime());
        var triB = ZodiacCalculator.computeAll(request.getPersonB().getBirthDate(), request.getPersonB().getBirthTime());

        log.info("Generating compatibility report: {}({}/{}/{}) x {}({}/{}/{})",
                request.getPersonA().getName(), triA.sun(), triA.moon(), triA.rising(),
                request.getPersonB().getName(), triB.sun(), triB.moon(), triB.rising());

        boolean isPremium = "claude".equalsIgnoreCase(request.getModel());
        String systemPrompt = buildSystemPrompt(isPremium);
        String userPrompt = buildUserPrompt(request, triA, triB, isPremium);
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

    private String buildSystemPrompt(boolean isPremium) {
        if (isPremium) {
            return buildPremiumSystemPrompt();
        }
        return buildFreeSystemPrompt();
    }

    private String buildFreeSystemPrompt() {
        return """
                你是「小登哥」，一位专业的星座占星师。请为以下两位用户生成一份中文星座合盘报告。

                【输出要求】
                1. 只输出 JSON，不要代码块，不要解释，不要额外前后缀。
                2. JSON 必须合法，所有字符串必须正确转义，字段之间必须有英文逗号。
                3. 所有 chapter.content 都必须是普通字符串，不要嵌套对象，不要列表标记。
                4. 不要在 JSON 末尾补任何总结或署名说明，署名只允许出现在最后一章正文里。
                5. 总字数控制在 2000-3000 字。

                【报告结构 - 6章】
                {
                  "score": 60-95 的整数（由系统计算，你只需在范围内填写）,
                  "relationshipType": "4到8个字的关系类型",
                  "tagline": "一句话总结，不超过30字",
                  "chapters": [
                    {"title": "你们的星座基因", "emoji": "✨", "content": "分析两人的太阳、月亮、上升星座，解释各自的性格底色..." },
                    {"title": "你们在一起的化学反应", "emoji": "💞", "content": "两人相处时的吸引点和火花..." },
                    {"title": "你们最容易出问题的地方", "emoji": "⚠️", "content": "潜在的矛盾和摩擦点..." },
                    {"title": "相处指南", "emoji": "🧭", "content": "具体的相处建议..." },
                    {"title": "未来三个月预演", "emoji": "🔮", "content": "未来三个月的感情走向..." },
                    {"title": "写给你的悄悄话", "emoji": "🌙", "content": "温柔的总结和鼓励，结尾署名：—— 小登哥 ✨" }
                  ],
                  "essence": [
                    "6句可收藏的建议，每句不超过30字"
                  ]
                }

                【写作风格】
                - 温暖、有洞察，像朋友聊天
                - 适当使用emoji增加亲和力
                - 避免过于学术化的占星术语
                """;
    }

    private String buildPremiumSystemPrompt() {
        return """
                你是「小登哥」，一位资深的私人占星师，拥有十年一对一咨询经验。请为以下两位用户生成一份深度星座合盘报告。

                【输出要求】
                1. 只输出 JSON，不要代码块，不要解释，不要额外前后缀。
                2. JSON 必须合法，所有字符串必须正确转义，字段之间必须有英文逗号。
                3. 所有 chapter.content 都必须是普通字符串，不要嵌套对象，不要列表标记。
                4. 不要在 JSON 末尾补任何总结或署名说明，署名只允许出现在最后一章正文里。
                5. 总字数控制在 5000-8000 字，每章至少 600 字。
                6. 必须包含具体场景描写，不要泛泛而谈。
                7. 全文必须使用第二人称"你"来叙述，营造一对一咨询的专属感。

                【报告结构 - 8章】
                {
                  "score": 60-95 的整数（由系统计算，你只需在范围内填写）,
                  "relationshipType": "4到8个字的关系类型",
                  "tagline": "一句话总结，不超过30字",
                  "chapters": [
                    {"title": "你们的星座基因", "emoji": "✨", "content": "深度分析两人的太阳、月亮、上升星座。用第二人称'你'叙述，描述这些特质在亲密关系中的具体表现。要有画面感和具体场景：'当你深夜emo时，你的月亮巨蟹让你渴望被拥抱，而TA的月亮水瓶却觉得需要空间...'不要只说星座特质，要描述'你'和'TA'在真实相处中的互动。" },
                    {"title": "你们在一起的化学反应", "emoji": "💞", "content": "描述两人相处时的具体场景和感受。必须包含：1)你身上让TA沉沦的特质；2)TA身上让你心动的瞬间；3)外人眼里你们是什么样的情侣。要有画面感：'你们第一次约会时，TA说的哪句话让你心里动了一下...'使用具体对话和场景描写。" },
                    {"title": "你们最容易出问题的地方", "emoji": "⚠️", "content": "分析3-4个具体矛盾场景。每个矛盾必须按以下五段式写：①场景还原（具体时间地点事件）；②你的期待反应（你希望得到什么回应）；③TA的实际反应（TA实际做了什么）；④结果（关系受到了什么影响）；⑤真相（从星座角度解读为什么会这样）。例如：'场景：你加班到很晚，希望TA来接你。你的期待：TA主动问'要不要我去接你'。TA的实际反应：TA说'那你打车回来吧，我先睡了'。结果：你觉得TA不在乎你。真相：TA的火星摩羯倾向于解决问题而非表达关心...'" },
                    {"title": "相处指南", "emoji": "🧭", "content": "提供6-8条具体可操作的相处策略。每条建议必须包含：①具体场景；②你可以这样说（给出 exact 话术示例）；③为什么有效（星座依据）。例如：'当TA冷战时，你可以这样说：'亲爱的，我现在需要你放下手机，抱我五分钟。'（然后亲TA一下）。为什么有效：TA的水瓶座需要明确的指令而非暗示...'" },
                    {"title": "未来三个月运势预演", "emoji": "🔮", "content": "详细描述未来三个月每个月的感情运势。第一个月：当前状态+关键事件；第二个月：转折点+需要注意的；第三个月：结果+建议。每个预测都要具体到星座能量变化，比如：'下个月金星进入你的第七宫，适合讨论同居或见家长...'" },
                    {"title": "前世缘分：你们是否曾相识", "emoji": "🌟", "content": "从灵魂占星角度解读两人的缘分深度。描述一种'似曾相识'的感觉：'你们第一次见面时，是否有一种莫名的熟悉感...'用故事化的方式描述前世可能的相遇场景，然后回到今生：'这一世，你们重逢是为了...'" },
                    {"title": "复合指南：如果分开了怎么办", "emoji": "💫", "content": "分析如果两人分手，复合的可能性和最佳时机。给出基于星座特质的具体挽回策略，包括：①最佳复合窗口期；②你应该做什么（具体行动）；③避免做什么；④复合后的关系升级建议。" },
                    {"title": "写给你的悄悄话", "emoji": "🌙", "content": "像知心朋友一样，用第二人称写一段300字左右的走心总结。提到：'你像一团火，想要温暖TA...'、'你是狮子，天生就该做女王，但女王也可以偶尔放下皇冠...'这种情感共鸣。最后自然引导：'这份报告可以转发给TA，或截图保存。如果想获取更详细的年度运势，可以关注小登哥的公众号。' 结尾署名：—— 小登哥 ✨" }
                  ],
                  "essence": [
                    "6条珍藏锦囊，每条15字以内，格式如：'当他专注其他事时，直接说'我需要你抱抱我''"
                  ]
                }

                【写作风格】
                - 全文使用第二人称"你"，像真人占星师一对一咨询
                - 有温度、有细节、有画面感，使用具体对话和场景
                - 适当制造'被看穿'的惊喜感，让用户觉得"这说的就是我"
                - 在关键处埋下情感钩子，让用户想分享或保存
                - 避免模板化，每个星座组合的描述都要有独特性
                """;
    }

    private String buildUserPrompt(CompatibilityRequest req,
                                   ZodiacCalculator.ZodiacTriplet triA,
                                   ZodiacCalculator.ZodiacTriplet triB,
                                   boolean isPremium) {
        var a = req.getPersonA();
        var b = req.getPersonB();

        // 使用算法计算分数和关系类型
        int calculatedScore = scoringService.calculateScore(req, triA, triB);
        String relationshipType = scoringService.inferRelationshipType(calculatedScore, triA.sun(), triB.sun());

        StringBuilder sb = new StringBuilder();
        sb.append("请为以下两位用户生成合盘报告。\n\n");
        sb.append("【系统已计算的合盘数据】\n");
        sb.append("合盘分数: ").append(calculatedScore).append("分\n");
        sb.append("关系类型: ").append(relationshipType).append("\n");
        sb.append("请在报告中使用以上分数和关系类型，不要自行编造。\n\n");

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
        sb.append("上升: ").append(triA.rising()).append("\n");
        if (isPremium) {
            sb.append("金星: ").append(triA.sun()).append("（基于太阳星座推算爱情观）").append("\n");
            sb.append("火星: ").append(triA.moon()).append("（基于月亮星座推算行动力）").append("\n");
        }
        sb.append("\n");

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
        sb.append("上升: ").append(triB.rising()).append("\n");
        if (isPremium) {
            sb.append("金星: ").append(triB.sun()).append("（基于太阳星座推算爱情观）").append("\n");
            sb.append("火星: ").append(triB.moon()).append("（基于月亮星座推算行动力）").append("\n");
        }
        sb.append("\n");

        if (isPremium) {
            sb.append("【付费版特别要求 - 必须遵守】\n");
            sb.append("1. 全文必须使用第二人称'你'来叙述，营造一对一咨询的专属感\n");
            sb.append("2. '你们最容易出问题的地方'章节：每个矛盾必须按'场景还原→你的期待→TA的实际反应→结果→真相'五段式写\n");
            sb.append("3. '相处指南'章节：每条建议必须包含具体场景+你可以这样说（exact话术）+为什么有效（星座依据）\n");
            sb.append("4. 分析金星（爱情观）和火星（行动力/性吸引力）的互动\n");
            sb.append("5. 在'复合指南'章节中，给出基于星座特质的具体挽回策略\n");
            sb.append("6. 在最后一章'写给你的悄悄话'中，用知心朋友的口吻写300字情感总结，自然引导用户转发/保存报告\n");
            sb.append("7. essence珍藏锦囊必须是6条，每条15字以内，格式如：'当他专注其他事时，直接说'我需要你抱抱我''\n");
            sb.append("8. 营造'小登哥一对一为你解读'的专属感，让用户觉得'这说的就是我'\n\n");
        }

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

        boolean isPremium = "claude".equalsIgnoreCase(request.getModel());
        int minChapters = isPremium ? PREMIUM_MIN_CHAPTERS : MIN_CHAPTERS;
        int minEssence = isPremium ? PREMIUM_MIN_ESSENCE : MIN_ESSENCE;

        chapters = ensureChapterDefaults(chapters, request, triA, triB, isPremium);
        essence = ensureEssenceDefaults(essence, request, triA, triB, isPremium);

        // 使用算法计算的分数，覆盖 AI 返回的分数
        int calculatedScore = scoringService.calculateScore(request, triA, triB);
        String relationshipType = textOrDefault(root.path("relationshipType"),
                scoringService.inferRelationshipType(calculatedScore, triA.sun(), triB.sun()));
        String tagline = textOrDefault(root.path("tagline"),
                request.getPersonA().getName() + "和" + request.getPersonB().getName() + "之间有吸引，也需要耐心磨合。");

        return CompatibilityResponse.builder()
                .score(calculatedScore)
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
                                                                      ZodiacCalculator.ZodiacTriplet triB,
                                                                      boolean isPremium) {
        List<CompatibilityResponse.Chapter> result = new ArrayList<>(chapters);
        List<CompatibilityResponse.Chapter> fallback = fallbackChapters(request, triA, triB, isPremium);
        int minChapters = isPremium ? PREMIUM_MIN_CHAPTERS : MIN_CHAPTERS;

        // 使用个性化标题替换 AI 返回的标题
        for (int i = 0; i < result.size(); i++) {
            CompatibilityResponse.Chapter current = result.get(i);
            CompatibilityResponse.Chapter base = fallback.get(Math.min(i, fallback.size() - 1));
            String personalizedTitle = scoringService.generateChapterTitle(i, triA.sun(), triB.sun(), isPremium);
            result.set(i, CompatibilityResponse.Chapter.builder()
                    .title(current.getTitle() == null || current.getTitle().isBlank() ? personalizedTitle : current.getTitle())
                    .emoji(current.getEmoji() == null || current.getEmoji().isBlank() ? base.getEmoji() : current.getEmoji())
                    .content(current.getContent() == null || current.getContent().isBlank() ? base.getContent() : current.getContent())
                    .build());
        }

        int idx = result.size();
        while (result.size() < minChapters && idx < fallback.size()) {
            result.add(fallback.get(idx));
            idx++;
        }

        return result;
    }

    private List<String> ensureEssenceDefaults(List<String> essence,
                                               CompatibilityRequest request,
                                               ZodiacCalculator.ZodiacTriplet triA,
                                               ZodiacCalculator.ZodiacTriplet triB,
                                               boolean isPremium) {
        List<String> result = new ArrayList<>(essence);
        List<String> fallback = fallbackEssence(request, triA, triB, isPremium);
        int minEssence = isPremium ? PREMIUM_MIN_ESSENCE : MIN_ESSENCE;
        int idx = 0;
        while (result.size() < minEssence && idx < fallback.size()) {
            result.add(fallback.get(idx));
            idx++;
        }
        return result;
    }

    private CompatibilityResponse buildFallbackResponse(CompatibilityRequest request,
                                                        ZodiacCalculator.ZodiacTriplet triA,
                                                        ZodiacCalculator.ZodiacTriplet triB,
                                                        String raw) {
        boolean isPremium = "claude".equalsIgnoreCase(request.getModel());
        int score = scoringService.calculateScore(request, triA, triB);
        return CompatibilityResponse.builder()
                .score(score)
                .relationshipType(scoringService.inferRelationshipType(score, triA.sun(), triB.sun()))
                .tagline(request.getPersonA().getName() + "和" + request.getPersonB().getName() + "之间有真实吸引，也需要更温柔的表达。")
                .chapters(fallbackChapters(request, triA, triB, isPremium))
                .essence(fallbackEssence(request, triA, triB, isPremium))
                .reportUid(generateReportUid(triA.sun(), triB.sun()))
                .zodiacA(toZodiacInfo(triA))
                .zodiacB(toZodiacInfo(triB))
                .build();
    }

    private List<CompatibilityResponse.Chapter> fallbackChapters(CompatibilityRequest request,
                                                                 ZodiacCalculator.ZodiacTriplet triA,
                                                                 ZodiacCalculator.ZodiacTriplet triB,
                                                                 boolean isPremium) {
        String nameA = request.getPersonA().getName();
        String nameB = request.getPersonB().getName();

        List<CompatibilityResponse.Chapter> chapters = new ArrayList<>();
        chapters.add(chapter(
            scoringService.generateChapterTitle(0, triA.sun(), triB.sun(), isPremium), "✨",
            nameA + "的太阳" + triA.sun() + "让TA在关系里更重视稳定和投入，月亮" + triA.moon() + "让情绪表达带着主观热度，上升" + triA.rising() + "又会把很多担心藏进细节里。"
                    + nameB + "这边的太阳" + triB.sun() + "更在意被看见的感觉，月亮" + triB.moon() + "决定了内心真正的安全需求，上升" + triB.rising() + "则影响TA在关系里的第一反应。你们不是没有默契，而是默契常常被表达方式拖慢。"));
        chapters.add(chapter(
            scoringService.generateChapterTitle(1, triA.sun(), triB.sun(), isPremium), "💞",
            nameA + "容易被" + nameB + "身上更鲜明、更直接的情绪吸引，" + nameB + "也会被" + nameA + "带来的稳定感安抚。好的时候，这段关系很容易形成一个人点火、一个人续航的组合。问题在于，一旦其中一方退回自己的舒适区，另一方就会误读成冷淡或不在乎。"));
        chapters.add(chapter(
            scoringService.generateChapterTitle(2, triA.sun(), triB.sun(), isPremium), "⚠️",
            "你们最大的摩擦往往不是爱得不够，而是节奏不一致。一个人希望马上回应，另一个人习惯先消化再表达；一个人想确认关系，另一个人先去处理现实细节。矛盾累积后，就会从具体事情升级成“你是不是根本不懂我”。这类关系最怕把情绪拖成沉默。"));
        chapters.add(chapter(
            scoringService.generateChapterTitle(3, triA.sun(), triB.sun(), isPremium), "🧭",
            "先约定一个固定的沟通动作，比方说遇到分歧时先说明情绪、再说诉求、最后给出具体请求。对" + nameA + "来说，少一点闷着做事、多一点把想法说出来；对" + nameB + "来说，少一点试探式表达、多一点直接说明自己要什么。你们需要的不是更激烈，而是更清楚。"));
        chapters.add(chapter(
            scoringService.generateChapterTitle(4, triA.sun(), triB.sun(), isPremium), "🔮",
            "接下来三个月，这段关系适合处理现实安排、边界感和期待值。只要把容易误解的事情说清楚，关系会稳得更快；如果继续靠猜，前期的小别扭很容易放大。建议把重要话题放到情绪平稳的时候谈，不要在最上头的时候决定关系走向。"));
        chapters.add(chapter(
            scoringService.generateChapterTitle(5, triA.sun(), triB.sun(), isPremium), "🌙",
            nameA + "，你们之间不是没有缘分，而是这段缘分更考验耐心和表达。真正重要的，不是谁更会爱，而是谁愿意在误解出现时往前走一步。你把心事说出来，TA才有机会真正靠近你。\n\n—— 小登哥 ✨"));

        if (isPremium) {
            chapters.add(chapter(
                scoringService.generateChapterTitle(6, triA.sun(), triB.sun(), true), "🌟",
                "从灵魂占星的角度来看，" + nameA + "和" + nameB + "的相遇并非偶然。你们的北交点和南交点形成了某种呼应，这意味着你们在灵魂层面有着未完成的课题。" + nameA + "的月亮" + triA.moon() + "和" + nameB + "的上升" + triB.rising() + "形成了柔和的相位，这解释了为什么你们第一次见面时会有那种莫名的熟悉感。\n\n前世你们可能是师生关系、伙伴关系，或者是彼此生命中重要的过客。这一世重逢，是为了完成前世未尽的缘分。"));
            chapters.add(chapter(
                scoringService.generateChapterTitle(7, triA.sun(), triB.sun(), true), "💫",
                "如果你们的感情正面临危机，或者已经分开，请不要绝望。根据你们的星座组合，" + triA.sun() + "和" + triB.sun() + "的复合窗口期通常在分开后的 3-6 个月。\n\n" + nameA + "需要做的是：给" + nameB + "足够的空间，不要频繁联系，让TA想念你的好。" + nameB + "需要做的是：正视自己的情感需求，不要因为骄傲而错过真正适合的人。\n\n最佳的复合时机是在水星顺行期间，选择一个双方都情绪平稳的日子，坦诚地表达你的感受。记住，复合不是为了回到过去，而是为了创造一个更好的未来。\n\n—— 小登哥 ✨"));
        }

        return chapters;
    }

    private List<String> fallbackEssence(CompatibilityRequest request,
                                         ZodiacCalculator.ZodiacTriplet triA,
                                         ZodiacCalculator.ZodiacTriplet triB,
                                         boolean isPremium) {
        String nameA = request.getPersonA().getName();
        String nameB = request.getPersonB().getName();
        List<String> essence = new ArrayList<>();
        essence.add("别把“我以为你懂”当成沟通。");
        essence.add(nameA + "先讲感受，" + nameB + "再讲需求，效率会高很多。");
        essence.add("稳定感不是沉默，是让对方知道你还在。");
        essence.add("情绪上来的时候先暂停，别急着判关系输赢。");
        essence.add("你们适合把模糊的问题说具体。");
        essence.add("真正拉开差距的，从来不是星座，是愿不愿意认真回应彼此。");

        if (isPremium) {
            essence.add("【短期】每周安排一次'无手机约会'，专注陪伴对方。");
            essence.add("【短期】吵架后 24 小时内必须有一次非指责性沟通。");
            essence.add("【短期】学会用对方的'爱的语言'表达关心。");
            essence.add("【长期】每年一起做一件新鲜事，保持关系的新鲜感。");
            essence.add("【长期】建立共同的财务目标，增强关系的稳定性。");
            essence.add("【长期】定期回顾这份报告，看看哪些建议已经实现了。");
            essence.add("【长期】关注水逆期，重要决定避开这段时间。");
            essence.add("【长期】培养一个共同爱好，成为你们的'关系锚点'。");
        }

        return essence;
    }

    private CompatibilityResponse.Chapter chapter(String title, String emoji, String content) {
        return CompatibilityResponse.Chapter.builder()
                .title(title)
                .emoji(emoji)
                .content(content)
                .build();
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
        e.setModelCode(normalizeModelCode(req.getModel()));
        e.setRelationshipType(resp.getRelationshipType());
        e.setTagline(resp.getTagline());
        e.setFullReport(rawJson);

        e.setIpAddress(ip);
        e.setUserAgent(userAgent != null && userAgent.length() > 500
                ? userAgent.substring(0, 500) : userAgent);
        e.setSharedCount(0);
        return e;
    }

    private String normalizeModelCode(String modelCode) {
        return "claude".equalsIgnoreCase(modelCode) ? "claude" : "deepseek";
    }
}
