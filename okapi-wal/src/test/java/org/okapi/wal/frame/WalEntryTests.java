/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.wal.frame;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.okapi.wal.exceptions.CorruptedRecordException;
import org.okapi.wal.exceptions.VeryHugeRecordException;
import org.okapi.wal.lsn.Lsn;

public class WalEntryTests {

  @Test
  public void testEmptyPayload() throws CorruptedRecordException {
    var entry = new WalEntry(Lsn.fromNumber(100L), new byte[] {});
    var serialized = entry.serialize();
    var deserialized = WalEntry.deserialize(serialized);
    Assertions.assertEquals(entry, deserialized);
  }

  @Test
  public void testNonEmptyPayload() throws CorruptedRecordException {
    var entry = new WalEntry(Lsn.fromNumber(100L), new byte[] {0x0, 0x1, 0x2});
    var serialized = entry.serialize();
    var deserialized = WalEntry.deserialize(serialized);
    Assertions.assertEquals(entry, deserialized);
  }

  @Test
  public void testMaxSizedPayload() {
    var largePayload = new byte[WalEntry.MAX_PAYLOAD_BYTES + 1];
    Assertions.assertThrows(
        VeryHugeRecordException.class, () -> new WalEntry(Lsn.fromNumber(100L), largePayload));
  }

  @Test
  public void testMalformed_notEnoughBytes() {
    var entry = new WalEntry(Lsn.fromNumber(100L), new byte[] {0x0, 0x1, 0x2});
    var serialized = entry.serialize();
    var truncated = new byte[serialized.length - 6];
    System.arraycopy(serialized, 0, truncated, 0, truncated.length);
    Assertions.assertThrows(CorruptedRecordException.class, () -> WalEntry.deserialize(truncated));
  }

  @Test
  public void testMalformed_missingEnd() {
    var entry = new WalEntry(Lsn.fromNumber(100L), new byte[] {0x0, 0x1, 0x2});
    var serialized = entry.serialize();
    var truncated = new byte[serialized.length - 4];
    System.arraycopy(serialized, 0, truncated, 0, truncated.length);
    Assertions.assertThrows(CorruptedRecordException.class, () -> WalEntry.deserialize(truncated));
  }

  @Test
  public void testMalformed_incompleteMagicEnd() {
    var entry = new WalEntry(Lsn.fromNumber(100L), new byte[] {0x0, 0x1, 0x2});
    var serialized = entry.serialize();
    var truncated = new byte[serialized.length - 3];
    System.arraycopy(serialized, 0, truncated, 0, truncated.length);
    Assertions.assertThrows(CorruptedRecordException.class, () -> WalEntry.deserialize(truncated));
  }
}
