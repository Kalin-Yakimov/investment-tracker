package com.kalin.investment_tracker.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.*;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ForgotPasswordRequest {

    @Email(message = "Email is not formatted")
    @NotEmpty(message = "Email is mandatory")
    @NotBlank(message = "Email is mandatory")
    private String email;
}
