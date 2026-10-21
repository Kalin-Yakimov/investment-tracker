package com.kalin.investment_tracker.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;
import lombok.*;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ResetPasswordRequest {

    @Email(message = "Email is not formatted")
    @NotEmpty(message = "Email is mandatory")
    @NotBlank(message = "Email is mandatory")
    @Size(max = 254)
    private String email;

    @NotEmpty(message = "Reset code is mandatory")
    @NotBlank(message = "Reset code is mandatory")
    @Pattern(regexp = "[0-9]{6}", message = "Enter a 6-digit code")
    private String code;

    @ValidPassword
    private String newPassword;
}
