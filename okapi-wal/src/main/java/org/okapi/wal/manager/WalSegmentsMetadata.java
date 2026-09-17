/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.wal.manager;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public class WalSegmentsMetadata {
  private final List<WalSegmentMetadata> segmentMetadata;

  private WalSegmentsMetadata(List<WalSegmentMetadata> segmentMetadata) {
    this.segmentMetadata = new CopyOnWriteArrayList<>(segmentMetadata);
  }

  private WalSegmentsMetadata() {
    this.segmentMetadata = new CopyOnWriteArrayList<>();
  }

  public void addMetadata(WalSegmentMetadata segmentMetadata) {
    this.segmentMetadata.add(segmentMetadata);
  }

  public void upsertMetadata(WalSegmentMetadata segmentMetadata) {
    this.segmentMetadata.removeIf(
        meta -> meta.getSegmentNumber() == segmentMetadata.getSegmentNumber());
    this.segmentMetadata.add(segmentMetadata);
  }

  public List<WalSegmentMetadata> getSegmentMetadata() {
    // Return a stable snapshot to avoid concurrent modification while streaming.
    return List.copyOf(segmentMetadata);
  }

  public static WalSegmentsMetadata ofEmpty() {
    return new WalSegmentsMetadata();
  }

  public static WalSegmentsMetadata fromExisting(WalSegmentsMetadata metadata) {
    var data = new WalSegmentsMetadata();
    for (var meta : metadata.getSegmentMetadata()) {
      data.addMetadata(meta);
    }
    return data;
  }
}
