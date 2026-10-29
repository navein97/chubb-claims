package com.chubb.claims.app;

import com.chubb.claims.auth.DevUserDirectory;
import com.chubb.claims.domain.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * DEV/DEMO ONLY (claims.seed-demo-data=true, set in docker-compose). Creates claims in every state
 * through the normal application service, so the same events flow through the outbox and Kafka
 * and the insights dashboards fill up exactly as they would in real use. Skips if data exists.
 */
@Component
@ConditionalOnProperty(name = "claims.seed-demo-data", havingValue = "true")
class DemoDataSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final ClaimService service;
    private final ClaimRepository claims;
    private final DevUserDirectory users;

    DemoDataSeeder(ClaimService service, ClaimRepository claims, DevUserDirectory users) {
        this.service = service;
        this.claims = claims;
        this.users = users;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (claims.count() > 0) {
            return;
        }
        Actor aisha = user("aisha");
        Actor ben = user("ben");
        Actor huimin = user("huimin");
        Actor raj = user("raj");
        Actor wei = user("wei");

        // Waiting in the MY-MOTOR and SG-PROPERTY queues
        submit(ben, Market.MY, ClaimType.MOTOR, 3, "12500", "Hit a deep pothole on the Federal Highway. Front suspension and nearside wheel damaged.");
        submit(aisha, Market.MY, ClaimType.MOTOR, 1, "2200", "Side mirror smashed while parked at Mid Valley Megamall.");
        submit(aisha, Market.SG, ClaimType.PROPERTY, 4, "18000", "Burst pipe under the kitchen sink flooded the kitchen and living room.");

        // Raj: waiting on the claimant
        UUID windscreen = submit(ben, Market.MY, ClaimType.MOTOR, 2, "640", "Windscreen chipped by debris from a lorry on the NKVE.");
        service.pickUp(raj, windscreen);
        service.startAssessment(raj, windscreen);
        service.requestInfo(raj, windscreen, "Please upload a close-up photo of the chip and tell us whether it is in the driver's line of sight.");

        // Hui Min: assessing, reserve revised after a workshop quote
        UUID rearEnd = submit(aisha, Market.MY, ClaimType.MOTOR, 6, "7800", "Rear-ended at a traffic light on Jalan Ampang. Rear bumper and boot lid damaged.");
        service.pickUp(huimin, rearEnd);
        service.startAssessment(huimin, rearEnd);
        service.updateReserve(huimin, rearEnd, new BigDecimal("7200.00"), "Workshop quote received");

        // Hui Min: approved and paid
        UUID flood = submit(ben, Market.MY, ClaimType.MOTOR, 12, "3100", "Car parked in a flooded basement car park in Shah Alam. Interior carpets and seats soaked.");
        service.pickUp(huimin, flood);
        service.startAssessment(huimin, flood);
        service.approve(huimin, flood, new BigDecimal("2950.00"), "Covered under flood extension, less excess");
        service.settle(huimin, flood);

        // Hui Min: rejected
        UUID lapsed = submit(aisha, Market.MY, ClaimType.MOTOR, 20, "15000", "Collision with a motorcycle at a junction in Petaling Jaya.");
        service.pickUp(huimin, lapsed);
        service.startAssessment(huimin, lapsed);
        service.reject(huimin, lapsed, "The policy had lapsed for non-payment before the incident date.");

        // Wei: assessing a Singapore property claim
        UUID ceiling = submit(ben, Market.SG, ClaimType.PROPERTY, 5, "4200", "Water from the unit above stained and collapsed part of the bedroom ceiling.");
        service.pickUp(wei, ceiling);
        service.startAssessment(wei, ceiling);
        service.updateReserve(wei, ceiling, new BigDecimal("3900.00"), "Contractor estimate");

        log.info("Seeded demo claims");
    }

    private UUID submit(Actor who, Market market, ClaimType type, int daysAgo, String estimate, String description) {
        String policy = "POL-" + market.name() + "-" + (10000 + Math.abs(description.hashCode() % 90000));
        return service.submit(who, policy, market, type, LocalDate.now().minusDays(daysAgo), description,
                new BigDecimal(estimate)).getId();
    }

    private Actor user(String username) {
        return users.byUsername(username).orElseThrow();
    }
}
