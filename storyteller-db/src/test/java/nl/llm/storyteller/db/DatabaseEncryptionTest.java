package nl.llm.storyteller.db;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseEncryptionTest {
  static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

  @Test
  @DisplayName("""
    Given a configured database encryption key and Unicode content,
    When the same content is encrypted twice,
    Then both values should decrypt correctly and use different nonces
    """)
  void encryptsUnicodeAndUsesDifferentNonces() {
    var encryption = DatabaseEncryption.fromBase64(KEY);
    String original = "Verhaal met 🦉 en café";
    String first = encryption.encrypt("session", "field", original);
    String second = encryption.encrypt("session", "field", original);
    assertNotEquals(first, second);
    assertFalse(first.contains(original));
    assertEquals(original, encryption.decrypt("session", "field", first));
    assertNull(encryption.encrypt("session", "field", (String) null));
    assertNull(encryption.decrypt("session", "field", (String) null));
    assertFalse(encryption.toString().contains(KEY));
  }

  @Test
  @DisplayName("""
    Given authenticated encrypted database content,
    When its key, content, session, or field context is incorrect,
    Then decryption should fail without returning plaintext
    """)
  void rejectsWrongKeysTamperingAndWrongContext() {
    var encryption = DatabaseEncryption.fromBase64(KEY);
    byte[] encrypted = encryption.encrypt("session", "image", new byte[]{1, 2, 3});
    assertArrayEquals(new byte[]{1, 2, 3}, encryption.decrypt("session", "image", encrypted));
    assertThrows(DatabaseException.class, () -> encryption.decrypt("other-session", "image", encrypted));
    assertThrows(DatabaseException.class, () -> encryption.decrypt("session", "other-field", encrypted));
    byte[] otherKey = new byte[32];
    otherKey[0] = 1;
    var other = DatabaseEncryption.fromBase64(Base64.getEncoder().encodeToString(otherKey));
    assertThrows(DatabaseException.class, () -> other.decrypt("session", "image", encrypted));
    encrypted[encrypted.length - 1] ^= 1;
    assertThrows(DatabaseException.class, () -> encryption.decrypt("session", "image", encrypted));
    assertThrows(DatabaseException.class, () -> encryption.decrypt("session", "field", "plaintext"));
    assertThrows(DatabaseException.class, () -> encryption.decrypt("session", "field", "stenc:v1:invalid"));
  }

  @Test
  @DisplayName("""
    Given invalid or absent database encryption configuration,
    When the key is validated or legacy content is processed,
    Then invalid keys should be rejected without disclosure and disabled encryption should preserve content
    """)
  void validatesKeysWithoutDisclosingThemAndPreservesDisabledBehavior() {
    String secret = "invalid-secret";
    var failure = assertThrows(IllegalArgumentException.class, () -> DatabaseEncryption.fromBase64(secret));
    assertFalse(failure.getMessage().contains(secret));
    assertThrows(IllegalArgumentException.class, () -> DatabaseEncryption.fromBase64("AAAA"));
    var disabled = DatabaseEncryption.fromBase64("");
    assertFalse(disabled.enabled());
    assertEquals("stenc:v1:plain text", disabled.decrypt("session", "field", "stenc:v1:plain text"));
    assertEquals("plain", disabled.encrypt("session", "field", "plain"));
  }
}
