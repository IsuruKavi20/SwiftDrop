package com.swiftdrop.backend.identity.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CallerCopyFilterTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void anyLoggedInPersonIsCopiedOnTheirFirstRequest() throws Exception {
        UUID kasun = UUID.randomUUID();

        // Any endpoint will do: even one that answers 404 or 403, the person is known after the request
        mockMvc.perform(get("/api/customers/me").with(jwt()
                .jwt(token -> token.subject(kasun.toString()).claim("name", "Kasun Silva")
                        .claim("roles", List.of("COURIER")))
                .authorities(new SimpleGrantedAuthority("delivery:read-own"))));

        assertThat(jdbc.queryForObject("SELECT name FROM app_users WHERE id = ?", String.class, kasun))
                .isEqualTo("Kasun Silva");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_user_roles WHERE user_id = ? AND role = 'COURIER'",
                Integer.class, kasun)).isEqualTo(1);
    }

    @Test
    void serviceClientsAreNotPeople() throws Exception {
        int before = jdbc.queryForObject("SELECT COUNT(*) FROM app_users", Integer.class);

        mockMvc.perform(get("/api/customers/me").with(jwt().jwt(token -> token.subject("svc_reporting"))));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_users", Integer.class)).isEqualTo(before);
    }

    @Test
    void noLoginNothingCopied() throws Exception {
        int before = jdbc.queryForObject("SELECT COUNT(*) FROM app_users", Integer.class);

        mockMvc.perform(get("/api/public/track/SD-000000"));

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM app_users", Integer.class)).isEqualTo(before);
    }
}
