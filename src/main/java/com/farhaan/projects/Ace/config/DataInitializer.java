package com.farhaan.projects.Ace.config;

import com.farhaan.projects.Ace.entities.Plan;
import com.farhaan.projects.Ace.repositories.PlanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Makes sure the three standard plans exist (runs on every start, safe to repeat). */
@Component
@RequiredArgsConstructor
public class DataInitializer implements ApplicationRunner {
    private final PlanRepository planRepository;
    private final AceProperties props;

    @Override
    public void run(ApplicationArguments args) {
        seed("FREE", "Free", null, 3, 30_000, 1, false);
        seed("PRO", "$19 / month", props.getBilling().getStripe().getPricePro(), 20, 500_000, 3, false);
        seed("TEAM", "$49 / month", props.getBilling().getStripe().getPriceTeam(), 100, null, 10, true);
    }

    private void seed(String name, String price, String stripePriceId, int maxProjects, Integer tokensPerDay, int maxPreviews, boolean unlimitedAi) {
        Plan plan = planRepository.findByName(name).orElseGet(() -> Plan.builder()
                .name(name).maxProjects(maxProjects).maxTokensPerDay(tokensPerDay)
                .maxPreviews(maxPreviews).unlimitedAi(unlimitedAi).active(true).build());
        plan.setPrice(price);
        if (stripePriceId != null && !stripePriceId.isBlank()) plan.setStripePriceId(stripePriceId);
        planRepository.save(plan);
    }
}
