package com.chubb.claims.domain;

import com.chubb.claims.events.ClaimEventType;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Claim aggregate root. All business rules live here, not in controllers or services:
 * who may do what, in which state, and what it does to the reserve.
 *
 * Every mutating method records the domain event(s) it produced in {@link #pendingEvents};
 * the application service drains them into the outbox in the same transaction.
 *
 * Concurrency: {@link #version} is an optimistic lock. Two handlers picking up the same
 * claim at once → the second commit fails instead of silently overwriting the first.
 */
@Entity
@Table(name = "claim")
public class Claim {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true)
    private String reference;

    @Version
    private long version;

    // --- what happened ---
    @Column(nullable = false)
    private UUID claimantId;

    @Column(nullable = false)
    private String claimantName;

    @Column(nullable = false)
    private String policyNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Market market;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClaimType type;

    @Column(nullable = false)
    private LocalDate incidentDate;

    @Column(nullable = false, length = 4000)
    private String description;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal estimatedLoss;

    // --- where it is ---
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClaimStatus status;

    /** Work queue the claim is routed to, derived from market + type (e.g. "MY-MOTOR"). */
    @Column(nullable = false)
    private String team;

    private UUID assignedHandlerId;
    private String assignedHandlerName;

    // --- money ---
    /** Expected cost of this claim. Sum over open claims = outstanding liability. */
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal reserveAmount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(precision = 19, scale = 2)
    private BigDecimal settledAmount;

    @Column(length = 2000)
    private String decisionReason;

    @Column(nullable = false)
    private Instant submittedAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "claim", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("requestedAt ASC")
    private List<InfoRequest> infoRequests = new ArrayList<>();

    @OneToMany(mappedBy = "claim", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("occurredAt ASC")
    private List<ClaimHistoryEntry> history = new ArrayList<>();

    @Transient
    private final List<ClaimEventType> pendingEvents = new ArrayList<>();

    protected Claim() {
    }

    // ------------------------------------------------------------------ factory

    public static Claim submit(Actor claimant, String policyNumber, Market market, ClaimType type,
                               LocalDate incidentDate, String description, BigDecimal estimatedLoss) {
        if (claimant.role() != Role.CLAIMANT) {
            throw DomainException.forbidden("Only claimants can submit claims");
        }
        if (incidentDate.isAfter(LocalDate.now())) {
            throw DomainException.invalid("Incident date cannot be in the future");
        }
        requireNonNegative(estimatedLoss, "Estimated loss");

        Claim c = new Claim();
        Instant now = Instant.now();
        c.id = UUID.randomUUID();
        c.reference = newReference(market);
        c.claimantId = claimant.id();
        c.claimantName = claimant.name();
        c.policyNumber = policyNumber;
        c.market = market;
        c.type = type;
        c.incidentDate = incidentDate;
        c.description = description;
        c.estimatedLoss = estimatedLoss;
        c.currency = market.currency();
        c.team = market.name() + "-" + type.name();
        c.status = ClaimStatus.SUBMITTED;
        // Provisional reserve = claimant's estimate until a handler assesses it.
        c.reserveAmount = estimatedLoss;
        c.submittedAt = now;
        c.updatedAt = now;
        c.recordHistory(claimant, null, ClaimStatus.SUBMITTED, "Claim submitted", true);
        c.pendingEvents.add(ClaimEventType.CLAIM_SUBMITTED);
        return c;
    }

    // ------------------------------------------------------------------ staff actions

    /** Handler takes an unassigned claim from their team's queue. */
    public void pickUp(Actor handler) {
        if (handler.role() != Role.HANDLER) {
            throw DomainException.forbidden("Only handlers can pick up claims");
        }
        if (!team.equals(handler.team())) {
            throw DomainException.forbidden("Claim belongs to team " + team);
        }
        if (assignedHandlerId != null) {
            throw DomainException.conflict("Claim already picked up by " + assignedHandlerName);
        }
        assignedHandlerId = handler.id();
        assignedHandlerName = handler.name();
        transition(handler, ClaimStatus.ASSIGNED, "Picked up by " + handler.name(), true);
        pendingEvents.add(ClaimEventType.CLAIM_ASSIGNED);
    }

    public void startAssessment(Actor actor) {
        requireOwnerOrManager(actor);
        transition(actor, ClaimStatus.UNDER_ASSESSMENT, "Assessment started", true);
        pendingEvents.add(ClaimEventType.CLAIM_STATUS_CHANGED);
    }

    public InfoRequest requestInfo(Actor actor, String question) {
        requireOwnerOrManager(actor);
        requireText(question, "Question");
        transition(actor, ClaimStatus.AWAITING_INFO, "Information requested: " + question, true);
        InfoRequest req = new InfoRequest(this, question, actor.id(), Instant.now());
        infoRequests.add(req);
        pendingEvents.add(ClaimEventType.INFO_REQUESTED);
        return req;
    }

    public void updateReserve(Actor actor, BigDecimal newReserve, String reason) {
        requireOwnerOrManager(actor);
        requireNonNegative(newReserve, "Reserve");
        if (!status.isOpen()) {
            throw DomainException.conflict("Cannot change reserve on a " + status + " claim");
        }
        BigDecimal old = reserveAmount;
        reserveAmount = newReserve;
        touch();
        // Internal: claimants don't see reserve movements.
        recordHistory(actor, status, status,
                "Reserve " + old + " → " + newReserve + " " + currency + (reason == null ? "" : " (" + reason + ")"),
                false);
        pendingEvents.add(ClaimEventType.RESERVE_UPDATED);
    }

    public void approve(Actor actor, BigDecimal amount, String reason) {
        requireOwnerOrManager(actor);
        requireNonNegative(amount, "Approved amount");
        if (amount.signum() == 0) {
            throw DomainException.invalid("Approved amount must be greater than zero");
        }
        transition(actor, ClaimStatus.APPROVED, "Approved for " + amount + " " + currency, true);
        settledAmount = amount;
        decisionReason = reason;
        // Liability is now the agreed amount, not the earlier estimate.
        reserveAmount = amount;
        pendingEvents.add(ClaimEventType.CLAIM_DECIDED);
    }

    public void reject(Actor actor, String reason) {
        requireOwnerOrManager(actor);
        requireText(reason, "Rejection reason");
        transition(actor, ClaimStatus.REJECTED, "Rejected: " + reason, true);
        decisionReason = reason;
        reserveAmount = BigDecimal.ZERO;
        pendingEvents.add(ClaimEventType.CLAIM_DECIDED);
    }

    public void settle(Actor actor) {
        requireOwnerOrManager(actor);
        transition(actor, ClaimStatus.SETTLED, "Settled: " + settledAmount + " " + currency + " paid", true);
        // Paid out → no longer outstanding.
        reserveAmount = BigDecimal.ZERO;
        pendingEvents.add(ClaimEventType.CLAIM_SETTLED);
    }

    // ------------------------------------------------------------------ claimant actions

    public void provideInfo(Actor claimant, UUID infoRequestId, String response) {
        if (!claimant.id().equals(claimantId)) {
            throw DomainException.forbidden("Not your claim");
        }
        requireText(response, "Response");
        InfoRequest req = infoRequests.stream()
                .filter(r -> r.getId().equals(infoRequestId))
                .findFirst()
                .orElseThrow(() -> DomainException.invalid("Unknown info request " + infoRequestId));
        if (!req.isOpen()) {
            throw DomainException.conflict("Info request already answered");
        }
        req.answer(response, Instant.now());
        transition(claimant, ClaimStatus.UNDER_ASSESSMENT, "Information provided", true);
        pendingEvents.add(ClaimEventType.INFO_PROVIDED);
    }

    // ------------------------------------------------------------------ visibility

    public boolean isVisibleTo(Actor actor) {
        return switch (actor.role()) {
            case CLAIMANT -> actor.id().equals(claimantId);
            case HANDLER -> team.equals(actor.team());
            case MANAGER -> true;
        };
    }

    // ------------------------------------------------------------------ internals

    private void transition(Actor actor, ClaimStatus target, String note, boolean visibleToClaimant) {
        if (!status.canMoveTo(target)) {
            throw DomainException.conflict("Cannot move claim from " + status + " to " + target);
        }
        ClaimStatus from = status;
        status = target;
        touch();
        recordHistory(actor, from, target, note, visibleToClaimant);
    }

    private void requireOwnerOrManager(Actor actor) {
        if (actor.role() == Role.MANAGER) {
            return;
        }
        if (actor.role() != Role.HANDLER || !actor.id().equals(assignedHandlerId)) {
            throw DomainException.forbidden("Only the assigned handler or a manager can do this");
        }
    }

    private void recordHistory(Actor actor, ClaimStatus from, ClaimStatus to, String note, boolean visible) {
        history.add(new ClaimHistoryEntry(this, actor, from, to, note, visible, Instant.now()));
    }

    private void touch() {
        updatedAt = Instant.now();
    }

    private static void requireNonNegative(BigDecimal amount, String field) {
        if (amount == null || amount.signum() < 0) {
            throw DomainException.invalid(field + " must be zero or positive");
        }
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw DomainException.invalid(field + " is required");
        }
    }

    private static String newReference(Market market) {
        return "CLM-" + market.name() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
    }

    /** Drained by the application service into the outbox. */
    public List<ClaimEventType> drainEvents() {
        List<ClaimEventType> out = List.copyOf(pendingEvents);
        pendingEvents.clear();
        return out;
    }

    // ------------------------------------------------------------------ getters

    public UUID getId() { return id; }
    public String getReference() { return reference; }
    public long getVersion() { return version; }
    public UUID getClaimantId() { return claimantId; }
    public String getClaimantName() { return claimantName; }
    public String getPolicyNumber() { return policyNumber; }
    public Market getMarket() { return market; }
    public ClaimType getType() { return type; }
    public LocalDate getIncidentDate() { return incidentDate; }
    public String getDescription() { return description; }
    public BigDecimal getEstimatedLoss() { return estimatedLoss; }
    public ClaimStatus getStatus() { return status; }
    public String getTeam() { return team; }
    public UUID getAssignedHandlerId() { return assignedHandlerId; }
    public String getAssignedHandlerName() { return assignedHandlerName; }
    public BigDecimal getReserveAmount() { return reserveAmount; }
    public String getCurrency() { return currency; }
    public BigDecimal getSettledAmount() { return settledAmount; }
    public String getDecisionReason() { return decisionReason; }
    public Instant getSubmittedAt() { return submittedAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<InfoRequest> getInfoRequests() { return Collections.unmodifiableList(infoRequests); }
    public List<ClaimHistoryEntry> getHistory() { return Collections.unmodifiableList(history); }
}
