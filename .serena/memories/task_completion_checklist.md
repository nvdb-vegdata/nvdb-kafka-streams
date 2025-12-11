# Task Completion Checklist

When a coding task is completed, follow this checklist to ensure quality and correctness:

## 1. Code Quality
- [ ] Code follows project conventions (see `code_style_and_conventions.md`)
- [ ] No unnecessary comments or boilerplate
- [ ] Self-documenting code with clear naming
- [ ] Immutability preferred (`val` over `var`)
- [ ] Null-safety leveraged, no `!!` operators

## 2. Compilation & Type Checking
- [ ] **Use Kotlin LSP to check for compilation errors** (primary method)
  - LSP configured via `opencode.json` with `kotlin-lsp --stdio`
  - Read relevant Kotlin files to trigger LSP diagnostics
- [ ] Check IntelliJ for compilation errors (may occasionally be out of sync)
- [ ] Resolve all type errors and warnings

## 3. Testing
- [ ] Run tests: `./gradlew test`
- [ ] All existing tests pass
- [ ] New tests added if functionality was added/changed
- [ ] Tests follow Arrange-Act-Assert pattern
- [ ] Tests assert correct outcomes (not just that we fail)
- [ ] No test wildcards used (`--tests SpecificTestClass`)
- [ ] Helper methods used to reduce boilerplate in tests

## 4. Build Verification
- [ ] Build succeeds: `./gradlew build`
- [ ] No deprecation warnings introduced
- [ ] Generated code up to date (if OpenAPI spec changed)

## 5. Runtime Verification (when applicable)
- [ ] Start Kafka if needed: `docker compose up -d`
- [ ] Application starts successfully: `./gradlew bootRun`
- [ ] Health check passes: http://localhost:8080/actuator/health
- [ ] Swagger UI accessible and reflects changes: http://localhost:8080/swagger-ui.html
- [ ] Kafka topics created correctly (check Kafka UI: http://localhost:8090)

## 6. Integration Testing (when applicable)
- [ ] Test API endpoints with curl or Swagger UI
- [ ] Verify Kafka messages in Kafka UI
- [ ] Check SQLite database for expected data

## 7. Git & Version Control
- [ ] Review changes: `git diff`
- [ ] Stage appropriate files only
- [ ] **DO NOT include "Generated with Claude Code" or similar in commit messages**
- [ ] Commit message follows conventional commits style
- [ ] No unintended files committed (.env, credentials, build artifacts)

## 8. Documentation
- [ ] Update README.md if public API or setup changed
- [ ] Update CLAUDE.md if project-specific instructions changed
- [ ] Code is self-documenting; comments only where necessary

## 9. Error Handling
- [ ] Proper error handling implemented
- [ ] Clear error messages
- [ ] Errors cleared on success, processing stops on errors

## 10. Final Review
- [ ] Changes match the original task requirements
- [ ] No over-engineering or unnecessary abstractions
- [ ] Only requested features implemented (no extra "improvements")
- [ ] If task completion is uncertain, consult with user

## Special Considerations

### For Kafka Changes
- [ ] Topology tested with `NvdbStreamTopologyTest`
- [ ] Serialization/deserialization works correctly
- [ ] Topic names and configuration are correct

### For NVDB API Changes
- [ ] API client handles rate limiting and errors
- [ ] Progress tracking updated correctly in SQLite
- [ ] Batch sizes and schedules are appropriate

### For Configuration Changes
- [ ] `application.yml` updated
- [ ] Environment variables documented
- [ ] Default values are sensible

## Don't Forget
- If LSP shows errors but you think they're false positives, note it and don't try to fix forever
- If tests keep failing and you don't understand why, stop and report it
- When something is unclear, ask for clarification
- Never delete tests unless explicitly instructed
