package com.kalin.investment_tracker.user;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Entity
public class Token {

    @Id
    @GeneratedValue
    private Integer id;
    private String token;
    private LocalDateTime createdAt;
    private LocalDateTime expiresAt;
    private LocalDateTime validatedAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private TokenType type;

    @ManyToOne
    @JoinColumn(name = "userId", nullable = false)
    private User user;

}
