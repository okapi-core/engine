/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.wal.bench;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.time.Duration;
import java.util.BitSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;
import java.util.zip.CRC32;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.okapi.wal.factory.WalResourcesFactory;
import org.okapi.wal.frame.WalEntry;
import org.okapi.wal.io.IllegalWalEntryException;
import org.okapi.wal.io.WalReader;
import org.okapi.wal.lsn.Lsn;
import org.okapi.wal.manager.WalManager;

@Disabled
class WalStreamRegressionBenchTest {
  private static final int RECORDS = 10_000;
  private static final int BATCH_SIZE = 127;
  private static final int COMMIT_EVERY_BATCHES = 3;
  private static final int SEGMENT_SIZE_BYTES = 2_048;
  private static final int MIN_WRITE_STAGGER_MILLIS = 1;
  private static final int MAX_WRITE_STAGGER_MILLIS = 10;
  private static final int MAGIC = 0x4f4b4150;
  private static final Duration TEST_TIMEOUT = Duration.ofSeconds(120);

  @TempDir Path walDir;

  @Test
  void concurrentWriterAndCheckerConsumerReadAllPayloadsFromCleanWal() throws Exception {
    var factory = new WalResourcesFactory(new WalManager.WalConfig(SEGMENT_SIZE_BYTES));
    var bundle = factory.createResourcesFromScratch(walDir);

    var start = new CountDownLatch(1);
    var producerDone = new AtomicBoolean(false);
    var written = new AtomicInteger();
    var checker = new PayloadChecker(RECORDS);
    var consumer =
        new CheckerWalConsumer(
            bundle.getReader(), bundle.getManager(), checker, BATCH_SIZE, COMMIT_EVERY_BATCHES);
    var driver = new CheckerWalConsumerDriver(consumer);

    try (var executor = Executors.newFixedThreadPool(2)) {
      Future<Void> producer =
          executor.submit(
              () -> {
                start.await();
                try {
                  for (int sequence = 0; sequence < RECORDS; sequence++) {
                    var lsn = bundle.getLsnSupplier().next();
                    bundle
                        .getWriter()
                        .append(new WalEntry(lsn, PayloadCodec.encode(sequence, lsn)));
                    written.incrementAndGet();
                    LockSupport.parkNanos(
                        TimeUnit.MILLISECONDS.toNanos(writeStaggerMillis(sequence)));
                  }
                  return null;
                } finally {
                  producerDone.set(true);
                }
              });

      Future<Void> consumerTask =
          executor.submit(
              () -> {
                start.await();
                long deadlineNanos = System.nanoTime() + TEST_TIMEOUT.toNanos();
                while (consumer.consumed() < RECORDS) {
                  driver.onTick();
                  if (consumer.lastTickConsumed() == 0) {
                    assertFalse(
                        producerDone.get() && System.nanoTime() > deadlineNanos,
                        "consumer stalled after producer completed at "
                            + consumer.consumed()
                            + " of "
                            + RECORDS
                            + " records");
                    LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
                  }
                }
                consumer.commitPending();
                return null;
              });

      start.countDown();
      await(producer);
      await(consumerTask);
      executor.shutdown();
      assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
    } finally {
      bundle.getWriter().close();
      bundle.getManager().close();
    }

    assertEquals(RECORDS, written.get());
    assertEquals(RECORDS, consumer.consumed());
    checker.assertComplete();
    assertEquals(Lsn.fromNumber(RECORDS), consumer.lastConsumedLsn());
    assertEquals(
        Optional.of(Lsn.fromNumber(RECORDS)),
        bundle.getManager().getCommittedLsn().map(commit -> commit.getLsn()));
    assertTrue(bundle.getReader().readBatchAndAdvance(1).isEmpty());
  }

  private static void await(Future<Void> task)
      throws InterruptedException, ExecutionException, TimeoutException {
    task.get(TEST_TIMEOUT.toSeconds(), TimeUnit.SECONDS);
  }

  private static long writeStaggerMillis(int sequence) {
    int spread = MAX_WRITE_STAGGER_MILLIS - MIN_WRITE_STAGGER_MILLIS + 1;
    return MIN_WRITE_STAGGER_MILLIS + Math.floorMod(sequence * 31 + 7, spread);
  }

  private static class CheckerWalConsumerDriver {
    private final CheckerWalConsumer consumer;

    private CheckerWalConsumerDriver(CheckerWalConsumer consumer) {
      this.consumer = consumer;
    }

    void onTick() {
      try {
        consumer.consumeRecords();
      } catch (IOException | IllegalWalEntryException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private static class CheckerWalConsumer {
    private final WalReader reader;
    private final WalManager manager;
    private final PayloadChecker checker;
    private final int batchSize;
    private final int commitEveryBatches;

    private int consumed;
    private int lastTickConsumed;
    private int batchesSinceCommit;
    private Lsn lastConsumedLsn = Lsn.getStart();
    private Lsn lastCommittedLsn = Lsn.getStart();

    private CheckerWalConsumer(
        WalReader reader,
        WalManager manager,
        PayloadChecker checker,
        int batchSize,
        int commitEveryBatches) {
      this.reader = reader;
      this.manager = manager;
      this.checker = checker;
      this.batchSize = batchSize;
      this.commitEveryBatches = commitEveryBatches;
    }

    void consumeRecords() throws IOException, IllegalWalEntryException {
      List<WalEntry> batch = reader.readBatchAndAdvance(batchSize);
      lastTickConsumed = batch.size();
      if (batch.isEmpty()) {
        return;
      }

      for (var entry : batch) {
        assertTrue(checker.isValid(new Payload(consumed, entry)));
        consumed++;
        lastConsumedLsn = entry.getLsn();
      }

      batchesSinceCommit++;
      if (batchesSinceCommit >= commitEveryBatches) {
        commitPending();
      }
    }

    void commitPending() throws IOException {
      if (lastCommittedLsn.equals(lastConsumedLsn)) {
        return;
      }
      manager.commitLsn(lastConsumedLsn);
      lastCommittedLsn = lastConsumedLsn;
      batchesSinceCommit = 0;
    }

    int consumed() {
      return consumed;
    }

    int lastTickConsumed() {
      return lastTickConsumed;
    }

    Lsn lastConsumedLsn() {
      return lastConsumedLsn;
    }
  }

  private record Payload(long expectedSequence, WalEntry entry) {}

  private static class PayloadChecker {
    private final BitSet seen;
    private final int expectedRecords;

    private PayloadChecker(int expectedRecords) {
      this.expectedRecords = expectedRecords;
      this.seen = new BitSet(expectedRecords);
    }

    boolean isValid(Payload payload) {
      var decoded = PayloadCodec.decode(payload.entry().getPayload());
      if (decoded.sequence() != payload.expectedSequence()) {
        return false;
      }
      if (decoded.sequence() < 0 || decoded.sequence() >= expectedRecords) {
        return false;
      }
      if (seen.get((int) decoded.sequence())) {
        return false;
      }
      if (!payload.entry().getLsn().equals(decoded.lsn())) {
        return false;
      }
      seen.set((int) decoded.sequence());
      return true;
    }

    void assertComplete() {
      assertEquals(expectedRecords, seen.cardinality());
      for (int i = 0; i < expectedRecords; i++) {
        assertTrue(seen.get(i), "missing payload sequence " + i);
      }
    }
  }

  private record DecodedPayload(long sequence, Lsn lsn) {}

  private static class PayloadCodec {
    private static final int HEADER_BYTES = Integer.BYTES + Long.BYTES + Long.BYTES + Integer.BYTES;
    private static final int CHECKSUM_BYTES = Long.BYTES;

    static byte[] encode(long sequence, Lsn lsn) {
      var body = bodyFor(sequence, lsn);
      var buffer =
          ByteBuffer.allocate(HEADER_BYTES + body.length + CHECKSUM_BYTES)
              .order(ByteOrder.BIG_ENDIAN);
      buffer.putInt(MAGIC);
      buffer.putLong(sequence);
      buffer.putLong(lsn.getNumber());
      buffer.putInt(body.length);
      buffer.put(body);
      buffer.putLong(checksum(sequence, lsn.getNumber(), body));
      return buffer.array();
    }

    static DecodedPayload decode(byte[] payload) {
      var buffer = ByteBuffer.wrap(payload).order(ByteOrder.BIG_ENDIAN);
      assertEquals(MAGIC, buffer.getInt());
      long sequence = buffer.getLong();
      long lsn = buffer.getLong();
      int bodyLength = buffer.getInt();
      assertTrue(bodyLength >= 0);
      assertEquals(CHECKSUM_BYTES, buffer.remaining() - bodyLength);

      var body = new byte[bodyLength];
      buffer.get(body);
      long checksum = buffer.getLong();
      assertEquals(checksum(sequence, lsn, body), checksum);
      assertEquals(ByteBuffer.wrap(bodyFor(sequence, Lsn.fromNumber(lsn))), ByteBuffer.wrap(body));
      return new DecodedPayload(sequence, Lsn.fromNumber(lsn));
    }

    private static byte[] bodyFor(long sequence, Lsn lsn) {
      int size = 32 + (int) ((sequence * 31 + lsn.getNumber() * 17) % 97);
      var body = new byte[size];
      long state = sequence * 0x9e3779b97f4a7c15L ^ lsn.getNumber();
      for (int i = 0; i < body.length; i++) {
        state ^= state << 13;
        state ^= state >>> 7;
        state ^= state << 17;
        body[i] = (byte) state;
      }
      return body;
    }

    private static long checksum(long sequence, long lsn, byte[] body) {
      var crc = new CRC32();
      var header = ByteBuffer.allocate(Long.BYTES + Long.BYTES).order(ByteOrder.BIG_ENDIAN);
      header.putLong(sequence);
      header.putLong(lsn);
      crc.update(header.array());
      crc.update(body);
      return crc.getValue();
    }
  }
}
