package dev.engineeringlab.stratify.archunit.testfixtures.facade;

import dev.engineeringlab.stratify.annotation.Facade;
import dev.engineeringlab.stratify.archunit.testfixtures.api.ApiService;
import dev.engineeringlab.stratify.archunit.testfixtures.core.CoreImplementation;

/** Test fixture: A facade class. */
@Facade
public final class TestFacade {
  private TestFacade() {}

  public static ApiService create() {
    return new CoreImplementation();
  }
}
