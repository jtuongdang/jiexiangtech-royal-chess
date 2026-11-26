package com.jiexiangtech.chess.infrastructure;

import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import com.jiexiangtech.chess.application.Passwords;

public final class Pbkdf2Passwords implements Passwords {
    private static final int ITERATIONS = 600_000;
    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private final SecureRandom random = new SecureRandom();

    @Override public String hash(char[] password) {
        byte[] salt = new byte[16];
        random.nextBytes(salt);
        var codec = Base64.getEncoder();
        return ALGORITHM + "$" + ITERATIONS + "$" + codec.encodeToString(salt) + "$"
                + codec.encodeToString(derive(password, salt, ITERATIONS));
    }

    @Override public boolean matches(char[] password, String encoded) {
        String[] fields = encoded.split("\\$", -1);
        if (fields.length != 4 || !ALGORITHM.equals(fields[0])) return false;
        try {
            int iterations = Integer.parseInt(fields[1]);
            if (iterations < 100_000 || iterations > 2_000_000) return false;
            var codec = Base64.getDecoder();
            byte[] salt = codec.decode(fields[2]);
            byte[] expected = codec.decode(fields[3]);
            return salt.length >= 16 && expected.length == 32
                    && MessageDigest.isEqual(expected, derive(password, salt, iterations));
        } catch (IllegalArgumentException malformed) {
            return false;
        }
    }

    private static byte[] derive(char[] password, byte[] salt, int iterations) {
        var spec = new PBEKeySpec(password, salt, iterations, 256);
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2 is unavailable", e);
        } finally {
            spec.clearPassword();
        }
    }
}
