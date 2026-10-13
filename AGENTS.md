# ReconcileX Engineering Instructions

## Goal
Ship a correct, testable payment reconciliation MVP
within two days.

## Priorities
1. Correctness
2. Security
3. Testability
4. Performance
5. Readability
6. Visual polish

## Engineering rules
- Java 21, Spring Boot, Maven, PostgreSQL.
- Use minor currency units stored as long.
- Never use floating-point values for money.
- Never silently discard duplicate transactions.
- Prefer simple, maintainable solutions.
- Do not add unnecessary frameworks or services.
- Add meaningful tests for core business logic.
- Validate all external inputs.
- Do not hardcode credentials or secrets.
- Never invent benchmark results or test outcomes.

## Subagent rules
- Delegate only independent tasks.
- Assign exclusive file ownership to each agent.
- The main agent owns integration and shared interfaces.
- Never overwrite another agent's work.
- Report actual files changed and tests executed.
- Do not commit, reset, or delete work without
  coordination from the main agent.

## Definition of done
- Application compiles.
- Core tests pass.
- End-to-end reconciliation works.
- CSV export works.
- README has verified setup instructions.
- Remaining limitations are documented.
