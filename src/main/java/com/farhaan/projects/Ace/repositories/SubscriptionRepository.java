package com.farhaan.projects.Ace.repositories;

import com.farhaan.projects.Ace.entities.Subscription;
import com.farhaan.projects.Ace.enums.SubscriptionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.Optional;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    Optional<Subscription> findFirstByUserIdAndStatusInOrderByCreatedAtDesc(Long userId, Collection<SubscriptionStatus> statuses);

    Optional<Subscription> findFirstByUserIdOrderByCreatedAtDesc(Long userId);

    Optional<Subscription> findByStripeSubscriptionId(String stripeSubscriptionId);
}
