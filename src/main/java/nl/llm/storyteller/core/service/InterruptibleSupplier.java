package nl.llm.storyteller.core.service;

import java.io.IOException;

@FunctionalInterface
interface InterruptibleSupplier<T> {
  T get() throws IOException, InterruptedException;
}
