package com.chubb.insights.api;

import com.chubb.insights.auth.StaffAuth;
import com.chubb.insights.auth.StaffAuth.Staff;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Read-only dashboards over the projection. Handlers are pinned to their own team;
 * managers see everything or filter by team.
 *
 * Every response carries {@code asOf}: the time of the latest event reflected. The data is
 * eventually consistent (typically sub-second behind claims-service) and the UI says so.
 */
@RestController
@RequestMapping("/api/insights")
public class InsightsController {

    private final JdbcClient jdbc;
    private final StaffAuth auth;

    public InsightsController(JdbcClient jdbc, StaffAuth auth) {
        this.jdbc = jdbc;
        this.auth = auth;
    }

    // ------------------------------------------------------------------ exposure

    public record ExposureRow(String market, String currency, String claimType, long openClaims,
                              BigDecimal outstandingReserve) {
    }

    public record CurrencyTotal(String currency, long openClaims, BigDecimal outstandingReserve) {
    }

    public record Exposure(Instant asOf, String team, List<ExposureRow> rows, List<CurrencyTotal> totals) {
    }

    /**
     * Outstanding liability = sum of reserves on open claims. Reported per currency:
     * converting to one reporting currency needs an FX source and a rate date — out of scope.
     */
    @GetMapping("/exposure")
    public Exposure exposure(@RequestHeader(value = "Authorization", required = false) String authorization,
                             @RequestParam(required = false) String team) {
        Staff staff = auth.require(authorization);
        String scope = effectiveTeam(staff, team);
        List<ExposureRow> rows = jdbc.sql("""
                        SELECT market, currency, claim_type, count(*) AS open_claims,
                               COALESCE(sum(reserve_amount), 0) AS outstanding
                        FROM claim_projection
                        WHERE closed_at IS NULL AND (CAST(:team AS varchar) IS NULL OR team = :team)
                        GROUP BY market, currency, claim_type
                        ORDER BY market, claim_type
                        """)
                .param("team", scope)
                .query((rs, i) -> new ExposureRow(rs.getString("market"), rs.getString("currency"),
                        rs.getString("claim_type"), rs.getLong("open_claims"), rs.getBigDecimal("outstanding")))
                .list();
        List<CurrencyTotal> totals = rows.stream()
                .collect(java.util.stream.Collectors.groupingBy(ExposureRow::currency,
                        java.util.TreeMap::new, java.util.stream.Collectors.toList()))
                .entrySet().stream()
                .map(e -> new CurrencyTotal(e.getKey(),
                        e.getValue().stream().mapToLong(ExposureRow::openClaims).sum(),
                        e.getValue().stream().map(ExposureRow::outstandingReserve).reduce(BigDecimal.ZERO, BigDecimal::add)))
                .toList();
        return new Exposure(asOf(), scope, rows, totals);
    }

    // ------------------------------------------------------------------ workload

    public record TeamLoad(String team, long unassigned, Instant oldestUnassignedAt, long inProgress,
                           long awaitingInfo, long closedLast30Days) {
    }

    public record HandlerLoad(UUID handlerId, String handlerName, String team, long open, long awaitingInfo,
                              long closedLast30Days, BigDecimal avgCycleDays) {
    }

    public record Workload(Instant asOf, String team, List<TeamLoad> teams, List<HandlerLoad> handlers) {
    }

    @GetMapping("/workload")
    public Workload workload(@RequestHeader(value = "Authorization", required = false) String authorization,
                             @RequestParam(required = false) String team) {
        Staff staff = auth.require(authorization);
        String scope = effectiveTeam(staff, team);

        List<TeamLoad> teams = jdbc.sql("""
                        SELECT team,
                               count(*) FILTER (WHERE status = 'SUBMITTED') AS unassigned,
                               min(submitted_at) FILTER (WHERE status = 'SUBMITTED') AS oldest_unassigned,
                               count(*) FILTER (WHERE status IN ('ASSIGNED','UNDER_ASSESSMENT','APPROVED')) AS in_progress,
                               count(*) FILTER (WHERE status = 'AWAITING_INFO') AS awaiting_info,
                               count(*) FILTER (WHERE closed_at >= now() - interval '30 days') AS closed_30d
                        FROM claim_projection
                        WHERE CAST(:team AS varchar) IS NULL OR team = :team
                        GROUP BY team
                        ORDER BY team
                        """)
                .param("team", scope)
                .query((rs, i) -> new TeamLoad(rs.getString("team"), rs.getLong("unassigned"),
                        toInstant(rs.getTimestamp("oldest_unassigned")), rs.getLong("in_progress"),
                        rs.getLong("awaiting_info"), rs.getLong("closed_30d")))
                .list();

        List<HandlerLoad> handlers = jdbc.sql("""
                        SELECT handler_id, handler_name, team,
                               count(*) FILTER (WHERE closed_at IS NULL) AS open,
                               count(*) FILTER (WHERE status = 'AWAITING_INFO') AS awaiting_info,
                               count(*) FILTER (WHERE closed_at >= now() - interval '30 days') AS closed_30d,
                               avg(extract(epoch FROM closed_at - submitted_at) / 86400)
                                   FILTER (WHERE closed_at >= now() - interval '30 days') AS avg_cycle_days
                        FROM claim_projection
                        WHERE handler_id IS NOT NULL AND (CAST(:team AS varchar) IS NULL OR team = :team)
                        GROUP BY handler_id, handler_name, team
                        ORDER BY open DESC, handler_name
                        """)
                .param("team", scope)
                .query((rs, i) -> new HandlerLoad(rs.getObject("handler_id", UUID.class), rs.getString("handler_name"),
                        rs.getString("team"), rs.getLong("open"), rs.getLong("awaiting_info"),
                        rs.getLong("closed_30d"), round(rs.getBigDecimal("avg_cycle_days"))))
                .list();

        return new Workload(asOf(), scope, teams, handlers);
    }

    // ------------------------------------------------------------------ helpers

    private static String effectiveTeam(Staff staff, String requested) {
        return staff.isManager() ? (requested == null || requested.isBlank() ? null : requested) : staff.team();
    }

    private Instant asOf() {
        // max() over an empty table is one row holding NULL: map it ourselves rather than via
        // query(Timestamp.class).single(), which rejects a null value.
        return jdbc.sql("SELECT max(last_event_at) AS latest FROM claim_projection")
                .query((rs, i) -> java.util.Optional.ofNullable(toInstant(rs.getTimestamp("latest"))))
                .single()
                .orElse(null);
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    private static BigDecimal round(BigDecimal value) {
        return value == null ? null : value.setScale(1, RoundingMode.HALF_UP);
    }
}
