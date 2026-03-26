package com.project.fnb.infrastructure.security;

import com.project.fnb.modules.global.entity.AccessKey;
import com.project.fnb.modules.global.repository.AccessKeyRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Component
@RequiredArgsConstructor
public class AccessKeyAuthenticationProvider implements AuthenticationProvider {

    private final AccessKeyRepository accessKeyRepository;

    @Override
    public Authentication authenticate(Authentication authentication) throws AuthenticationException {
        String keyString = (String) authentication.getCredentials();

        AccessKey accessKey = accessKeyRepository.findByKeyStringAndIsActiveTrue(keyString)
                .orElseThrow(() -> new BadCredentialsException("Access Key không hợp lệ hoặc đã bị vô hiệu hoá"));

        AccessKeyUserDetails userDetails = new AccessKeyUserDetails(
                accessKey.getId(),
                keyString,
                accessKey.getTenantId(),
                "ROLE_" + accessKey.getRole().name(),
                Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + accessKey.getRole().name()))
        );

        return new AccessKeyAuthenticationToken(userDetails, keyString, userDetails.getAuthorities());
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return AccessKeyAuthenticationToken.class.isAssignableFrom(authentication);
    }
}
