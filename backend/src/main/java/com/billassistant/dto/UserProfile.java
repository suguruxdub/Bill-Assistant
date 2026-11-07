package com.billassistant.dto;

import com.billassistant.model.UserEntity;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** Public user fields; password hashes and session tokens must never be returned here. */
public record UserProfile(
        long id, String username, String nickname, String email, String phone,
        String avatarUrl, String gender, LocalDate birthday, LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static UserProfile from(UserEntity user) {
        return new UserProfile(user.getId(), user.getUsername(), user.getNickname(),
                user.getEmail(), user.getPhone(), user.getAvatarUrl(), user.getGender(),
                user.getBirthday(), user.getCreatedAt(), user.getUpdatedAt());
    }
}
