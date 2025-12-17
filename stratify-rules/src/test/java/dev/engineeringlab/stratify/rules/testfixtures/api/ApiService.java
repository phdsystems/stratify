package dev.engineeringlab.stratify.rules.testfixtures.api;

import dev.engineeringlab.stratify.rules.testfixtures.common.CommonClass;
import dev.engineeringlab.stratify.rules.testfixtures.spi.SpiInterface;

/** Test fixture: A service interface in the API layer. */
public interface ApiService {
  CommonClass execute();

  SpiInterface getProvider();
}
