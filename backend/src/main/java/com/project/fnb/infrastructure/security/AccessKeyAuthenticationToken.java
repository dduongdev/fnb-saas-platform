package com.project.fnb.infrastructure.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;

import java.util.Collection;

public class AccessKeyAuthenticationToken extends AbstractAuthenticationToken {

    private final String accessKey;
    private final AccessKeyUserDetails principal;

    public AccessKeyAuthenticationToken(String accessKey) {
        super(null);
        this.accessKey = accessKey;
        this.principal = null;
        setAuthenticated(false);
    }

    public AccessKeyAuthenticationToken(AccessKeyUserDetails principal, String accessKey, Collection<? extends GrantedAuthority> authorities) {
        super(authorities);
        this.principal = principal;
        this.accessKey = accessKey;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return accessKey;
    }

    @Override
    public Object getPrincipal() {
        return principal;
    }
}
