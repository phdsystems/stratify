# Stratify Processor Architecture

## Overview

The `stratify-processor` module provides compile-time annotation processing to auto-generate `META-INF/services` files for Java ServiceLoader discovery.

## Module Structure

```
stratify-processor/
├── doc/
│   └── 3-design/
│       ├── architecture.md          # This file
│       └── annotation-processing.md # Detailed processing flow
├── pom.xml
└── src/main/
    ├── java/dev/engineeringlab/stratify/processor/
    │   ├── AbstractProviderServiceProcessor.java
    │   ├── ProviderProcessor.java
    │   └── package-info.java
    └── resources/META-INF/services/
        └── javax.annotation.processing.Processor
```

## Component Architecture

```mermaid
graph TB
    subgraph "stratify-processor"
        PP[ProviderProcessor]
        APSP[AbstractProviderServiceProcessor]

        PP -->|extends| APSP
    end

    subgraph "javax.annotation.processing"
        AP[AbstractProcessor]
        PE[ProcessingEnvironment]
        RE[RoundEnvironment]
        F[Filer]
        M[Messager]
    end

    subgraph "stratify-annotations"
        PA[@Provider]
    end

    APSP -->|extends| AP
    APSP -->|uses| PE
    APSP -->|uses| RE
    APSP -->|writes via| F
    APSP -->|logs via| M
    PP -->|processes| PA
```

## Processing Workflow

```mermaid
flowchart TD
    A[javac starts compilation] --> B[ServiceLoader discovers processors]
    B --> C[ProviderProcessor.init]
    C --> D{More rounds?}

    D -->|Yes| E[Scan @Provider classes]
    E --> F[Find implemented interfaces]
    F --> G{Interface ends<br/>with 'Provider'?}

    G -->|Yes| H[Register for ServiceLoader]
    G -->|No| I[Skip interface]

    H --> J[Check superclass interfaces]
    I --> J
    J --> D

    D -->|No, processingOver| K[writeServiceFiles]
    K --> L[Create META-INF/services files]
    L --> M[Compilation complete]

    style A fill:#e1f5fe
    style M fill:#c8e6c9
    style H fill:#fff9c4
    style L fill:#fff9c4
```

## Sequence Diagram

```mermaid
sequenceDiagram
    participant JC as javac
    participant SL as ServiceLoader
    participant PP as ProviderProcessor
    participant APSP as AbstractProviderServiceProcessor
    participant F as Filer

    rect rgb(240, 248, 255)
        Note over JC,SL: Processor Discovery
        JC->>SL: load(Processor.class)
        SL-->>JC: [ProviderProcessor]
    end

    rect rgb(255, 248, 240)
        Note over JC,APSP: Initialization
        JC->>PP: init(ProcessingEnvironment)
        PP->>APSP: super.init()
        APSP->>APSP: Store filer, messager, typeUtils
    end

    rect rgb(240, 255, 240)
        Note over JC,APSP: Processing Rounds
        loop Each compilation round
            JC->>PP: process(annotations, roundEnv)
            PP->>APSP: super.process()

            loop Each @Provider class
                APSP->>APSP: processProviderClass()
                APSP->>APSP: findInterfaces()

                alt ends with "Provider"
                    APSP->>APSP: registerProviderInterface()
                    Note right of APSP: Store: interface → impl
                end
            end
        end
    end

    rect rgb(255, 255, 240)
        Note over APSP,F: File Generation
        JC->>PP: process() [processingOver=true]
        PP->>APSP: writeServiceFiles()

        loop Each registered interface
            APSP->>F: createResource(META-INF/services/...)
            F-->>APSP: FileObject
            APSP->>F: write(implementations)
        end
    end
```

## Class Responsibilities

| Class | Responsibility |
|-------|----------------|
| `AbstractProviderServiceProcessor` | Base class with all processing logic |
| `ProviderProcessor` | Binds to `@Provider` annotation |

## Extension Points

To support a new provider annotation:

```java
@SupportedAnnotationTypes("com.example.MyProvider")
@SupportedSourceVersion(SourceVersion.RELEASE_21)
public class MyProviderProcessor extends AbstractProviderServiceProcessor {

    @Override
    protected boolean isProviderInterface(String name) {
        // Custom logic if needed
        return name.endsWith("Provider") || name.endsWith("Service");
    }
}
```

## Related Documentation

- [Annotation Processing Details](./annotation-processing.md) - Step-by-step processing flow
