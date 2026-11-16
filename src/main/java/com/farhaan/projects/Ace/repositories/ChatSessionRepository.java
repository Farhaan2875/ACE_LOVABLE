package com.farhaan.projects.Ace.repositories;

import com.farhaan.projects.Ace.entities.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {
    Optional<ChatSession> findFirstByProjectIdAndUserIdAndDeletedAtIsNull(Long projectId, Long userId);
}
