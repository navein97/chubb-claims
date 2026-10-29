package com.chubb.claims.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Append-only audit trail. Drives the claimant's timeline and answers "who did what, when"
 * for staff. {@code visibleToClaimant} hides internal notes (e.g. reserve changes) from claimants.
 */
@Entity
@Table(name = "claim_history")
public class ClaimHistoryEntry {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id")
    private Claim claim;

    @Column(nullable = false)
    private Instant occurredAt;

    @Column(nullable = false)
    private UUID actorId;

    @Column(nullable = false)
    private String actorName;

    @Enumerated(EnumType.STRING)
    private ClaimStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClaimStatus toStatus;

    @Column(nullable = false, length = 2000)
    private String note;

    @Column(nullable = false)
    private boolean visibleToClaimant;

    protected ClaimHistoryEntry() {
    }

    ClaimHistoryEntry(Claim claim, Actor actor, ClaimStatus from, ClaimStatus to,
                      String note, boolean visibleToClaimant, Instant at) {
        this.id = UUID.randomUUID();
        this.claim = claim;
        this.actorId = actor.id();
        this.actorName = actor.name();
        this.fromStatus = from;
        this.toStatus = to;
        this.note = note;
        this.visibleToClaimant = visibleToClaimant;
        this.occurredAt = at;
    }

    public UUID getId() { return id; }
    public Instant getOccurredAt() { return occurredAt; }
    public UUID getActorId() { return actorId; }
    public String getActorName() { return actorName; }
    public ClaimStatus getFromStatus() { return fromStatus; }
    public ClaimStatus getToStatus() { return toStatus; }
    public String getNote() { return note; }
    public boolean isVisibleToClaimant() { return visibleToClaimant; }
}
