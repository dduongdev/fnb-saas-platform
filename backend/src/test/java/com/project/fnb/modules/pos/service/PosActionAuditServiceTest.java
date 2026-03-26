package com.project.fnb.modules.pos.service;

import com.project.fnb.infrastructure.security.TenantContext;
import com.project.fnb.modules.pos.entity.PosActionAudit;
import com.project.fnb.modules.pos.repository.PosActionAuditRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.math.BigDecimal;

import com.project.fnb.infrastructure.security.AppUserPrincipal;
import com.project.fnb.modules.global.entity.User;
import org.mockito.ArgumentCaptor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;

@ExtendWith(MockitoExtension.class)
public class PosActionAuditServiceTest {

    @Mock
    private PosActionAuditRepository repository;

    @InjectMocks
    private PosActionAuditService auditService;

    @BeforeEach
    void setup() {
        TenantContext.setTenantId("tenant-1");
    }

    @AfterEach
    void cleanup() {
        SecurityContextHolder.clearContext();
        TenantContext.clear();
    }

    @Test
    void record_Success_AsOwnerJwt() {
        Jwt jwt = Jwt.withTokenValue("token")
                .header("alg", "none")
                .subject("owner-123")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .build();

        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(jwt, null));

        auditService.record("session.pay", "INVOICE", "inv-1", BigDecimal.valueOf(123.45), "Thanh toán");

        verify(repository).save(any(PosActionAudit.class));
    }

    @Test
    void record_Success_AsAppUserPrincipal() {
        User user = User.builder()
                .id("user-abc")
                .username("testuser")
                .email("test@example.com")
                .fullName("Test User")
                .build();

        AppUserPrincipal appUser = new AppUserPrincipal(user, java.util.List.of(new SimpleGrantedAuthority("ROLE_OWNER")));
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken(appUser, null));

        auditService.record("session.open", "SESSION", "session-1", null, "Mở bàn");

        ArgumentCaptor<PosActionAudit> captor = ArgumentCaptor.forClass(PosActionAudit.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo("Test User (testuser)");
        assertThat(captor.getValue().getUserType()).isEqualTo("OWNER");
    }
}
