package nl.llm.storyteller.api;

import nl.llm.storyteller.core.AtomicFileWriter;
import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.asn1.x509.BasicConstraints;
import org.bouncycastle.asn1.x509.ExtendedKeyUsage;
import org.bouncycastle.asn1.x509.Extension;
import org.bouncycastle.asn1.x509.GeneralName;
import org.bouncycastle.asn1.x509.GeneralNames;
import org.bouncycastle.asn1.x509.KeyPurposeId;
import org.bouncycastle.asn1.x509.KeyUsage;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

final class ApiTlsMaterial {
  private static final char[] STORE_PASSWORD = new char[0];
  private static final String STORE_TYPE = "PKCS12";
  private static final String CA_ALIAS = "storyteller-ca";
  private static final String SERVER_ALIAS = "storyteller";
  private static final String CA_STORE_NAME = "storyteller-ca.p12";
  private static final String SERVER_STORE_NAME = "storyteller.p12";
  private static final String CA_CERTIFICATE_NAME = "storyteller-ca.crt";
  private static final int RSA_KEY_SIZE = 2048;
  private static final long CA_VALIDITY_DAYS = 3_650;
  private static final long SERVER_VALIDITY_DAYS = 824;

  private final Path keyStore;
  private final Path caCertificate;

  private ApiTlsMaterial(Path keyStore, Path caCertificate) {
    this.keyStore = keyStore;
    this.caCertificate = caCertificate;
  }

  static synchronized ApiTlsMaterial prepare(ApiTlsConfig config) {
    Path directory = config.directory().toAbsolutePath().normalize();
    Path caStore = directory.resolve(CA_STORE_NAME);
    Path serverStore = directory.resolve(SERVER_STORE_NAME);
    Path caCertificate = directory.resolve(CA_CERTIFICATE_NAME);
    try {
      Files.createDirectories(directory);
      KeyStore authority = Files.exists(caStore)
        ? loadKeyStore(caStore)
        : createAuthority(caStore, caCertificate);
      if (!Files.exists(caCertificate)) {
        writeCertificate(caCertificate, (X509Certificate) authority.getCertificate(CA_ALIAS));
      }
      Set<String> names = subjectAlternativeNames(config.subjectAlternativeNames());
      if (!hasUsableServerCertificate(serverStore, names)) {
        createServerCertificate(authority, serverStore, names);
      }
      restrictPrivateFile(caStore);
      restrictPrivateFile(serverStore);
      return new ApiTlsMaterial(serverStore, caCertificate);
    } catch (IOException ex) {
      throw new UncheckedIOException("Could not prepare API TLS certificate material.", ex);
    } catch (GeneralSecurityException ex) {
      throw new IllegalStateException("Could not prepare API TLS certificate material.", ex);
    }
  }

  Path keyStore() {
    return keyStore;
  }

  Path caCertificate() {
    return caCertificate;
  }

  private static KeyStore createAuthority(Path storePath, Path certificatePath)
    throws GeneralSecurityException, IOException {
    KeyPair keyPair = generateKeyPair();
    X500Name name = new X500Name("CN=Storyteller Local CA");
    Instant now = Instant.now();
    JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
      name, serialNumber(), Date.from(now.minus(1, ChronoUnit.DAYS)),
      Date.from(now.plus(CA_VALIDITY_DAYS, ChronoUnit.DAYS)), name, keyPair.getPublic()
    );
    JcaX509ExtensionUtils extensions = new JcaX509ExtensionUtils();
    builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(true));
    builder.addExtension(Extension.keyUsage, true, new KeyUsage(KeyUsage.keyCertSign | KeyUsage.cRLSign));
    builder.addExtension(Extension.subjectKeyIdentifier, false, extensions.createSubjectKeyIdentifier(keyPair.getPublic()));
    X509Certificate certificate = sign(builder, keyPair.getPrivate());
    certificate.verify(keyPair.getPublic());

    KeyStore store = emptyKeyStore();
    store.setKeyEntry(CA_ALIAS, keyPair.getPrivate(), STORE_PASSWORD, new X509Certificate[]{certificate});
    writeKeyStore(storePath, store);
    writeCertificate(certificatePath, certificate);
    return store;
  }

  private static void createServerCertificate(KeyStore authority, Path storePath, Set<String> names)
    throws GeneralSecurityException, IOException {
    X509Certificate caCertificate = (X509Certificate) authority.getCertificate(CA_ALIAS);
    KeyPair keyPair = generateKeyPair();
    Instant now = Instant.now();
    JcaX509v3CertificateBuilder builder = new JcaX509v3CertificateBuilder(
      new X500Name(caCertificate.getSubjectX500Principal().getName()), serialNumber(),
      Date.from(now.minus(1, ChronoUnit.DAYS)), Date.from(now.plus(SERVER_VALIDITY_DAYS, ChronoUnit.DAYS)),
      new X500Name("CN=Storyteller"), keyPair.getPublic()
    );
    JcaX509ExtensionUtils extensions = new JcaX509ExtensionUtils();
    builder.addExtension(Extension.basicConstraints, true, new BasicConstraints(false));
    builder.addExtension(
      Extension.keyUsage, true, new KeyUsage(KeyUsage.digitalSignature | KeyUsage.keyEncipherment)
    );
    builder.addExtension(
      Extension.extendedKeyUsage, false, new ExtendedKeyUsage(KeyPurposeId.id_kp_serverAuth)
    );
    builder.addExtension(Extension.subjectAlternativeName, false, generalNames(names));
    builder.addExtension(Extension.subjectKeyIdentifier, false, extensions.createSubjectKeyIdentifier(keyPair.getPublic()));
    builder.addExtension(Extension.authorityKeyIdentifier, false, extensions.createAuthorityKeyIdentifier(caCertificate));
    X509Certificate certificate = sign(builder, (PrivateKey) authority.getKey(CA_ALIAS, STORE_PASSWORD));
    certificate.verify(caCertificate.getPublicKey());

    KeyStore store = emptyKeyStore();
    store.setKeyEntry(
      SERVER_ALIAS, keyPair.getPrivate(), STORE_PASSWORD, new X509Certificate[]{certificate, caCertificate}
    );
    writeKeyStore(storePath, store);
  }

  private static boolean hasUsableServerCertificate(Path storePath, Set<String> names) {
    if (!Files.exists(storePath)) {
      return false;
    }
    try {
      X509Certificate certificate = (X509Certificate) loadKeyStore(storePath).getCertificate(SERVER_ALIAS);
      certificate.checkValidity(Date.from(Instant.now().plus(30, ChronoUnit.DAYS)));
      return certificateNames(certificate).containsAll(names);
    } catch (GeneralSecurityException | IOException _) {
      return false;
    }
  }

  private static Set<String> certificateNames(X509Certificate certificate) throws GeneralSecurityException {
    Set<String> names = new LinkedHashSet<>();
    CollectionSupport.addSubjectAlternativeNames(certificate, names);
    return names.stream().map(ApiTlsMaterial::normalizeName).collect(java.util.stream.Collectors.toSet());
  }

  private static GeneralNames generalNames(Set<String> names) {
    List<GeneralName> values = names.stream()
      .map(name -> new GeneralName(isIpAddress(name) ? GeneralName.iPAddress : GeneralName.dNSName, name))
      .toList();
    return new GeneralNames(values.toArray(GeneralName[]::new));
  }

  private static Set<String> subjectAlternativeNames(List<String> configured) {
    Set<String> names = new LinkedHashSet<>();
    configured.stream().map(ApiTlsMaterial::normalizeName).forEach(names::add);
    names.add("localhost");
    names.add(normalizeName("127.0.0.1"));
    names.add(normalizeName("::1"));
    try {
      names.add(InetAddress.getLocalHost().getHostName());
      var interfaces = NetworkInterface.getNetworkInterfaces();
      List<NetworkInterface> ordered = new ArrayList<>(Collections.list(interfaces));
      ordered.sort(Comparator.comparing(NetworkInterface::getName));
      for (NetworkInterface networkInterface : ordered) {
        if (!networkInterface.isUp()) {
          continue;
        }
        networkInterface.getInetAddresses().asIterator().forEachRemaining(address ->
          names.add(address.getHostAddress().replaceFirst("%.*$", ""))
        );
      }
    } catch (IOException _) {
      // Explicit and loopback names still produce a usable local certificate.
    }
    return names;
  }

  private static String normalizeName(String value) {
    String name = value.replaceFirst("%.*$", "");
    if (!isIpAddress(name)) {
      return name;
    }
    try {
      return InetAddress.getByName(name).getHostAddress().replaceFirst("%.*$", "");
    } catch (IOException _) {
      return name;
    }
  }

  private static boolean isIpAddress(String value) {
    return value.contains(":") || value.matches("[0-9.]+");
  }

  private static KeyPair generateKeyPair() throws GeneralSecurityException {
    KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
    generator.initialize(RSA_KEY_SIZE);
    return generator.generateKeyPair();
  }

  private static X509Certificate sign(JcaX509v3CertificateBuilder builder, java.security.PrivateKey privateKey)
    throws GeneralSecurityException {
    try {
      return new JcaX509CertificateConverter().getCertificate(
        builder.build(new JcaContentSignerBuilder("SHA256withRSA").build(privateKey))
      );
    } catch (org.bouncycastle.operator.OperatorCreationException | java.security.cert.CertificateException ex) {
      throw new GeneralSecurityException(ex);
    }
  }

  private static BigInteger serialNumber() {
    return new BigInteger(160, new SecureRandom()).abs();
  }

  private static KeyStore emptyKeyStore() throws GeneralSecurityException, IOException {
    KeyStore store = KeyStore.getInstance(STORE_TYPE);
    store.load(null, STORE_PASSWORD);
    return store;
  }

  private static KeyStore loadKeyStore(Path path) throws GeneralSecurityException, IOException {
    KeyStore store = KeyStore.getInstance(STORE_TYPE);
    try (var input = Files.newInputStream(path)) {
      store.load(input, STORE_PASSWORD);
    }
    return store;
  }

  private static void writeKeyStore(Path path, KeyStore store) throws GeneralSecurityException, IOException {
    ByteArrayOutputStream output = new ByteArrayOutputStream();
    store.store(output, STORE_PASSWORD);
    AtomicFileWriter.write(path, output.toByteArray());
  }

  private static void writeCertificate(Path path, X509Certificate certificate) {
    AtomicFileWriter.write(path, temporaryPath -> {
      try (OutputStream output = Files.newOutputStream(temporaryPath)) {
        writePem(output, certificate);
      }
    });
  }

  private static void writePem(OutputStream output, X509Certificate certificate) throws IOException {
    try (JcaPEMWriter writer = new JcaPEMWriter(new OutputStreamWriter(output, StandardCharsets.US_ASCII))) {
      writer.writeObject(certificate);
    }
  }

  private static void restrictPrivateFile(Path path) {
    try {
      Files.setPosixFilePermissions(path, Set.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE));
    } catch (IOException | UnsupportedOperationException _) {
      // POSIX permissions are unavailable on some supported file systems, including Windows.
    }
  }

  private static final class CollectionSupport {
    private CollectionSupport() {
    }

    static void addSubjectAlternativeNames(X509Certificate certificate, Set<String> names)
      throws java.security.cert.CertificateParsingException {
      var alternativeNames = certificate.getSubjectAlternativeNames();
      if (alternativeNames == null) {
        return;
      }
      for (var alternativeName : alternativeNames) {
        if (alternativeName.size() >= 2 && alternativeName.get(1) instanceof String name) {
          names.add(name);
        }
      }
    }
  }
}
