package com.ecommerce.auth.config;

import com.ecommerce.auth.entity.Role;
import com.ecommerce.auth.entity.User;
import com.ecommerce.auth.repository.UserRepository;
import java.util.EnumSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Garante que as contas configuradas existam, sem sobrescrever o que já existe. */
@Component
@EnableConfigurationProperties(BootstrapProperties.class)
public class BootstrapAccounts implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(BootstrapAccounts.class);

    private final BootstrapProperties properties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public BootstrapAccounts(BootstrapProperties properties, UserRepository userRepository,
                             PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        createIfMissing(properties.admin(), EnumSet.of(Role.ADMIN), "administrador");
        createIfMissing(properties.serviceAccount(), EnumSet.of(Role.SERVICE), "conta de serviço");
    }

    private void createIfMissing(BootstrapProperties.Account account, Set<Role> roles, String description) {
        if (account == null || !account.isConfigured()) {
            return;
        }
        String email = account.email().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            return;
        }

        String name = account.fullName() == null || account.fullName().isBlank()
                ? description
                : account.fullName();
        User user = userRepository.save(User.create(
                email, passwordEncoder.encode(account.password()), name, roles));
        log.info("Criada {} inicial: {}", description, user.getId());
    }
}
