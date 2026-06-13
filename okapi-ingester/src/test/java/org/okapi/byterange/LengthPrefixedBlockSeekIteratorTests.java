/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.byterange;

import com.google.common.primitives.Ints;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Test;
import org.okapi.io.NotEnoughBytesException;
import org.okapi.io.StreamReadingException;
import org.okapi.metrics.io.MetricsPage;
import org.okapi.metrics.io.MetricsPageCodec;
import org.okapi.metrics.primitives.GaugeBlock;
import org.okapi.metrics.primitives.HistoBlock;
import org.okapi.rest.metrics.ExportMetricsRequest;
import org.okapi.rest.metrics.MetricType;
import org.okapi.rest.metrics.payloads.Gauge;
import org.okapi.rest.metrics.payloads.Histo;
import org.okapi.rest.metrics.payloads.HistoPoint;
import org.okapi.s3.ByteArrayByteRangeSupplier;
import org.okapi.testutils.OkapiTestUtils;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.okapi.arrays.ArrayUtils.concatArrays;

@Slf4j
public class LengthPrefixedBlockSeekIteratorTests {

  @Test
  void testBodySeek()
      throws IOException, NotEnoughBytesException, StreamReadingException, RangeIterationException {
    var bytes = getSingleGaugeMetricsPage();
    var byteSeek = new ByteArrayByteRangeSupplier(bytes);
    var iterator = new LengthPrefixedBlockSeekIterator(byteSeek);
    iterator.readMetadata();
    iterator.readOffsetTable();
    var offsetAndLen = iterator.getOffsetAndLen("metric1{}");
    assertTrue(offsetAndLen.isPresent());

    var gaugeBlock = offsetAndLen.get();
    var bodyBytes = iterator.readBlock(gaugeBlock.getOffset(), gaugeBlock.getLen());
    var gauge = new GaugeBlock();
    gauge.fromChecksummedByteArray(bodyBytes, 0, bodyBytes.length);
    var secondly = gauge.getSecondlyStat(1L, new double[] {0.0, 1.0});
    assertEquals(1, secondly.getMean());
  }

  @Test
  void testBodySeek_mixedMetrics()
      throws IOException, RangeIterationException, NotEnoughBytesException, StreamReadingException {
    var bytes = getMixedMetricsPage();
    var byteSeek = new ByteArrayByteRangeSupplier(bytes);
    var iterator = new LengthPrefixedBlockSeekIterator(byteSeek);
    iterator.readMetadata();
    iterator.readOffsetTable();
    var gaugeOffsetAndLen = iterator.getOffsetAndLen("metric1{}");
    assertTrue(gaugeOffsetAndLen.isPresent());
    var histoOffsetAndLen = iterator.getOffsetAndLen("metric2{}");
    assertTrue(histoOffsetAndLen.isPresent());

    // read gauge block -> validate gauge
    var gaugeBlock = gaugeOffsetAndLen.get();
    var bodyBytes = iterator.readBlock(gaugeBlock.getOffset(), gaugeBlock.getLen());
    var gauge = new GaugeBlock();
    gauge.fromChecksummedByteArray(bodyBytes, 0, bodyBytes.length);
    var secondly = gauge.getSecondlyStat(1L, new double[] {0.0, 1.0});
    assertEquals(1, secondly.getMean());

    // read histo block -> validate histo
    var histoBlock = histoOffsetAndLen.get();
    var histoBodyBytes = iterator.readBlock(histoBlock.getOffset(), histoBlock.getLen());
    var histo = new HistoBlock();
    histo.fromChecksummedByteArray(histoBodyBytes, 0, histoBodyBytes.length);
    var readonlyHistoOpt = histo.getHistogram(1000L);
    assertTrue(readonlyHistoOpt.isPresent());
    var readonlyHisto = readonlyHistoOpt.get();
    assertEquals(3, readonlyHisto.getBucketCounts().size());
    OkapiTestUtils.assertListEquals(readonlyHisto.getBuckets(), Arrays.asList(10.f, 20.f));
    OkapiTestUtils.assertListEquals(readonlyHisto.getBucketCounts(), Arrays.asList(1L, 2L, 3L));
  }

  @Test
  void testUnknownBlockPath_beforeAndAfterReadOffsetTable()
      throws IOException, RangeIterationException, NotEnoughBytesException, StreamReadingException {
    var bytes = getSingleGaugeMetricsPage();
    var byteSeek = new ByteArrayByteRangeSupplier(bytes);
    var iterator = new LengthPrefixedBlockSeekIterator(byteSeek);

    // Before reading offset table
    assertFalse(iterator.getOffsetAndLen("doesNotExist{}").isPresent());
    assertFalse(iterator.hasBlock("doesNotExist{}"));

    iterator.readMetadata();
    iterator.readOffsetTable();

    // After reading offset table
    assertFalse(iterator.getOffsetAndLen("doesNotExist{}").isPresent());
    assertFalse(iterator.hasBlock("doesNotExist{}"));
  }

  @Test
  void testReadOffsetTable_idempotent()
      throws IOException, RangeIterationException, NotEnoughBytesException, StreamReadingException {
    var bytes = getSingleGaugeMetricsPage();
    var byteSeek = new ByteArrayByteRangeSupplier(bytes);
    var iterator = new LengthPrefixedBlockSeekIterator(byteSeek);
    iterator.readMetadata();

    iterator.readOffsetTable();
    var first = iterator.getOffsetAndLen("metric1{}").orElseThrow();

    // Read again and ensure values are stable
    iterator.readOffsetTable();
    var second = iterator.getOffsetAndLen("metric1{}").orElseThrow();
    assertEquals(first.getOffset(), second.getOffset());
    assertEquals(first.getLen(), second.getLen());
  }

  @Test
  void testReadOffsetTable_withoutReadMetadata_throws() throws IOException {
    var bytes = getSingleGaugeMetricsPage();
    var byteSeek = new ByteArrayByteRangeSupplier(bytes);
    var iterator = new LengthPrefixedBlockSeekIterator(byteSeek);

    // Calling readOffsetTable before readMetadata should fail
    assertThrows(Exception.class, () -> iterator.readOffsetTable());
  }

  @Test
  void testEmptyMetricsPage()
      throws IOException, RangeIterationException, NotEnoughBytesException, StreamReadingException {
    var page = new MetricsPage(1000L, 1000L, 1000, 0.01);
    var codec = new MetricsPageCodec();
    var bytes = codec.serialize(page);

    var byteSeek = new ByteArrayByteRangeSupplier(bytes);
    var iterator = new LengthPrefixedBlockSeekIterator(byteSeek);
    iterator.readMetadata();
    iterator.readOffsetTable();

    assertFalse(iterator.hasBlock("anything{}"));
    assertFalse(iterator.getOffsetAndLen("anything{}").isPresent());
  }

  @Test
  void testMultiplePages_forwardIsolation()
      throws IOException, RangeIterationException, NotEnoughBytesException, StreamReadingException {
    var pageA = getSingleGaugeMetricsPageWithName("metricA");
    var pageB = getSingleGaugeMetricsPageWithName("metricB");
    var bytes = concatArrays(pageA, pageB);

    var byteSeek = new ByteArrayByteRangeSupplier(bytes);
    var iterator = new LengthPrefixedBlockSeekIterator(byteSeek);

    // Page A
    iterator.readMetadata();
    iterator.readOffsetTable();
    assertTrue(iterator.hasBlock("metricA{}"));
    assertFalse(iterator.hasBlock("metricB{}"));

    iterator.forward();

    // Page B
    iterator.readMetadata();
    iterator.readOffsetTable();
    assertTrue(iterator.hasBlock("metricB{}"));
    assertFalse(iterator.hasBlock("metricA{}"));
  }

  @Test
  void testCorruptedOffsetTableChecksum()
      throws IOException, RangeIterationException, NotEnoughBytesException, StreamReadingException {
    var bytes = getSingleGaugeMetricsPage();
    var corrupted = Arrays.copyOf(bytes, bytes.length);

    // Compute bodyStart = 12 + mdLen
    var mdLen = Ints.fromByteArray(Arrays.copyOfRange(corrupted, 4, 8));
    var bodyStart = 12 + mdLen;
    var offsetTableLen =
        Ints.fromByteArray(Arrays.copyOfRange(corrupted, bodyStart, bodyStart + 4));

    // Flip a byte inside the offset table bytes to break CRC
    var flipIndex = bodyStart + 4 + Math.max(0, offsetTableLen - 1);
    corrupted[flipIndex] ^= 0x1;

    var byteSeek = new ByteArrayByteRangeSupplier(corrupted);
    var iterator = new LengthPrefixedBlockSeekIterator(byteSeek);
    iterator.readMetadata();

    assertThrows(IOException.class, () -> iterator.readOffsetTable());
  }

  @Test
  void testCorruptedBlockChecksum()
      throws IOException, RangeIterationException, NotEnoughBytesException {
    var bytes = getSingleGaugeMetricsPage();

    // First, discover the block offset and len from a clean read
    var cleanSeek = new ByteArrayByteRangeSupplier(bytes);
    var probe = new LengthPrefixedBlockSeekIterator(cleanSeek);
    probe.readMetadata();
    probe.readOffsetTable();
    var offsetAndLen = probe.getOffsetAndLen("metric1{}").orElseThrow();

    // Compute where the block bytes live in the raw page
    var mdLen = Ints.fromByteArray(Arrays.copyOfRange(bytes, 4, 8));
    var bodyStart = 12 + mdLen;
    var offsetTableLen = Ints.fromByteArray(Arrays.copyOfRange(bytes, bodyStart, bodyStart + 4));
    var blockStreamStart = bodyStart + 4 + offsetTableLen + 4;
    var blockStart = (int) (blockStreamStart + offsetAndLen.getOffset());

    var corrupted = Arrays.copyOf(bytes, bytes.length);
    corrupted[blockStart] ^= 0x1; // flip one byte in the block payload

    var byteSeek = new ByteArrayByteRangeSupplier(corrupted);
    var iterator = new LengthPrefixedBlockSeekIterator(byteSeek);
    iterator.readMetadata();
    iterator.readOffsetTable();

    var ol = iterator.getOffsetAndLen("metric1{}").orElseThrow();
    var bodyBytes = iterator.readBlock(ol.getOffset(), ol.getLen());

    var gauge = new GaugeBlock();
    assertThrows(
        StreamReadingException.class,
        () -> gauge.fromChecksummedByteArray(bodyBytes, 0, bodyBytes.length));
  }

  @Test
  void testBlockStreamStartAlignment()
      throws IOException, RangeIterationException, NotEnoughBytesException, StreamReadingException {
    var bytes = getMixedMetricsPage();
    var byteSeek = new ByteArrayByteRangeSupplier(bytes);
    var iterator = new LengthPrefixedBlockSeekIterator(byteSeek);
    iterator.readMetadata();
    iterator.readOffsetTable();

    // Read the full doc block as seen by the base iterator
    var fullDocBlock = iterator.readPageBody();
    var offsetTableLen = Ints.fromByteArray(Arrays.copyOfRange(fullDocBlock, 0, 4));
    var blockStreamStart = 4 + offsetTableLen + 4;

    var ol = iterator.getOffsetAndLen("metric1{}").orElseThrow();
    var sliced =
        Arrays.copyOfRange(
            fullDocBlock,
            (int) (blockStreamStart + ol.getOffset()),
            (int) (blockStreamStart + ol.getOffset() + ol.getLen()));
    var viaIterator = iterator.readBlock(ol.getOffset(), ol.getLen());

    assertArrayEquals(sliced, viaIterator);
  }

  @Test
  void testNonZeroOffsetsAndReadable()
      throws IOException, RangeIterationException, NotEnoughBytesException, StreamReadingException {
    var bytes = getMixedMetricsPage();
    var byteSeek = new ByteArrayByteRangeSupplier(bytes);
    var iterator = new LengthPrefixedBlockSeekIterator(byteSeek);
    iterator.readMetadata();
    iterator.readOffsetTable();

    var olGauge = iterator.getOffsetAndLen("metric1{}").orElseThrow();
    var olHisto = iterator.getOffsetAndLen("metric2{}").orElseThrow();

    assertTrue(olGauge.getOffset() >= 0);
    assertTrue(olHisto.getOffset() >= 0);

    var gaugeBytes = iterator.readBlock(olGauge.getOffset(), olGauge.getLen());
    var gauge = new GaugeBlock();
    gauge.fromChecksummedByteArray(gaugeBytes, 0, gaugeBytes.length);
  }

  @Test
  void testLargeOffsetTable_gauges()
      throws IOException, RangeIterationException, NotEnoughBytesException, StreamReadingException {
    var page = new MetricsPage(1000L, 1000L, 1000, 0.01);
    for (int i = 0; i < 60; i++) {
      var req =
          ExportMetricsRequest.builder()
              .metricName("metric_" + i)
              .type(MetricType.GAUGE)
              .gauge(Gauge.builder().ts(List.of(1000L, 2000L)).value(List.of(1.0f, 2.0f)).build())
              .build();
      page.append(req);
    }
    var codec = new MetricsPageCodec();
    var bytes = codec.serialize(page);

    var byteSeek = new ByteArrayByteRangeSupplier(bytes);
    var iterator = new LengthPrefixedBlockSeekIterator(byteSeek);
    iterator.readMetadata();
    iterator.readOffsetTable();

    for (int i : new int[] {0, 5, 25, 40, 59}) {
      var path = "metric_" + i + "{}";
      assertTrue(iterator.hasBlock(path));
      var ol = iterator.getOffsetAndLen(path).orElseThrow();
      var blockBytes = iterator.readBlock(ol.getOffset(), ol.getLen());
      var gauge = new GaugeBlock();
      gauge.fromChecksummedByteArray(blockBytes, 0, blockBytes.length);
    }
  }

  byte[] getSingleGaugeMetricsPage() throws IOException {
    var page = new MetricsPage(1000L, 1000L, 1000, 0.01);
    var request =
        ExportMetricsRequest.builder()
            .metricName("metric1")
            .type(MetricType.GAUGE)
            .gauge(
                Gauge.builder()
                    .ts(List.of(1000L, 2000L, 3000L))
                    .value(List.of(1.0f, 2.0f, 3.0f))
                    .build())
            .build();
    page.append(request);
    var codec = new MetricsPageCodec();
    return codec.serialize(page);
  }

  byte[] getSingleGaugeMetricsPageWithName(String metricName) throws IOException {
    var page = new MetricsPage(1000L, 1000L, 1000, 0.01);
    var request =
        ExportMetricsRequest.builder()
            .metricName(metricName)
            .type(MetricType.GAUGE)
            .gauge(
                Gauge.builder()
                    .ts(List.of(1000L, 2000L, 3000L))
                    .value(List.of(1.0f, 2.0f, 3.0f))
                    .build())
            .build();
    page.append(request);
    var codec = new MetricsPageCodec();
    return codec.serialize(page);
  }

  byte[] getMixedMetricsPage() throws IOException {
    var page = new MetricsPage(1000L, 1000L, 1000, 0.01);
    var request =
        ExportMetricsRequest.builder()
            .metricName("metric1")
            .type(MetricType.GAUGE)
            .gauge(
                Gauge.builder()
                    .ts(List.of(1000L, 2000L, 3000L))
                    .value(List.of(1.0f, 2.0f, 3.0f))
                    .build())
            .build();
    var request2 =
        ExportMetricsRequest.builder()
            .metricName("metric2")
            .type(MetricType.HISTO)
            .histo(
                Histo.builder()
                    .histoPoints(
                        Arrays.asList(
                            HistoPoint.builder()
                                .start(1000L)
                                .temporality(HistoPoint.TEMPORALITY.DELTA)
                                .buckets(new float[] {10.f, 20.f})
                                .bucketCounts(new long[] {1, 2, 3})
                                .build()))
                    .build())
            .build();
    page.append(request);
    page.append(request2);
    var codec = new MetricsPageCodec();
    return codec.serialize(page);
  }
}
