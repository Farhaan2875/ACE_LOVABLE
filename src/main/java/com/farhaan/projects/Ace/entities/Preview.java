package com.farhaan.projects.Ace.entities;

import com.farhaan.projects.Ace.enums.PreviewStatus;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/** The running (or last) live preview of a project. One row per project, reused on every restart. */
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "previews", uniqueConstraints = @UniqueConstraint(name = "uq_previews_project", columnNames = "project_id"))
public class Preview {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne(optional = false)
    Project project;

    String namespace;   // Kubernetes namespace (kubernetes runner only)

    String podName;

    String previewUrl;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    PreviewStatus status;

    @Column(length = 1000)
    String statusMessage;

    Instant startedAt;

    Instant terminatedAt;

    @CreationTimestamp
    Instant createdAt;

    @UpdateTimestamp
    Instant updatedAt;
}
