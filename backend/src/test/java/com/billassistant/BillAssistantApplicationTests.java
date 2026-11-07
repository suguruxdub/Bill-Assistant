package com.billassistant;

import com.billassistant.auth.AuthCookieService;
import com.billassistant.support.SessionTestConfiguration;
import com.billassistant.model.Bill;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(SessionTestConfiguration.class)
@Transactional
class BillAssistantApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    private Cookie sessionCookie;

    @BeforeEach
    void signIn() throws Exception {
        sessionCookie = mockMvc.perform(post("/api/auth/register")
                        .header("X-Requested-With", "BillAssistant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"billtest\",\"password\":\"test-password\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getCookie(AuthCookieService.ACCESS_COOKIE);
    }

    @Test
    void healthReturnsOk() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ok"));
    }

    @Test
    void createdBillIsAvailableInList() throws Exception {
        String response = mockMvc.perform(post("/api/bills").cookie(sessionCookie).header("X-Requested-With", "BillAssistant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id":999999,"title":"测试账单","amount":25.50,"billingDate":"2026-11-01",
                                 "category":"餐饮","paid":true}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.title").value("测试账单"))
                .andReturn().getResponse().getContentAsString();

        Bill created = objectMapper.readValue(response, Bill.class);
        assertNotEquals(999999L, created.id());
        assertEquals("测试账单", jdbcTemplate.queryForObject(
                "SELECT title FROM bills WHERE id = ?", String.class, created.id()));

        mockMvc.perform(get("/api/bills").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.title == '测试账单')].amount").value(25.50));
    }

    @Test
    void startsWithNoExampleBills() throws Exception {
        mockMvc.perform(get("/api/bills").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void billsAreOrderedByDateThenIdDescending() throws Exception {
        createBill("older", "2026-10-31");
        createBill("first", "2026-11-01");
        createBill("second", "2026-11-01");

        mockMvc.perform(get("/api/bills").cookie(sessionCookie))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].title").value("second"))
                .andExpect(jsonPath("$[1].title").value("first"))
                .andExpect(jsonPath("$[2].title").value("older"))
                .andExpect(jsonPath("$[0].paid").value(false))
                .andExpect(jsonPath("$[0].billingDate").value("2026-11-01"))
                .andExpect(jsonPath("$[0].category").value("餐饮"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "0.001", "1.234", "1000000000000.00"})
    void rejectsAmountsOutsideDatabasePrecision(String amount) throws Exception {
        mockMvc.perform(post("/api/bills").cookie(sessionCookie).header("X-Requested-With", "BillAssistant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"测试","amount":%s,"billingDate":"2026-11-01",
                                 "category":"餐饮","paid":false}
                                """.formatted(amount)))
                .andExpect(status().isBadRequest());
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM bills", Integer.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"title", "category"})
    void rejectsTextLongerThanDatabaseColumn(String field) throws Exception {
        var request = objectMapper.createObjectNode()
                .put("title", "测试").put("amount", 1)
                .put("billingDate", "2026-11-01").put("category", "餐饮");
        request.put(field, "x".repeat(field.equals("title") ? 256 : 65));

        mockMvc.perform(post("/api/bills").cookie(sessionCookie).header("X-Requested-With", "BillAssistant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsBytes(request)))
                .andExpect(status().isBadRequest());
        assertEquals(0, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM bills", Integer.class));
    }

    private void createBill(String title, String date) throws Exception {
        mockMvc.perform(post("/api/bills").cookie(sessionCookie).header("X-Requested-With", "BillAssistant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"%s","amount":0.01,"billingDate":"%s",
                                 "category":"餐饮","paid":false}
                                """.formatted(title, date)))
                .andExpect(status().isCreated());
    }

    @Test
    void rejectsInvalidBill() throws Exception {
        mockMvc.perform(post("/api/bills").cookie(sessionCookie).header("X-Requested-With", "BillAssistant")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"","amount":-1,"billingDate":"2026-11-01",
                                 "category":"餐饮","paid":false}
                                """))
                .andExpect(status().isBadRequest());
    }
}
