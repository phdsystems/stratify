module dev.engineeringlab.stratify.core {
  requires static lombok;
  requires dev.engineeringlab.stratify.annotation;
  requires org.slf4j;
  requires com.fasterxml.jackson.databind;
  requires com.fasterxml.jackson.dataformat.yaml;

  exports dev.engineeringlab.stratify.config;
  exports dev.engineeringlab.stratify.error;
  exports dev.engineeringlab.stratify.provider;
  exports dev.engineeringlab.stratify.registry;
}
