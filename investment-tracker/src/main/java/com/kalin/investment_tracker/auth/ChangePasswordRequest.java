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
public class ChangePasswordRequest {

    @NotEmpty(message = "Current password is mandatory")
    @NotBlank(message = "Current password is mandatory")
    private String currentPassword;

    @NotEmpty(message = "New password is mandatory")
    @NotBlank(message = "New password is mandatory")
    @Size(min = 6, message = "New password should be at least 6 characters long")
    private String newPassword;

    @NotEmpty(message = "Confirmation password is mandatory")
    @NotBlank(message = "Confirmation password is mandatory")
    private String confirmationPassword;
}
