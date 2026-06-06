package com.scenic.ai.modules.app.user.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Component
public class JwtUtil {

    private static final long EXPIRE_MS = 7L * 24 * 60 * 60 * 1000;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${app.jwt.secret:scenic-ai-app-user-secret}")
    private String secret;

    public String generateToken(String userId) {
        try {
            Map<String, Object> header = new HashMap<>();
            header.put("alg", "HS256");
            header.put("typ", "JWT");

            Map<String, Object> payload = new HashMap<>();
            payload.put("user_id", userId);
            payload.put("exp", System.currentTimeMillis() + EXPIRE_MS);

            String headerText = base64Url(objectMapper.writeValueAsBytes(header));
            String payloadText = base64Url(objectMapper.writeValueAsBytes(payload));
            String unsignedToken = headerText + "." + payloadText;
            String signature = sign(unsignedToken);

            return unsignedToken + "." + signature;
        } catch (Exception e) {
            throw new RuntimeException("生成 token 失败", e);
        }
    }

    public String parseUserId(String token) {
        try {
            if (token == null || token.trim().isEmpty()) {
                return null;
            }

            String realToken = token.trim();
            String lowerToken = realToken.toLowerCase();
            if (lowerToken.startsWith("bearer ")) {
                realToken = realToken.substring(7).trim();
            }
            if ((realToken.startsWith("\"") && realToken.endsWith("\""))
                    || (realToken.startsWith("'") && realToken.endsWith("'"))) {
                realToken = realToken.substring(1, realToken.length() - 1).trim();
            }

            String[] parts = realToken.split("\\.");
            if (parts.length != 3) {
                return null;
            }

            String unsignedToken = parts[0] + "." + parts[1];
            String expectedSign = sign(unsignedToken);

            if (!MessageDigest.isEqual(
                    expectedSign.getBytes(StandardCharsets.UTF_8),
                    parts[2].getBytes(StandardCharsets.UTF_8)
            )) {
                return null;
            }

            byte[] payloadBytes = Base64.getUrlDecoder().decode(parts[1]);
            Map<String, Object> payload = objectMapper.readValue(
                    payloadBytes,
                    new TypeReference<Map<String, Object>>() {}
            );

            Object expObj = payload.get("exp");
            if (expObj instanceof Number) {
                long exp = ((Number) expObj).longValue();
                if (exp > 0 && exp < 10_000_000_000L) {
                    exp = exp * 1000;
                }
                if (System.currentTimeMillis() > exp) {
                    return null;
                }
            }

            Object userId = firstNonNull(
                    payload.get("user_id"),
                    payload.get("userId"),
                    payload.get("sub")
            );
            return userId == null ? null : String.valueOf(userId);
        } catch (Exception e) {
            return null;
        }
    }

    private Object firstNonNull(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String sign(String data) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        SecretKeySpec keySpec = new SecretKeySpec(
                secret.getBytes(StandardCharsets.UTF_8),
                "HmacSHA256"
        );
        mac.init(keySpec);
        return base64Url(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
    }

    private String base64Url(byte[] bytes) {
        return Base64.getUrlEncoder()
                .withoutPadding()
                .encodeToString(bytes);
    }
}
