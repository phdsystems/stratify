# SEA Module Structure

## Single Module (Depth 0)

```mermaid
graph TB
    subgraph "my-module (Parent POM)"
        CM[my-module-common<br/>L1: DTOs, Errors]
        S[my-module-spi<br/>L2: Provider Interfaces]
        A[my-module-api<br/>L3: Service Contracts]
        CO[my-module-core<br/>L4: Implementations]
        F[my-module-facade<br/>L5: Entry Point]
    end

    F --> CO
    CO --> A
    CO --> S
    A --> S
    A --> CM
    S --> CM
    CO --> CM

    style F fill:#4CAF50,color:#fff
    style CO fill:#FF9800,color:#fff
    style A fill:#2196F3,color:#fff
    style S fill:#9C27B0,color:#fff
    style CM fill:#607D8B,color:#fff
```

## Domain with Capabilities (Depth 1)

```mermaid
graph TB
    subgraph "llm (Aggregator)"
        subgraph "llm-provider"
            P_CM[common]
            P_S[spi]
            P_A[api]
            P_CO[core]
            P_F[facade]
        end

        subgraph "llm-evaluation"
            E_CM[common]
            E_S[spi]
            E_A[api]
            E_CO[core]
            E_F[facade]
        end

        subgraph "llm-memory"
            M_CM[common]
            M_S[spi]
            M_A[api]
            M_CO[core]
            M_F[facade]
        end
    end

    style P_F fill:#4CAF50,color:#fff
    style E_F fill:#4CAF50,color:#fff
    style M_F fill:#4CAF50,color:#fff
```

## Framework Structure (Depth 2)

```mermaid
graph TB
    subgraph "framework"
        subgraph "product-a"
            subgraph "domain-x"
                X[SEA Modules]
            end
            subgraph "domain-y"
                Y[SEA Modules]
            end
        end

        subgraph "product-b"
            subgraph "domain-z"
                Z[SEA Modules]
            end
        end
    end

    style X fill:#4CAF50
    style Y fill:#4CAF50
    style Z fill:#4CAF50
```

## Package Organization

```mermaid
graph LR
    subgraph "com.example.mymodule"
        subgraph "common"
            CM_M[model/]
            CM_E[exception/]
        end

        subgraph "spi"
            S_P[providers/]
        end

        subgraph "api"
            A_S[services/]
            A_F[factory/]
        end

        subgraph "core"
            CO_I[impl/]
            CO_R[registry/]
        end

        subgraph "facade"
            F_E[MyModule.java]
        end
    end
```

## Consumer Dependency View

```mermaid
graph TD
    subgraph "Consumer Project"
        C[Consumer Code]
    end

    subgraph "my-module (Maven)"
        F[my-module<br/>artifactId]
    end

    subgraph "Transitive (hidden)"
        CM[my-module-common]
        S[my-module-spi]
        A[my-module-api]
        CO[my-module-core]
    end

    C -->|depends on| F
    F -.->|includes| CM
    F -.->|includes| S
    F -.->|includes| A
    F -.->|includes| CO

    style F fill:#4CAF50,color:#fff
    style CM fill:#ccc
    style S fill:#ccc
    style A fill:#ccc
    style CO fill:#ccc
```

## Maven Module Hierarchy

```
my-module/
├── pom.xml                     # packaging: pom
│   └── <modules>
│       ├── my-module-common
│       ├── my-module-spi
│       ├── my-module-api
│       ├── my-module-core
│       └── my-module-facade
│
├── my-module-common/
│   └── pom.xml                 # artifactId: my-module-common
│
├── my-module-spi/
│   └── pom.xml                 # artifactId: my-module-spi
│
├── my-module-api/
│   └── pom.xml                 # artifactId: my-module-api
│
├── my-module-core/
│   └── pom.xml                 # artifactId: my-module-core
│
└── my-module-facade/
    └── pom.xml                 # artifactId: my-module (NOT -facade!)
```
