package dev.engineeringlab.stratify.rules.testfixtures.core;

import dev.engineeringlab.stratify.rules.testfixtures.api.ApiService;
import dev.engineeringlab.stratify.rules.testfixtures.common.CommonClass;
import dev.engineeringlab.stratify.rules.testfixtures.spi.SpiInterface;

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
