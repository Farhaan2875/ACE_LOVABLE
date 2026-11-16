package com.farhaan.projects.Ace.services;

import com.farhaan.projects.Ace.Dto.billing.PlanLimitsResponse;
import com.farhaan.projects.Ace.Dto.billing.UsageTodayResponse;

public interface UsageService {
    UsageTodayResponse getTodayUsageOfUser(Long userId);

    PlanLimitsResponse getCurrentSubscriptionLimitsOfUser(Long userId);

    /** Throws PlanLimitException (HTTP 402) when today's token quota is used up. */
    void assertCanUseAi(Long userId);

    void recordUsage(Long userId, Long projectId, String action, int tokens, long durationMs, String metaJson);
}
