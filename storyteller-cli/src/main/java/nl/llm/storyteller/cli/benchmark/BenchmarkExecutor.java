package nl.llm.storyteller.cli.benchmark;

import nl.llm.storyteller.core.ApplicationContext;

import java.io.IOException;

@FunctionalInterface
public interface BenchmarkExecutor {
  BenchmarkResult run(ApplicationContext sourceContext, BenchmarkOptions options) throws IOException, InterruptedException;
}
