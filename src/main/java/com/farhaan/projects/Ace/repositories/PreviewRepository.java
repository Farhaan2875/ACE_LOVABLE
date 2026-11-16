package com.farhaan.projects.Ace.repositories;

import com.farhaan.projects.Ace.entities.Preview;
import com.farhaan.projects.Ace.enums.PreviewStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface PreviewRepository extends JpaRepository<Preview, Long> {
    Optional<Preview> findByProjectId(Long projectId);

    List<Preview> findByStatusIn(Collection<PreviewStatus> statuses);

    /** How many live previews the projects owned by this user are using right now. */
    @Query("SELECT COUNT(pv) FROM Preview pv WHERE pv.project.owner.id = :userId AND pv.status IN :statuses")
    long countActiveForOwner(@Param("userId") Long userId, @Param("statuses") Collection<PreviewStatus> statuses);
}
