/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.io;

import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;

public class OkapiIo {

  public static int readInt(InputStream is) throws IOException, StreamReadingException {
    // read a 32 bit integer from the stream
    byte[] intBytes = new byte[4];
    for (int i = 0; i < 4; i++) {
      int read = is.read();
      if (read == -1) {
        throw new StreamReadingException("Not enough bytes to read an integer");
      }
      intBytes[i] = (byte) read;
    }

    // convert the byte array to an integer
    return Ints.fromByteArray(intBytes);
  }

  public static long readLong(InputStream is) throws IOException, StreamReadingException {
    byte[] longBytes = new byte[8];
    for (int i = 0; i < 8; i++) {
      int read = is.read();
      if (read == -1) {
        throw new StreamReadingException("Not enough bytes to read an integer");
      }
      longBytes[i] = (byte) read;
    }
    return Longs.fromByteArray(longBytes);
  }

  public static int writeInt(OutputStream os, int x) throws IOException {
    var bytes = Ints.toByteArray(x);
    os.write(bytes);
    return bytes.length;
  }

  public static int writeBoolean(OutputStream os, boolean b) throws IOException {
    return writeInt(os, b ? 1 : 0);
  }

  public static int writeDouble(OutputStream os, double d) throws IOException {
    var arr = Longs.toByteArray(Double.doubleToLongBits(d));
    os.write(arr);
    return arr.length;
  }

  public static void writeFloat(OutputStream os, float f) throws IOException {
    var ints = Float.floatToIntBits(f);
    writeInt(os, ints);
  }

  public static float readFloat(InputStream is) throws IOException, StreamReadingException {
    var intArr = OkapiIo.readInt(is);
    return Float.intBitsToFloat(intArr);
  }

  public static int writeLong(OutputStream os, long t) throws IOException {
    var arr = Longs.toByteArray(t);
    os.write(arr);
    return arr.length;
  }

  public static int writeBytes(OutputStream os, byte[] bytes) throws IOException {
    // total 80bytes of metadata
    var written = writeInt(os, bytes.length); // write the length of the byte array
    os.write(bytes); // write the byte array
    written += bytes.length;
    return written;
  }

  public static int writeBytesWithoutLenPrefix(OutputStream os, byte[] bytes) throws IOException {
    var written = 0;
    os.write(bytes); // write the byte array
    written += bytes.length;
    return written;
  }

  public static int writeString(OutputStream os, String s) throws IOException {
    return writeBytes(os, s.getBytes());
  }

  public static String readString(InputStream is) throws IOException, StreamReadingException {
    byte[] bytes = OkapiIo.readBytes(is);
    return new String(bytes);
  }

  public static boolean readBoolean(InputStream is) throws IOException, StreamReadingException {
    int val = readInt(is);
    return val != 0;
  }

  public static double readDouble(InputStream is) throws IOException, StreamReadingException {
    var longArr = OkapiIo.readLong(is);
    return Double.longBitsToDouble(longArr);
  }

  public static byte[] readBytes(InputStream is) throws IOException, StreamReadingException {
    int length = readInt(is);
    return readNBytes(is, length);
  }

  public static byte[] readNBytes(InputStream is, int n)
      throws IOException, StreamReadingException {
    byte[] bytes = new byte[n];
    int read = is.read(bytes);
    if (read != n) {
      throw new StreamReadingException("Not enough bytes to read the byte array");
    }
    return bytes;
  }

  public static void write(OutputStream os, byte b) throws IOException {
    os.write(b);
  }

  public static byte read(InputStream is) throws IOException, StreamReadingException {
    int read = is.read();
    if (read == -1) {
      throw new StreamReadingException("Not enough bytes to read a byte");
    }
    return (byte) read;
  }

  public static void checkMagicNumber(InputStream is, String expectedMagicNumber)
      throws IOException, StreamReadingException {
    var inputStr = OkapiIo.readString(is);
    if (!inputStr.equals(expectedMagicNumber)) {
      throw new StreamReadingException(
          "Invalid magic number. Expected: " + expectedMagicNumber + ", but got: " + inputStr);
    }
  }

  public static void write(FileChannel channel, byte[] payload) throws IOException {
    byte[] header = Ints.toByteArray(payload.length);
    ByteBuffer buf = ByteBuffer.allocate(4 + payload.length);
    buf.put(header);
    buf.put(payload);
    buf.flip();
    while (buf.hasRemaining()) {
      channel.write(buf);
    }
  }
}
