# Contributing

Thank you for your interest in contributing to ureport-testng-reporter!

## Development setup

- Java 11+
- Maven 3.8+

```sh
git clone https://github.com/ureport/ureport-testng-reporter.git
cd ureport-testng-reporter
mvn compile
```

## Running tests

```sh
# Unit tests only
mvn test

# Unit + integration tests
mvn verify
```

## Submitting changes

1. Fork the repository and create a feature branch.
2. Make your changes with tests.
3. Ensure `mvn verify` passes with no compilation errors.
4. Open a pull request against `main`.

## Code style

- Standard Java conventions.
- No external dependencies beyond those listed in `pom.xml`.
- Keep the library minimal — avoid adding dependencies not strictly required.
