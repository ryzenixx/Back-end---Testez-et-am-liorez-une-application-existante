package com.openclassrooms.etudiant.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openclassrooms.etudiant.dto.StudentRequestDTO;
import com.openclassrooms.etudiant.repository.StudentRepository;
import com.openclassrooms.etudiant.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mysql.MySQLContainer;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class StudentControllerTest {
    private static final String STUDENTS_URL = "/api/students";
    private static final String AGENT_LOGIN = "crud-agent";
    private static final String AGENT_PASSWORD = "test-password";
    private static final StudentRequestDTO STUDENT =
            new StudentRequestDTO("Camille", "Martin", "camille@example.org");

    @Container
    static final MySQLContainer mysql = new MySQLContainer("mysql:latest");

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private StudentRepository studentRepository;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private JwtEncoder jwtEncoder;

    @DynamicPropertySource
    static void configureDatabase(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create");
    }

    @AfterEach
    void clearDatabase() {
        studentRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void managesAStudentWithATokenFromLogin() throws Exception {
        String authorization = "Bearer " + login();

        var created = mockMvc.perform(post(STUDENTS_URL)
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(STUDENT)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.firstName").value(STUDENT.firstName()))
                .andExpect(jsonPath("$.lastName").value(STUDENT.lastName()))
                .andExpect(jsonPath("$.email").value(STUDENT.email()))
                .andReturn().getResponse();

        long studentId = objectMapper.readTree(created.getContentAsString()).required("id").asLong();
        String studentUrl = STUDENTS_URL + "/" + studentId;
        assertThat(created.getHeader(HttpHeaders.LOCATION)).isEqualTo(studentUrl);

        mockMvc.perform(get(STUDENTS_URL).header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(studentId));

        mockMvc.perform(get(studentUrl).header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(STUDENT.email()));

        StudentRequestDTO changes = new StudentRequestDTO("Camille", "Durand", "camille.durand@example.org");
        mockMvc.perform(put(studentUrl)
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changes)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(studentId))
                .andExpect(jsonPath("$.lastName").value(changes.lastName()));

        mockMvc.perform(get(studentUrl).header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastName").value(changes.lastName()))
                .andExpect(jsonPath("$.email").value(changes.email()));

        mockMvc.perform(delete(studentUrl).header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isNoContent());

        mockMvc.perform(get(studentUrl).header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isNotFound());
        mockMvc.perform(put(studentUrl)
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(changes)))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete(studentUrl).header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(status().isNotFound());
        mockMvc.perform(get(STUDENTS_URL).header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void rejectsEveryStudentRouteWithoutAuthentication() throws Exception {
        String studentUrl = STUDENTS_URL + "/1";
        for (MockHttpServletRequestBuilder request : List.of(
                post(STUDENTS_URL), get(STUDENTS_URL), get(studentUrl), put(studentUrl), delete(studentUrl))) {
            mockMvc.perform(request)
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));
        }
    }

    @Test
    void rejectsAnInvalidBearerToken() throws Exception {
        mockMvc.perform(get(STUDENTS_URL).header(HttpHeaders.AUTHORIZATION, "Bearer invalid-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void rejectsAnExpiredJwt() throws Exception {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(AGENT_LOGIN)
                .issuedAt(now.minus(Duration.ofHours(1)))
                .expiresAt(now.minusSeconds(1))
                .build();

        assertJwtRejected(claims);
    }

    @Test
    void rejectsJwtWithoutExpiration() throws Exception {
        JwtClaimsSet claims = JwtClaimsSet.builder().subject(AGENT_LOGIN).issuedAt(Instant.now()).build();

        assertJwtRejected(claims);
    }

    @Test
    void rejectsJwtWithoutSubject() throws Exception {
        Instant now = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder().issuedAt(now).expiresAt(now.plusSeconds(60)).build();

        assertJwtRejected(claims);
    }

    @Test
    void rejectsInvalidStudentData() throws Exception {
        String authorization = "Bearer " + login();
        StudentRequestDTO invalidStudent = new StudentRequestDTO(" ", "Martin", "invalid");

        mockMvc.perform(post(STUDENTS_URL)
                        .header(HttpHeaders.AUTHORIZATION, authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidStudent)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get(STUDENTS_URL).header(HttpHeaders.AUTHORIZATION, authorization))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    private void assertJwtRejected(JwtClaimsSet claims) throws Exception {
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        String token = jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();

        mockMvc.perform(get(STUDENTS_URL).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    private String login() throws Exception {
        mockMvc.perform(post("/api/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "firstName", "Test", "lastName", "Agent",
                                "login", AGENT_LOGIN, "password", AGENT_PASSWORD))))
                .andExpect(status().isCreated());

        return mockMvc.perform(post("/api/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "login", AGENT_LOGIN, "password", AGENT_PASSWORD))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }
}
