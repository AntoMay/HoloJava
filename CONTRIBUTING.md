# Contributing to HoloJava

Thank you for considering contributing to HoloJava! This is a Java ME (CLDC 1.1 / MIDP 2.0) survival roguelite game inspired by HoloCure.

## Development Process

1. **Fork the repository** and create a branch from `main`
2. **Read the roadmap** - Check `Roadmap.md` to understand the current phase and what work is needed
3. **Run the test suite** - Execute `./tools/run_fullgame_tests.sh` to ensure all 41 suites (~4,300 checks) pass
4. **CLDC compliance** - Ensure the CLDC compliance gate passes: `./tools/verify_cldc_classes.sh`
5. **Follow the coding conventions** - Preserve Java ME compatibility; no `java.net.*`, `Runtime.exec()`, or unsupported APIs
6. **Write tests first** - New features should have corresponding test suites under `tools/tests/`
7. **Update documentation** - Keep `PROJECT_STATUS.md` and `Roadmap.md` up to date
8. **Submit a Pull Request** - Fill in the PR template with change summary, motivation, and testing performed

## Project Status

- Current phase: Phase 8 (Distribution Readiness) - v1.0.1 released
- 41 test suites with ~4,300 checks run green on every commit
- CLDC bytecode compatibility gate is validated after every build
- See `PROJECT_STATUS.md` for the latest status and next action

## Reporting Issues

- Gunakan [Issues](https://github.com/AntoMay/HoloJava/issues) untuk melaporkan bug atau mengajukan fitur/saran.
- Use the [Bug report](.github/ISSUE_TEMPLATE/bug_report.md) template for bug reports
- Use the [Feature request](.github/ISSUE_TEMPLATE/feature_request.md) template for feature suggestions
- Kirim perubahan kode melalui [Pull Requests](https://github.com/AntoMay/HoloJava/pulls)
- Label issues appropriately: `good first issue`, `help wanted`, `question`, `documentation`, `security`

## Style Guidelines

- Preserve CLDC 1.1 / MIDP 2.0 compatibility
- No modern Java syntax that breaks CLDC compatibility
- No `java.net.*`, `Socket`, `URL`, or `Runtime.exec()` code
- Maintain the existing build infrastructure (custom shell scripts, ProGuard, CLDC stubs)
- Document any design decisions in `docs/TECHNICAL_DECISIONS.md`

Thank you for contributing to HoloJava!