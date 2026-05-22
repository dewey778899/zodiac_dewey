package com.zodiac.api.controller;

import com.zodiac.api.dto.CompatibilityRequest;
import com.zodiac.api.dto.CompatibilityResponse;
import com.zodiac.api.dto.WechatUpdateRequest;
import com.zodiac.api.repository.SoulmateReportRepository;
import com.zodiac.api.service.AnalyticsService;
import com.zodiac.api.service.CompatibilityService;
import com.zodiac.api.service.RateLimitService;
import com.zodiac.api.util.IpUtil;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class CompatibilityController {

    private final CompatibilityService compatibilityService;
    private final RateLimitService rateLimitService;
    private final SoulmateReportRepository repository;
    private final AnalyticsService analyticsService;

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of(
                "status", "ok",
                "service", "zodiac-api",
                "global_used", rateLimitService.getGlobalUsed(),
                "global_total", rateLimitService.getDailyTotal()
        );
    }

    @PostMapping("/compatibility")
    public ResponseEntity<?> generate(@Valid @RequestBody CompatibilityRequest request,
                                       HttpServletRequest httpReq) {
        String ip = IpUtil.getClientIp(httpReq);
        String ua = httpReq.getHeader("User-Agent");

        // 1. 限流检查
        String limitMsg = rateLimitService.tryAcquire(ip);
        if (limitMsg != null) {
            return ResponseEntity.status(429).body(Map.of(
                    "error", "rate_limited",
                    "message", limitMsg
            ));
        }

        // 2. 生成报告
        try {
            CompatibilityResponse resp = compatibilityService.generateReport(request, ip, ua);
            analyticsService.recordGenerateSuccess(request.getModel(), resp.getReportUid(), ip, ua);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            log.error("生成报告失败,回滚限流计数", e);
            rateLimitService.rollback(ip);
            return ResponseEntity.status(500).body(Map.of(
                    "error", "generation_failed",
                    "message", e.getMessage() != null ? e.getMessage() : "生成失败"
            ));
        }
    }

    @PostMapping("/wechat")
    public ResponseEntity<?> updateWechat(@Valid @RequestBody WechatUpdateRequest req) {
        int updated = repository.updateWechatId(req.getReportUid(), req.getWechatId());
        if (updated > 0) {
            return ResponseEntity.ok(Map.of("status", "ok", "message", "提交成功,小登哥会看到你的消息 💕"));
        }
        return ResponseEntity.status(404).body(Map.of(
                "error", "not_found",
                "message", "报告编号不存在"
        ));
    }

    @PostMapping("/share/{uid}")
    public ResponseEntity<?> recordShare(@PathVariable String uid) {
        repository.incrementShareCount(uid);
        return ResponseEntity.ok(Map.of("status", "ok"));
    }
}
