package iuh.fit.payment_service.controller;

import iuh.fit.common.dto.response.ApiResponse;
import iuh.fit.payment_service.dto.response.AdminRevenueOverviewResponse;
import iuh.fit.payment_service.dto.response.DailyRevenueSummary;
import iuh.fit.payment_service.entity.PaymentTransaction;
import iuh.fit.payment_service.enums.PaymentMethod;
import iuh.fit.payment_service.enums.PaymentStatus;
import iuh.fit.payment_service.service.AdminRevenueService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/admin/revenue")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
@Tag(name = "Admin Revenue API", description = "Admin endpoints for viewing system revenue and payment statistics")
public class AdminRevenueController {

    private final AdminRevenueService adminRevenueService;

    @GetMapping("/overview")
    @Operation(
            summary = "Get admin revenue overview",
            description = "Get total revenue, total transactions, growth rates, daily breakdowns, method breakdowns, and status breakdowns."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Overview retrieved successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access denied - ADMIN role required")
    })
    public ResponseEntity<ApiResponse<AdminRevenueOverviewResponse>> getOverview(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate
    ) {
        log.info("[Controller] GET /api/admin/revenue/overview - startDate={}, endDate={}", startDate, endDate);
        Instant start = parseInstant(startDate, false);
        Instant end = parseInstant(endDate, true);
        AdminRevenueOverviewResponse response = adminRevenueService.getRevenueOverview(start, end);
        return ResponseEntity.ok(ApiResponse.<AdminRevenueOverviewResponse>builder()
                .message("Revenue overview retrieved successfully")
                .result(response)
                .build());
    }

    @GetMapping("/daily")
    @Operation(
            summary = "Get daily revenue breakdown",
            description = "Get a list of successful transactions and total revenues grouped by day."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Daily breakdown retrieved successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access denied - ADMIN role required")
    })
    public ResponseEntity<ApiResponse<List<DailyRevenueSummary>>> getDailyRevenue(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate
    ) {
        log.info("[Controller] GET /api/admin/revenue/daily - startDate={}, endDate={}", startDate, endDate);
        Instant start = parseInstant(startDate, false);
        Instant end = parseInstant(endDate, true);
        List<DailyRevenueSummary> response = adminRevenueService.getDailyRevenueBreakdown(start, end);
        return ResponseEntity.ok(ApiResponse.<List<DailyRevenueSummary>>builder()
                .message("Daily revenue breakdown retrieved successfully")
                .result(response)
                .build());
    }

    @GetMapping("/transactions")
    @Operation(
            summary = "Get transactions list for admin",
            description = "Get a paginated list of all payment transactions in the system with status, method, and date filters."
    )
    @ApiResponses(value = {
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Transactions retrieved successfully"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "403", description = "Access denied - ADMIN role required")
    })
    public ResponseEntity<ApiResponse<Page<PaymentTransaction>>> getTransactions(
            @RequestParam(required = false) List<PaymentStatus> statuses,
            @RequestParam(required = false) List<PaymentMethod> methods,
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "createdAt,desc") String sort
    ) {
        log.info("[Controller] GET /api/admin/revenue/transactions - statuses={}, methods={}, page={}, size={}, sort={}",
                statuses, methods, page, size, sort);

        List<Sort.Order> orders = new ArrayList<>();
        if (sort != null && !sort.isBlank()) {
            String[] parts = sort.split(",");
            String property = parts[0];
            String direction = parts.length > 1 ? parts[1] : "desc";
            orders.add(new Sort.Order(
                    direction.equalsIgnoreCase("asc") ? Sort.Direction.ASC : Sort.Direction.DESC,
                    property
            ));
        }

        Pageable pageable = PageRequest.of(page, size, Sort.by(orders));
        Instant start = parseInstant(startDate, false);
        Instant end = parseInstant(endDate, true);
        Page<PaymentTransaction> result = adminRevenueService.getTransactions(statuses, methods, start, end, pageable);

        return ResponseEntity.ok(ApiResponse.<Page<PaymentTransaction>>builder()
                .message("Transactions retrieved successfully")
                .result(result)
                .build());
    }

    @GetMapping("/statistics")
    @Operation(
            summary = "Get admin revenue statistics for date range",
            description = "Get total revenue, total transactions, growth rates, daily breakdowns, method breakdowns, and status breakdowns."
    )
    public ResponseEntity<ApiResponse<AdminRevenueOverviewResponse>> getStatistics(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate
    ) {
        log.info("[Controller] GET /api/admin/revenue/statistics - startDate={}, endDate={}", startDate, endDate);
        Instant start = parseInstant(startDate, false);
        Instant end = parseInstant(endDate, true);
        AdminRevenueOverviewResponse response = adminRevenueService.getRevenueOverview(start, end);
        return ResponseEntity.ok(ApiResponse.<AdminRevenueOverviewResponse>builder()
                .message("Revenue statistics retrieved successfully")
                .result(response)
                .build());
    }

    @GetMapping("/weekly")
    @Operation(
            summary = "Get weekly admin revenue overview",
            description = "Get total revenue, growth rates, and breakdowns for the last 7 days."
    )
    public ResponseEntity<ApiResponse<AdminRevenueOverviewResponse>> getWeeklyRevenue() {
        log.info("[Controller] GET /api/admin/revenue/weekly");
        AdminRevenueOverviewResponse response = adminRevenueService.getWeeklyRevenue();
        return ResponseEntity.ok(ApiResponse.<AdminRevenueOverviewResponse>builder()
                .message("Weekly revenue overview retrieved successfully")
                .result(response)
                .build());
    }

    @GetMapping("/monthly")
    @Operation(
            summary = "Get monthly admin revenue overview",
            description = "Get total revenue, growth rates, and breakdowns for the last 30 days."
    )
    public ResponseEntity<ApiResponse<AdminRevenueOverviewResponse>> getMonthlyRevenue() {
        log.info("[Controller] GET /api/admin/revenue/monthly");
        AdminRevenueOverviewResponse response = adminRevenueService.getMonthlyRevenue();
        return ResponseEntity.ok(ApiResponse.<AdminRevenueOverviewResponse>builder()
                .message("Monthly revenue overview retrieved successfully")
                .result(response)
                .build());
    }

    @GetMapping("/daily/{date}")
    @Operation(
            summary = "Get revenue overview for a specific date",
            description = "Get total revenue, growth rates, and breakdowns for a specific date in YYYY-MM-DD format."
    )
    public ResponseEntity<ApiResponse<AdminRevenueOverviewResponse>> getDailyRevenueOverview(
            @PathVariable String date
    ) {
        log.info("[Controller] GET /api/admin/revenue/daily/{}", date);
        AdminRevenueOverviewResponse response = adminRevenueService.getDailyRevenueOverview(date);
        return ResponseEntity.ok(ApiResponse.<AdminRevenueOverviewResponse>builder()
                .message("Daily revenue overview for " + date + " retrieved successfully")
                .result(response)
                .build());
    }

    private Instant parseInstant(String dateStr, boolean isEnd) {
        if (dateStr == null || dateStr.isBlank()) {
            return null;
        }
        try {
            return Instant.parse(dateStr);
        } catch (Exception e) {
            try {
                return java.time.OffsetDateTime.parse(dateStr).toInstant();
            } catch (Exception e2) {
                try {
                    java.time.LocalDate localDate = java.time.LocalDate.parse(dateStr);
                    if (isEnd) {
                        return localDate.atTime(java.time.LocalTime.MAX).atZone(java.time.ZoneOffset.UTC).toInstant();
                    } else {
                        return localDate.atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
                    }
                } catch (Exception e3) {
                    try {
                        java.time.LocalDateTime localDateTime = java.time.LocalDateTime.parse(dateStr);
                        return localDateTime.toInstant(java.time.ZoneOffset.UTC);
                    } catch (Exception e4) {
                        log.error("Failed to parse date string: {}", dateStr, e4);
                        throw new IllegalArgumentException("Invalid date format: " + dateStr + ". Expected format is YYYY-MM-DD or ISO-8601.");
                    }
                }
            }
        }
    }
}
