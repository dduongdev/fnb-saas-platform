package com.project.fnb.modules.global.service;

import com.project.fnb.modules.global.dto.UserResponse;
import com.project.fnb.modules.global.entity.User;
import com.project.fnb.modules.global.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Dịch vụ quản lý thông tin người dùng trong hệ thống.
 * 
 * <p>Class này cung cấp các chức năng liên quan đến xử lý dữ liệu người dùng,
 * bao gồm đồng bộ thông tin người dùng từ JWT token (thường được cấp bởi OAuth2/Keycloak).
 * Dịch vụ tự động tạo mới người dùng nếu chưa tồn tại hoặc cập nhật thông tin
 * nếu người dùng đã tồn tại trong hệ thống.</p>
 * 
 * <p><b>Tính năng chính:</b></p>
 * <ul>
 *   <li>Đồng bộ dữ liệu người dùng từ JWT token OAuth2</li>
 *   <li>Tự động tạo người dùng mới với trustScore mặc định</li>
 *   <li>Cập nhật thông tin email và họ tên từ token</li>
 *   <li>Trả về thông tin người dùng dưới dạng DTO</li>
 * </ul>
 * 
 * <p><b>Ghi chú về trustScore:</b><br>
 * trustScore được đặt mặc định là 100 cho người dùng mới. Đây có thể là một chỉ số
 * về độ tin cậy hoặc danh tiếng của người dùng trong hệ thống.</p>
 * 
 * @author Project Team
 * @version 1.0
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    /**
     * Đồng bộ thông tin người dùng từ JWT token OAuth2/Keycloak.
     * 
     * <p>Phương thức này thực hiện các bước sau:</p>
     * <ol>
     *   <li>Trích xuất thông tin từ JWT token (userId, email, fullName, preferred_username)</li>
     *   <li>Tìm kiếm người dùng trong database theo userId</li>
     *   <li>Nếu không tìm thấy, tạo mới người dùng với trustScore = 100</li>
     *   <li>Cập nhật email và họ tên (dùng fullName nếu có, ngược lại dùng preferred_username)</li>
     *   <li>Lưu người dùng vào database</li>
     *   <li>Trả về thông tin người dùng dưới dạng UserResponse</li>
     * </ol>
     * 
     * <p><b>Ghi chú:</b></p>
     * <ul>
     *   <li>Phương thức này được gọi thường xuyên để đảm bảo thông tin người dùng luôn cập nhật</li>
     *   <li>Nếu fullName trong token bị null, sẽ sử dụng preferred_username làm tên đầy đủ</li>
     *   <li>Được bảo vệ bằng @Transactional để đảm bảo tính nhất quán dữ liệu</li>
     * </ul>
     * 
     * @param jwt JWT token từ OAuth2/Keycloak provider, phải chứa các claims:
     *            <ul>
     *              <li>subject (sub): userId - định danh duy nhất của người dùng</li>
     *              <li>email: địa chỉ email người dùng</li>
     *              <li>name: họ tên đầy đủ của người dùng (có thể null)</li>
     *              <li>preferred_username: tên người dùng ưu tiên (dùng khi name là null)</li>
     *            </ul>
     * 
     * @return {@link UserResponse} chứa thông tin người dùng sau khi được đồng bộ,
     *         bao gồm: id, email, fullName, avatarUrl, trustScore, phone
     * 
     * @throws org.springframework.security.core.AuthenticationException
     *         nếu JWT token không hợp lệ hoặc thiếu các claims cần thiết
     */
    @Transactional
    public UserResponse syncUserFromToken(Jwt jwt) {
        String userId = jwt.getSubject(); 
        String email = jwt.getClaimAsString("email");
        String fullName = jwt.getClaimAsString("name"); 
        String preferredUsername = jwt.getClaimAsString("preferred_username");

        User user = userRepository.findById(userId)
                .orElseGet(() -> User.builder()
                        .id(userId)
                        .trustScore(100) 
                        .build());

        user.setEmail(email);
        user.setFullName(fullName != null ? fullName : preferredUsername);
        
        
        User savedUser = userRepository.save(user);

        return UserResponse.builder()
                .id(savedUser.getId())
                .email(savedUser.getEmail())
                .fullName(savedUser.getFullName())
                .avatarUrl(savedUser.getAvatarUrl())
                .trustScore(savedUser.getTrustScore())
                .phone(savedUser.getPhone())
                .build();
    }
}