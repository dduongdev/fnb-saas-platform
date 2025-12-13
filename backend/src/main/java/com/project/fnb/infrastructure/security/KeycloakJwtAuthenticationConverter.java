package com.project.fnb.infrastructure.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Converter để chuyển đổi Keycloak JWT token sang Spring Security Authentication token.
 * 
 * <p>Class này thực hiện vai trò của một Converter trong Spring Security OAuth2,
 * chịu trách nhiệm trích xuất thông tin từ JWT token do Keycloak cấp phát
 * và chuyển đổi thành AbstractAuthenticationToken có thể sử dụng được
 * trong Spring Security context.</p>
 * 
 * <p><b>Mục đích chính:</b></p>
 * <ul>
 *   <li>Trích xuất authorities (quyền hạn) từ Keycloak JWT token</li>
 *   <li>Kết hợp authorities mặc định từ OAuth2 với realm roles từ Keycloak</li>
 *   <li>Tạo JwtAuthenticationToken có thể sử dụng trong Security Context</li>
 *   <li>Sử dụng preferred_username từ token làm principal name</li>
 * </ul>
 * 
 * <p><b>Cấu trúc JWT token từ Keycloak:</b></p>
 * <pre>
 * {@code
 * {
 *   "sub": "user-id",
 *   "preferred_username": "john.doe",
 *   "realm_access": {
 *     "roles": ["admin", "user", "manager"]
 *   },
 *   "scope": "openid profile email",
 *   ...
 * }
 * }
 * </pre>
 * 
 * <p><b>Cơ chế hoạt động:</b></p>
 * <ol>
 *   <li>Spring Security gọi convert() với JWT token từ request</li>
 *   <li>Sử dụng JwtGrantedAuthoritiesConverter (mặc định) để trích xuất authorities</li>
 *   <li>Trích xuất realm roles từ claim "realm_access.roles"</li>
 *   <li>Chuyển đổi roles thành GrantedAuthority (ROLE_xxx format)</li>
 *   <li>Kết hợp authorities từ cả hai nguồn</li>
 *   <li>Tạo JwtAuthenticationToken với authorities kết hợp</li>
 *   <li>Token được lưu vào SecurityContext để sử dụng trong application</li>
 * </ol>
 * 
 * <p><b>Ví dụ về role conversion:</b></p>
 * <ul>
 *   <li>"admin" → GrantedAuthority("ROLE_admin")</li>
 *   <li>"user" → GrantedAuthority("ROLE_user")</li>
 *   <li>"manager" → GrantedAuthority("ROLE_manager")</li>
 * </ul>
 * 
 * <p><b>Lợi ích:</b></p>
 * <ul>
 *   <li>Tích hợp Keycloak roles vào Spring Security</li>
 *   <li>Cho phép sử dụng @PreAuthorize("hasRole('admin')") annotations</li>
 *   <li>Hỗ trợ multiple authorities từ các nguồn khác nhau</li>
 *   <li>Flexible cấu hình quyền hạn</li>
 *   <li>Tương thích với Spring Security standard</li>
 * </ul>
 * 
 * <p><b>Mối liên hệ với các thành phần:</b></p>
 * <ul>
 *   <li>Keycloak: Cấp phát JWT token</li>
 *   <li>Spring Security: Quản lý authentication/authorization</li>
 *   <li>SecurityConfig: Cấu hình converter này trong security chain</li>
 *   <li>Controller/@PreAuthorize: Sử dụng roles từ token</li>
 * </ul>
 * 
 * <p><b>Lưu ý quan trọng:</b></p>
 * <ul>
 *   <li>Token phải có claim "realm_access" để roles được trích xuất</li>
 *   <li>Nếu "realm_access" không tồn tại, trả về empty set authorities</li>
 *   <li>Prefix "ROLE_" được thêm vào tất cả roles từ Keycloak</li>
 *   <li>Authorities mặc định từ OAuth2 cũng được giữ lại</li>
 *   <li>principal name được lấy từ "preferred_username" claim</li>
 * </ul>
 * 
 * @author Project Team
 * @version 1.0
 * @see org.springframework.core.convert.converter.Converter
 * @see org.springframework.security.oauth2.jwt.Jwt
 * @see AbstractAuthenticationToken
 * @see JwtAuthenticationToken
 */
public class KeycloakJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    /**
     * Chuyển đổi JWT token từ Keycloak thành JwtAuthenticationToken cho Spring Security.
     * 
     * <p>Phương thức này là phương thức chính của Converter, được gọi bởi Spring Security
     * khi xử lý JWT token từ HTTP request. Nó trích xuất tất cả authorities từ token
     * (bao gồm cả OAuth2 scopes và Keycloak realm roles) rồi tạo một authentication token
     * có thể sử dụng được trong Security Context.</p>
     * 
     * <p><b>Quy trình chi tiết:</b></p>
     * <ol>
     *   <li>Tạo JwtGrantedAuthoritiesConverter (converter mặc định của Spring)</li>
     *   <li>Trích xuất authorities mặc định từ JWT token</li>
     *   <li>Gọi extractResourceRoles() để lấy realm roles từ Keycloak</li>
     *   <li>Kết hợp cả hai tập hợp authorities vào một Set</li>
     *   <li>Tạo JwtAuthenticationToken mới với:</li>
     *   <ul>
     *     <li>JWT token gốc</li>
     *     <li>Tập hợp authorities kết hợp</li>
     *     <li>preferred_username làm principal name</li>
     *   </ul>
     *   <li>Trả về authentication token</li>
     * </ol>
     * 
     * <p><b>Authorities được trích xuất:</b></p>
     * <ul>
     *   <li><b>Từ JwtGrantedAuthoritiesConverter (mặc định):</b></li>
     *   <ul>
     *     <li>Scopes từ token (nếu có)</li>
     *     <li>Authorities từ "authorities" claim (nếu có)</li>
     *   </ul>
     *   <li><b>Từ extractResourceRoles():</b></li>
     *   <ul>
     *     <li>Realm roles từ "realm_access.roles"</li>
     *     <li>Với prefix "ROLE_" (ví dụ: "ROLE_admin")</li>
     *   </ul>
     * </ul>
     * 
     * <p><b>Ví dụ kết quả:</b></p>
     * <pre>
     * {@code
     * Input JWT claims:
     * {
     *   "preferred_username": "john.doe",
     *   "realm_access": {
     *     "roles": ["admin", "user"]
     *   }
     * }
     * 
     * Output JwtAuthenticationToken:
     * - Principal: "john.doe"
     * - Authorities: [ROLE_admin, ROLE_user, ...other authorities from default converter]
     * }
     * </pre>
     * 
     * <p><b>Ghi chú quan trọng:</b></p>
     * <ul>
     *   <li>Sử dụng Set để đảm bảo không có authorities trùng lặp</li>
     *   <li>Stream.concat() kết hợp hai Stream authorities</li>
     *   <li>Preferred_username được sử dụng làm principal (thường là username)</li>
     *   <li>Phương thức này được gọi cho mỗi request có JWT token</li>
     *   <li>Authentication token kết quả được lưu vào SecurityContext</li>
     * </ul>
     * 
     * @param jwt JWT token từ Keycloak chứa user information và roles.
     *            Token phải chứa claim "preferred_username" (bắt buộc)
     *            và "realm_access.roles" (nên có nhưng tùy chọn).
     * 
     * @return {@link AbstractAuthenticationToken} (cụ thể là JwtAuthenticationToken)
     *         chứa:</li>
     *         - JWT token gốc</li>
     *         - Tập hợp authorities kết hợp (OAuth2 + Keycloak realm roles)</li>
     *         - Principal name từ "preferred_username"</li>
     * 
     * @throws ClassCastException nếu "realm_access.roles" không phải là Collection<String>
     * @throws NullPointerException nếu jwt là null hoặc missing required claims
     * 
     * @see #extractResourceRoles(Jwt)
     * @see JwtGrantedAuthoritiesConverter
     * @see JwtAuthenticationToken
     */
    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        JwtGrantedAuthoritiesConverter defaultConverter = new JwtGrantedAuthoritiesConverter();
        Collection<GrantedAuthority> authorities = defaultConverter.convert(jwt);

        Collection<GrantedAuthority> roleAuthorities = extractResourceRoles(jwt);

        Set<GrantedAuthority> combinedAuthorities = Stream.concat(
                authorities.stream(),
                roleAuthorities.stream()
        ).collect(Collectors.toSet());

        return new JwtAuthenticationToken(jwt, combinedAuthorities, jwt.getClaimAsString("preferred_username"));
    }

    /**
     * Trích xuất realm roles từ JWT token và chuyển đổi thành GrantedAuthority.
     * 
     * <p>Phương thức private helper này trích xuất roles từ claim "realm_access.roles"
     * trong JWT token do Keycloak cấp phát. Mỗi role được chuyển đổi thành
     * GrantedAuthority với prefix "ROLE_" để tương thích với Spring Security convention.</p>
     * 
     * <p><b>Cấu trúc JWT claim:</b></p>
     * <pre>
     * {@code
     * "realm_access": {
     *   "roles": ["admin", "user", "manager", "viewer"]
     * }
     * }
     * </pre>
     * 
     * <p><b>Quy trình chi tiết:</b></p>
     * <ol>
     *   <li>Kiểm tra nếu "realm_access" claim tồn tại</li>
     *   <li>Nếu null, trả về empty Set (không có roles)</li>
     *   <li>Lấy map "realm_access" từ JWT claim</li>
     *   <li>Trích xuất Collection<String> "roles" từ map</li>
     *   <li>Stream qua tất cả roles</li>
     *   <li>Chuyển đổi mỗi role thành SimpleGrantedAuthority("ROLE_" + role)</li>
     *   <li>Thu thập vào Set<GrantedAuthority></li>
     *   <li>Trả về tập hợp authorities</li>
     * </ol>
     * 
     * <p><b>Ví dụ chuyển đổi:</b></p>
     * <pre>
     * {@code
     * Input roles: ["admin", "user", "manager"]
     * Output authorities:
     * - SimpleGrantedAuthority("ROLE_admin")
     * - SimpleGrantedAuthority("ROLE_user")
     * - SimpleGrantedAuthority("ROLE_manager")
     * }
     * </pre>
     * 
     * <p><b>Xử lý các trường hợp đặc biệt:</b></p>
     * <ul>
     *   <li>Nếu "realm_access" claim không tồn tại → trả về empty Set</li>
     *   <li>Nếu "roles" không tồn tại hoặc null → NullPointerException (lỗi)</li>
     *   <li>Nếu roles là empty list → trả về empty Set</li>
     *   <li>Nếu role name là empty string → vẫn tạo "ROLE_" (có thể muốn validate)</li>
     * </ul>
     * 
     * <p><b>Spring Security Convention:</b></p>
     * <ul>
     *   <li>Tất cả roles phải bắt đầu bằng "ROLE_" prefix</li>
     *   <li>Điều này cho phép sử dụng @PreAuthorize("hasRole('admin')") annotations</li>
     *   <li>Spring tự động xử lý prefix "ROLE_" khi kiểm tra authorization</li>
     * </ul>
     * 
     * <p><b>Ghi chú quan trọng:</b></p>
     * <ul>
     *   <li>Phương thức là private, chỉ được sử dụng nội bộ bởi convert()</li>
     *   <li>@SuppressWarnings("unchecked") được sử dụng vì Cast Collection->Collection<String></li>
     *   <li>Sử dụng Set để đảm bảo không có roles trùng lặp</li>
     *   <li>Nếu "realm_access.roles" không phải Collection<String>, sẽ throw ClassCastException</li>
     *   <li>Khuyến khích validate role names để tránh "ROLE_" trống</li>
     * </ul>
     * 
     * <p><b>Khuyến cáo cải tiến:</b></p>
     * <pre>
     * {@code
     * // Thêm validation để tránh empty role names
     * return roles.stream()
     *         .filter(role -> role != null && !role.isBlank())
     *         .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
     *         .collect(Collectors.toSet());
     * }
     * </pre>
     * 
     * @param jwt JWT token từ Keycloak chứa "realm_access" claim.
     *            Token có thể không có "realm_access" claim (tùy chọn).
     * 
     * @return {@link Collection<GrantedAuthority>} chứa tất cả realm roles
     *         được chuyển đổi thành GrantedAuthority format.
     *         Nếu không có "realm_access" claim, trả về empty Set.
     *         Nếu không có roles, trả về empty Set.
     * 
     * @throws ClassCastException nếu "realm_access.roles" không phải Collection<String>
     * @throws NullPointerException nếu "realm_access" tồn tại nhưng không có "roles" key
     * 
     * @see SimpleGrantedAuthority
     * @see Collectors#toSet()
     */
    @SuppressWarnings("unchecked")
    private Collection<GrantedAuthority> extractResourceRoles(Jwt jwt) {
        Map<String, Object> realmAccess;
        Collection<String> roles;

        if (jwt.getClaim("realm_access") == null) {
            return Set.of();
        }
        
        realmAccess = jwt.getClaim("realm_access");
        roles = (Collection<String>) realmAccess.get("roles");

        return roles.stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .collect(Collectors.toSet());
    }
}