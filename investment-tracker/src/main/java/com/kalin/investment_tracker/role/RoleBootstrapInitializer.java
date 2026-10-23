package com.kalin.investment_tracker.role;

import com.kalin.investment_tracker.user.User;
import com.kalin.investment_tracker.user.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class RoleBootstrapInitializer implements ApplicationRunner {

    private final RoleRepository roleRepository;
    private final UserRepository userRepository;

    @Value("${app.bootstrap-admin-email:}")
    private String bootstrapAdminEmail;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        ensureRole(RoleNames.USER);
        ensureRole(RoleNames.SUPPORT);
        Role adminRole = ensureRole(RoleNames.ADMIN);

        String adminEmail = bootstrapAdminEmail == null ? "" : bootstrapAdminEmail.trim();
        if (adminEmail.isEmpty()) {
            log.info("No bootstrap administrator email is configured");
            return;
        }

        User user = userRepository.findLockedByEmail(adminEmail).orElse(null);
        if (user == null) {
            log.warn("Bootstrap administrator account '{}' does not exist yet", adminEmail);
            return;
        }

        boolean alreadyAdmin = user.getRoles().stream()
                .anyMatch(role -> RoleNames.ADMIN.equals(role.getName()));
        if (!alreadyAdmin) {
            List<Role> roles = new ArrayList<>(user.getRoles());
            roles.add(adminRole);
            user.setRoles(roles);
            userRepository.save(user);
            log.info("Assigned ADMIN role to the configured bootstrap administrator account");
        }
    }

    private Role ensureRole(String name) {
        return roleRepository.findByName(name).orElseGet(() -> roleRepository.save(
                Role.builder().name(name).build()
        ));
    }
}
