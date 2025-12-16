# SEA Layer Dependencies

```mermaid
graph TD
    subgraph "Consumer View"
        C[Consumer Code]
    end

    subgraph "SEA Module"
        F[L5: FACADE]
        CO[L4: CORE]
        A[L3: API]
        S[L2: SPI]
        CM[L1: COMMON]
    end

    C --> F
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
    style C fill:#E91E63,color:#fff
```

## Dependency Rules

| Layer | Can Depend On |
|-------|---------------|
| FACADE (L5) | CORE |
| CORE (L4) | API, SPI, COMMON |
| API (L3) | SPI, COMMON |
| SPI (L2) | COMMON |
| COMMON (L1) | External only |

## Visibility

```mermaid
graph LR
    subgraph "Visible to Consumers"
        F[FACADE]
    end

    subgraph "Hidden from Consumers"
        CO[CORE]
        A[API]
        S[SPI]
        CM[COMMON]
    end

    F -.->|re-exports| A
    F -.->|re-exports| CM

    style F fill:#4CAF50,color:#fff
    style CO fill:#ccc
    style A fill:#ccc
    style S fill:#ccc
    style CM fill:#ccc
```
