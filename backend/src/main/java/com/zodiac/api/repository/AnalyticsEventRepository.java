package com.zodiac.api.repository;

import com.zodiac.api.entity.AnalyticsEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AnalyticsEventRepository extends JpaRepository<AnalyticsEvent, Long> {

    long countByEventType(String eventType);

    long countByEventTypeAndCreatedAtGreaterThanEqual(String eventType, LocalDateTime start);

    long countByEventTypeAndModelCode(String eventType, String modelCode);

    long countByEventTypeAndModelCodeAndCreatedAtGreaterThanEqual(String eventType, String modelCode, LocalDateTime start);

    long countByEventTypeAndChannel(String eventType, String channel);

    long countByEventTypeAndChannelAndCreatedAtGreaterThanEqual(String eventType, String channel, LocalDateTime start);

    List<AnalyticsEvent> findByCreatedAtGreaterThanEqualOrderByCreatedAtAsc(LocalDateTime start);
}
