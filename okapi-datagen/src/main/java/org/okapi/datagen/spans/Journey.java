/*
 * SPDX-License-Identifier: Apache-2.0
 */
package org.okapi.datagen.spans;

import io.opentelemetry.proto.trace.v1.Span.SpanKind;
import java.util.List;
import lombok.Builder;
import lombok.Singular;
import lombok.Value;

@Value
@Builder
public class Journey {
  @Singular List<Step> rootSteps;

  public static Journey defaultJourney() {
    var search =
        Step.builder()
            .spanName("catalog/search")
            .component("catalog-service")
            .kind(SpanKind.SPAN_KIND_SERVER)
            .attribute(OtelShorthand.kv("shop.category", "astronomy"))
            .build();
    var detail =
        Step.builder()
            .spanName("catalog/product-details")
            .component("catalog-service")
            .kind(SpanKind.SPAN_KIND_SERVER)
            .attribute(OtelShorthand.kv("shop.item", "telescope"))
            .build();
    var addToCart =
        Step.builder()
            .spanName("cart/add")
            .component("cart-service")
            .kind(SpanKind.SPAN_KIND_SERVER)
            .attribute(OtelShorthand.kv("shop.item", "telescope"))
            .build();

    var paymentGateway =
        Step.builder()
            .spanName("payment-gateway/charge")
            .component("payment-gateway")
            .kind(SpanKind.SPAN_KIND_CLIENT)
            .attribute(OtelShorthand.kv("payment.provider", "cosmos-pay"))
            .build();

    var payment =
        Step.builder()
            .spanName("payment/authorize")
            .component("payment-service")
            .kind(SpanKind.SPAN_KIND_SERVER)
            .attribute(OtelShorthand.kv("payment.method", "card"))
            .child(paymentGateway)
            .build();

    var ordersDb =
        Step.builder()
            .spanName("orders-db/write")
            .component("orders-db")
            .kind(SpanKind.SPAN_KIND_CLIENT)
            .attribute(OtelShorthand.kv("db.system", "postgresql"))
            .build();

    var inventory =
        Step.builder()
            .spanName("inventory/reserve")
            .component("inventory-service")
            .kind(SpanKind.SPAN_KIND_SERVER)
            .attribute(OtelShorthand.kv("shop.stock", "reserve"))
            .child(ordersDb)
            .build();

    var shipping =
        Step.builder()
            .spanName("shipping/quote")
            .component("shipping-service")
            .kind(SpanKind.SPAN_KIND_SERVER)
            .attribute(OtelShorthand.kv("shipping.zone", "earth-orbit"))
            .build();

    var checkout =
        Step.builder()
            .spanName("checkout/complete")
            .component("checkout-service")
            .kind(SpanKind.SPAN_KIND_SERVER)
            .attribute(OtelShorthand.kv("checkout.currency", "USD"))
            .child(payment)
            .child(inventory)
            .child(shipping)
            .build();

    var notify =
        Step.builder()
            .spanName("notification/send-confirmation")
            .component("notification-service")
            .kind(SpanKind.SPAN_KIND_SERVER)
            .attribute(OtelShorthand.kv("notification.channel", "email"))
            .build();

    var frontend =
        Step.builder()
            .spanName("frontend/purchase")
            .component("frontend")
            .kind(SpanKind.SPAN_KIND_SERVER)
            .attribute(OtelShorthand.kv("shop.journey", "telescope-purchase"))
            .child(search)
            .child(detail)
            .child(addToCart)
            .child(checkout)
            .child(notify)
            .build();

    return Journey.builder().rootStep(frontend).build();
  }
}
