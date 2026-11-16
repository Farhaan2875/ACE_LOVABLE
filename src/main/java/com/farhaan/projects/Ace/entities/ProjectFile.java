package com.farhaan.projects.Ace.entities;

import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

/** Metadata about one file of a project. The file content itself lives in MinIO / object storage. */
@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "project_files",
        uniqueConstraints = @UniqueConstraint(name = "uq_project_files_path", columnNames = {"project_id", "path"}))
public class ProjectFile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne(optional = false)
    Project project;

    @Column(nullable = false)
    String path;

    @Column(nullable = false)
    String minioObjectKey;

    Long size;

    @CreationTimestamp
    Instant createdAt;
    @UpdateTimestamp
    Instant updatedAt;

    @ManyToOne
    User createdBy;
    @ManyToOne
    User updatedBy;
}
