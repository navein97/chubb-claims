package com.chubb.claims.api;

import com.chubb.claims.api.ClaimDtos.*;
import com.chubb.claims.app.ClaimService;
import com.chubb.claims.domain.Actor;
import com.chubb.claims.domain.Claim;
import com.chubb.claims.domain.ClaimStatus;
import io.swagger.v3.oas.annotations.Parameter;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Commands are explicit verbs (pick-up, approve, ...) rather than a generic PATCH on status:
 * each one maps to one business action with its own payload and rules.
 */
@RestController
@RequestMapping("/api/claims")
public class ClaimController {

    private final ClaimService service;

    public ClaimController(ClaimService service) {
        this.service = service;
    }

    // ------------------------------------------------------------------ claimant

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ClaimDetail submit(@Parameter(hidden = true) Actor actor, @Valid @RequestBody SubmitClaimRequest req) {
        Claim c = service.submit(actor, req.policyNumber(), req.market(), req.type(),
                req.incidentDate(), req.description(), req.estimatedLoss());
        return ClaimDetail.of(c, actor);
    }

    @GetMapping("/mine")
    public List<ClaimSummary> mine(@Parameter(hidden = true) Actor actor) {
        return summaries(service.mine(actor), actor);
    }

    @PostMapping("/{id}/info-requests/{requestId}/response")
    public ClaimDetail provideInfo(@Parameter(hidden = true) Actor actor, @PathVariable UUID id,
                                   @PathVariable UUID requestId, @Valid @RequestBody InfoResponseBody body) {
        return ClaimDetail.of(service.provideInfo(actor, id, requestId, body.response()), actor);
    }

    // ------------------------------------------------------------------ shared

    @GetMapping("/{id}")
    public ClaimDetail get(@Parameter(hidden = true) Actor actor, @PathVariable UUID id) {
        return ClaimDetail.of(service.get(actor, id), actor);
    }

    // ------------------------------------------------------------------ staff

    @GetMapping("/queue")
    public List<ClaimSummary> queue(@Parameter(hidden = true) Actor actor) {
        return summaries(service.queue(actor), actor);
    }

    @GetMapping("/assigned")
    public List<ClaimSummary> assigned(@Parameter(hidden = true) Actor actor) {
        return summaries(service.assignedToMe(actor), actor);
    }

    @GetMapping
    public List<ClaimSummary> search(@Parameter(hidden = true) Actor actor,
                                     @RequestParam(required = false) String team,
                                     @RequestParam(required = false) ClaimStatus status) {
        return summaries(service.search(actor, team, status), actor);
    }

    @PostMapping("/{id}/pick-up")
    public ClaimDetail pickUp(@Parameter(hidden = true) Actor actor, @PathVariable UUID id) {
        return ClaimDetail.of(service.pickUp(actor, id), actor);
    }

    @PostMapping("/{id}/start-assessment")
    public ClaimDetail startAssessment(@Parameter(hidden = true) Actor actor, @PathVariable UUID id) {
        return ClaimDetail.of(service.startAssessment(actor, id), actor);
    }

    @PostMapping("/{id}/info-requests")
    public ClaimDetail requestInfo(@Parameter(hidden = true) Actor actor, @PathVariable UUID id,
                                   @Valid @RequestBody InfoRequestBody body) {
        return ClaimDetail.of(service.requestInfo(actor, id, body.question()), actor);
    }

    @PutMapping("/{id}/reserve")
    public ClaimDetail updateReserve(@Parameter(hidden = true) Actor actor, @PathVariable UUID id,
                                     @Valid @RequestBody ReserveBody body) {
        return ClaimDetail.of(service.updateReserve(actor, id, body.amount(), body.reason()), actor);
    }

    @PostMapping("/{id}/approve")
    public ClaimDetail approve(@Parameter(hidden = true) Actor actor, @PathVariable UUID id,
                               @Valid @RequestBody ApproveBody body) {
        return ClaimDetail.of(service.approve(actor, id, body.amount(), body.reason()), actor);
    }

    @PostMapping("/{id}/reject")
    public ClaimDetail reject(@Parameter(hidden = true) Actor actor, @PathVariable UUID id,
                              @Valid @RequestBody RejectBody body) {
        return ClaimDetail.of(service.reject(actor, id, body.reason()), actor);
    }

    @PostMapping("/{id}/settle")
    public ClaimDetail settle(@Parameter(hidden = true) Actor actor, @PathVariable UUID id) {
        return ClaimDetail.of(service.settle(actor, id), actor);
    }

    private static List<ClaimSummary> summaries(List<Claim> claims, Actor actor) {
        return claims.stream().map(c -> ClaimSummary.of(c, actor)).toList();
    }
}
