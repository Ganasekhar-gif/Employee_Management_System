package com.company.expense.config;

import com.company.expense.dto.request.CreateUserRequest;
import com.company.expense.enums.RoleName;
import com.company.expense.repository.UserRepository;
import com.company.expense.service.UserService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class AdminAccountInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountInitializer.class);

    private final BootstrapAdminProperties properties;
    private final UserRepository userRepository;
    private final UserService userService;

    public AdminAccountInitializer(
            BootstrapAdminProperties properties, UserRepository userRepository, UserService userService) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.userService = userService;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (userRepository.existsByRole_Name(RoleName.ADMIN)) {
            return;
        }
        if (!properties.isConfigured()) {
            log.warn("No administrator exists. Set BOOTSTRAP_ADMIN_PASSWORD to create one at startup.");
            return;
        }
        userService.createUser(new CreateUserRequest(
                properties.email(), properties.password(), properties.fullName(), RoleName.ADMIN, null, null));
        log.info("Created bootstrap administrator {}", properties.email());
    }
}
