set quiet

# Default recipe to display help information
[private]
default:
  just --list --unsorted

# Build the docker image
[private]
[no-exit-message]
build-docker:
    docker build --file="infra/Dockerfile" --tag="keycloak-spi-plugin" .

# Build the project with default Keycloak version
[no-exit-message]
build: build-docker
    docker run --rm --volume="${PWD}:/opt/maven" keycloak-spi-plugin mvn -B clean package

# Run unit tests
[no-exit-message]
test: build-docker
    docker run --rm --volume="${PWD}:/opt/maven" keycloak-spi-plugin mvn -B test -DskipTests=false

# Build for a specific Keycloak version
build-version VERSION: build-docker
    docker run --rm --volume="${PWD}:/opt/maven" keycloak-spi-plugin mvn -B clean package -P keycloak-{{VERSION}}

# Start Keycloak with the authenticator
up:
    docker compose up

# Stop Keycloak and clean up
down:
    docker compose down

# Watch the Keycloak logs
logs:
    docker compose logs -f keycloak

# Start a shell in the Keycloak container
shell:
    docker compose exec keycloak bash

# List all available Keycloak versions
versions:
    @echo "Supported Keycloak versions:"
    @echo "- 26.5.3 (default)"
    @echo "- 26.4.7"
    @echo "- 26.3.5"
    @echo "- 26.2.5"

# Run E2E tests (optionally specify a file pattern, requires test-e2e-setup if FILE is provided)
test-e2e KC_VERSION="26.5.3" FILE="": (build-version KC_VERSION)
    #!/usr/bin/env bash
    cd tests/e2e
    if [ -z "{{FILE}}" ]; then
        KC_VERSION={{KC_VERSION}} docker compose --profile test up --build --abort-on-container-exit --exit-code-from test-runner; rc=$?
        KC_VERSION={{KC_VERSION}} docker compose --profile test down
        exit $rc
    else
        docker compose run --rm test-runner npx playwright test {{FILE}}
    fi

# Start test infrastructure only (for debugging or running specific tests)
test-e2e-setup KC_VERSION="26.5.3": (build-version KC_VERSION)
    cd tests/e2e && KC_VERSION={{KC_VERSION}} docker compose up -d --wait

# Stop test containers
test-e2e-down:
    cd tests/e2e && docker compose --profile test down
