package nl.llm.storyteller.service;

import java.io.IOException;

@FunctionalInterface
interface InterruptibleSupplier<T> {
    T get() throws IOException, InterruptedException;
}
