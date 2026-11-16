# Security Policy

## Supported Versions

Use this table to get the supported version of the project.

| Version | Supported |
|---------|-----------|
| 1.0.1   | :white_check_mark: |
| < 1.0.1 | :x: |

## Reporting a Vulnerability

If you discover a security vulnerability in HoloJava, please report it by opening an issue with the `security` label, or email [example@email.com].

We aim to acknowledge all security reports within 5 business days and will prioritize fixes for issues that affect runtime behavior or CLDC compliance.

## Development Security Guidelines

- All code must pass the CLDC compliance gate (`./tools/verify_cldc_classes.sh`)
- No `java.net.*`, `Socket`, `URL`, or `Runtime.exec()` code allowed
- Dependency validation is performed during build via `./tools/check_cldc_compliance.py`
- Security-sensitive code paths are reviewed during pull request evaluation

## Preferred Languages

- English for security reports
- Indonesian for local community discussions

Thank you for helping keep HoloJava secure.