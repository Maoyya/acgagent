package com.darkness.auth.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TokenVO {
    private String accessToken;
    private String refreshToken;
    private Long expiresIn;
}
