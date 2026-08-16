package nl.llm.storyteller.core;

import java.nio.file.Path;

public final class TestAppConfigFactory {
    private TestAppConfigFactory() {
    }

    public static AppConfig load(Path baseDir) {
        return AppConfigLoader.load(baseDir, null);
    }
}
