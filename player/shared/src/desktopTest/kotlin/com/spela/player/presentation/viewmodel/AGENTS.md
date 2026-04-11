# ViewModel tests — dispatcher and cleanup rules

Gotchas discovered while rewriting `ChallengeManagerTest`. Applies to
any test that exercises a ViewModel (or similar component) which
launches long-running background coroutines — challenge timers,
session heartbeats, polling loops, etc.

## Prefer `UnconfinedTestDispatcher` for `io → main` handoffs

`kotlinx.coroutines.test.runTest` defaults to a `StandardTestDispatcher`
which queues launches instead of running them eagerly. If the code
under test does a `withContext(io) { … }` launch and the test then
does `advanceUntilIdle()` to drain, the scheduler can race against
already-enqueued work and the test flakes on CI runners without
reproducing locally.

Use `UnconfinedTestDispatcher` when the test exercises real dispatcher
hops. It runs coroutines eagerly up to the first real suspension, so a
call like `restartChallenge` that hops `io → main` via `withContext`
completes synchronously as far as the test scheduler is concerned. The
assertion reads the final state directly, no scheduler draining
needed.

`ChallengeManagerTest` is the reference example — it replaced a flaky
VM-level test (`EmulationViewModelChallengeTest.restartChallenge…`)
that had been ignored across PRs #355–#361 due to exactly this kind of
dispatcher race.

## Always call `cleanup()` before `runTest` returns

If the subject under test starts a background timer loop
(`while (isActive) { delay(100); … }`), that coroutine will survive
past the end of your test body. `runTest`'s implicit
`advanceUntilIdle()` at block exit will then either block forever
(OOM, CI hang) or fail with a scheduler-not-idle error.

Rule: any test that triggers a method which starts a background timer
(directly, or transitively through something like `startChallengeTimer`)
MUST explicitly call `subject.cleanup()` before the `runTest` block
returns. Put it in an `@AfterTest` or at the tail of each test method.

The 16-minute `:shared:desktopTest` hang observed during #363
validation was almost certainly this — a separate test file was
leaking a long-running timer into the scheduler and blocking the
suite.
