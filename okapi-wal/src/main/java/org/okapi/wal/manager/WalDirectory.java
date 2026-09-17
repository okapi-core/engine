/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.wal.manager;

import com.google.gson.Gson;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import lombok.Getter;
import org.okapi.wal.commit.WalCommit;

public class WalDirectory {

  @Getter Path root;
  Gson gson;

  public WalDirectory(Path root) {
    this.root = root;
    this.gson = new Gson();
  }

  public Path getWalSegment(int epoch) {
    return root.resolve("segment_" + epoch + "_log");
  }

  public Path getCommitFile() {
    return root.resolve("commit.json");
  }

  public Path getLockFile() {
    return root.resolve("wal_manager.lock");
  }

  public Path getSegmentTable() {
    return root.resolve("segment_table.json");
  }

  public Path getWalCommit() {
    return root.resolve("wal_commit.json");
  }

  public Optional<WalCommit> getLatestCommit() throws IOException {
    var commitPath = getWalCommit();
    if (!Files.exists(commitPath)) return Optional.empty();
    var json = Files.readString(commitPath);
    return Optional.ofNullable(gson.fromJson(json, WalCommit.class));
  }

  public Optional<WalSegmentsMetadata> getSegmentMetadata() throws IOException {
    var path = getSegmentTable();
    if (!Files.exists(path)) return Optional.empty();
    return Optional.ofNullable(gson.fromJson(Files.readString(path), WalSegmentsMetadata.class));
  }

  public int getNextEpoch() throws IOException {
    return 1 + getCurrentMaxEpoch();
  }

  public int getCurrentMaxEpoch() throws IOException {
    try (var allFiles = Files.list(root)) {
      return allFiles
          .map(f -> f.getFileName().toString())
          .filter(fname -> fname.startsWith("segment_") && fname.endsWith("_log"))
          .map(fname -> fname.split("_")[1])
          .map(Integer::parseInt)
          .reduce(Math::max)
          .orElse(-1);
    }
  }

  public Optional<Integer> getSegmentImmediatelyAfter(int seg) throws IOException {
    try (var allFiles = Files.list(root)) {
      return allFiles
          .map(f -> f.getFileName().toString())
          .filter(fname -> fname.startsWith("segment_") && fname.endsWith("_log"))
          .map(fname -> fname.split("_")[1])
          .map(Integer::parseInt)
          .sorted()
          .filter(s -> s > seg)
          .findFirst();
    }
  }
}
