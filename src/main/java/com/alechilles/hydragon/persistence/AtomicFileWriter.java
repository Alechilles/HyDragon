package com.alechilles.hydragon.persistence;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/** Injectable same-directory atomic replacement boundary used by {@link HyDragonStateStore}. */
@FunctionalInterface
public interface AtomicFileWriter {
    void writeAtomically(Path destination, byte[] content) throws IOException;

    static AtomicFileWriter systemWriter() {
        return AtomicFileWriter::writeWithJdk;
    }

    private static void writeWithJdk(Path destination, byte[] content) throws IOException {
        Path absoluteDestination = destination.toAbsolutePath().normalize();
        Path directory = absoluteDestination.getParent();
        if (directory == null) {
            throw new IOException("Persistence path has no parent directory: " + destination);
        }
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, absoluteDestination.getFileName() + ".", ".tmp");
        boolean moved = false;
        try {
            try (FileChannel channel = FileChannel.open(
                    temporary,
                    StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING)) {
                ByteBuffer buffer = ByteBuffer.wrap(content);
                while (buffer.hasRemaining()) {
                    channel.write(buffer);
                }
                channel.force(true);
            }
            try {
                Files.move(
                        temporary,
                        absoluteDestination,
                        StandardCopyOption.ATOMIC_MOVE,
                        StandardCopyOption.REPLACE_EXISTING);
                moved = true;
            } catch (AtomicMoveNotSupportedException exception) {
                throw new IOException(
                        "The persistence filesystem does not support atomic replacement for " + absoluteDestination,
                        exception);
            }
        } finally {
            if (!moved) {
                Files.deleteIfExists(temporary);
            }
        }
    }
}
