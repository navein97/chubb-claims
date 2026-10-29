package com.chubb.claims.domain;

import com.chubb.claims.events.ClaimEventType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Pure domain tests: no Spring, no DB. These pin down the business rules. */
class ClaimTest {

    private final Actor claimant = new Actor(UUID.randomUUID(), "Aisha", Role.CLAIMANT, null);
    private final Actor otherClaimant = new Actor(UUID.randomUUID(), "Ben", Role.CLAIMANT, null);
    private final Actor handler = new Actor(UUID.randomUUID(), "Hui Min", Role.HANDLER, "MY-MOTOR");
    private final Actor otherHandler = new Actor(UUID.randomUUID(), "Raj", Role.HANDLER, "MY-MOTOR");
    private final Actor wrongTeamHandler = new Actor(UUID.randomUUID(), "Somchai", Role.HANDLER, "TH-MOTOR");
    private final Actor manager = new Actor(UUID.randomUUID(), "Grace", Role.MANAGER, "MY-MOTOR");

    private Claim claim;

    @BeforeEach
    void setUp() {
        claim = Claim.submit(claimant, "POL-123", Market.MY, ClaimType.MOTOR,
                LocalDate.now().minusDays(2), "Rear-ended at traffic light", new BigDecimal("4500.00"));
    }

    @Nested
    class Submission {

        @Test
        void derivesCurrencyTeamAndProvisionalReserveFromInput() {
            assertThat(claim.getStatus()).isEqualTo(ClaimStatus.SUBMITTED);
            assertThat(claim.getCurrency()).isEqualTo("MYR");
            assertThat(claim.getTeam()).isEqualTo("MY-MOTOR");
            assertThat(claim.getReserveAmount()).isEqualByComparingTo("4500.00");
            assertThat(claim.getReference()).startsWith("CLM-MY-");
            assertThat(claim.drainEvents()).containsExactly(ClaimEventType.CLAIM_SUBMITTED);
        }

        @Test
        void rejectsFutureIncidentDate() {
            assertThatThrownBy(() -> Claim.submit(claimant, "POL-1", Market.SG, ClaimType.PROPERTY,
                    LocalDate.now().plusDays(1), "x", BigDecimal.TEN))
                    .isInstanceOf(DomainException.class)
                    .extracting("kind").isEqualTo(DomainException.Kind.INVALID);
        }

        @Test
        void staffCannotSubmitClaims() {
            assertThatThrownBy(() -> Claim.submit(handler, "POL-1", Market.MY, ClaimType.MOTOR,
                    LocalDate.now(), "x", BigDecimal.TEN))
                    .extracting("kind").isEqualTo(DomainException.Kind.FORBIDDEN);
        }
    }

    @Nested
    class PickUp {

        @Test
        void handlerInSameTeamCanPickUp() {
            claim.drainEvents();
            claim.pickUp(handler);
            assertThat(claim.getStatus()).isEqualTo(ClaimStatus.ASSIGNED);
            assertThat(claim.getAssignedHandlerId()).isEqualTo(handler.id());
            assertThat(claim.drainEvents()).containsExactly(ClaimEventType.CLAIM_ASSIGNED);
        }

        @Test
        void secondHandlerGetsConflict() {
            claim.pickUp(handler);
            assertThatThrownBy(() -> claim.pickUp(otherHandler))
                    .extracting("kind").isEqualTo(DomainException.Kind.CONFLICT);
        }

        @Test
        void handlerFromAnotherTeamIsForbidden() {
            assertThatThrownBy(() -> claim.pickUp(wrongTeamHandler))
                    .extracting("kind").isEqualTo(DomainException.Kind.FORBIDDEN);
        }
    }

    @Nested
    class Assessment {

        @BeforeEach
        void assigned() {
            claim.pickUp(handler);
            claim.startAssessment(handler);
        }

        @Test
        void onlyAssignedHandlerOrManagerCanAct() {
            assertThatThrownBy(() -> claim.updateReserve(otherHandler, BigDecimal.ONE, null))
                    .extracting("kind").isEqualTo(DomainException.Kind.FORBIDDEN);
            claim.updateReserve(manager, new BigDecimal("5000"), "Workshop quote");
            assertThat(claim.getReserveAmount()).isEqualByComparingTo("5000");
        }

        @Test
        void infoRequestRoundTrip() {
            InfoRequest req = claim.requestInfo(handler, "Please upload the police report");
            assertThat(claim.getStatus()).isEqualTo(ClaimStatus.AWAITING_INFO);

            assertThatThrownBy(() -> claim.provideInfo(otherClaimant, req.getId(), "hi"))
                    .extracting("kind").isEqualTo(DomainException.Kind.FORBIDDEN);

            claim.provideInfo(claimant, req.getId(), "Report no. 8812 attached");
            assertThat(claim.getStatus()).isEqualTo(ClaimStatus.UNDER_ASSESSMENT);
            assertThat(req.isOpen()).isFalse();

            assertThatThrownBy(() -> claim.provideInfo(claimant, req.getId(), "again"))
                    .extracting("kind").isEqualTo(DomainException.Kind.CONFLICT);
        }

        @Test
        void cannotApproveWhileAwaitingInfo() {
            claim.requestInfo(handler, "Photos please");
            assertThatThrownBy(() -> claim.approve(handler, BigDecimal.TEN, null))
                    .extracting("kind").isEqualTo(DomainException.Kind.CONFLICT);
        }

        @Test
        void approveThenSettleClearsOutstandingReserve() {
            claim.approve(handler, new BigDecimal("4200.00"), "Within policy limits");
            assertThat(claim.getReserveAmount()).isEqualByComparingTo("4200.00");

            claim.settle(handler);
            assertThat(claim.getStatus()).isEqualTo(ClaimStatus.SETTLED);
            assertThat(claim.getReserveAmount()).isEqualByComparingTo("0");
            assertThat(claim.getStatus().isOpen()).isFalse();
        }

        @Test
        void officerCannotApproveAboveMarketLimit_managerCan() {
            // MY limit is MYR 20,000
            assertThatThrownBy(() -> claim.approve(handler, new BigDecimal("20000.01"), null))
                    .extracting("kind").isEqualTo(DomainException.Kind.FORBIDDEN);
            assertThat(claim.getStatus()).isEqualTo(ClaimStatus.UNDER_ASSESSMENT);

            claim.approve(manager, new BigDecimal("25000.00"), "Approved by manager");
            assertThat(claim.getStatus()).isEqualTo(ClaimStatus.APPROVED);
        }

        @Test
        void officerCanApproveExactlyAtLimit() {
            claim.approve(handler, new BigDecimal("20000.00"), null);
            assertThat(claim.getStatus()).isEqualTo(ClaimStatus.APPROVED);
        }

        @Test
        void rejectZeroesReserveAndIsTerminal() {
            claim.reject(handler, "Policy lapsed before incident date");
            assertThat(claim.getReserveAmount()).isEqualByComparingTo("0");
            assertThatThrownBy(() -> claim.updateReserve(handler, BigDecimal.ONE, null))
                    .extracting("kind").isEqualTo(DomainException.Kind.CONFLICT);
        }

        @Test
        void negativeReserveIsInvalid() {
            assertThatThrownBy(() -> claim.updateReserve(handler, new BigDecimal("-1"), null))
                    .extracting("kind").isEqualTo(DomainException.Kind.INVALID);
        }

        @Test
        void reserveChangesAreHiddenFromClaimantTimeline() {
            claim.updateReserve(handler, new BigDecimal("6000"), "Parts shortage");
            var last = claim.getHistory().get(claim.getHistory().size() - 1);
            assertThat(last.isVisibleToClaimant()).isFalse();
        }
    }

    @Nested
    class Visibility {

        @Test
        void claimantSeesOnlyOwnClaims_handlerSeesOwnTeam_managerSeesAll() {
            assertThat(claim.isVisibleTo(claimant)).isTrue();
            assertThat(claim.isVisibleTo(otherClaimant)).isFalse();
            assertThat(claim.isVisibleTo(handler)).isTrue();
            assertThat(claim.isVisibleTo(wrongTeamHandler)).isFalse();
            assertThat(claim.isVisibleTo(manager)).isTrue();
        }
    }

    @ParameterizedTest
    @EnumSource(value = ClaimStatus.class, names = {"REJECTED", "SETTLED"})
    void terminalStatesAllowNoTransitions(ClaimStatus terminal) {
        for (ClaimStatus target : ClaimStatus.values()) {
            assertThat(terminal.canMoveTo(target)).isFalse();
        }
    }
}
