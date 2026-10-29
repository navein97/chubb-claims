package com.chubb.claims.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end through HTTP, JPA and a real Postgres. */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ClaimApiIT {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    static final String AISHA = "00000000-0000-0000-0000-00000000c001";
    static final String BEN = "00000000-0000-0000-0000-00000000c002";
    static final String HUIMIN = "00000000-0000-0000-0000-00000000a001";
    static final String RAJ = "00000000-0000-0000-0000-00000000a002";
    static final String WEI = "00000000-0000-0000-0000-00000000a003";

    @Autowired
    MockMvc mvc;
    @Autowired
    ObjectMapper json;

    @Test
    void fullLifecycle_claimantAndHandlerSeeDifferentViews() throws Exception {
        String id = submit(AISHA);

        // Wrong team can't see it at all (404, not 403)
        call(get("/api/claims/" + id), WEI).andExpect(status().isNotFound());
        // Other claimant can't see it either
        call(get("/api/claims/" + id), BEN).andExpect(status().isNotFound());

        // Shows in MY-MOTOR queue
        JsonNode queue = body(call(get("/api/claims/queue"), HUIMIN).andExpect(status().isOk()));
        assertThat(queue.findValuesAsText("id")).contains(id);

        call(post("/api/claims/" + id + "/pick-up"), HUIMIN)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ASSIGNED"))
                .andExpect(jsonPath("$.allowedActions").value(org.hamcrest.Matchers.hasItem("START_ASSESSMENT")));

        call(post("/api/claims/" + id + "/start-assessment"), HUIMIN).andExpect(status().isOk());
        call(put("/api/claims/" + id + "/reserve").content("{\"amount\":5200.00,\"reason\":\"Workshop quote\"}"), HUIMIN)
                .andExpect(jsonPath("$.reserveAmount").value(5200.00));

        JsonNode afterInfo = body(call(post("/api/claims/" + id + "/info-requests")
                .content("{\"question\":\"Please send the police report\"}"), HUIMIN)
                .andExpect(jsonPath("$.status").value("AWAITING_INFO")));
        String requestId = afterInfo.get("infoRequests").get(0).get("id").asText();

        // Claimant view: no reserve, no internal reserve note, no staff names, can respond
        call(get("/api/claims/" + id), AISHA)
                .andExpect(jsonPath("$.reserveAmount").doesNotExist())
                .andExpect(jsonPath("$.timeline[*].note").value(org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.containsString("Reserve")))))
                .andExpect(jsonPath("$.timeline[0].actorName").doesNotExist())
                .andExpect(jsonPath("$.allowedActions[0]").value("PROVIDE_INFO"));

        call(post("/api/claims/" + id + "/info-requests/" + requestId + "/response")
                .content("{\"response\":\"Report no. 8812\"}"), AISHA)
                .andExpect(jsonPath("$.status").value("UNDER_ASSESSMENT"));

        call(post("/api/claims/" + id + "/approve").content("{\"amount\":4800.00,\"reason\":\"Covered\"}"), HUIMIN)
                .andExpect(jsonPath("$.status").value("APPROVED"));
        call(post("/api/claims/" + id + "/settle"), HUIMIN)
                .andExpect(jsonPath("$.status").value("SETTLED"))
                .andExpect(jsonPath("$.reserveAmount").value(0));

        // Terminal: further transitions are 409
        call(post("/api/claims/" + id + "/settle"), HUIMIN).andExpect(status().isConflict());
    }

    @Test
    void concurrentPickUp_exactlyOneHandlerWins() throws Exception {
        String id = submit(AISHA);
        CyclicBarrier barrier = new CyclicBarrier(2);
        ExecutorService pool = Executors.newFixedThreadPool(2);
        List<Future<Integer>> results = new ArrayList<>();
        for (String handler : List.of(HUIMIN, RAJ)) {
            results.add(pool.submit(() -> {
                barrier.await();
                return call(post("/api/claims/" + id + "/pick-up"), handler).andReturn().getResponse().getStatus();
            }));
        }
        List<Integer> statuses = new ArrayList<>();
        for (Future<Integer> f : results) {
            statuses.add(f.get(30, TimeUnit.SECONDS));
        }
        pool.shutdown();
        assertThat(statuses).containsExactlyInAnyOrder(200, 409);
    }

    @Test
    void validationAndAuthErrors() throws Exception {
        mvc.perform(get("/api/claims/mine")).andExpect(status().isUnauthorized());

        call(post("/api/claims").content("{\"market\":\"MY\"}"), AISHA)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("policyNumber")));

        // Handler can't submit; claimant can't approve
        call(post("/api/claims").content(submitBody()), HUIMIN).andExpect(status().isForbidden());
        String id = submit(AISHA);
        call(post("/api/claims/" + id + "/approve").content("{\"amount\":1}"), AISHA)
                .andExpect(status().isForbidden());
        // Skipping a state: approve straight from SUBMITTED by the would-be handler is forbidden until picked up
        call(post("/api/claims/" + id + "/pick-up"), HUIMIN).andExpect(status().isOk());
        call(post("/api/claims/" + id + "/approve").content("{\"amount\":1}"), HUIMIN)
                .andExpect(status().isConflict());
    }

    // ------------------------------------------------------------------ helpers

    private String submit(String claimant) throws Exception {
        JsonNode created = body(call(post("/api/claims").content(submitBody()), claimant)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("SUBMITTED"))
                .andExpect(jsonPath("$.currency").value("MYR")));
        return created.get("id").asText();
    }

    private static String submitBody() {
        return """
                {"policyNumber":"POL-MY-1001","market":"MY","type":"MOTOR",
                 "incidentDate":"%s","description":"Rear-ended at traffic light","estimatedLoss":4500.00}
                """.formatted(LocalDate.now().minusDays(1));
    }

    private ResultActions call(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req,
                               String userId) throws Exception {
        return mvc.perform(req.header("Authorization", "Bearer " + userId).contentType(MediaType.APPLICATION_JSON));
    }

    private JsonNode body(ResultActions result) throws Exception {
        return json.readTree(result.andReturn().getResponse().getContentAsString());
    }
}
