package com.farhaan.projects.Ace.entities;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

/** One usage record (an AI request). Summing tokensUsed per day gives the daily quota. */
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "user_logs", indexes = @Index(name = "idx_user_logs_user_created", columnList = "user_id, created_at"))
public class UserLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne(optional = false)
    User user;

    @ManyToOne
    Project project;

    String action;

    Integer tokensUsed;

    Integer durationMs;

    @Column(columnDefinition = "text")
    String metaData;

    @CreationTimestamp
    Instant createdAt;
}
