package com.zodiac.api.util;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * 星座计算工具
 * - 太阳:基于生日精确计算
 * - 月亮:基于月亮 27.3 天绕地球一周的天文近似
 * - 上升:基于出生时间(2 小时切换一个),需要 birthTime,否则用太阳代替
 * <p>
 * 注:MVP 简化版,后期可接入 Swiss Ephemeris 替换为精确天文计算。
 */
public class ZodiacCalculator {

    private static final String[] ZODIAC_NAMES = {
            "白羊座", "金牛座", "双子座", "巨蟹座", "狮子座", "处女座",
            "天秤座", "天蝎座", "射手座", "摩羯座", "水瓶座", "双鱼座"
    };

    public static String computeSun(String birthDate) {
        if (birthDate == null || birthDate.isBlank()) return "未知";
        try {
            LocalDate d = LocalDate.parse(birthDate, DateTimeFormatter.ISO_LOCAL_DATE);
            int m = d.getMonthValue(), day = d.getDayOfMonth();
            if ((m == 3 && day >= 21) || (m == 4 && day <= 19)) return "白羊座";
            if ((m == 4 && day >= 20) || (m == 5 && day <= 20)) return "金牛座";
            if ((m == 5 && day >= 21) || (m == 6 && day <= 21)) return "双子座";
            if ((m == 6 && day >= 22) || (m == 7 && day <= 22)) return "巨蟹座";
            if ((m == 7 && day >= 23) || (m == 8 && day <= 22)) return "狮子座";
            if ((m == 8 && day >= 23) || (m == 9 && day <= 22)) return "处女座";
            if ((m == 9 && day >= 23) || (m == 10 && day <= 23)) return "天秤座";
            if ((m == 10 && day >= 24) || (m == 11 && day <= 22)) return "天蝎座";
            if ((m == 11 && day >= 23) || (m == 12 && day <= 21)) return "射手座";
            if ((m == 12 && day >= 22) || (m == 1 && day <= 19)) return "摩羯座";
            if ((m == 1 && day >= 20) || (m == 2 && day <= 18)) return "水瓶座";
            return "双鱼座";
        } catch (Exception e) {
            return "未知";
        }
    }

    public static String computeMoon(String birthDate, String birthTime) {
        if (birthDate == null || birthDate.isBlank()) return "未知";
        try {
            LocalDate ref = LocalDate.of(2000, 1, 6);
            LocalDate d = LocalDate.parse(birthDate, DateTimeFormatter.ISO_LOCAL_DATE);

            long daysDiff = d.toEpochDay() - ref.toEpochDay();
            double hourFraction;
            if (birthTime != null && !birthTime.isBlank()) {
                try {
                    LocalTime t = LocalTime.parse(birthTime);
                    hourFraction = (t.getHour() + t.getMinute() / 60.0) / 24.0;
                } catch (Exception ignore) {
                    hourFraction = 0.5;
                }
            } else {
                hourFraction = 0.5;
            }

            double totalDays = daysDiff + hourFraction;
            double moonDegree = (totalDays / 27.3216) * 360.0;
            moonDegree = ((moonDegree % 360) + 360) % 360;
            int signIndex = (int) (moonDegree / 30);
            return ZODIAC_NAMES[signIndex];
        } catch (Exception e) {
            return "未知";
        }
    }

    public static String computeRising(String birthDate, String birthTime) {
        if (birthDate == null || birthDate.isBlank()) return "未知";
        if (birthTime == null || birthTime.isBlank()) {
            return computeSun(birthDate);
        }

        try {
            String sun = computeSun(birthDate);
            int sunIdx = -1;
            for (int i = 0; i < ZODIAC_NAMES.length; i++) {
                if (ZODIAC_NAMES[i].equals(sun)) {
                    sunIdx = i;
                    break;
                }
            }
            if (sunIdx < 0) return "未知";

            LocalTime t = LocalTime.parse(birthTime);
            double timeOffset = t.getHour() + t.getMinute() / 60.0;
            int offset = (int) ((timeOffset - 6 + 24) % 24 / 2);
            int risingIdx = (sunIdx + offset) % 12;
            return ZODIAC_NAMES[risingIdx];
        } catch (Exception e) {
            return computeSun(birthDate);
        }
    }

    public static ZodiacTriplet computeAll(String birthDate, String birthTime) {
        return new ZodiacTriplet(
                computeSun(birthDate),
                computeMoon(birthDate, birthTime),
                computeRising(birthDate, birthTime)
        );
    }

    public record ZodiacTriplet(String sun, String moon, String rising) {}
}
