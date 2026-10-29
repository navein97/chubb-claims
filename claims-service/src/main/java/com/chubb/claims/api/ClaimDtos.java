package com.chubb.claims.api;

import com.chubb.claims.domain.*;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * API contract. Two views of the same claim: claimants never see the reserve, internal
 * history entries, or who on staff did what — only what matters to them.
 */
public final class ClaimDtos {

    private ClaimDtos() {
    }

    // ------------------------------------------------------------------ requests

    public record SubmitClaimRequest(
            @NotBlank @Size(max = 60) String policyNumber,
            @NotNull Market market,
            @NotNull ClaimType type,
            @NotNull @PastOrPresent LocalDate incidentDate,
            @NotBlank @Size(max = 4000) String description,
            @NotNull @PositiveOrZero @Digits(integer = 17, fraction = 2) BigDecimal estimatedLoss) {
    }

    public record InfoRequestBody(@NotBlank @Size(max = 2000) String question) {
    }

    public record InfoResponseBody(@NotBlank @Size(max = 4000) String response) {
    }

    public record ReserveBody(
            @NotNull @PositiveOrZero @Digits(integer = 17, fraction = 2) BigDecimal amount,
            @Size(max = 500) String reason) {
    }

    public record ApproveBody(
            @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal amount,
            @Size(max = 2000) String reason) {
    }

    public record RejectBody(@NotBlank @Size(max = 2000) String reason) {
    }

    // ------------------------------------------------------------------ responses

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ClaimSummary(
            UUID id, String reference, ClaimStatus status, Market market, ClaimType type, String team,
            String claimantName, String assignedHandlerName,
            BigDecimal reserveAmount, String currency,
            Instant submittedAt, Instant updatedAt) {

        static ClaimSummary of(Claim c, Actor viewer) {
            boolean staff = viewer.isStaff();
            return new ClaimSummary(c.getId(), c.getReference(), c.getStatus(), c.getMarket(), c.getType(),
                    c.getTeam(), c.getClaimantName(), c.getAssignedHandlerName(),
                    staff ? c.getReserveAmount() : null, c.getCurrency(),
                    c.getSubmittedAt(), c.getUpdatedAt());
        }
    }

    public record InfoRequestView(UUID id, String question, String response,
                                  Instant requestedAt, Instant respondedAt, boolean open) {
        static InfoRequestView of(InfoRequest r) {
            return new InfoRequestView(r.getId(), r.getQuestion(), r.getResponse(),
                    r.getRequestedAt(), r.getRespondedAt(), r.isOpen());
        }
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record TimelineEntry(Instant at, ClaimStatus fromStatus, ClaimStatus toStatus,
                                String note, String actorName) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ClaimDetail(
            UUID id, String reference, long version, ClaimStatus status,
            Market market, ClaimType type, String team, String policyNumber,
            LocalDate incidentDate, String description,
            String claimantName, String assignedHandlerName,
            BigDecimal estimatedLoss, BigDecimal reserveAmount, BigDecimal settledAmount, String currency,
            String decisionReason, Instant submittedAt, Instant updatedAt,
            List<InfoRequestView> infoRequests,
            List<TimelineEntry> timeline,
            Set<ClaimAction> allowedActions,
            /** Officer's own approval limit for this claim's market; only sent to officers. */
            BigDecimal approvalLimit) {

        static ClaimDetail of(Claim c, Actor viewer) {
            boolean staff = viewer.isStaff();
            List<TimelineEntry> timeline = c.getHistory().stream()
                    .filter(h -> staff || h.isVisibleToClaimant())
                    .map(h -> new TimelineEntry(h.getOccurredAt(), h.getFromStatus(), h.getToStatus(),
                            h.getNote(), staff ? h.getActorName() : null))
                    .toList();
            return new ClaimDetail(
                    c.getId(), c.getReference(), c.getVersion(), c.getStatus(),
                    c.getMarket(), c.getType(), c.getTeam(), c.getPolicyNumber(),
                    c.getIncidentDate(), c.getDescription(),
                    c.getClaimantName(), c.getAssignedHandlerName(),
                    c.getEstimatedLoss(), staff ? c.getReserveAmount() : null, c.getSettledAmount(), c.getCurrency(),
                    c.getDecisionReason(), c.getSubmittedAt(), c.getUpdatedAt(),
                    c.getInfoRequests().stream().map(InfoRequestView::of).toList(),
                    timeline,
                    c.allowedActions(viewer),
                    viewer.role() == Role.HANDLER ? c.getMarket().handlerApprovalLimit() : null);
        }
    }
}
