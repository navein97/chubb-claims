package com.chubb.insights.projection;

import com.chubb.claims.events.ClaimEvent;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.util.Set;

@Repository
public class ClaimProjectionRepository {

    private static final Set<String> TERMINAL = Set.of("REJECTED", "SETTLED");

    private final JdbcClient jdbc;

    public ClaimProjectionRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Idempotent, order-tolerant upsert in one statement. The WHERE on the conflict branch means:
     * a duplicate (same version) or a stale event (lower version) changes nothing.
     *
     * @return true if the event changed the projection
     */
    public boolean apply(ClaimEvent e) {
        boolean closed = TERMINAL.contains(e.status());
        int rows = jdbc.sql("""
                        INSERT INTO claim_projection (claim_id, claim_ref, market, claim_type, team, status,
                            handler_id, handler_name, reserve_amount, settled_amount, currency,
                            submitted_at, closed_at, claim_version, last_event_at)
                        VALUES (:id, :ref, :market, :type, :team, :status, :handlerId, :handlerName,
                            :reserve, :settled, :currency, :submittedAt, :closedAt, :version, :at)
                        ON CONFLICT (claim_id) DO UPDATE SET
                            status = EXCLUDED.status,
                            team = EXCLUDED.team,
                            handler_id = EXCLUDED.handler_id,
                            handler_name = EXCLUDED.handler_name,
                            reserve_amount = EXCLUDED.reserve_amount,
                            settled_amount = EXCLUDED.settled_amount,
                            closed_at = EXCLUDED.closed_at,
                            claim_version = EXCLUDED.claim_version,
                            last_event_at = EXCLUDED.last_event_at
                        WHERE claim_projection.claim_version < EXCLUDED.claim_version
                        """)
                .param("id", e.claimId())
                .param("ref", e.claimRef())
                .param("market", e.market())
                .param("type", e.claimType())
                .param("team", e.team())
                .param("status", e.status())
                .param("handlerId", e.handlerId())
                .param("handlerName", e.handlerName())
                .param("reserve", e.reserveAmount())
                .param("settled", e.settledAmount())
                .param("currency", e.currency())
                .param("submittedAt", Timestamp.from(e.submittedAt()))
                .param("closedAt", closed ? Timestamp.from(e.occurredAt()) : null)
                .param("version", e.claimVersion())
                .param("at", Timestamp.from(e.occurredAt()))
                .update();
        return rows > 0;
    }
}
