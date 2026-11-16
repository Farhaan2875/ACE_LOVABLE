package com.farhaan.projects.Ace.repositories;

import com.farhaan.projects.Ace.entities.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {

    // Only projects where the user is a member (owner, editor or viewer) - NOT every project in the system.
    @Query("""
            SELECT p FROM Project p
            WHERE p.deletedAt IS NULL
              AND EXISTS (SELECT 1 FROM ProjectMember pm WHERE pm.id.projectId = p.id AND pm.id.userId = :userId)
            ORDER BY p.updatedAt DESC""")
    List<Project> findAllAccessibleByUser(@Param("userId") Long userId);

    @Query("""
            SELECT p FROM Project p
            WHERE p.id = :projectId
              AND p.deletedAt IS NULL
              AND EXISTS (SELECT 1 FROM ProjectMember pm WHERE pm.id.projectId = p.id AND pm.id.userId = :userId)""")
    Optional<Project> findAccessibleProjectById(@Param("projectId") Long projectId,
                                                @Param("userId") Long userId);

    long countByOwnerIdAndDeletedAtIsNull(Long ownerId);
}
