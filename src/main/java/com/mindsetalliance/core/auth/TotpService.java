package com.mindsetalliance.core.auth;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.security.GeneralSecurityException;
import java.time.Instant;
import java.util.Base64;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public final class TotpService {

    private TotpService() {
    }

    public static boolean verify(String base32Secret, String code, boolean demoMode, String demoCode) {
        if (demoMode && demoCode != null && demoCode.equals(code)) {
            return true;
        }
        if (base32Secret == null || code == null || !code.matches("\\d{6}")) {
            return false;
        }
        long timestep = Instant.now().getEpochSecond() / 30;
        for (long drift = -1; drift <= 1; drift++) {
            if (code.equals(generate(base32Secret, timestep + drift))) {
                return true;
            }
        }
        return false;
    }

    public static String generate(String base32Secret, long timestep) {
        try {
            byte[] key = decodeBase32(base32Secret);
            byte[] data = ByteBuffer.allocate(8).order(ByteOrder.BIG_ENDIAN).putLong(timestep).array();
            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(key, "HmacSHA1"));
            byte[] hash = mac.doFinal(data);
            int offset = hash[hash.length - 1] & 0x0F;
            int binary = ((hash[offset] & 0x7F) << 24)
                    | ((hash[offset + 1] & 0xFF) << 16)
                    | ((hash[offset + 2] & 0xFF) << 8)
                    | (hash[offset + 3] & 0xFF);
            return String.format("%06d", binary % 1_000_000);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static byte[] decodeBase32(String secret) {
        String alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
        String normalized = secret.replace(" ", "").replace("=", "").toUpperCase();
        ByteBuffer buffer = ByteBuffer.allocate((normalized.length() * 5 + 7) / 8);
        int bufferBits = 0;
        int bufferValue = 0;
        for (char c : normalized.toCharArray()) {
            int idx = alphabet.indexOf(c);
            if (idx < 0) {
                continue;
            }
            bufferValue = (bufferValue << 5) | idx;
            bufferBits += 5;
            if (bufferBits >= 8) {
                buffer.put((byte) ((bufferValue >> (bufferBits - 8)) & 0xFF));
                bufferBits -= 8;
            }
        }
        byte[] out = new byte[buffer.position()];
        buffer.rewind();
        buffer.get(out);
        return out;
    }

    public static String randomSecret() {
        byte[] bytes = new byte[20];
        new java.security.SecureRandom().nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }
}
