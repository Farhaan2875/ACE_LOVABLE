package com.farhaan.projects.Ace.repositories;

import com.farhaan.projects.Ace.entities.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /** Newest first. Use PageRequest.of(0, n) to get "the last n messages". */
    @Query("SELECT m FROM ChatMessage m WHERE m.chatSession.id = :sessionId ORDER BY m.id DESC")
    List<ChatMessage> findRecent(@Param("sessionId") Long sessionId, Pageable pageable);
}
