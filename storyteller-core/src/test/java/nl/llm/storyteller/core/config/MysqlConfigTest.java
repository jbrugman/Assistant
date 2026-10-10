package nl.llm.storyteller.core.config;

import org.junit.jupiter.api.Test;
import java.nio.file.Path;
import java.nio.file.Files;
import java.util.Properties;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class MysqlConfigTest {
  @TempDir Path directory;

  @Test
  void defaultsToH2AndRejectsInvalidSwitch() {
    Properties properties = new Properties();
    assertFalse(MysqlConfig.from(properties).enabled());
    properties.setProperty("mysql", "yes");
    assertThrows(IllegalArgumentException.class, () -> MysqlConfig.from(properties));
  }

  @Test
  void loadsMysqlFromStorytellerConfiguration() throws Exception {
    Path file = directory.resolve("application.config");
    Files.writeString(file, "mysql=true\nmysql.username=test-user\nmysql.password=test-secret\n");
    MysqlConfig mysql = AppConfigLoader.load(directory, file).mysql();
    assertTrue(mysql.enabled());
    assertEquals("jdbc:mysql://localhost:3306/storyteller", mysql.url());
    assertEquals("test-user", mysql.username());
    assertEquals("test-secret", mysql.password());
    assertFalse(mysql.toString().contains("test-secret"));
  }
}
