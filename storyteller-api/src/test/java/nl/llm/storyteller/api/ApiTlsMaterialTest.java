package nl.llm.storyteller.api;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApiTlsMaterialTest {
  @TempDir
  Path temporaryDirectory;

  @Test
  @DisplayName("""
    Given TLS is enabled without existing certificate material,
    When the TLS material is prepared twice,
    Then it should create and reuse a long-lived CA and an Apple-compatible server certificate
    """)
  void shouldCreateAndReuseTlsMaterial() throws Exception {
    ApiTlsConfig config = new ApiTlsConfig(true, 7443, temporaryDirectory, List.of("storyteller.home.arpa"));

    ApiTlsMaterial first = ApiTlsMaterial.prepare(config);
    byte[] firstKeyStore = Files.readAllBytes(first.keyStore());
    ApiTlsMaterial second = ApiTlsMaterial.prepare(config);

    X509Certificate authority = loadCertificate(first.caCertificate());
    X509Certificate server = loadServerCertificate(second.keyStore());
    assertTrue(authority.getBasicConstraints() >= 0);
    assertTrue(Duration.between(Instant.now(), authority.getNotAfter().toInstant()).toDays() > 3_600);
    assertTrue(Duration.between(Instant.now(), server.getNotAfter().toInstant()).toDays() <= 824);
    assertTrue(Duration.between(Instant.now(), server.getNotAfter().toInstant()).toDays() > 800);
    assertTrue(server.getSubjectAlternativeNames().stream()
      .anyMatch(name -> "storyteller.home.arpa".equals(name.get(1))));
    assertArrayEquals(firstKeyStore, Files.readAllBytes(second.keyStore()));
  }

  private static X509Certificate loadCertificate(Path path) throws Exception {
    try (InputStream input = Files.newInputStream(path)) {
      return (X509Certificate) CertificateFactory.getInstance("X.509").generateCertificate(input);
    }
  }

  private static X509Certificate loadServerCertificate(Path path) throws Exception {
    KeyStore store = KeyStore.getInstance("PKCS12");
    try (InputStream input = Files.newInputStream(path)) {
      store.load(input, new char[0]);
    }
    return (X509Certificate) store.getCertificate("storyteller");
  }
}
