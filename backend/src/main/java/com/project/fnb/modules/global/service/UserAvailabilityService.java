package com.project.fnb.modules.global.service;

import com.project.fnb.common.exception.AppException;
import com.project.fnb.modules.global.dto.AvailabilityDto;
import com.project.fnb.modules.global.entity.User;
import com.project.fnb.modules.global.entity.UserAvailability;
import com.project.fnb.modules.global.repository.UserAvailabilityRepository;
import com.project.fnb.modules.global.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserAvailabilityService {

    private final UserAvailabilityRepository availabilityRepository;
    private final UserRepository userRepository;

    @Transactional
    public List<AvailabilityDto> updateAvailability(String userId, List<AvailabilityDto> requests) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AppException(404, "User not found"));

        // 1. Xóa lịch cũ
        availabilityRepository.deleteByUserId(userId);

        // 2. Thêm lịch mới
        List<UserAvailability> entities = requests.stream()
                .map(dto -> UserAvailability.builder()
                        .user(user)
                        .dayOfWeek(dto.getDayOfWeek())
                        .startTime(dto.getStartTime())
                        .endTime(dto.getEndTime())
                        .build())
                .collect(Collectors.toList());
        
        availabilityRepository.saveAll(entities);

        return requests;
    }

    public List<AvailabilityDto> getAvailability(String userId) {
        return availabilityRepository.findByUserId(userId).stream()
                .map(e -> AvailabilityDto.builder()
                        .dayOfWeek(e.getDayOfWeek())
                        .startTime(e.getStartTime())
                        .endTime(e.getEndTime())
                        .build())
                .collect(Collectors.toList());
    }
}