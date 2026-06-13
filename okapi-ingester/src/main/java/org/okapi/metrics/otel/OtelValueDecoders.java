package org.okapi.metrics.otel;

import io.opentelemetry.proto.common.v1.AnyValue;
import io.opentelemetry.proto.common.v1.KeyValue;
import io.opentelemetry.proto.metrics.v1.Exemplar;
import io.opentelemetry.proto.metrics.v1.NumberDataPoint;
import java.util.Arrays;
import java.util.Optional;
import org.okapi.rest.common.AnyValueJson;
import org.okapi.rest.common.KeyValueJson;
import org.okapi.rest.common.NumberValue;

public class OtelValueDecoders {
  public static Optional<NumberValue> decodeExemplarAsDouble(Exemplar exemplar) {
    var builder = NumberValue.builder();
    return switch (exemplar.getValueCase()) {
      case AS_DOUBLE -> Optional.of(builder.aDouble(exemplar.getAsDouble()).build());
      case AS_INT -> Optional.of(builder.anInteger(exemplar.getAsInt()).build());
      case VALUE_NOT_SET -> Optional.empty();
    };
  }

  public static Optional<AnyValueJson> decodeAnyValue(AnyValue anyValue) {
    var anyVal = AnyValueJson.builder();
    return switch (anyValue.getValueCase()) {
      case STRING_VALUE -> Optional.of(anyVal.aString(anyValue.getStringValue()).build());
      case BOOL_VALUE -> Optional.of(anyVal.aBoolean(anyValue.getBoolValue()).build());
      case INT_VALUE -> Optional.of(anyVal.anInteger(anyValue.getIntValue()).build());
      case DOUBLE_VALUE -> Optional.of(anyVal.aDouble(anyValue.getDoubleValue()).build());
      default -> Optional.empty();
    };
  }

  public static Optional<KeyValueJson> decodeKv(KeyValue keyValue) {
    var key = keyValue.getKey();
    var anyVal = decodeAnyValue(keyValue.getValue());
    return anyVal.map(v -> KeyValueJson.builder().key(key).value(v).build());
  }

  public static double extractNumberAsDouble(NumberDataPoint p) {
    return switch (p.getValueCase()) {
      case AS_DOUBLE -> p.getAsDouble();
      case AS_INT -> p.getAsInt();
      case VALUE_NOT_SET -> 0.0d;
    };
  }

  public static String anyValueToString(AnyValue v) {
    if (v == null) return "";
    return switch (v.getValueCase()) {
      case STRING_VALUE -> v.getStringValue();
      case BOOL_VALUE -> Boolean.toString(v.getBoolValue());
      case INT_VALUE -> Long.toString(v.getIntValue());
      case DOUBLE_VALUE -> Double.toString(v.getDoubleValue());
      case ARRAY_VALUE -> "unrecognized_arr";
      case KVLIST_VALUE -> "unrecognized_kvlist";
      case BYTES_VALUE -> Arrays.toString(v.getBytesValue().toByteArray());
      case VALUE_NOT_SET -> "";
    };
  }
}
