package com.billassistant.agent.tool;

import com.billassistant.model.Bill;
import com.billassistant.service.BillService;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** Read-only tools bound to the authenticated user for one request. */
public final class BillAgentTools {

    private final List<Bill> bills;

    public BillAgentTools(BillService billService, long uid) {
        this.bills = billService.findAll(uid);
    }

    @Tool(description = "查询当前用户在日期范围、分类和金额条件下的账单。日期格式为 YYYY-MM-DD。")
    public List<Bill> queryBills(
            @ToolParam(description = "开始日期，YYYY-MM-DD，可为空") String startDate,
            @ToolParam(description = "结束日期，YYYY-MM-DD，可为空") String endDate,
            @ToolParam(description = "分类，可为空") String category,
            @ToolParam(description = "最小金额，可为空") BigDecimal minAmount,
            @ToolParam(description = "最大金额，可为空") BigDecimal maxAmount,
            @ToolParam(description = "最多返回条数，建议不超过 50") Integer limit) {
        LocalDate start = parseDate(startDate);
        LocalDate end = parseDate(endDate);
        int max = limit == null ? 50 : Math.max(1, Math.min(limit, 50));
        return bills.stream()
                .filter(bill -> start == null || !bill.billingDate().isBefore(start))
                .filter(bill -> end == null || !bill.billingDate().isAfter(end))
                .filter(bill -> category == null || category.isBlank() || bill.category().equalsIgnoreCase(category.trim()))
                .filter(bill -> minAmount == null || bill.amount().compareTo(minAmount) >= 0)
                .filter(bill -> maxAmount == null || bill.amount().compareTo(maxAmount) <= 0)
                .limit(max)
                .toList();
    }

    @Tool(description = "统计当前用户账单的总金额、笔数、平均金额和分类汇总。日期格式为 YYYY-MM-DD。")
    public Map<String, Object> summarizeBills(
            @ToolParam(description = "开始日期，YYYY-MM-DD，可为空") String startDate,
            @ToolParam(description = "结束日期，YYYY-MM-DD，可为空") String endDate,
            @ToolParam(description = "分类，可为空") String category) {
        LocalDate start = parseDate(startDate);
        LocalDate end = parseDate(endDate);
        List<Bill> selected = bills.stream()
                .filter(bill -> start == null || !bill.billingDate().isBefore(start))
                .filter(bill -> end == null || !bill.billingDate().isAfter(end))
                .filter(bill -> category == null || category.isBlank() || bill.category().equalsIgnoreCase(category.trim()))
                .toList();
        BigDecimal total = selected.stream().map(Bill::amount).reduce(BigDecimal.ZERO, BigDecimal::add);
        Map<String, BigDecimal> byCategory = selected.stream().collect(Collectors.groupingBy(
                Bill::category, Collectors.reducing(BigDecimal.ZERO, Bill::amount, BigDecimal::add)));
        BigDecimal average = selected.isEmpty() ? BigDecimal.ZERO : total.divide(BigDecimal.valueOf(selected.size()), 2, java.math.RoundingMode.HALF_UP);
        return Map.of("billCount", selected.size(), "totalAmount", total, "averageAmount", average, "categoryBreakdown", byCategory);
    }

    private static LocalDate parseDate(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return LocalDate.parse(value);
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
