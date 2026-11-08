package com.billassistant.agent;

import com.billassistant.agent.tool.BillAgentTools;
import com.billassistant.model.Bill;
import com.billassistant.service.BillService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BillAgentToolsTests {

    @Test
    void filtersOnlyTheBillsReturnedForTheBoundUser() {
        BillService billService = mock(BillService.class);
        when(billService.findAll(42)).thenReturn(List.of(
                new Bill(1, "午餐", new BigDecimal("35.00"), LocalDate.of(2026, 11, 2), "餐饮", true),
                new Bill(2, "地铁", new BigDecimal("8.00"), LocalDate.of(2026, 11, 3), "交通", true),
                new Bill(3, "晚餐", new BigDecimal("80.00"), LocalDate.of(2026, 10, 31), "餐饮", false)));

        BillAgentTools tools = new BillAgentTools(billService, 42);

        List<Bill> result = tools.queryBills("2026-11-01", "2026-11-30", "餐饮",
                new BigDecimal("30"), new BigDecimal("50"), 10);

        assertEquals(List.of("午餐"), result.stream().map(Bill::title).toList());
        verify(billService).findAll(42);
    }

    @Test
    void summarizesTheBoundUserBillsWithoutCallingTheModel() {
        BillService billService = mock(BillService.class);
        when(billService.findAll(7)).thenReturn(List.of(
                new Bill(1, "午餐", new BigDecimal("35.00"), LocalDate.of(2026, 11, 2), "餐饮", true),
                new Bill(2, "咖啡", new BigDecimal("15.00"), LocalDate.of(2026, 11, 3), "餐饮", true),
                new Bill(3, "地铁", new BigDecimal("8.00"), LocalDate.of(2026, 10, 31), "交通", true)));

        Map<String, Object> summary = new BillAgentTools(billService, 7)
                .summarizeBills("2026-11-01", "2026-11-30", null);

        assertEquals(2, summary.get("billCount"));
        assertEquals(new BigDecimal("50.00"), summary.get("totalAmount"));
        assertEquals(new BigDecimal("25.00"), summary.get("averageAmount"));
        assertEquals(Map.of("餐饮", new BigDecimal("50.00")), summary.get("categoryBreakdown"));
    }
}
