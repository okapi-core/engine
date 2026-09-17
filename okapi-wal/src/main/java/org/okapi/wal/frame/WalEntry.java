/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.wal.frame;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import lombok.Getter;
import org.okapi.wal.exceptions.CorruptedRecordException;
import org.okapi.wal.exceptions.VeryHugeRecordException;
import org.okapi.wal.lsn.Lsn;

/**
 * Frame format:
 *
 * <pre>
 * MAGIC(4) | VERSION(1) | FRAME_LEN(4) | HEADER_LEN(2) | EPOCH(4) | NUMBER(8) | PAYLOAD(FRAME_LEN - HEADER_LEN) | MAGIC_END(4)
 * </pre>
 *
 * FRAME_LEN = HEADER_LEN + PAYLOAD_LEN + CRC32_LEN + MAGIC_END.length HEADER_LEN = bytes from
 * VERSION through PAYLOAD_LEN (inclusive) to make resync possible.
 */
public class WalEntry {
  // static configuration
  public static final int MAX_PAYLOAD_BYTES = 16 * 1024 * 1024;

  public static final byte[] MAGIC = new byte[] {'W', 'A', 'L', 'S'};
  public static final byte[] MAGIC_END = new byte[] {'W', 'A', 'L', 'E'};

  private static final byte VERSION = 1;
  private static final short HEADER_LENGTH = 8; // NUMBER
  private static final int MIN_FRAME_SIZE =
      MAGIC.length
          + 1 /* version */
          + 4 /* frame */
          + 2 /* header */
          + HEADER_LENGTH
          + MAGIC_END.length;

  @Getter private final Lsn lsn;
  @Getter private final byte[] payload;

  @Override
  public boolean equals(Object rhs) {
    if (rhs instanceof WalEntry right) {
      return right.getLsn().equals(lsn) && Arrays.equals(payload, right.payload);
    } else return false;
  }

  public WalEntry(Lsn lsn, byte[] payload) {
    this.lsn = Objects.requireNonNull(lsn, "lsn is required");
    this.payload = Objects.requireNonNull(payload, "payload is required");
    if (payload.length > MAX_PAYLOAD_BYTES) {
      throw new VeryHugeRecordException("Payload too large: " + payload.length);
    }
  }

  public byte[] serialize() {
    int frameLength = HEADER_LENGTH + payload.length;
    var buffer = ByteBuffer.allocate(MIN_FRAME_SIZE + payload.length).order(ByteOrder.BIG_ENDIAN);

    // put magic first
    buffer.put(MAGIC);
    buffer.put(VERSION);
    buffer.putInt(frameLength);
    buffer.putShort(HEADER_LENGTH);
    buffer.putLong(lsn.getNumber());
    buffer.put(payload);
    buffer.put(MAGIC_END);
    return buffer.array();
  }

  public static WalEntry deserialize(byte[] frameBytes) throws CorruptedRecordException {
    Objects.requireNonNull(frameBytes, "frame is required");
    if (frameBytes.length < MIN_FRAME_SIZE) {
      throw new CorruptedRecordException("Frame too small: " + frameBytes.length);
    }

    var buffer = ByteBuffer.wrap(frameBytes).order(ByteOrder.BIG_ENDIAN);
    validateMagic(buffer);

    byte version = buffer.get();
    if (version != VERSION) {
      throw new CorruptedRecordException("Unsupported WAL frame version: " + version);
    }

    int frameLength = buffer.getInt();
    short headerLength = buffer.getShort();
    if (headerLength != HEADER_LENGTH) {
      throw new CorruptedRecordException("Unexpected header length: " + headerLength);
    }

    long number = buffer.getLong();
    int payloadLength = frameLength - headerLength;
    if (payloadLength < 0 || payloadLength > MAX_PAYLOAD_BYTES) {
      throw new CorruptedRecordException("Invalid payload length: " + payloadLength);
    }

    if (frameLength < headerLength) {
      throw new CorruptedRecordException("Frame length should be greater than header length.");
    }

    if (buffer.remaining() < payloadLength) {
      throw new CorruptedRecordException("Truncated frame content");
    }

    byte[] payload = new byte[payloadLength];
    buffer.get(payload);
    validateMagicEnd(buffer);
    try {
      return new WalEntry(new Lsn(number), payload);
    } catch (IllegalArgumentException e) {
      throw new CorruptedRecordException("Invalid LSN values", e);
    }
  }

  private static void validateMagic(ByteBuffer buffer) throws CorruptedRecordException {
    byte[] magic = new byte[MAGIC.length];
    buffer.get(magic);
    if (!Arrays.equals(magic, MAGIC)) {
      throw new CorruptedRecordException("Invalid WAL frame magic");
    }
  }

  private static void validateMagicEnd(ByteBuffer buffer) throws CorruptedRecordException {
    byte[] magicEnd = new byte[MAGIC_END.length];
    if (buffer.remaining() < MAGIC_END.length) {
      throw new CorruptedRecordException("Not enough bytes to read MAGIC_END");
    }
    buffer.get(magicEnd);
    if (!Arrays.equals(magicEnd, MAGIC_END)) {
      throw new CorruptedRecordException("Invalid WAL frame end magic");
    }
  }

  public static Lsn getMaxLsn(List<WalEntry> walEntry) {
    return walEntry.stream().map(WalEntry::getLsn).reduce(Lsn::max).orElse(Lsn.zeroLsn());
  }
}
