package nl.llm.storyteller.db;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;

/** Encryption of database values only; callers continue to work with plaintext. */
public final class DatabaseEncryption {
  public static final String KEY_CONFIGURATION_PROPERTY = "database.encryption.key";
  private static final String TEXT_PREFIX = "stenc:v1:";
  private static final byte[] BINARY_PREFIX = {'S', 'T', 'E', 'N', 'C', 1};
  private static final int NONCE_BYTES = 12;
  private final SecretKeySpec key;
  private final SecureRandom random = new SecureRandom();

  private DatabaseEncryption(byte[] key) {
    this.key = key == null ? null : new SecretKeySpec(key, "AES");
  }

  public static DatabaseEncryption fromBase64(String value) {
    if (value == null || value.isBlank()) {
      return new DatabaseEncryption(null);
    }
    byte[] bytes;
    try {
      bytes = Base64.getDecoder().decode(value.trim());
    } catch (IllegalArgumentException ex) {
      throw new IllegalArgumentException(KEY_CONFIGURATION_PROPERTY + " must be a Base64-encoded 32-byte key.");
    }
    if (bytes.length != 32) {
      throw new IllegalArgumentException(KEY_CONFIGURATION_PROPERTY + " must be a Base64-encoded 32-byte key.");
    }
    try {
      return new DatabaseEncryption(bytes);
    } finally {
      Arrays.fill(bytes, (byte) 0);
    }
  }

  public boolean enabled() {
    return key != null;
  }

  String encrypt(String sessionId, String field, String value) {
    if (value == null || !enabled()) {
      return value;
    }
    return TEXT_PREFIX + Base64.getEncoder().encodeToString(encrypt(sessionId, field,
      value.getBytes(StandardCharsets.UTF_8)));
  }

  String decrypt(String sessionId, String field, String value) {
    if (value == null) {
      return null;
    }
    if (!enabled()) {
      return value;
    }
    if (!value.startsWith(TEXT_PREFIX)) {
      throw failure();
    }
    try {
      return new String(decrypt(sessionId, field, Base64.getDecoder().decode(value.substring(TEXT_PREFIX.length()))),
        StandardCharsets.UTF_8);
    } catch (IllegalArgumentException ex) {
      throw failure();
    }
  }

  byte[] encrypt(String sessionId, String field, byte[] value) {
    if (value == null || !enabled()) {
      return value;
    }
    byte[] nonce = new byte[NONCE_BYTES];
    random.nextBytes(nonce);
    try {
      Cipher cipher = cipher(Cipher.ENCRYPT_MODE, nonce, sessionId, field);
      byte[] encrypted = cipher.doFinal(value);
      return ByteBuffer.allocate(BINARY_PREFIX.length + nonce.length + encrypted.length)
        .put(BINARY_PREFIX).put(nonce).put(encrypted).array();
    } catch (GeneralSecurityException ex) {
      throw new DatabaseException("Could not encrypt database content.", ex);
    }
  }

  byte[] decrypt(String sessionId, String field, byte[] value) {
    if (value == null) {
      return null;
    }
    boolean encrypted = value.length >= BINARY_PREFIX.length
      && Arrays.equals(BINARY_PREFIX, Arrays.copyOf(value, BINARY_PREFIX.length));
    if (!enabled()) {
      return value;
    }
    if (!encrypted || value.length < BINARY_PREFIX.length + NONCE_BYTES + 16) {
      throw failure();
    }
    try {
      byte[] nonce = Arrays.copyOfRange(value, BINARY_PREFIX.length, BINARY_PREFIX.length + NONCE_BYTES);
      return cipher(Cipher.DECRYPT_MODE, nonce, sessionId, field)
        .doFinal(value, BINARY_PREFIX.length + NONCE_BYTES, value.length - BINARY_PREFIX.length - NONCE_BYTES);
    } catch (GeneralSecurityException ex) {
      throw failure();
    }
  }

  private Cipher cipher(int mode, byte[] nonce, String sessionId, String field) throws GeneralSecurityException {
    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
    cipher.init(mode, key, new GCMParameterSpec(128, nonce));
    cipher.updateAAD((sessionId + "\u0000" + field).getBytes(StandardCharsets.UTF_8));
    return cipher;
  }

  private static DatabaseException failure() {
    return new DatabaseException("Could not decrypt database content: missing/wrong key or damaged content.", null);
  }

  @Override
  public String toString() {
    return "DatabaseEncryption[enabled=" + enabled() + "]";
  }
}
