# M229 — Versioned domain contracts

Engine 0.65.1 / Build 818 integration candidate.

Domain schema versions are independent of app, engine and storage versions. The public request normalizer and generated-program projection enforce v1 headers. Unversioned legacy records migrate by an immutable, extension-preserving transformation; explicit unsupported versions fail with a typed error instead of being interpreted as current data. Program validation covers stable unique session identities, finite time limits, integer set counts and prescription bounds. Exposure validation preserves unknown effort as null and supports signed assisted loading.

Core TypeScript declarations describe request, program, prescription and exposure boundaries. They do not claim to type every historical compatibility export. Machine-readable descriptors and runtime validators share the v1 boundary. The standalone export includes these declarations.

Prescription identity, field ownership and shell projection primitives now live in `shell-projection.js`. Both generation adaptation and volume repair depend on that lower-level module. The former adapter–volume dependency cycle is removed; all canonical computation modules form an acyclic graph.

Validation: explicit migration/JSON roundtrips, input immutability, malformed and future-version refusal, generated contract validation, module graph, prescription ownership and M226/M227 boundary regressions. The release and adaptation gates retain Build 816 golden output parity. This milestone introduces contract metadata without changing training decisions.
