package com.kalin.investment_tracker.auth;

import lombok.*;

@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ChangePasswordRequest {

    @ValidPassword(newPassword = false)
    private String currentPassword;

    @ValidPassword
    private String newPassword;

    @ValidPassword(newPassword = false)
    private String confirmationPassword;
}
