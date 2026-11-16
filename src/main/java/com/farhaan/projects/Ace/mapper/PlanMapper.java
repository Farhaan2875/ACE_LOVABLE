package com.farhaan.projects.Ace.mapper;

import com.farhaan.projects.Ace.Dto.billing.PlanResponse;
import com.farhaan.projects.Ace.entities.Plan;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface PlanMapper {
    PlanResponse toPlanResponse(Plan plan);

    List<PlanResponse> toPlanResponse(List<Plan> plans);
}
