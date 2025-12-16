package dev.engineeringlab.stratify.archunit.testfixtures.core;

import dev.engineeringlab.stratify.archunit.testfixtures.api.ApiService;
import dev.engineeringlab.stratify.archunit.testfixtures.common.CommonClass;
import dev.engineeringlab.stratify.archunit.testfixtures.spi.SpiInterface;

/** Test fixture: An implementation in the Core layer. */
public class CoreImplementation implements ApiService {
  @Override
  public CommonClass execute() {
    return new CommonClass();
  }

  @Override
  public SpiInterface getProvider() {
    return CommonClass::new;
  }
}
