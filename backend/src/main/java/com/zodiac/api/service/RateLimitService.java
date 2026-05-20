package com.zodiac.api.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.zodiac.api.repository.SoulmateReportRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 限流服务
 * - 全局每日总额度(默认 200 次)
 * - 单 IP 每日次数(默认 3 次)
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RateLimitService {

    private final SoulmateReportRepository repository;

    @Value("${ratelimit.daily-total}")
    private int dailyTotal;

    @Value("${ratelimit.per-ip-daily}")
    private int perIpDaily;

    private final AtomicLong globalDailyCounter = new AtomicLong(0);
    private volatile LocalDate currentDay = LocalDate.now();

    private final Cache<String, AtomicLong> ipCounter = Caffeine.newBuilder()
            .expireAfterWrite(24, TimeUnit.HOURS)
            .maximumSize(10000)
            .build();

    public synchronized void syncFromDatabase() {
        try {
            LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
            long count = repository.countTodayReports(startOfDay);
            globalDailyCounter.set(count);
            log.info("启动同步:今天已生成 {} 份报告", count);
        } catch (Exception e) {
            log.warn("同步限流计数失败,使用 0: {}", e.getMessage());
            globalDailyCounter.set(0);
        }
    }

    @Scheduled(cron = "0 0 0 * * *")
    public void resetDaily() {
        globalDailyCounter.set(0);
        currentDay = LocalDate.now();
        log.info("日计数器已重置");
    }

    public String tryAcquire(String ip) {
        if (!LocalDate.now().equals(currentDay)) {
            globalDailyCounter.set(0);
            currentDay = LocalDate.now();
        }

        long now = globalDailyCounter.incrementAndGet();
        if (now > dailyTotal) {
            globalDailyCounter.decrementAndGet();
            return String.format("今日测算名额已满(%d 份),明天再来吧 ✨", dailyTotal);
        }

        AtomicLong ipCount = ipCounter.get(ip, k -> new AtomicLong(0));
        long ipNow = ipCount.incrementAndGet();
        if (ipNow > perIpDaily) {
            ipCount.decrementAndGet();
            globalDailyCounter.decrementAndGet();
            return String.format("你今天已经测了 %d 次了,明天再来吧 💕", perIpDaily);
        }

        return null;
    }

    public void rollback(String ip) {
        globalDailyCounter.decrementAndGet();
        AtomicLong ipCount = ipCounter.getIfPresent(ip);
        if (ipCount != null) ipCount.decrementAndGet();
    }

    public long getGlobalUsed() { return globalDailyCounter.get(); }
    public int getDailyTotal() { return dailyTotal; }
}
