# M171 shared generation context and transactional repair

App **3.221.0**, build **777**, Pursuit Engine **0.62.7**.

Item 6 is implemented across normal generation repair, phase-transition continuity repair, and static-cycle repair. Candidate structure is fingerprinted and memoized, catalog/schedule/equipment/avoidance lookups are shared per generation, and candidate ordering is lexicographic by critical, major, warning, then objective.
