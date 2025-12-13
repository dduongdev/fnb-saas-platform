package com.project.fnb.infrastructure.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.representations.idm.GroupRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.ws.rs.core.Response;

/**
 * Service quản lý Identity (Keycloak) cho hệ thống FnB SaaS.
 * 
 * <p>Class này cung cấp các business logic để tương tác với Keycloak Admin API,
 * bao gồm quản lý groups, realms, và các tài nguyên liên quan đến authentication/authorization.</p>
 * 
 * <p><b>Các tính năng chính:</b></p>
 * <ul>
 *   <li>Tạo Keycloak group cho mỗi tenant (để quản lý role/permission)</li>
 *   <li>Quản lý realm (từ OAuth2 issuer URI)</li>
 *   <li>Tương tác với Keycloak Admin API thông qua Keycloak client</li>
 * </ul>
 * 
 * <p><b>Group Naming Convention:</b></p>
 * <ul>
 *   <li>Format: "tenant_{tenantId}_staff"</li>
 *   <li>Ví dụ: "tenant_550e8400-e29b-41d4-a716-446655440000_staff"</li>
 *   <li>Dùng để isolate permissions của mỗi tenant</li>
 * </ul>
 * 
 * <p><b>Configuration:</b></p>
 * <ul>
 *   <li>Keycloak client được inject (Spring Security OAuth2 configuration)</li>
 *   <li>issuerUri được inject từ application.yml (spring.security.oauth2.resourceserver.jwt.issuer-uri)</li>
 *   <li>Realm name được extract từ issuer URI (ví dụ: issuer-uri = http://keycloak:8080/auth/realms/fnb → realm = fnb)</li>
 * </ul>
 * 
 * <p><b>Keycloak Integration:</b></p>
 * <ul>
 *   <li>Keycloak Admin API được sử dụng để quản lý groups</li>
 *   <li>Groups được dùng để map roles/permissions cho users</li>
 *   <li>Mỗi tenant có group riêng để isolate quyền hạn</li>
 * </ul>
 * 
 * <p><b>Response Handling:</b></p>
 * <ul>
 *   <li>201: Group được tạo thành công</li>
 *   <li>409: Group đã tồn tại (conflict)</li>
 *   <li>Khác: Error - log error message</li>
 *   <li>Response luôn được close() để tránh resource leak</li>
 * </ul>
 * 
 * @author Project Team
 * @version 1.0
 * @see Keycloak
 * @see RealmResource
 * @see GroupRepresentation
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IdentityService {

    private final Keycloak keycloak;

    @Value("${spring.security.oauth2.resourceserver.jwt.issuer-uri}")
    private String issuerUri;

    /**
     * Lấy RealmResource từ Keycloak dựa trên issuer URI.
     * 
     * <p>Phương thức này extract realm name từ OAuth2 issuer URI,
     * sau đó sử dụng Keycloak admin client để lấy RealmResource object.
     * RealmResource được dùng để quản lý các tài nguyên trong realm (groups, users, roles, v.v.).</p>
     * 
     * <p><b>URI Parsing Logic:</b></p>
     * <ul>
     *   <li>Ví dụ issuer URI: http://keycloak:8080/auth/realms/fnb</li>
     *   <li>String được split bằng "/" delimiter</li>
     *   <li>Lấy phần tử cuối cùng (substring sau lastIndexOf("/"))</li>
     *   <li>Kết quả realm name: "fnb"</li>
     * </ul>
     * 
     * <p><b>Configuration Required:</b></p>
     * <ul>
     *   <li>spring.security.oauth2.resourceserver.jwt.issuer-uri phải được set trong application.yml</li>
     *   <li>Keycloak bean phải được configure trong SecurityConfig</li>
     * </ul>
     * 
     * <p><b>Cached vs Non-Cached:</b></p>
     * <ul>
     *   <li>Hiện tại phương thức không cache RealmResource</li>
     *   <li>Mỗi lần gọi đều extract realm name lại (có thể optimize với caching)</li>
     *   <li>Trong tương lai có thể thêm @Cacheable để improve performance</li>
     * </ul>
     * 
     * @return RealmResource object đại diện cho Keycloak realm (ví dụ: "fnb")
     * 
     * @throws RuntimeException nếu Keycloak client không thể kết nối hoặc issuer URI invalid
     * 
     * @see Keycloak#realm(String)
     * @see RealmResource
     */
    private RealmResource getRealm() {
        String realmName = issuerUri.substring(issuerUri.lastIndexOf("/") + 1);
        return keycloak.realm(realmName);
    }

    /**
     * Tạo Keycloak group cho một tenant để quản lý quyền hạn (roles/permissions).
     * 
     * <p>Phương thức này gọi Keycloak Admin API để tạo group mới.
     * Group được dùng để map roles/permissions cho users của tenant đó.
     * Mỗi tenant có group riêng để isolate quyền hạn giữa các tenant khác nhau.</p>
     * 
     * <p><b>Group Naming Convention:</b></p>
     * <ul>
     *   <li>Format: "tenant_{tenantId}_staff"</li>
     *   <li>Ví dụ: tenantId = "550e8400-e29b-41d4-a716" → group name = "tenant_550e8400-e29b-41d4-a716_staff"</li>
     *   <li>Naming convention giúp dễ identify group của tenant nào</li>
     *   <li>Suffix "_staff" để phân biệt với các group khác (có thể có group "customers" v.v.)</li>
     * </ul>
     * 
     * <p><b>Quy trình chi tiết:</b></p>
     * <ol>
     *   <li>Tạo groupName = "tenant_{tenantId}_staff"</li>
     *   <li>Tạo GroupRepresentation object</li>
     *   <li>Set group name vào GroupRepresentation</li>
     *   <li>Gọi Keycloak Admin API: getRealm().groups().add(group)</li>
     *   <li>Kiểm tra response status code</li>
     *   <li>Log kết quả (success, warning, hoặc error)</li>
     *   <li>Đóng response resource (trong mọi trường hợp)</li>
     * </ol>
     * 
     * <p><b>Response Status Handling:</b></p>
     * <ul>
     *   <li>201 Created: Group được tạo thành công → log info</li>
     *   <li>409 Conflict: Group đã tồn tại → log warning (có thể do retry request)</li>
     *   <li>Khác: Error → log error message với status code</li>
     *   <li>Response.close() được gọi để release HTTP resource (tránh resource leak)</li>
     * </ul>
     * 
     * <p><b>Usage Context:</b></p>
     * <ul>
     *   <li>Được gọi khi tạo tenant mới (trong TenantService.createTenant())</li>
     *   <li>Tạo infrastructure cho quản lý quyền hạn của tenant</li>
     *   <li>Group sau đó có thể được assign roles/permissions</li>
     * </ul>
     * 
     * <p><b>Error Handling:</b></p>
     * <ul>
     *   <li>Method không throw exception (tất cả errors được log)</li>
     *   <li>Nếu tạo group fail, tenant vẫn được tạo (not blocking)</li>
     *   <li>Trong tương lai có thể thêm retry logic hoặc queue mechanism</li>
     * </ul>
     * 
     * <p><b>Keycloak Admin API:</b></p>
     * <ul>
     *   <li>getRealm() → lấy RealmResource</li>
     *   <li>getRealm().groups() → lấy groups manager</li>
     *   <li>.add(group) → tạo group mới via REST API</li>
     *   <li>HTTP POST /admin/realms/{realm}/groups</li>
     * </ul>
     * 
     * @param tenantId Unique identifier của tenant (UUID, bắt buộc)
     *                 Ví dụ: "550e8400-e29b-41d4-a716-446655440000"
     * 
     * @throws IllegalArgumentException nếu tenantId là null/empty (implicit, validation nên ở caller)
     * @throws RuntimeException nếu Keycloak client không thể kết nối hoặc API error
     * 
     * @see GroupRepresentation
     * @see RealmResource#groups()
     * @see Keycloak#realm(String)
     * @see Response#getStatus()
     */
    public void createTenantGroup(String tenantId) {
        String groupName = "tenant_" + tenantId + "_staff";
        
        GroupRepresentation group = new GroupRepresentation();
        group.setName(groupName);
        
        Response response = getRealm().groups().add(group);
        
        if (response.getStatus() == 201) {
            log.info("Keycloak: Created group '{}'", groupName);
        } else if (response.getStatus() == 409) {
            log.warn("ℹKeycloak: Group '{}' already exists", groupName);
        } else {
            log.error("Keycloak: Failed to create group. Status: {}", response.getStatus());
        }
        response.close();
    }
}