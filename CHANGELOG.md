# Changelog

## 0.2.0 - 2026-11-22

### Features

- **protocol:** Implement Scoreline v0.2 network protocol
- **protocol:** Add GoldenWriter and update protocol files
- **RequestGate:** Implement RequestGate class for stat requests
- **RequestTimer:** Implement request timing logic
- **platform:** Add network service for channel checks and sending
- **protocol:** Add server and client sides of the stats exchange
- **neoforge:** Register payloads and forward game events

### Fixes

- **release:** Update next-version.sh script: no tag is an error
- **constants:** Make Constants class final and private constructor
- **server:** Handle stats request rejection for clients without channel
- **goldens:** Refuse to regenerate protocol folders that a release contains

### Other

- **spec:** Update design spec with new details
- **spec:** Update graph state and deletion details
- **spec:** Correct recording detail levels in spec
- **spec:** Add v0.14 storage review details
- **.gitignore:** Add .claude directory to ignore list
- Remove the author byline from the spec and the v0.1 plan
- **spec:** Use vanilla stat names and fail releases without a tag
- **plan:** Add v0.2 implementation plan
- **common, platform:** Format private constructors on one line
- Add missing Javadoc to template classes
- Assign publishing properties with '=' for Gradle 10
- **payload:** Add unit tests for Hello, PlayerStats, StatsRequest, and StatsResponse payloads
- **RequestGate:** Add unit tests for RequestGate functionality
- **spec:** Enhance hello payload handling for mixed versions
- **RequestTimer:** Add comprehensive unit tests for RequestTimer functionality
- **spec:** Answer the open point on payload size limits
- **coverage:** Measure all classes by default, per class and in total
- **spec:** Add performance comparison, profiling and thorough checks to v0.2
- **plan:** Add performance comparison, profiling and thorough checks to v0.2 plan
- Plan game tests with a mock client that exchanges payloads
- **plan:** Record Task 6 findings on source set, handler tick and test filter
- **gametest:** Add game tests with a mock client for the stats exchange
- Add spark to local runs through CurseMaven
- **spec:** Rework the test setup
- **plan:** Rework the plan to match the spec changes
- **protocol:** Read every golden file of every protocol version
- **payload:** Check that the largest legal request fits the server's limit
- **gametest:** Add a hostile client test and a logout test
- **gametest:** Correct what a channel version mismatch does to a client
- **gametest:** Write a coverage report of the game-test run
- **jmh:** Add JMH benchmarks with allocation and a hot-path benchmark check
- Keep library and plugin versions in a version catalog
- **perf:** Compare two versions' benchmarks with PerfCompare and a script
- **perf:** Add a manual profiling run with JFR method timing
- Add mutation testing and fuzzing
- Add the thorough-checks workflow
- Check the jar and run the smoke test on a dedicated server
- Run mutation testing when only tests change
- Write the code without equivalent mutants and raise the mutation gates to 100 %
- **plan:** Tick the checks seen on the v0.2 pull request
## 0.1.0 - 2026-11-14

### Features

- Log the loaded version on start
- **RequestBudget:** Implement RequestBudget class with tests
- **jacoco:** Integrate JaCoCo for coverage reporting
- **gametest:** Add gameTestServer configuration and smoke script
- **ci:** Add GitHub Actions build workflow

### Fixes

- **ci:** Make the release scripts executable

### Other

- Create the repository from the MultiLoader Template
- Add the design spec
- Add the v0.1 implementation plan
- Tick off Task 1 Step 3
- Remove the Fabric module
- Turn the template into Scoreline
- Name the jar scoreline-<version>+<minecraft>-<module>.jar
- Note the VS Code Gradle import setting in Task 2 Step 3
- **build:** Add JUnit 5 dependencies for unit testing
- Add Task 5 code style check to the v0.1 plan
- Check the code style with Spotless
- Reformat the Java code to the shared formatter profile
- Fail the build when tests fail
- Test: fail the build when tests fail
- Add the version scripts for releases
- Add the release workflow and changelog config
# Changelog
