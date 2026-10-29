package com.chubb.claims.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ClaimRepository extends JpaRepository<Claim, UUID> {

    List<Claim> findTop100ByClaimantIdOrderBySubmittedAtDesc(UUID claimantId);

    /** Unassigned work for a team, oldest first (FIFO). */
    List<Claim> findTop100ByTeamAndStatusOrderBySubmittedAtAsc(String team, ClaimStatus status);

    List<Claim> findTop100ByStatusOrderBySubmittedAtAsc(ClaimStatus status);

    List<Claim> findTop100ByAssignedHandlerIdAndStatusInOrderByUpdatedAtDesc(UUID handlerId,
                                                                            Collection<ClaimStatus> statuses);

    @Query("""
            select c from Claim c
            where (:team is null or c.team = :team)
              and (:status is null or c.status = :status)
            order by c.submittedAt desc
            limit 200
            """)
    List<Claim> search(@Param("team") String team, @Param("status") ClaimStatus status);
}
