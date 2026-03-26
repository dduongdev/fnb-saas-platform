package com.project.fnb.modules.global.service;

import com.project.fnb.common.exception.AppException;
import com.project.fnb.infrastructure.storage.StorageService;
import com.project.fnb.modules.global.dto.UserResponse;
import com.project.fnb.modules.global.entity.User;
import com.project.fnb.modules.global.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

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
    private final StorageService storageService;

    @Transactional
    public String updateAvatar(String userId, MultipartFile file) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(404, "User not found"));

        String avatarUrl = storageService.uploadUserProfileImage(file);

        if (user.getAvatarUrl() != null) storageService.deleteFile(user.getAvatarUrl());

        user.setAvatarUrl(avatarUrl);
        userRepository.save(user);

        return avatarUrl;
    }
}