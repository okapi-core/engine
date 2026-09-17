/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.wal.manager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class WalDirectoryTests {

  @TempDir Path temp;

  @Test
  void testFindNextEpoch_emptyDir() throws IOException {
    var walDirectory = new WalDirectory(temp);
    Assertions.assertEquals(0, walDirectory.getNextEpoch());
  }

  @Test
  void testFindNextEpoch_nonEmptyDir() throws IOException {
    var walDirectory = new WalDirectory(temp);
    var seg1 = walDirectory.getWalSegment(0);
    var seg2 = walDirectory.getWalSegment(1);
    Files.createFile(seg1);
    Files.createFile(seg2);
    Assertions.assertEquals(2, walDirectory.getNextEpoch());
  }

  @Test
  void testFindNextEpoch_withGaps() throws IOException {
    var walDirectory = new WalDirectory(temp);
    var seg1 = walDirectory.getWalSegment(0);
    var seg2 = walDirectory.getWalSegment(2);
    Files.createFile(seg1);
    Files.createFile(seg2);
    Assertions.assertEquals(3, walDirectory.getNextEpoch());
  }

  @Test
  void testFindNextEpoch_withOtherFiles() throws IOException {
    var walDirectory = new WalDirectory(temp);
    var seg1 = walDirectory.getWalSegment(0);
    var commitFile = walDirectory.getCommitFile();
    Files.createFile(seg1);
    Files.createFile(commitFile);
    Assertions.assertEquals(1, walDirectory.getNextEpoch());
  }

  @Test
  void testGetSegmentImmediatelyAfter() throws IOException {
    var walDirectory = new WalDirectory(temp);
    Files.createFile(walDirectory.getWalSegment(0));
    Files.createFile(walDirectory.getWalSegment(2));
    Files.createFile(walDirectory.getWalSegment(5));

    Assertions.assertEquals(2, walDirectory.getSegmentImmediatelyAfter(0).orElseThrow());
    Assertions.assertEquals(5, walDirectory.getSegmentImmediatelyAfter(2).orElseThrow());
    Assertions.assertTrue(walDirectory.getSegmentImmediatelyAfter(5).isEmpty());
  }
}
