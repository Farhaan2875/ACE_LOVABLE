package com.farhaan.projects.Ace.repositories;

import com.farhaan.projects.Ace.entities.Plan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlanRepository extends JpaRepository<Plan, Long> {
    List<Plan> findByActiveTrueOrderByIdAsc();

    Optional<Plan> findByName(String name);

    Optional<Plan> findByStripePriceId(String stripePriceId);
}
