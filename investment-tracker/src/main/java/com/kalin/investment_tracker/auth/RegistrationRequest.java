package com.kalin.investment_tracker.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.*;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RegistrationRequest {

    @NotEmpty(message = "Username is mandatory")
    @NotBlank(message = "Username is mandatory")
    @Size(min = 3, max = 30, message = "Username must be between 3 and 30 characters")
    private String username;

    @Email(message = "Email is not formatted")
    @NotEmpty(message = "Email is mandatory")
    @NotBlank(message = "Email is mandatory")
    @Size(max = 254)
    private String email;

    @NotEmpty(message = "Password is mandatory")
    @NotBlank(message = "Password is mandatory")
    @Size(min = 6, message = "Password should be at least 6 characters long")
    private String password;
}
