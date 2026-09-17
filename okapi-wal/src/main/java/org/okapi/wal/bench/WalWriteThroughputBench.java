/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.wal.bench;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Random;
import java.util.concurrent.Callable;
import org.okapi.wal.factory.WalResourcesFactory;
import org.okapi.wal.frame.WalEntry;
import org.okapi.wal.manager.WalManager;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(
    name = "wal-write-throughput-bench",
    mixinStandardHelpOptions = true,
    description = "Measures WAL write throughput using generated random payloads.",
    usageHelpAutoWidth = true)
public class WalWriteThroughputBench implements Callable<Integer> {
  private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#,##0.00");

  @Spec private CommandSpec spec;

  @Option(
      names = "--wal-dir",
      required = true,
      description = "Directory used for the benchmark WAL. It is deleted before and after the run.")
  private Path walDir;

  @Option(names = "--entries", required = true, description = "Number of WAL entries to write.")
  private long entries;

  @Option(
      names = "--min-payload-bytes",
      required = true,
      description = "Minimum generated payload size in bytes, inclusive.")
  private int minPayloadBytes;

  @Option(
      names = "--max-payload-bytes",
      required = true,
      description = "Maximum generated payload size in bytes, inclusive.")
  private int maxPayloadBytes;

  @Option(
      names = "--segment-size-bytes",
      required = true,
      description = "WAL segment size in bytes.")
  private long segmentSizeBytes;

  @Option(
      names = "--seed",
      description =
          "Random seed for reproducible payload sizes and contents. Defaults to nanoTime.")
  private Long seed;

  @Override
  public Integer call() throws Exception {
    validateArgs();
    var effectiveSeed = seed == null ? System.nanoTime() : seed;

    deleteRecursivelyIfExists(walDir);
    try {
      Files.createDirectories(walDir);
      var result = runBenchmark(effectiveSeed);
      printReport(result);
      return 0;
    } finally {
      deleteRecursivelyIfExists(walDir);
    }
  }

  private BenchmarkResult runBenchmark(long effectiveSeed) throws Exception {
    var random = new Random(effectiveSeed);
    var walConfig = new WalManager.WalConfig(segmentSizeBytes);
    var resourceFactory = new WalResourcesFactory(walConfig);
    var generatedPayloads = generatePayloads(random);

    var bundle = resourceFactory.createResourcesFromScratch(walDir);
    long elapsedNanos;
    try (var manager = bundle.getManager();
        var writer = bundle.getWriter()) {
      var lsnSupplier = bundle.getLsnSupplier();
      var startedAtNanos = System.nanoTime();
      for (var payload : generatedPayloads) {
        writer.append(new WalEntry(lsnSupplier.next(), payload));
      }
      elapsedNanos = System.nanoTime() - startedAtNanos;
    }

    return new BenchmarkResult(
        entries,
        minPayloadBytes,
        maxPayloadBytes,
        segmentSizeBytes,
        effectiveSeed,
        generatedPayloads.totalPayloadBytes(),
        elapsedNanos);
  }

  private GeneratedPayloads generatePayloads(Random random) {
    var payloads = new ArrayList<byte[]>();
    long totalPayloadBytes = 0L;
    for (long i = 0; i < entries; i++) {
      int payloadSize = nextPayloadSize(random);
      byte[] payload = new byte[payloadSize];
      random.nextBytes(payload);
      payloads.add(payload);
      totalPayloadBytes += payloadSize;
    }
    return new GeneratedPayloads(payloads, totalPayloadBytes);
  }

  private int nextPayloadSize(Random random) {
    if (minPayloadBytes == maxPayloadBytes) {
      return minPayloadBytes;
    }
    return minPayloadBytes + random.nextInt(maxPayloadBytes - minPayloadBytes + 1);
  }

  private void validateArgs() {
    if (entries <= 0) {
      throw new CommandLine.ParameterException(
          spec.commandLine(), "--entries must be greater than 0");
    }
    if (minPayloadBytes < 0) {
      throw new CommandLine.ParameterException(
          spec.commandLine(), "--min-payload-bytes must be greater than or equal to 0");
    }
    if (maxPayloadBytes < minPayloadBytes) {
      throw new CommandLine.ParameterException(
          spec.commandLine(), "--max-payload-bytes must be greater than or equal to min");
    }
    if (segmentSizeBytes <= 0) {
      throw new CommandLine.ParameterException(
          spec.commandLine(), "--segment-size-bytes must be greater than 0");
    }
    var normalized = walDir.toAbsolutePath().normalize();
    if (normalized.getParent() == null) {
      throw new CommandLine.ParameterException(
          spec.commandLine(), "--wal-dir must not be a filesystem root");
    }
    walDir = normalized;
  }

  private static void deleteRecursivelyIfExists(Path path) throws IOException {
    if (!Files.exists(path)) {
      return;
    }
    try (var paths = Files.walk(path)) {
      var sorted = paths.sorted(Comparator.reverseOrder()).toList();
      for (var item : sorted) {
        Files.deleteIfExists(item);
      }
    }
  }

  private static void printReport(BenchmarkResult result) {
    double elapsedSeconds = result.elapsedNanos() / 1_000_000_000.0;
    double throughputBytesPerSecond = result.totalPayloadBytes() / elapsedSeconds;
    double throughputMiBPerSecond = throughputBytesPerSecond / (1024.0 * 1024.0);

    System.out.println("WAL write throughput benchmark");
    System.out.println("entries=" + result.entries());
    System.out.println(
        "payload_bytes_min="
            + result.minPayloadBytes()
            + ", payload_bytes_max="
            + result.maxPayloadBytes());
    System.out.println("segment_size_bytes=" + result.segmentSizeBytes());
    System.out.println("seed=" + result.seed());
    System.out.println("total_payload_bytes=" + result.totalPayloadBytes());
    System.out.println("elapsed_seconds=" + DECIMAL_FORMAT.format(elapsedSeconds));
    System.out.println(
        "throughput_bytes_per_second=" + DECIMAL_FORMAT.format(throughputBytesPerSecond));
    System.out.println(
        "throughput_mib_per_second=" + DECIMAL_FORMAT.format(throughputMiBPerSecond));
  }

  public static void main(String[] args) {
    int exitCode = new CommandLine(new WalWriteThroughputBench()).execute(args);
    System.exit(exitCode);
  }

  private record BenchmarkResult(
      long entries,
      int minPayloadBytes,
      int maxPayloadBytes,
      long segmentSizeBytes,
      long seed,
      long totalPayloadBytes,
      long elapsedNanos) {}

  private record GeneratedPayloads(List<byte[]> payloads, long totalPayloadBytes)
      implements Iterable<byte[]> {
    @Override
    public java.util.Iterator<byte[]> iterator() {
      return payloads.iterator();
    }
  }
}
