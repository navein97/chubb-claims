package com.chubb.claims.app;

import com.chubb.claims.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Application service: loads the aggregate, checks visibility, calls the domain method, saves.
 * One transaction per command. No business rules here — those live in {@link Claim}.
 */
@Service
public class ClaimService {

    private static final EnumSet<ClaimStatus> HANDLER_ACTIVE = EnumSet.of(
            ClaimStatus.ASSIGNED, ClaimStatus.UNDER_ASSESSMENT, ClaimStatus.AWAITING_INFO, ClaimStatus.APPROVED);

    private final ClaimRepository claims;
    private final ClaimEventRecorder events;

    public ClaimService(ClaimRepository claims, ClaimEventRecorder events) {
        this.claims = claims;
        this.events = events;
    }

    // ------------------------------------------------------------------ commands

    @Transactional
    public Claim submit(Actor claimant, String policyNumber, Market market, ClaimType type,
                        LocalDate incidentDate, String description, BigDecimal estimatedLoss) {
        Claim claim = Claim.submit(claimant, policyNumber, market, type, incidentDate, description, estimatedLoss);
        return persist(claim);
    }

    @Transactional
    public Claim pickUp(Actor actor, UUID claimId) {
        return mutate(actor, claimId, c -> c.pickUp(actor));
    }

    @Transactional
    public Claim startAssessment(Actor actor, UUID claimId) {
        return mutate(actor, claimId, c -> c.startAssessment(actor));
    }

    @Transactional
    public Claim requestInfo(Actor actor, UUID claimId, String question) {
        return mutate(actor, claimId, c -> c.requestInfo(actor, question));
    }

    @Transactional
    public Claim provideInfo(Actor actor, UUID claimId, UUID requestId, String response) {
        return mutate(actor, claimId, c -> c.provideInfo(actor, requestId, response));
    }

    @Transactional
    public Claim updateReserve(Actor actor, UUID claimId, BigDecimal amount, String reason) {
        return mutate(actor, claimId, c -> c.updateReserve(actor, amount, reason));
    }

    @Transactional
    public Claim approve(Actor actor, UUID claimId, BigDecimal amount, String reason) {
        return mutate(actor, claimId, c -> c.approve(actor, amount, reason));
    }

    @Transactional
    public Claim reject(Actor actor, UUID claimId, String reason) {
        return mutate(actor, claimId, c -> c.reject(actor, reason));
    }

    @Transactional
    public Claim settle(Actor actor, UUID claimId) {
        return mutate(actor, claimId, c -> c.settle(actor));
    }

    // ------------------------------------------------------------------ queries

    @Transactional(readOnly = true)
    public Claim get(Actor actor, UUID claimId) {
        return withDetails(load(actor, claimId));
    }

    @Transactional(readOnly = true)
    public List<Claim> mine(Actor claimant) {
        return claims.findTop100ByClaimantIdOrderBySubmittedAtDesc(claimant.id());
    }

    /** Unassigned claims: a handler sees their team's queue, a manager sees every team's. */
    @Transactional(readOnly = true)
    public List<Claim> queue(Actor actor) {
        requireStaff(actor);
        return actor.role() == Role.MANAGER
                ? claims.findTop100ByStatusOrderBySubmittedAtAsc(ClaimStatus.SUBMITTED)
                : claims.findTop100ByTeamAndStatusOrderBySubmittedAtAsc(actor.team(), ClaimStatus.SUBMITTED);
    }

    @Transactional(readOnly = true)
    public List<Claim> assignedToMe(Actor actor) {
        requireStaff(actor);
        return claims.findTop100ByAssignedHandlerIdAndStatusInOrderByUpdatedAtDesc(actor.id(), HANDLER_ACTIVE);
    }

    /** Staff search. Handlers are pinned to their own team regardless of the filter passed. */
    @Transactional(readOnly = true)
    public List<Claim> search(Actor actor, String team, ClaimStatus status) {
        requireStaff(actor);
        String effectiveTeam = actor.role() == Role.HANDLER ? actor.team() : team;
        return claims.search(effectiveTeam, status);
    }

    // ------------------------------------------------------------------ internals

    private Claim mutate(Actor actor, UUID claimId, Consumer<Claim> action) {
        Claim claim = load(actor, claimId);
        action.accept(claim);
        return persist(claim);
    }

    private Claim persist(Claim claim) {
        // Drain from the instance the domain mutated: if JPA merges, the returned copy
        // would not carry the transient pending events.
        var produced = claim.drainEvents();
        // Flush so @Version is incremented before events snapshot it.
        Claim saved = claims.saveAndFlush(claim);
        events.record(saved, produced);
        return withDetails(saved);
    }

    /**
     * Commands and the detail query return the full claim; the controller maps it after the
     * transaction closes (open-in-view is off), so load the child collections while still inside it.
     */
    private static Claim withDetails(Claim claim) {
        claim.getHistory().size();
        claim.getInfoRequests().size();
        return claim;
    }

    /** Invisible claims return 404, not 403, so ids of other people's claims aren't confirmed. */
    private Claim load(Actor actor, UUID claimId) {
        Claim claim = claims.findById(claimId).orElseThrow(() -> new NotFoundException("Claim not found"));
        if (!claim.isVisibleTo(actor)) {
            throw new NotFoundException("Claim not found");
        }
        return claim;
    }

    private static void requireStaff(Actor actor) {
        if (!actor.isStaff()) {
            throw DomainException.forbidden("Staff only");
        }
    }
}
