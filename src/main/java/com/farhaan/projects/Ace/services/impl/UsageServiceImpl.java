package com.farhaan.projects.Ace.services.impl;

import com.farhaan.projects.Ace.Dto.billing.PlanLimitsResponse;
import com.farhaan.projects.Ace.Dto.billing.UsageTodayResponse;
import com.farhaan.projects.Ace.entities.Plan;
import com.farhaan.projects.Ace.entities.UserLog;
import com.farhaan.projects.Ace.enums.PreviewStatus;
import com.farhaan.projects.Ace.error.PlanLimitException;
import com.farhaan.projects.Ace.repositories.PreviewRepository;
import com.farhaan.projects.Ace.repositories.ProjectRepository;
import com.farhaan.projects.Ace.repositories.UserLogRepository;
import com.farhaan.projects.Ace.repositories.UserRepository;
import com.farhaan.projects.Ace.services.PlanService;
import com.farhaan.projects.Ace.services.UsageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

@Service
@RequiredArgsConstructor
public class UsageServiceImpl implements UsageService {

    private final PlanService planService;
    private final UserLogRepository userLogRepository;
    private final PreviewRepository previewRepository;
    private final UserRepository userRepository;
    private final ProjectRepository projectRepository;

    /** "Today" always means the UTC day, so the quota resets at 00:00 UTC for everyone. */
    private Instant startOfToday() {
        return LocalDate.now(ZoneOffset.UTC).atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    @Override
    @Transactional(readOnly = true)
    public UsageTodayResponse getTodayUsageOfUser(Long userId) {
        Plan plan = planService.getEffectivePlan(userId);
        long used = userLogRepository.sumTokensSince(userId, startOfToday());
        long previews = previewRepository.countActiveForOwner(userId, List.of(PreviewStatus.CREATING, PreviewStatus.RUNNING));
        Integer tokenLimit = Boolean.TRUE.equals(plan.getUnlimitedAi()) ? null : plan.getMaxTokensPerDay();   // null = unlimited
        return new UsageTodayResponse((int) Math.min(used, Integer.MAX_VALUE), tokenLimit, (int) previews, plan.getMaxPreviews());
    }

    @Override
    @Transactional(readOnly = true)
    public PlanLimitsResponse getCurrentSubscriptionLimitsOfUser(Long userId) {
        Plan plan = planService.getEffectivePlan(userId);
        return new PlanLimitsResponse(plan.getName(), plan.getMaxTokensPerDay(), plan.getMaxProjects(),
                Boolean.TRUE.equals(plan.getUnlimitedAi()));
    }

    @Override
    @Transactional(readOnly = true)
    public void assertCanUseAi(Long userId) {
        Plan plan = planService.getEffectivePlan(userId);
        if (Boolean.TRUE.equals(plan.getUnlimitedAi()) || plan.getMaxTokensPerDay() == null) return;

        long used = userLogRepository.sumTokensSince(userId, startOfToday());
        if (used >= plan.getMaxTokensPerDay()) {
            throw new PlanLimitException("Daily AI limit reached (" + used + " / " + plan.getMaxTokensPerDay()
                    + " tokens). Upgrade your plan or come back after 00:00 UTC.");
        }
    }

    @Override
    @Transactional
    public void recordUsage(Long userId, Long projectId, String action, int tokens, long durationMs, String metaJson) {
        userLogRepository.save(UserLog.builder()
                .user(userRepository.getReferenceById(userId))
                .project(projectId == null ? null : projectRepository.getReferenceById(projectId))
                .action(action)
                .tokensUsed(tokens)
                .durationMs((int) Math.min(durationMs, Integer.MAX_VALUE))
                .metaData(metaJson)
                .build());
    }
}
