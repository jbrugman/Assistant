package nl.llm.storyteller.core;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

public final class AtomicFileWriter {
  private static final ConcurrentMap<Path, ReentrantLock> PATH_LOCKS = new ConcurrentHashMap<>();

  private AtomicFileWriter() { }

  public static void write(Path path, byte[] content) {
    write(path, temporaryPath -> Files.write(temporaryPath, content));
  }

  public static void write(Path path, TemporaryFileWriter writer) {
    Path target = path.toAbsolutePath().normalize();
    ReentrantLock lock = PATH_LOCKS.computeIfAbsent(target, _ -> new ReentrantLock());
    lock.lock();
    try {
      writeLocked(target, writer);
    } finally {
      lock.unlock();
    }
  }

  private static void writeLocked(Path target, TemporaryFileWriter writer) {
    Path temporaryPath = null;
    try {
      Path parent = target.getParent();
      Files.createDirectories(parent);
      temporaryPath = Files.createTempFile(parent, temporaryPrefix(target), ".tmp");
      writer.write(temporaryPath);
      flush(temporaryPath);
      move(temporaryPath, target);
      temporaryPath = null;
    } catch (IOException ex) {
      throw new UncheckedIOException("Could not safely write " + target + ".", ex);
    } finally {
      deleteTemporaryFile(temporaryPath);
    }
  }

  private static String temporaryPrefix(Path target) {
    String fileName = target.getFileName().toString();
    return fileName.length() >= 3 ? fileName : (fileName + "___").substring(0, 3);
  }

  private static void flush(Path path) throws IOException {
    try (FileChannel channel = FileChannel.open(path, StandardOpenOption.WRITE)) {
      channel.force(true);
    }
  }

  private static void move(Path source, Path target) throws IOException {
    try {
      Files.move(source, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    } catch (AtomicMoveNotSupportedException _) {
      Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
    }
  }

  private static void deleteTemporaryFile(Path temporaryPath) {
    if (temporaryPath == null) {
      return;
    }
    try {
      Files.deleteIfExists(temporaryPath);
    } catch (IOException _) {
      // Best-effort cleanup after preserving the original target.
    }
  }

  @FunctionalInterface
  public interface TemporaryFileWriter {
    void write(Path temporaryPath) throws IOException;
  }
}
