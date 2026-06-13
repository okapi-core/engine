/*
 * Copyright The OkapiCore Authors
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.promql;

import java.util.*;
import org.okapi.promql.eval.ExpressionResult;
import org.okapi.promql.eval.InstantVectorResult;
import org.okapi.promql.eval.ScalarResult;
import org.okapi.promql.eval.VectorData;

public class PromQlResponseMapper {

  public static final String NAME = "__name__";

  public static GetPromQlResponse<List<String>> mapStringList(List<String> list) {
    var response = new GetPromQlResponse<List<String>>();
    response.setStatus("success");
    response.setData(list);
    return response;
  }

  public static GetPromQlResponse<PromQlData<?>> toResult(
      ExpressionResult result, RETURN_TYPE returnType) {
    if (returnType == RETURN_TYPE.VECTOR_OR_SCALAR) {
      var response = new GetPromQlResponse<PromQlData<?>>();
      if (result instanceof InstantVectorResult) {
        var data = mapInstanceVector(result);
        response.setStatus("success");
        response.setData(data);
        return response;
      } else if (result instanceof ScalarResult) {
        var data = mapScalar(result);
        response.setStatus("success");
        response.setData(data);
        return response;
      }
    } else if (returnType == RETURN_TYPE.MATRIX) {
      var response = new GetPromQlResponse<PromQlData<?>>();
      var data = mapMatrixSeries(result);
      response.setStatus("success");
      response.setData(data);
      return response;
    }
    throw new IllegalStateException();
  }

  public static PromQlData<List<VectorSeries>> mapInstanceVector(ExpressionResult result) {
    var iv = (InstantVectorResult) result;
    var promQlResponse = new PromQlData<List<VectorSeries>>();
    promQlResponse.setResultType(PromQlResultType.MATRIX);
    List<VectorSeries> asVectorSeriesList =
        iv.data().stream()
            .map(
                seriesSample -> {
                  var metric = toPrometheusName(seriesSample.id());
                  var sample = fromEngineSampleToRestSample(seriesSample.sample());
                  var series = new VectorSeries(metric, sample);
                  return series;
                })
            .toList();
    promQlResponse.setResultType(PromQlResultType.VECTOR);
    promQlResponse.setResult(asVectorSeriesList);
    return promQlResponse;
  }

  public static PromQlData<Sample> mapScalar(ExpressionResult result) {
    var scalar = (ScalarResult) result;
    var promQlData = new PromQlData<Sample>();
    promQlData.setResultType(PromQlResultType.SCALAR);
    var now = System.currentTimeMillis() / 1000.;
    promQlData.setResult(new Sample(now, Double.toString(scalar.getValue())));
    return promQlData;
  }

  public static PromQlData<List<MatrixSeries>> mapMatrixSeries(ExpressionResult result) {
    var asIv = (InstantVectorResult) result;
    var asMat = asIv.toMatrix();
    var promQlData = new PromQlData<List<MatrixSeries>>();
    List<MatrixSeries> asMatSeries =
        asMat.entrySet().stream()
            .map(
                w -> {
                  var toName = toPrometheusName(w.getKey());
                  var values =
                      w.getValue().stream()
                          .map(PromQlResponseMapper::fromEngineSampleToRestSample)
                          .toList();
                  return new MatrixSeries(toName, values);
                })
            .toList();
    promQlData.setResult(asMatSeries);
    promQlData.setResultType(PromQlResultType.MATRIX);
    return promQlData;
  }

  public static Sample fromEngineSampleToRestSample(VectorData.Sample engineSample) {
    var inSecond = engineSample.ts() / 1000.;
    var strVal = "" + engineSample.value();
    return new Sample(inSecond, strVal);
  }

  public static Map<String, String> toPrometheusName(VectorData.SeriesId seriesId) {
    return Collections.unmodifiableMap(
        new HashMap<>(seriesId.labels().tags()) {
          {
            put(NAME, seriesId.metric());
          }
        });
  }

  public enum RETURN_TYPE {
    VECTOR_OR_SCALAR,
    MATRIX,
  }
}
