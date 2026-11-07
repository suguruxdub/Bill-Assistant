package com.billassistant.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record RegisterRequest(
        @NotBlank @Pattern(regexp = "[a-zA-Z0-9][a-zA-Z0-9_]{2,31}",
                message = "用户名需为 3～32 位字母、数字或下划线，且不能以下划线开头") String username,
        @NotBlank @Size(min = 8, max = 72, message = "密码需为 8～72 个字符，且不超过 72 个 UTF-8 字节") String password,
        @Size(max = 64) String nickname,
        @Email @Size(max = 254) String email,
        @Pattern(regexp = "[+0-9() -]{6,32}", message = "请输入有效的联系电话") String phone,
        @Pattern(regexp = "UNSPECIFIED|MALE|FEMALE|OTHER") String gender,
        @PastOrPresent LocalDate birthday
) {
}
