# ✉️ Keycloak Email OTP Authenticator ![GitHub release (latest by date)](https://img.shields.io/github/v/release/for-keycloak/email-otp-authenticator)

A custom authentication SPI for Keycloak that provides an Email-based One-Time Password (OTP) step in the authentication flow. This authenticator sends a time-limited OTP code to the user's email address and validates it.


## Features

- Sends OTP codes via email using Keycloak's email service
- Configurable OTP code format (length and character set)
- Configurable expiration time
- Supports resending of codes
- Internationalization support for multiple languages
- Compatible with multiple Keycloak versions


## Configuration

The authenticator provides the following configuration options:

- **User Role**: Only applies the authenticator to users with this role (default: `<null>`)
- **Negate User Role**: Applies the authenticator to users without the selected role, inverting the condition (default: `false`)
- **Code Length**: Length of the generated OTP code (default: `6`)
- **Code Alphabet**: Characters used for generating the code (default: `23456789ABCDEFGHJKLMNPQRSTUVWXYZ`)
- **Code Expiration**: Time in seconds before the code expires (default: `600` = 10 minutes)


## Installation

### Option 1: Using Docker

Add the following to your Dockerfile:

```dockerfile
# Download and install the authenticator
ARG EMAIL_OTP_AUTHENTICATOR_VERSION="v1.1.1" # x-release-please-version
ARG EMAIL_OTP_AUTHENTICATOR_KC_VERSION="26.2.0"
ADD https://github.com/for-keycloak/email-otp-authenticator/releases/download/${EMAIL_OTP_AUTHENTICATOR_VERSION}/email-otp-authenticator-${EMAIL_OTP_AUTHENTICATOR_VERSION}-kc-${EMAIL_OTP_AUTHENTICATOR_KC_VERSION}.jar \
    /opt/keycloak/providers/email-otp-authenticator.jar
```

### Option 2: Manual Installation

1. Download the JAR file from the [releases page](https://github.com/for-keycloak/email-otp-authenticator/releases)
2. Copy it to the `providers` directory of your Keycloak installation


## Local Development

### Prerequisites

- [Just](https://github.com/casey/just)
- Docker & Docker Compose (optional, for testing)

### Building

Using just:
```bash
# Build for the default Keycloak version (26.2.0)
just build

# Build for a specific Keycloak version
just build-version 25.0.6
```


### Testing with Docker Compose

A docker-compose configuration is provided for testing, which includes:

- Keycloak server with the authenticator installed (accessible at http://localhost:8080)
- MailHog for email testing (accessible at http://localhost:8025)

Start the environment:
```bash
just build # Builds the authenticator
just up    # Starts Keycloak with the authenticator
```

```bash
# You can use admin/admin as the default credentials
# You SHOULD configure the mail settings in the realm you want to test
# - Set the server to `mailhog`
# - Set the port to `1025`

# The user you want to test with MUST have their `email` address set
```

```bash
just down  # Stops the environment
```

Access:
- Keycloak: http://localhost:8080 (admin/admin)
- MailHog: http://localhost:8025 (to view sent emails)


## Supported Keycloak Versions

The authenticator is built and tested with multiple Keycloak versions:

- 26.2.0 (default)
- 26.1.5
- 26.0.8
- 25.0.6
- 24.0.5

While the builds differ slightly for each version, the core functionality remains the same. The version-specific builds ensure compatibility and proper integration with each Keycloak release.


## License

This project is released under the [Unlicense](./UNLICENSE). This means you can copy, modify, publish, use, compile, sell, or distribute this software, either in source code form or as a compiled binary, for any purpose, commercial or non-commercial, and by any means.

### Why Unlicense?

We chose the Unlicense because we believe in giving back to the community. We struggled to find a properly working and maintained email OTP solution for Keycloak and wanted to ensure others wouldn't face the same challenges. By removing all restrictions, we hope to:

- Enable widespread adoption and improvement of the solution
- Allow integration into any project without licensing concerns
- Encourage community contributions and evolution of the code


## Contributing

Contributions are very welcome! Whether it's:

- Bug reports
- Feature requests
- Code contributions
- Documentation improvements
- Translations for new languages

Please feel free to submit issues and pull requests.


## Development Notes

The project uses:

- Maven for building
- [just](https://github.com/casey/just) for common development tasks
- Docker & Docker Compose for testing
- Release Please for versioning and release management
- GitHub Actions for CI/CD

See the `justfile` for available commands and development shortcuts.
