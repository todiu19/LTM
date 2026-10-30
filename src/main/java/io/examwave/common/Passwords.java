package io.examwave.common;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

public final class Passwords {
    private static final int ROUNDS = 210_000;
    private static final int KEY_BITS = 256;
    private static final SecureRandom RANDOM = new SecureRandom();

    private Passwords() {}

    public static String hash(String password) {
        validate(password);
        byte[] salt = new byte[16];
        RANDOM.nextBytes(salt);
        return ROUNDS + ":" + Base64.getEncoder().encodeToString(salt) + ":"
                + Base64.getEncoder().encodeToString(derive(password, salt, ROUNDS));
    }

    public static boolean matches(String password, String encoded) {
        try {
            String[] parts = encoded.split(":", 3);
            int rounds = Integer.parseInt(parts[0]);
            byte[] salt = Base64.getDecoder().decode(parts[1]);
            byte[] expected = Base64.getDecoder().decode(parts[2]);
            return MessageDigest.isEqual(expected, derive(password, salt, rounds));
        } catch (Exception ignored) {
            return false;
        }
    }

    public static void validate(String password) {
        if (password == null || password.length() < 8 || password.length() > 128)
            throw new IllegalArgumentException("Mật khẩu phải có từ 8 đến 128 ký tự.");
    }

    private static byte[] derive(String password, byte[] salt, int rounds) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, rounds, KEY_BITS);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (Exception failure) {
            throw new IllegalStateException("Không thể bảo vệ mật khẩu.", failure);
        }
    }
}
