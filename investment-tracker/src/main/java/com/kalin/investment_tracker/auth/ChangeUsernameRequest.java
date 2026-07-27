package com.kalin.investment_tracker.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChangeUsernameRequest {

    @NotEmpty(message = "New username is mandatory")
    @NotBlank(message = "New username is mandatory")
    @Size(min = 3, max = 30, message = "Username must be between 3 and 30 characters")
    private String newUsername;
}
