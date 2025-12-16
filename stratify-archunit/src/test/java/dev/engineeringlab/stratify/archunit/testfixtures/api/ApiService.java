package dev.engineeringlab.stratify.archunit.testfixtures.api;

import dev.engineeringlab.stratify.archunit.testfixtures.common.CommonClass;
import dev.engineeringlab.stratify.archunit.testfixtures.spi.SpiInterface;

/** Test fixture: A service interface in the API layer. */
public interface ApiService {
  CommonClass execute();

  SpiInterface getProvider();
}
