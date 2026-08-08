package com.sentinelgateway.gateway.signing;

import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Computes and verifies HMAC-SHA256 request signatures.
 *
 * Canonical message format:
 *   {METHOD}\n{path}\n{timestamp}\n{nonce}\n{bodyHash}
 *
 * Body hash: SHA-256 hex digest of the raw request body.
 * Empty body: SHA-256 of empty string.
 *
 * Signature: HMAC-SHA256(sharedSecret, canonicalMessage) as lowercase hex.
 */
@Component
public class HmacSigner {

    private static final String HMAC_ALGORITHM = "HmacSHA256";

    /**
     * Computes the HMAC-SHA256 signature for the given request parameters.
     */
    public String sign(String secret, String method, String path,
                       String timestamp, String nonce, byte[] body) {
        String bodyHash = sha256Hex(body);
        String canonical = method.toUpperCase() + "\n"
                + path + "\n"
                + timestamp + "\n"
                + nonce + "\n"
                + bodyHash;
        return hmacHex(secret, canonical);
    }

    /**
     * Constant-time signature comparison to prevent timing attacks.
     */
    public boolean verify(String expected, String actual) {
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                actual.getBytes(StandardCharsets.UTF_8));
    }

    public static String sha256Hex(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(data != null ? data : new byte[0]));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String hmacHex(String secret, String message) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGORITHM));
            return HexFormat.of().formatHex(mac.doFinal(message.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }
}
