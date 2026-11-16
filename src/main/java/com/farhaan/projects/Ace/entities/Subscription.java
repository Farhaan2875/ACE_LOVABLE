package com.farhaan.projects.Ace.entities;

import com.farhaan.projects.Ace.enums.SubscriptionStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "subscriptions", indexes = @Index(name = "idx_subscriptions_user", columnList = "user_id"))
public class Subscription {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne(optional = false)
    User user; // hibernate ORM will take care of relationships (didn't define userId here)

    @ManyToOne(optional = false)
    Plan plan;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    SubscriptionStatus status;

    String stripeCustomerId;

    @Column(unique = true)
    String stripeSubscriptionId;

    Instant currentPeriodStart;
    Instant currentPeriodEnd; // will keep on updating as subscription continues

    @Builder.Default
    Boolean cancelAtPeriodEnd = false; // if user wants to cancel the subscription when the period ends (by default false)

    @CreationTimestamp
    Instant createdAt;
    @UpdateTimestamp
    Instant updatedAt;
}
