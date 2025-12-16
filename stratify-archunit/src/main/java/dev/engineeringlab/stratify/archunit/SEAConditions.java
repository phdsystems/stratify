package dev.engineeringlab.stratify.archunit;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;

/**
 * Helper predicates for building SEA ArchUnit rules.
 *
 * <p>Provides reusable predicates for identifying:
 *
 * <ul>
 *   <li>Classes belonging to specific SEA layers
 *   <li>Provider implementations
 *   <li>Facade classes
 * </ul>
 */
public final class SEAConditions {

  private SEAConditions() {}

  /** Predicate for classes in the Common layer. */
  public static DescribedPredicate<JavaClass> inCommonLayer() {
    return DescribedPredicate.describe(
        "in common layer", javaClass -> javaClass.getPackageName().contains(".common"));
  }

  /** Predicate for classes in the SPI layer. */
  public static DescribedPredicate<JavaClass> inSpiLayer() {
    return DescribedPredicate.describe(
        "in SPI layer", javaClass -> javaClass.getPackageName().contains(".spi"));
  }

  /** Predicate for classes in the API layer. */
  public static DescribedPredicate<JavaClass> inApiLayer() {
    return DescribedPredicate.describe(
        "in API layer", javaClass -> javaClass.getPackageName().contains(".api"));
  }

  /** Predicate for classes in the Core layer. */
  public static DescribedPredicate<JavaClass> inCoreLayer() {
    return DescribedPredicate.describe(
        "in core layer",
        javaClass ->
            javaClass.getPackageName().contains(".core")
                || javaClass.getPackageName().contains(".impl"));
  }

  /** Predicate for classes in the Facade layer. */
  public static DescribedPredicate<JavaClass> inFacadeLayer() {
    return DescribedPredicate.describe(
        "in facade layer",
        javaClass ->
            javaClass.getPackageName().contains(".facade")
                || javaClass.getSimpleName().endsWith("Facade"));
  }

  /** Predicate for provider implementations. */
  public static DescribedPredicate<JavaClass> isProvider() {
    return DescribedPredicate.describe(
        "is a provider implementation",
        javaClass ->
            javaClass.isAnnotatedWith("dev.engineeringlab.stratify.annotation.Provider")
                || javaClass.getSimpleName().endsWith("Provider"));
  }

  /** Predicate for facade classes. */
  public static DescribedPredicate<JavaClass> isFacade() {
    return DescribedPredicate.describe(
        "is a facade",
        javaClass ->
            javaClass.isAnnotatedWith("dev.engineeringlab.stratify.annotation.Facade")
                || javaClass.getSimpleName().endsWith("Facade"));
  }

  /** Predicate for internal classes. */
  public static DescribedPredicate<JavaClass> isInternal() {
    return DescribedPredicate.describe(
        "is internal",
        javaClass ->
            javaClass.getPackageName().contains(".internal")
                || javaClass.isAnnotatedWith("dev.engineeringlab.stratify.annotation.Internal"));
  }
}
