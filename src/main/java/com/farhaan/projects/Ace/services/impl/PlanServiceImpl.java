package com.farhaan.projects.Ace.services.impl;

import com.farhaan.projects.Ace.Dto.billing.PlanResponse;
import com.farhaan.projects.Ace.entities.Plan;
import com.farhaan.projects.Ace.enums.SubscriptionStatus;
import com.farhaan.projects.Ace.mapper.PlanMapper;
import com.farhaan.projects.Ace.repositories.PlanRepository;
import com.farhaan.projects.Ace.repositories.SubscriptionRepository;
import com.farhaan.projects.Ace.services.PlanService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PlanServiceImpl implements PlanService {

    /** Subscription states that still give the user paid features. */
    public static final Set<SubscriptionStatus> PAID_STATUSES =
            Set.of(SubscriptionStatus.ACTIVE, SubscriptionStatus.TRIALING, SubscriptionStatus.PAST_DUE);

    private final PlanRepository planRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final PlanMapper planMapper;

    @Override
    @Transactional(readOnly = true)
    public List<PlanResponse> getAllActivePlans() {
        return planMapper.toPlanResponse(planRepository.findByActiveTrueOrderByIdAsc());
    }

    @Override
    @Transactional(readOnly = true)
    public Plan getEffectivePlan(Long userId) {
        return subscriptionRepository.findFirstByUserIdAndStatusInOrderByCreatedAtDesc(userId, PAID_STATUSES)
                .map(sub -> sub.getPlan())
                .orElseGet(this::getFreePlan);
    }

    @Override
    @Transactional(readOnly = true)
    public Plan getFreePlan() {
        return planRepository.findByName("FREE")
                .orElseThrow(() -> new IllegalStateException("FREE plan is missing - DataInitializer did not run"));
    }
}
