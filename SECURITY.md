# Security Policy

## Supported Versions

Kami merekomendasikan untuk selalu menggunakan **update/rilis terbaru** dari project ini.

| Version | Supported |
|---------|-----------|
| Latest (update terbaru, direkomendasikan) | :white_check_mark: |
| Versi lama / < latest | :x: |

## Reporting a Vulnerability

If you discover a security vulnerability in HoloJava, please report it by opening an [Issue](https://github.com/AntoMay/HoloJava/issues) with the `security` label, or email [antipramukaindonesia@gmail.com](mailto:antipramukaindonesia@gmail.com). If you plan to contribute a fix, please submit a [Pull Request](https://github.com/AntoMay/HoloJava/pulls).

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