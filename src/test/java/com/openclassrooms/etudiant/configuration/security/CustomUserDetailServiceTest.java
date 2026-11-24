package com.openclassrooms.etudiant.configuration.security;

import com.openclassrooms.etudiant.entities.User;
import com.openclassrooms.etudiant.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailServiceTest {
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomUserDetailService userDetailService;

    @Test
    void returnsRegisteredAgentDetails() {
        // Spring Security needs the stored hash to authenticate the agent.
        User agent = new User();
        agent.setLogin("agent.p2");
        agent.setPassword("stored-hash");
        when(userRepository.findByLogin("agent.p2")).thenReturn(Optional.of(agent));

        UserDetails details = userDetailService.loadUserByUsername("agent.p2");

        assertThat(details.getUsername()).isEqualTo("agent.p2");
        assertThat(details.getPassword()).isEqualTo("stored-hash");
    }
}
