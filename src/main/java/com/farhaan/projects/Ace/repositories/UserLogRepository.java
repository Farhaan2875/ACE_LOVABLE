package com.farhaan.projects.Ace.repositories;

import com.farhaan.projects.Ace.entities.UserLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public interface UserLogRepository extends JpaRepository<UserLog, Long> {

    @Query("SELECT COALESCE(SUM(l.tokensUsed), 0) FROM UserLog l WHERE l.user.id = :userId AND l.createdAt >= :since")
    long sumTokensSince(@Param("userId") Long userId, @Param("since") Instant since);
}
