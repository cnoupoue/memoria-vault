# Security Policy

## Supported Versions

Security fixes are applied to the latest version on the `main` branch.

## Reporting a Vulnerability

Please do not open a public issue for a suspected security vulnerability.

Report it privately by contacting the maintainer through GitHub or by using GitHub's private vulnerability reporting feature if it is enabled for this repository.

Please include:

* A clear description of the issue
* Steps to reproduce it
* The potential impact
* Any suggested mitigation, if available

You will receive an acknowledgement as soon as possible.

## Scope

The following areas are especially sensitive in SnapMemoria:

* Local filesystem access
* Media streaming endpoints
* Source path validation
* Upload or export functionality
* GitHub Actions workflows
* Dependencies and build tooling

SnapMemoria is designed to handle private personal media locally. Security reports involving exposure of local paths, arbitrary file access, or unintended media access are especially important.
