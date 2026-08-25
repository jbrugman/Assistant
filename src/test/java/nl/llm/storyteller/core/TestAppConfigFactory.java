package nl.llm.storyteller.core;

import java.nio.file.Path;

public final class TestAppConfigFactory {
    private TestAppConfigFactory() {
    }

    public static nl.llm.storyteller.core.config.AppConfig load(Path baseDir) {
        return nl.llm.storyteller.core.config.AppConfigLoader.load(baseDir, null);
    }
}
