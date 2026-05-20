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

@Slf4j
@Service
@RequiredArgsConstructor
public class CompatibilityService {

    private final ClaudeService claudeService;
    private final SoulmateReportRepository repository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final SecureRandom rng = new SecureRandom();

    private static final String CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";

    public CompatibilityResponse generateReport(CompatibilityRequest request, String ip, String userAgent) {
        var triA = ZodiacCalculator.computeAll(request.getPersonA().getBirthDate(), request.getPersonA().getBirthTime());
        var triB = ZodiacCalculator.computeAll(request.getPersonB().getBirthDate(), request.getPersonB().getBirthTime());

        log.info("生成合盘:{}({}/{}/{}) × {}({}/{}/{})",
                request.getPersonA().getName(), triA.sun(), triA.moon(), triA.rising(),
                request.getPersonB().getName(), triB.sun(), triB.moon(), triB.rising());

        String systemPrompt = buildSystemPrompt();
        String userPrompt = buildUserPrompt(request, triA, triB);
        String raw = claudeService.generate(systemPrompt, userPrompt);

        CompatibilityResponse response = parseResponse(raw, triA, triB);

        try {
            SoulmateReport entity = toEntity(request, response, triA, triB, raw, ip, userAgent);
            repository.save(entity);
        } catch (Exception e) {
            log.warn("入库失败,不影响返回报告: {}", e.getMessage());
        }

        return response;
    }

    private String buildSystemPrompt() {
        return """
                你是「小登哥」——一位真实在恋爱中的资深星座观察者(本人是射手男,女朋友是天蝎女,所以特别懂这一对,但对其他星座组合也有深度洞察)。
                
                你的语言风格:
                - 温柔、细腻、共情,像一个走心的姐姐在跟好朋友讲话
                - 但有幽默感和洞察力,擅长用一句话戳破真相,让人忍不住笑然后心里一沉
                - 文字有质感,不口语化,不用"哈哈""哎呀""嘛"这种语气词
                - 偶尔用"我跟你讲""说真的""你别不信""相信我"这种亲近但不轻浮的表达
                - 句子有节奏,长短结合,不要全是大段长句
                - 拒绝以下表达:"星座姐姐""小姐姐""babe""亲""哈哈"
                
                你的署名:小登哥(不是"姐姐",绝对不是"姐姐")
                
                你必须严格按 JSON 格式输出,不要任何 JSON 以外的内容,不要用 markdown 代码块包裹。
                
                输出格式:
                {
                  "score": 数字 60-95 之间,代表整体匹配度,
                  "relationshipType": "关系类型,4-8字,如:灵魂深度互补型、火土平衡型、甜蜜磨合型、双向救赎型 等",
                  "tagline": "一句话总结你们的关系特质,不超过 30 字,有洞察力",
                  "chapters": [
                    {
                      "title": "你们的星座基因",
                      "emoji": "✨",
                      "content": "约 700 字。直接讲两人在亲密关系里最深的渴望和恐惧。带上太阳/月亮/上升的解读(太阳=外在性格,月亮=情绪内核,上升=别人眼里的你)。"
                    },
                    {
                      "title": "你们在一起的化学反应",
                      "emoji": "💕",
                      "content": "约 800 字。三个小节:你身上让 TA 沉沦的特质、TA 身上让你心动的特质、外人眼里你们是什么样的情侣。"
                    },
                    {
                      "title": "你们最容易出问题的地方",
                      "emoji": "⚡",
                      "content": "约 1200 字。写 3-4 个核心矛盾,每个矛盾用'场景还原-期待反应-实际反应-结果-真相'五段式。要敢于戳痛,要具体。"
                    },
                    {
                      "title": "相处指南",
                      "emoji": "🌹",
                      "content": "约 1000 字。给 4-5 条可执行到'说什么话/做什么动作'的具体建议。绝对不能用'多沟通'这种废话。"
                    },
                    {
                      "title": "未来三个月预测",
                      "emoji": "🔮",
                      "content": "约 600 字。给出具体月份的关系走向,包含关键节点提醒。"
                    },
                    {
                      "title": "写给你的悄悄话",
                      "emoji": "💌",
                      "content": "约 350 字。纯共情段,用小登哥的口吻收尾。结尾署名必须是 '—— 小登哥 ✨',不能写'你的星座姐姐'之类。"
                    }
                  ],
                  "essence": [
                    "6 条可直接收藏的精华相处锦囊,每条不超过 60 字",
                    "每条都是从前面章节提炼的具体动作或关键洞察",
                    "可以用 **重点** 标记最戳的几个字",
                    "第 6 条要有点幽默感,像小登哥的口吻",
                    "...",
                    "..."
                  ]
                }
                
                内容硬性要求:
                1. 全程不写"白羊座热情""巨蟹座顾家"等百度百科式描述
                2. 用对方真实的名字称呼,而不是"她/他"——比如"snow 这种类型的女生..." 而不是"她那种类型..."
                3. 第三章是核心,要写得最深入最具体
                4. 第四章建议要具体到"开场白怎么说""遇到 XX 情况怎么做"
                5. 第六章结尾必须以 "—— 小登哥 ✨" 落款,这是出品人署名
                6. 整体字数 4500-5500 字
                7. 文风:走心但克制,有洞察但不油腻,幽默但不浮夸
                """;
    }

    private String buildUserPrompt(CompatibilityRequest req,
                                    ZodiacCalculator.ZodiacTriplet triA,
                                    ZodiacCalculator.ZodiacTriplet triB) {
        var a = req.getPersonA();
        var b = req.getPersonB();
        StringBuilder sb = new StringBuilder();
        sb.append("请为以下两位用户生成深度合盘报告:\n\n");

        sb.append("【用户 A · 报告主角】\n");
        sb.append("姓名:").append(a.getName()).append("\n");
        sb.append("性别:").append("male".equals(a.getGender()) ? "男" : "女").append("\n");
        sb.append("生日:").append(a.getBirthDate()).append("\n");
        if (a.getBirthTime() != null && !a.getBirthTime().isBlank())
            sb.append("出生时间:").append(a.getBirthTime()).append("\n");
        if (a.getBirthPlace() != null && !a.getBirthPlace().isBlank())
            sb.append("出生地:").append(a.getBirthPlace()).append("\n");
        sb.append("☀ 太阳:").append(triA.sun()).append("\n");
        sb.append("🌙 月亮:").append(triA.moon()).append("\n");
        sb.append("⬆ 上升:").append(triA.rising()).append("\n\n");

        sb.append("【用户 B · TA】\n");
        sb.append("姓名:").append(b.getName()).append("\n");
        sb.append("性别:").append("male".equals(b.getGender()) ? "男" : "女").append("\n");
        sb.append("生日:").append(b.getBirthDate()).append("\n");
        if (b.getBirthTime() != null && !b.getBirthTime().isBlank())
            sb.append("出生时间:").append(b.getBirthTime()).append("\n");
        if (b.getBirthPlace() != null && !b.getBirthPlace().isBlank())
            sb.append("出生地:").append(b.getBirthPlace()).append("\n");
        sb.append("☀ 太阳:").append(triB.sun()).append("\n");
        sb.append("🌙 月亮:").append(triB.moon()).append("\n");
        sb.append("⬆ 上升:").append(triB.rising()).append("\n\n");

        sb.append("请严格按系统提示的 JSON 格式输出,这份报告是给主角 ").append(a.getName())
          .append(" 看的,所有称呼角度从 ").append(a.getName()).append(" 出发。");

        return sb.toString();
    }

    private CompatibilityResponse parseResponse(String raw,
                                                  ZodiacCalculator.ZodiacTriplet triA,
                                                  ZodiacCalculator.ZodiacTriplet triB) {
        try {
            String cleaned = raw.trim();
            if (cleaned.startsWith("```json")) cleaned = cleaned.substring(7);
            else if (cleaned.startsWith("```")) cleaned = cleaned.substring(3);
            if (cleaned.endsWith("```")) cleaned = cleaned.substring(0, cleaned.length() - 3);
            cleaned = cleaned.trim();

            int first = cleaned.indexOf('{');
            int last = cleaned.lastIndexOf('}');
            if (first >= 0 && last > first) {
                cleaned = cleaned.substring(first, last + 1);
            }

            JsonNode root = objectMapper.readTree(cleaned);

            List<CompatibilityResponse.Chapter> chapters = new ArrayList<>();
            JsonNode chaptersNode = root.path("chapters");
            if (chaptersNode.isArray()) {
                for (Iterator<JsonNode> it = chaptersNode.elements(); it.hasNext();) {
                    JsonNode c = it.next();
                    chapters.add(CompatibilityResponse.Chapter.builder()
                            .title(c.path("title").asText())
                            .emoji(c.path("emoji").asText("✨"))
                            .content(c.path("content").asText())
                            .build());
                }
            }

            List<String> essence = new ArrayList<>();
            JsonNode essenceNode = root.path("essence");
            if (essenceNode.isArray()) {
                for (Iterator<JsonNode> it = essenceNode.elements(); it.hasNext();) {
                    essence.add(it.next().asText());
                }
            }

            return CompatibilityResponse.builder()
                    .score(root.path("score").asInt(82))
                    .relationshipType(root.path("relationshipType").asText("灵魂共振型"))
                    .tagline(root.path("tagline").asText(""))
                    .chapters(chapters)
                    .essence(essence)
                    .reportUid(generateReportUid(triA.sun(), triB.sun()))
                    .zodiacA(toZodiacInfo(triA))
                    .zodiacB(toZodiacInfo(triB))
                    .build();
        } catch (Exception e) {
            log.error("解析 Claude 响应失败,raw 前 500 字符: {}",
                    raw.length() > 500 ? raw.substring(0, 500) : raw, e);
            throw new RuntimeException("报告生成异常,请重试", e);
        }
    }

    private CompatibilityResponse.ZodiacInfo toZodiacInfo(ZodiacCalculator.ZodiacTriplet t) {
        return CompatibilityResponse.ZodiacInfo.builder()
                .sun(t.sun()).moon(t.moon()).rising(t.rising())
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
