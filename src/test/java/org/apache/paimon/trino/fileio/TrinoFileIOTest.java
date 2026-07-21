/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.paimon.trino.fileio;

import org.apache.paimon.fs.Path;
import org.apache.paimon.fs.TwoPhaseOutputStream;

import io.trino.filesystem.FileIterator;
import io.trino.filesystem.FileMayHaveAlreadyExistedException;
import io.trino.filesystem.Location;
import io.trino.filesystem.TrinoFileSystem;
import io.trino.filesystem.TrinoInputFile;
import io.trino.filesystem.TrinoOutputFile;
import io.trino.filesystem.local.LocalFileSystem;
import io.trino.memory.context.AggregatedMemoryContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit tests for {@link TrinoFileIO}. */
public class TrinoFileIOTest {

    @TempDir java.nio.file.Path tempDir;

    @Test
    public void testTryToWriteAtomicUsesCreateExclusiveOnObjectStore() throws Exception {
        TestTrinoFileSystem trinoFileSystem =
                new TestTrinoFileSystem(
                        tempDir, ExclusiveMode.CREATE_NEW_FILE, RenameMode.NORMAL_RENAME);
        TrinoFileIO fileIO = newObjectStoreFileIO(trinoFileSystem);
        Path target = localPath("table/schema/1.schema");

        assertThat(fileIO.tryToWriteAtomic(target, "v1")).isTrue();
        assertThat(readContent("table/schema/1.schema")).isEqualTo("v1");
    }

    @Test
    public void testTryToWriteAtomicReturnsFalseOnFileAlreadyExists() throws Exception {
        TestTrinoFileSystem trinoFileSystem =
                new TestTrinoFileSystem(
                        tempDir, ExclusiveMode.THROW_FILE_EXISTS, RenameMode.NORMAL_RENAME);
        TrinoFileIO fileIO = newObjectStoreFileIO(trinoFileSystem);
        Path target = localPath("table/schema/1.schema");

        assertThat(fileIO.tryToWriteAtomic(target, "v1")).isFalse();
        assertThat(fileIO.exists(target)).isFalse();
    }

    @Test
    public void testTryToWriteAtomicReturnsFalseOnMayHaveAlreadyExisted() throws Exception {
        TestTrinoFileSystem trinoFileSystem =
                new TestTrinoFileSystem(
                        tempDir, ExclusiveMode.THROW_MAY_HAVE_EXISTED, RenameMode.NORMAL_RENAME);
        TrinoFileIO fileIO = newObjectStoreFileIO(trinoFileSystem);
        Path target = localPath("table/schema/1.schema");

        assertThat(fileIO.tryToWriteAtomic(target, "v1")).isFalse();
        assertThat(fileIO.exists(target)).isFalse();
    }

    @Test
    public void testTryToWriteAtomicFallsBackWhenExclusiveCreateUnsupported() throws Exception {
        TestTrinoFileSystem trinoFileSystem =
                new TestTrinoFileSystem(
                        tempDir, ExclusiveMode.UNSUPPORTED, RenameMode.NORMAL_RENAME);
        TrinoFileIO fileIO = newObjectStoreFileIO(trinoFileSystem);
        Path target = localPath("table/schema/1.schema");

        assertThat(fileIO.tryToWriteAtomic(target, "v1")).isTrue();
        assertThat(readContent("table/schema/1.schema")).isEqualTo("v1");
    }

    @Test
    public void testTwoPhaseOutputStreamCommitsWithoutRename() throws Exception {
        TestTrinoFileSystem trinoFileSystem =
                new TestTrinoFileSystem(
                        tempDir, ExclusiveMode.UNSUPPORTED, RenameMode.RENAME_UNSUPPORTED);
        TrinoFileIO fileIO = newObjectStoreFileIO(trinoFileSystem);
        Path target = localPath("table/data/file");

        TwoPhaseOutputStream outputStream = fileIO.newTwoPhaseOutputStream(target, false);
        outputStream.write("payload".getBytes(StandardCharsets.UTF_8));
        outputStream.closeForCommit().commit(fileIO);

        assertThat(readContent("table/data/file")).isEqualTo("payload");
        assertThat(trinoFileSystem.renameFileCalls()).isEqualTo(0);
    }

    @Test
    public void testTwoPhaseOutputStreamDiscardDeletesTarget() throws Exception {
        TestTrinoFileSystem trinoFileSystem =
                new TestTrinoFileSystem(
                        tempDir, ExclusiveMode.UNSUPPORTED, RenameMode.RENAME_UNSUPPORTED);
        TrinoFileIO fileIO = newObjectStoreFileIO(trinoFileSystem);
        Path target = localPath("table/data/file");

        TwoPhaseOutputStream outputStream = fileIO.newTwoPhaseOutputStream(target, false);
        outputStream.write("payload".getBytes(StandardCharsets.UTF_8));
        outputStream.closeForCommit().discard(fileIO);

        assertThat(fileIO.exists(target)).isFalse();
        assertThat(trinoFileSystem.renameFileCalls()).isEqualTo(0);
    }

    private TrinoFileIO newObjectStoreFileIO(TrinoFileSystem trinoFileSystem) {
        return new TrinoFileIO(trinoFileSystem, new Path("s3://bucket/warehouse"));
    }

    private static Path localPath(String relativePath) {
        return new Path("local:///" + relativePath);
    }

    private String readContent(String relativePath) throws IOException {
        return Files.readString(tempDir.resolve(relativePath), StandardCharsets.UTF_8);
    }

    private enum ExclusiveMode {
        CREATE_NEW_FILE,
        THROW_FILE_EXISTS,
        THROW_MAY_HAVE_EXISTED,
        UNSUPPORTED
    }

    private enum RenameMode {
        NORMAL_RENAME,
        RENAME_UNSUPPORTED
    }

    private static class TestTrinoFileSystem implements TrinoFileSystem {

        private final LocalFileSystem delegate;
        private final ExclusiveMode exclusiveMode;
        private final RenameMode renameMode;
        private int renameFileCalls;

        private TestTrinoFileSystem(
                java.nio.file.Path rootPath, ExclusiveMode exclusiveMode, RenameMode renameMode) {
            this.delegate = new LocalFileSystem(rootPath);
            this.exclusiveMode = exclusiveMode;
            this.renameMode = renameMode;
        }

        @Override
        public TrinoInputFile newInputFile(Location location) {
            return delegate.newInputFile(location);
        }

        @Override
        public TrinoInputFile newInputFile(Location location, long length) {
            return delegate.newInputFile(location, length);
        }

        @Override
        public TrinoInputFile newInputFile(Location location, long length, Instant lastModified) {
            return delegate.newInputFile(location, length, lastModified);
        }

        @Override
        public TrinoOutputFile newOutputFile(Location location) {
            TrinoOutputFile delegateOutputFile = delegate.newOutputFile(location);
            return new TestTrinoOutputFile(
                    delegateOutputFile, delegate.toFilePath(location), exclusiveMode);
        }

        @Override
        public void deleteFile(Location location) throws IOException {
            delegate.deleteFile(location);
        }

        @Override
        public void deleteDirectory(Location location) throws IOException {
            delegate.deleteDirectory(location);
        }

        @Override
        public void renameFile(Location source, Location target) throws IOException {
            renameFileCalls++;
            if (renameMode == RenameMode.RENAME_UNSUPPORTED) {
                throw new IOException("S3 does not support renames");
            }
            delegate.renameFile(source, target);
        }

        @Override
        public FileIterator listFiles(Location location) throws IOException {
            return delegate.listFiles(location);
        }

        @Override
        public Optional<Boolean> directoryExists(Location location) throws IOException {
            return delegate.directoryExists(location);
        }

        @Override
        public void createDirectory(Location location) throws IOException {
            delegate.createDirectory(location);
        }

        @Override
        public void renameDirectory(Location source, Location target) throws IOException {
            delegate.renameDirectory(source, target);
        }

        @Override
        public Set<Location> listDirectories(Location location) throws IOException {
            return delegate.listDirectories(location);
        }

        @Override
        public Optional<Location> createTemporaryDirectory(
                Location targetPath, String temporaryPrefix, String relativePrefix)
                throws IOException {
            return delegate.createTemporaryDirectory(targetPath, temporaryPrefix, relativePrefix);
        }

        private int renameFileCalls() {
            return renameFileCalls;
        }
    }

    private static class TestTrinoOutputFile implements TrinoOutputFile {

        private final TrinoOutputFile delegate;
        private final java.nio.file.Path localPath;
        private final ExclusiveMode exclusiveMode;

        private TestTrinoOutputFile(
                TrinoOutputFile delegate,
                java.nio.file.Path localPath,
                ExclusiveMode exclusiveMode) {
            this.delegate = delegate;
            this.localPath = localPath;
            this.exclusiveMode = exclusiveMode;
        }

        @Override
        public void createOrOverwrite(byte[] data) throws IOException {
            delegate.createOrOverwrite(data);
        }

        @Override
        public void createExclusive(byte[] data) throws IOException {
            switch (exclusiveMode) {
                case CREATE_NEW_FILE:
                    java.nio.file.Path parent = localPath.getParent();
                    if (parent != null) {
                        Files.createDirectories(parent);
                    }
                    Files.write(
                            localPath,
                            data,
                            StandardOpenOption.CREATE_NEW,
                            StandardOpenOption.WRITE);
                    return;
                case THROW_FILE_EXISTS:
                    throw new FileAlreadyExistsException(localPath.toString());
                case THROW_MAY_HAVE_EXISTED:
                    throw new FileMayHaveAlreadyExistedException(
                            "Put failed, provenance unknown", new IOException("retryable"));
                case UNSUPPORTED:
                default:
                    throw new UnsupportedOperationException("exclusive create is not supported");
            }
        }

        @Override
        public OutputStream create(AggregatedMemoryContext memoryContext) throws IOException {
            return delegate.create(memoryContext);
        }

        @Override
        public Location location() {
            return delegate.location();
        }
    }
}
