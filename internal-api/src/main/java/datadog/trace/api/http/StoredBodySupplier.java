package datadog.trace.api.http;

import datadog.trace.api.function.Supplier;
import javax.annotation.Nonnull;

public interface StoredBodySupplier extends Supplier<String> {
  @Nonnull
  String get();
}
