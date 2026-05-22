package com.zodiac.api.controller;

import com.zodiac.api.dto.AdminLoginRequest;
import com.zodiac.api.dto.AdminOverviewResponse;
import com.zodiac.api.dto.AdminReportPageResponse;
import com.zodiac.api.exception.AdminAuthException;
import com.zodiac.api.service.AdminAuthService;
import com.zodiac.api.service.AdminDashboardService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminAuthService adminAuthService;
    private final AdminDashboardService adminDashboardService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@Valid @RequestBody AdminLoginRequest request) {
        var result = adminAuthService.login(request.getUsername(), request.getPassword());
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "token", result.token(),
                "expiresAt", result.expiresAt().toString()
        ));
    }

    @GetMapping("/overview")
    public AdminOverviewResponse overview(HttpServletRequest request) {
        requireAdmin(request);
        return adminDashboardService.getOverview();
    }

    @GetMapping("/reports")
    public AdminReportPageResponse reports(HttpServletRequest request,
                                           @RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "20") int size,
                                           @RequestParam(required = false) String query) {
        requireAdmin(request);
        return adminDashboardService.getReports(query, page, size);
    }

    private void requireAdmin(HttpServletRequest request) {
        String token = resolveToken(request);
        adminAuthService.requireValidToken(token);
    }

    private String resolveToken(HttpServletRequest request) {
        String header = request.getHeader("X-Admin-Token");
        if (header != null && !header.isBlank()) {
            return header;
        }
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring(7);
        }
        throw new AdminAuthException("请先登录后台");
    }
}
