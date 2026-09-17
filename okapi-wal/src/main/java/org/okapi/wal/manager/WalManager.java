/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.wal.manager;

import com.google.common.base.Preconditions;
import com.google.gson.Gson;
import java.io.Closeable;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Optional;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.okapi.wal.commit.WalCommit;
import org.okapi.wal.filelock.FileLockException;
import org.okapi.wal.filelock.FileLockUtils;
import org.okapi.wal.lsn.Lsn;

/**
 * Allocates WAL segment files in a directory using a fixed number of epoch slots.
 *
 * <p>Filename pattern: wal_{epoch}_log, zero-based epochs.
 */
@Slf4j
public class WalManager implements Closeable {

  @Getter
  public static class WalConfig {
    long segmentSize;

    public WalConfig(long maxSegSize) {
      Preconditions.checkArgument(maxSegSize > 0, "maxSegSize should be >0");
      this.segmentSize = maxSegSize;
    }
  }

  private final WalDirectory walDirectory;
  @Getter private int currentSegment;
  private Path currentWal;
  @Getter private final WalConfig walConfig;
  private final FileLock fileLock;
  private final FileChannel lockFileFc;
  private final WalSegmentsMetadata walSegmentsMetadata;

  @Getter @Setter private Lsn lastWrittenLsn;
  Gson gson;

  public WalManager(Path dir, WalConfig config) throws IOException, FileLockException {
    this(new WalDirectory(dir), config);
  }

  public WalManager(WalDirectory walDirectory, WalConfig walConfig)
      throws IOException, FileLockException {
    var dir = walDirectory.getRoot();
    if (!Files.exists(dir)) {
      Files.createDirectories(dir);
    }

    this.walDirectory = new WalDirectory(dir);
    var lockFile = this.walDirectory.getLockFile();
    if (!Files.exists(lockFile)) {
      Files.createFile(lockFile);
    }
    this.lockFileFc = FileChannel.open(lockFile, StandardOpenOption.WRITE);
    try {
      this.fileLock = FileLockUtils.tryLockOrFail(this.lockFileFc);
    } catch (FileLockException e) {
      log.error("Could not lock file: {}", lockFile);
      throw new FileLockException("Locking failed for file: " + lockFile);
    }
    if (walConfig.getSegmentSize() <= 0) {
      throw new IllegalArgumentException("maxWalSize must be positive");
    }
    this.walConfig = walConfig;
    var current = walDirectory.getSegmentMetadata();
    this.walSegmentsMetadata =
        current.map(WalSegmentsMetadata::fromExisting).orElseGet(WalSegmentsMetadata::ofEmpty);
    this.gson = new Gson();
    var latestLsn = repairLatestSegmentAndMetadata(this.walDirectory);
    checkCommitConsistency(this.walDirectory, latestLsn);
    this.setLastWrittenLsn(latestLsn);
    this.currentSegment = walDirectory.getNextEpoch();
    this.currentWal = walDirectory.getWalSegment(currentSegment);
  }

  public Optional<Long> getMaxLsnInSegment(int segment) {
    if (segment == currentSegment) {
      return Optional.of(this.lastWrittenLsn.getNumber());
    }
    return walSegmentsMetadata.getSegmentMetadata().stream()
        .filter(meta -> meta.getSegmentNumber() == segment)
        .findFirst()
        .map(WalSegmentMetadata::getLargestLsn);
  }

  public Optional<Integer> getSegmentContainingLsn(Lsn lsn) {
    for (var entry : walSegmentsMetadata.getSegmentMetadata()) {
      if (entry.getLargestLsn() >= lsn.getNumber()) {
        return Optional.of(entry.getSegmentNumber());
      }
    }
    if (this.lastWrittenLsn.equals(lsn)) {
      return Optional.of(this.currentSegment);
    }
    return Optional.empty();
  }

  public void commitLsn(Lsn lsn) throws IOException {
    var walCommit = new WalCommit(lsn);
    var json = gson.toJson(walCommit);
    Files.writeString(walDirectory.getWalCommit(), json);
  }

  public Optional<WalCommit> getCommittedLsn() throws IOException {
    var walCommitPath = walDirectory.getWalCommit();
    if (!Files.exists(walCommitPath)) return Optional.empty();
    var json = Files.readString(walCommitPath);
    return Optional.of(gson.fromJson(json, WalCommit.class));
  }

  public Path allocateOrGetSegment() throws IOException {
    if (!Files.exists(currentWal)) {
      Files.createFile(currentWal);
      return currentWal;
    }

    if (Files.size(currentWal) < this.walConfig.getSegmentSize()) {
      return currentWal;
    }

    rotate();
    return currentWal;
  }

  private void rotate() throws IOException {
    int prevEpoch = currentSegment;
    int nextEpoch = (currentSegment + 1);
    Path nextWal = walDirectory.getWalSegment(nextEpoch);
    Files.createFile(nextWal);
    currentSegment = nextEpoch;
    currentWal = nextWal;
    this.walSegmentsMetadata.addMetadata(
        WalSegmentMetadata.builder()
            .segmentNumber(prevEpoch)
            .largestLsn(this.lastWrittenLsn.getNumber())
            .build());
    var serialized = gson.toJson(this.walSegmentsMetadata);
    Files.writeString(this.walDirectory.getSegmentTable(), serialized);
  }

  @Override
  public void close() throws IOException {
    this.fileLock.release();
    this.lockFileFc.close();
  }

  private Lsn repairLatestSegmentAndMetadata(WalDirectory walDirectory) throws IOException {
    int latestSegment = walDirectory.getCurrentMaxEpoch();
    while (latestSegment >= 0) {
      var walSegment = walDirectory.getWalSegment(latestSegment);
      var result = SegmentTruncator.truncate(walSegment);
      if (result.getLastGoodLsn().isPresent()) {
        var lastGoodLsn = result.getLastGoodLsn().get();
        walSegmentsMetadata.upsertMetadata(
            WalSegmentMetadata.builder()
                .segmentNumber(latestSegment)
                .largestLsn(lastGoodLsn.getNumber())
                .build());
        persistSegmentTable();
        return lastGoodLsn;
      }
      Files.deleteIfExists(walSegment);
      latestSegment--;
    }

    persistSegmentTable();
    return Lsn.getStart();
  }

  private void checkCommitConsistency(WalDirectory walDirectory, Lsn latestLsn) throws IOException {
    var existingCommit = walDirectory.getLatestCommit();
    if (existingCommit.isEmpty()) {
      return;
    }
    var commitLsn = existingCommit.get().getLsn();
    if (commitLsn.compareTo(latestLsn) > 0) {
      throw new IllegalStateException(
          "Commit LSN "
              + commitLsn.getNumber()
              + " is ahead of latest WAL LSN "
              + latestLsn.getNumber());
    }
  }

  private void persistSegmentTable() throws IOException {
    var serialized = gson.toJson(this.walSegmentsMetadata);
    Files.writeString(this.walDirectory.getSegmentTable(), serialized);
  }
}
