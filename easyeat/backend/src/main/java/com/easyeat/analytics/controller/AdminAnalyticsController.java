package com.easyeat.analytics.controller;

import com.easyeat.analytics.dto.DishRankingItemResponse;
import com.easyeat.analytics.dto.FinancialHealthResponse;
import com.easyeat.analytics.service.DishRankingService;
import com.easyeat.analytics.service.FinancialHealthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/analytics")
@PreAuthorize("hasRole('ADMINISTRADOR') or hasRole('ADMIN')")
public class AdminAnalyticsController {

    private final FinancialHealthService financialHealthService;
    private final DishRankingService dishRankingService;

    public AdminAnalyticsController(
            FinancialHealthService financialHealthService,
            DishRankingService dishRankingService
    ) {
        this.financialHealthService = financialHealthService;
        this.dishRankingService = dishRankingService;
    }

    @GetMapping("/financial-health")
    public ResponseEntity<FinancialHealthResponse> getFinancialHealth(
            @RequestParam(defaultValue = "day") String granularity
    ) {
        FinancialHealthResponse response = financialHealthService.getFinancialHealth(granularity);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                .body(response);
    }

    @GetMapping("/dish-ranking")
    public ResponseEntity<List<DishRankingItemResponse>> getDishRanking(
            @RequestParam(defaultValue = "all") String granularity
    ) {
        List<DishRankingItemResponse> response = dishRankingService.getDishRanking(granularity);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "no-cache, no-store, must-revalidate")
                .body(response);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ProblemDetail> handleIllegalArgument(
            IllegalArgumentException ex,
            HttpServletRequest request
    ) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setType(URI.create("https://easyeat.com/errors/bad-request"));
        problem.setTitle("Bad Request");
        problem.setInstance(URI.create(request.getRequestURI()));
        return ResponseEntity.badRequest().body(problem);
    }
}
