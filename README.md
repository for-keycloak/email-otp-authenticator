# ✉️ Keycloak Email OTP Authenticator ![GitHub release (latest by date)](https://img.shields.io/github/v/release/for-keycloak/email-otp-authenticator)

A custom authentication SPI for Keycloak that provides an Email-based One-Time Password (OTP) step in the authentication flow. This authenticator sends a time-limited OTP code to the user's email address and validates it.


## Features

- Sends OTP codes via email using Keycloak's email service
- Configurable OTP code format (length and character set)
- Configurable expiration time
- Supports resending of codes
- **IP Trust**: Automatically remember trusted IP addresses to skip OTP (rolling window)
- **Device Trust**: User opt-in to remember trusted devices via cookie
- Internationalization support for multiple languages
- Compatible with multiple Keycloak versions


## Configuration

The authenticator provides the following configuration options:

### Basic Settings

- **User Role**: Only applies the authenticator to users with this role (default: `<null>`)
- **Negate User Role**: Applies the authenticator to users without the selected role, inverting the condition (default: `false`)
- **Code Length**: Length of the generated OTP code (default: `6`)
- **Code Alphabet**: Characters used for generating the code (default: `23456789ABCDEFGHJKLMNPQRSTUVWXYZ`)
- **Code Expiration**: Time in seconds before the code expires (default: `600` = 10 minutes)

### IP Trust Settings

- **Enable IP Trust**: If enabled, users won't be asked for OTP again from the same IP address within the trust duration (default: `false`)
- **IP Trust Duration (minutes)**: The number of minutes an IP address remains trusted. Each successful login refreshes this window - this is a rolling expiration (default: `60` = 1 hour)

### Device Trust Settings

- **Enable Device Trust**: If enabled, users can opt-in to trust their device via a checkbox on the OTP form (default: `false`)
- **Device Trust Duration (days)**: The number of days a device remains trusted. Set to `0` for permanent trust (default: `365` = 1 year)

### Trust Behavior Settings

- **Trust Only When Sole Authenticator**: If enabled, IP/device trust only applies when email OTP is the only authenticator (not alternative with other methods). When disabled, trust applies regardless of flow configuration (default: `true`)


## How Trust Features Work

### IP Trust (Rolling Window)

When IP Trust is enabled, the authenticator remembers which IP addresses have successfully completed OTP verification. On subsequent logins from the same IP within the trust duration, OTP is skipped automatically.

**Key characteristics:**
- **Automatic**: No user action required
- **Rolling expiration**: Each successful login (with or without OTP) refreshes the trust window
- **Network-wide**: Works across all devices on the same network/IP

**Example with 60-minute duration:**

| Time | IP | Trust State | Action | New Expiration |
|------|-----|-------------|--------|----------------|
| 09:00 | 192.168.1.1 | None | OTP required ✓ | 10:00 |
| 09:45 | 192.168.1.1 | Valid | OTP skipped | 10:45 |
| 10:30 | 192.168.1.1 | Valid | OTP skipped | 11:30 |
| 11:35 | 192.168.1.1 | Expired | OTP required ✓ | 12:35 |

### Device Trust (User Opt-in)

When Device Trust is enabled, a checkbox appears on the OTP form allowing users to remember their device. If checked, a secure cookie is stored, and future logins from that device/browser skip OTP.

**Key characteristics:**
- **Opt-in**: User must explicitly check the checkbox
- **Device-specific**: Uses a secure cookie (HttpOnly, Secure, SameSite=Lax)
- **Priority**: Device trust takes priority over IP trust when both are enabled

### ACR (Authentication Context Class Reference) Values

The authenticator sets different ACR values based on how authentication was completed:

| ACR Value | Description |
|-----------|-------------|
| `email-otp` | User entered a valid OTP code |
| `email-otp-trusted-ip` | OTP was skipped due to trusted IP |
| `email-otp-trusted-device` | OTP was skipped due to trusted device |

These ACR values can be used by applications to understand the authentication strength and make authorization decisions accordingly.


## Installation

### Option 1: Using Docker

Add the following to your Dockerfile:

```dockerfile
# Download and install the authenticator
ARG EMAIL_OTP_AUTHENTICATOR_VERSION="v1.3.2" # x-release-please-version
ARG EMAIL_OTP_AUTHENTICATOR_KC_VERSION="26.5.2"
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
# Build for the default Keycloak version (26.5.2)
just build

# Build for a specific Keycloak version
just build-version 26.2.5
```


### Testing with Docker Compose

A docker-compose configuration is provided for testing, which includes:

- Keycloak server with the authenticator installed (accessible at http://localhost:8080)
- MailPit for email testing (accessible at http://localhost:8025)

Start the environment:
```bash
just build # Builds the authenticator
just up    # Starts Keycloak with the authenticator
```

```bash
# You can use admin/admin as the default credentials
# You SHOULD configure the mail settings in the realm you want to test
# - Set the server to `mailpit`
# - Set the port to `1025`

# The user you want to test with MUST have their `email` address set
```

```bash
just down  # Stops the environment
```

Access:
- Keycloak: http://localhost:8080 (admin/admin)
- MailPit: http://localhost:8025 (to view sent emails)


## Supported Keycloak Versions

The authenticator is built and tested with multiple Keycloak versions:

- 26.5.2 (default)
- 26.4.7
- 26.3.5
- 26.2.5

While the builds differ slightly for each version, the core functionality remains the same. The version-specific builds ensure compatibility and proper integration with each Keycloak release.


## Known Limitations

### Email OTP appears in "Try Another Way" for users without the required role

When using role-based filtering in an ALTERNATIVE flow alongside other 2FA methods (e.g., TOTP), Email OTP may still appear in Keycloak's "Try another way" selection list for users who don't have the required role. This is a Keycloak architectural limitation - the selection list is built using a different code path that doesn't fully respect our role filtering.

**Important:** Selecting Email OTP in this case will **not** bypass 2FA. The user will be redirected back to complete their other configured 2FA method (e.g., TOTP).

**Workaround:** If you need to completely hide Email OTP for certain users, use Keycloak's built-in **Condition - User Role** execution in a conditional subflow instead of the authenticator's role filtering option.


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
