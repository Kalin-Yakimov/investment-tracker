package com.kalin.investment_tracker.email;

import com.kalin.investment_tracker.admin.ActivityEventService;
import com.kalin.investment_tracker.user.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EmailDeliveryAuditService {
    private final UserRepository userRepository;
    private final ActivityEventService activityEventService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(Integer userId) {
        userRepository.findById(userId).ifPresent(user -> activityEventService.recordSecurityEvent(
                user, "VERIFICATION_EMAIL_FAILED",
                "Verification email delivery failed. The user can request another code.", null
        ));
    }
}
