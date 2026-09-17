/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.rest.promql;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.okapi.promql.eval.ExpressionResult;
import org.okapi.promql.eval.InstantVectorResult;
import org.okapi.promql.eval.ScalarResult;
import org.okapi.promql.eval.VectorData;

public class PromQlResponseMapper {

  public static final String NAME = "__name__";

  public static PromQlStringListResponse mapStringList(List<String> list) {
    var response = new PromQlStringListResponse();
    response.setStatus("success");
    response.setData(list);
    return response;
  }

  public static GetPromQlResponse toResult(ExpressionResult result, RETURN_TYPE returnType) {
    if (returnType == RETURN_TYPE.VECTOR_OR_SCALAR) {
      if (result instanceof InstantVectorResult) {
        var response = new PromQlVectorResponse();
        response.setStatus("success");
        response.setData(mapInstanceVector(result));
        return response;
      } else if (result instanceof ScalarResult) {
        var response = new PromQlScalarResponse();
        response.setStatus("success");
        response.setData(mapScalar(result));
        return response;
      }
    } else if (returnType == RETURN_TYPE.MATRIX) {
      var response = new PromQlMatrixResponse();
      response.setStatus("success");
      response.setData(mapMatrixSeries(result));
      return response;
    }
    throw new IllegalStateException();
  }

  public static PromQlVectorData mapInstanceVector(ExpressionResult result) {
    var iv = (InstantVectorResult) result;
    var promQlResponse = new PromQlVectorData();
    List<VectorSeries> asVectorSeriesList =
        iv.data().stream()
            .map(
                seriesSample -> {
                  var metric = toPrometheusName(seriesSample.id());
                  var sample = fromEngineSampleToRestSample(seriesSample.sample());
                  return new VectorSeries(metric, sample);
                })
            .toList();
    promQlResponse.setResultType(PromQlResultType.VECTOR);
    promQlResponse.setResult(asVectorSeriesList);
    return promQlResponse;
  }

  public static PromQlScalarData mapScalar(ExpressionResult result) {
    var scalar = (ScalarResult) result;
    var promQlData = new PromQlScalarData();
    var now = System.currentTimeMillis() / 1000.;
    promQlData.setResult(new Sample(now, Double.toString(scalar.getValue())));
    return promQlData;
  }

  public static PromQlMatrixData mapMatrixSeries(ExpressionResult result) {
    var asIv = (InstantVectorResult) result;
    var asMat = asIv.toMatrix();
    var promQlData = new PromQlMatrixData();
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
