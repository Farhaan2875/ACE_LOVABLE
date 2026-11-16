package com.farhaan.projects.Ace.entities;

import com.farhaan.projects.Ace.enums.MessageRole;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.FieldDefaults;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
@Entity
@AllArgsConstructor
@NoArgsConstructor
@Builder
@Table(name = "chat_messages", indexes = @Index(name = "idx_chat_messages_session", columnList = "chat_session_id, id"))
public class ChatMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    ChatSession chatSession;

    @Column(columnDefinition = "text")
    String content;

    // JSON such as {"files":["src/App.jsx"]} - which files the assistant wrote in this answer
    @Column(columnDefinition = "text")
    String toolCalls;

    Integer tokensUsed;

    @CreationTimestamp
    Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    MessageRole role;
}
