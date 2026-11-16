package com.farhaan.projects.Ace.services;

import com.farhaan.projects.Ace.Dto.billing.PlanResponse;
import com.farhaan.projects.Ace.entities.Plan;

import java.util.List;

public interface PlanService {
    List<PlanResponse> getAllActivePlans();

    /** The plan the user is on right now: their paid subscription, or FREE. */
    Plan getEffectivePlan(Long userId);

    Plan getFreePlan();
}
