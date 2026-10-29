package com.chubb.claims.domain;

import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

/** A handler's request for more information from the claimant. Owned by {@link Claim}. */
@Entity
@Table(name = "info_request")
public class InfoRequest {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "claim_id")
    private Claim claim;

    @Column(nullable = false, length = 2000)
    private String question;

    @Column(length = 4000)
    private String response;

    @Column(nullable = false)
    private UUID requestedBy;

    @Column(nullable = false)
    private Instant requestedAt;

    private Instant respondedAt;

    protected InfoRequest() {
    }

    InfoRequest(Claim claim, String question, UUID requestedBy, Instant at) {
        this.id = UUID.randomUUID();
        this.claim = claim;
        this.question = question;
        this.requestedBy = requestedBy;
        this.requestedAt = at;
    }

    void answer(String response, Instant at) {
        this.response = response;
        this.respondedAt = at;
    }

    public boolean isOpen() {
        return respondedAt == null;
    }

    public UUID getId() { return id; }
    public String getQuestion() { return question; }
    public String getResponse() { return response; }
    public UUID getRequestedBy() { return requestedBy; }
    public Instant getRequestedAt() { return requestedAt; }
    public Instant getRespondedAt() { return respondedAt; }
}
