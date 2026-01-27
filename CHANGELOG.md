# Changelog

## [1.3.1](https://github.com/for-keycloak/email-otp-authenticator/compare/v1.3.0...v1.3.1) (2026-01-27)


### Dependencies

* bump org.junit.jupiter:junit-jupiter from 5.11.4 to 6.0.2 ([f2f8872](https://github.com/for-keycloak/email-otp-authenticator/commit/f2f8872dbb9bea6712f364a24034b25218c24161))
* bump postgres from 17.4 to 18.1 ([1882fe5](https://github.com/for-keycloak/email-otp-authenticator/commit/1882fe5cf694f6f5498b8e54701574d8efbace6c))
* bump the minor-versions group with 2 updates ([b4128b6](https://github.com/for-keycloak/email-otp-authenticator/commit/b4128b64b6fd91d9a38e274855b508afa86588e3))

## [1.3.0](https://github.com/for-keycloak/email-otp-authenticator/compare/v1.2.4...v1.3.0) (2026-01-27)


### Features

* **trust:** add IP and device trust to skip repeated OTP ([1f51527](https://github.com/for-keycloak/email-otp-authenticator/commit/1f51527491abb062b451c7e7c625e47ea1e34511))


### Bug Fixes

* **e2e:** accept both numerals formats across Keycloak versions ([94b0f31](https://github.com/for-keycloak/email-otp-authenticator/commit/94b0f31e744b4f9e7cb8ff606597a15da35aa712))


### Tests

* **trust:** add unit and e2e tests for trust features ([7f6583b](https://github.com/for-keycloak/email-otp-authenticator/commit/7f6583b818ec7c923d1cb0aa8bc08b63110e5f87))


### Continuous Integration

* **tests:** add parallel workflow for unit and e2e tests ([79e1279](https://github.com/for-keycloak/email-otp-authenticator/commit/79e1279181280038374b30a5408e5e4cd62c33db))

## [1.2.4](https://github.com/for-keycloak/email-otp-authenticator/compare/v1.2.3...v1.2.4) (2026-01-25)


### Continuous Integration

* **workflows:** harden permissions and use GITHUB_TOKEN ([df007f5](https://github.com/for-keycloak/email-otp-authenticator/commit/df007f57c45d6443a96dbb37bc1e012a2c00ed11))

## [1.2.3](https://github.com/for-keycloak/email-otp-authenticator/compare/v1.2.2...v1.2.3) (2026-01-25)


### Dependencies

* bump the minor-versions group across 1 directory with 6 updates ([#30](https://github.com/for-keycloak/email-otp-authenticator/issues/30)) ([eda1bb0](https://github.com/for-keycloak/email-otp-authenticator/commit/eda1bb0188b2acda1e8ab0df6af4318d13e55e6f))

## [1.2.2](https://github.com/for-keycloak/email-otp-authenticator/compare/v1.2.1...v1.2.2) (2026-01-25)


### Bug Fixes

* **auth:** prevent 2FA bypass when user lacks required role in alternative flows ([954adb7](https://github.com/for-keycloak/email-otp-authenticator/commit/954adb7c9caee2d9466ecb507294b0cad919fd19))


### Miscellaneous Chores

* **build:** update Keycloak versions ([933cd80](https://github.com/for-keycloak/email-otp-authenticator/commit/933cd803ffb8c4d751a5bf948f0400d859486099))


### Dependencies

* **ci:** bump actions/checkout to v6.0.2 ([2d57874](https://github.com/for-keycloak/email-otp-authenticator/commit/2d578747cdff0dccca0dd0ff916b92da499b9338))

## [1.2.1](https://github.com/for-keycloak/email-otp-authenticator/compare/v1.2.0...v1.2.1) (2026-01-25)


### Bug Fixes

* **ci:** update cosign command for v4 bundle format ([c1d67ee](https://github.com/for-keycloak/email-otp-authenticator/commit/c1d67eee8b87657acc8856da52b7dc3b9b6da2c1))

## [1.2.0](https://github.com/for-keycloak/email-otp-authenticator/compare/v1.1.4...v1.2.0) (2026-01-25)


### Features

* **build:** add support for Keycloak 26.2.5-26.5.1 ([689b796](https://github.com/for-keycloak/email-otp-authenticator/commit/689b7961d4cd449affa5457787f459b32dbfdc41))
* **otp:** add role-based conditional authentication ([70c201e](https://github.com/for-keycloak/email-otp-authenticator/commit/70c201ea640ef17a1a20feb5fc0c8e584ce49439)), closes [#15](https://github.com/for-keycloak/email-otp-authenticator/issues/15)


### Bug Fixes

* **email:** calculate TTL minutes in Java instead of FreeMarker ([e5b6572](https://github.com/for-keycloak/email-otp-authenticator/commit/e5b65729b36e30c22473521678f870b0c0c8ea84))


### Tests

* **e2e:** add Playwright end-to-end tests ([5e16d7b](https://github.com/for-keycloak/email-otp-authenticator/commit/5e16d7bd12f2f6abe684ebf926f2ee55cb5eaa25))


### Continuous Integration

* **release:** update GitHub Actions to latest versions ([2ad13a5](https://github.com/for-keycloak/email-otp-authenticator/commit/2ad13a5b2ffad8da68f4100822b84b7b8afd9cf1))

## [1.1.4](https://github.com/for-keycloak/email-otp-authenticator/compare/v1.1.3...v1.1.4) (2025-05-05)


### Bug Fixes

* regenerate a new code when clicking on resend ([#13](https://github.com/for-keycloak/email-otp-authenticator/issues/13)) ([2934627](https://github.com/for-keycloak/email-otp-authenticator/commit/293462754bdb1b923abb60d1c723f07c619a0027))

## [1.1.3](https://github.com/for-keycloak/email-otp-authenticator/compare/v1.1.2...v1.1.3) (2025-05-02)


### Bug Fixes

* only regenerate otp when needed ([#11](https://github.com/for-keycloak/email-otp-authenticator/issues/11)) ([32bd510](https://github.com/for-keycloak/email-otp-authenticator/commit/32bd5109a01a41d179e5173a8d099e3f9d0d72de))

## [1.1.2](https://github.com/for-keycloak/email-otp-authenticator/compare/v1.1.1...v1.1.2) (2025-04-23)


### Dependencies

* bump jakarta.ws.rs:jakarta.ws.rs-api from 3.1.0 to 4.0.0 ([#3](https://github.com/for-keycloak/email-otp-authenticator/issues/3)) ([d09c727](https://github.com/for-keycloak/email-otp-authenticator/commit/d09c72784d4d93cb51b918ca9d837bd89f5a2bc9))
* bump maven from 3.9.9-eclipse-temurin-21-jammy to 3-eclipse-temurin-22-jammy in /infra in the minor-versions group ([#2](https://github.com/for-keycloak/email-otp-authenticator/issues/2)) ([7a6541a](https://github.com/for-keycloak/email-otp-authenticator/commit/7a6541abf0e4ee670166faeea6a0422165f94e77))
* bump sigstore/cosign-installer from 3.8.1 to 3.8.2 in the minor-versions group ([#8](https://github.com/for-keycloak/email-otp-authenticator/issues/8)) ([08319f4](https://github.com/for-keycloak/email-otp-authenticator/commit/08319f411ad88db1c613beb6c792e8f6eacc173a))

## [1.1.1](https://github.com/for-keycloak/email-otp-authenticator/compare/v1.1.0...v1.1.1) (2025-04-18)


### Documentation

* added some missing docs and auto update version in README.md ([ad850e8](https://github.com/for-keycloak/email-otp-authenticator/commit/ad850e8b14522153f42aecf4c8272ccd3a3ccab2))


### Miscellaneous Chores

* add a pull request template ([af7b4d4](https://github.com/for-keycloak/email-otp-authenticator/commit/af7b4d4dbc452ae9575dbf050bcff2c6051337b1))
* add UNLICENSE file ([f75339b](https://github.com/for-keycloak/email-otp-authenticator/commit/f75339bf18b795f6d2c1d35ef590a5dd691fbf0c))

## [1.1.0](https://github.com/for-keycloak/email-otp-authenticator/compare/v1.0.0...v1.1.0) (2025-04-18)


### Features

* add conditional filters on role ([27ff32f](https://github.com/for-keycloak/email-otp-authenticator/commit/27ff32f795c92f7b01be32a2f075499bfa2e863b))


### Bug Fixes

* error messages ([e7c6cf5](https://github.com/for-keycloak/email-otp-authenticator/commit/e7c6cf5b132b9247faac4c7a87b115b934b068b5))


### Documentation

* fix typo in installation instructions ([a4d33c4](https://github.com/for-keycloak/email-otp-authenticator/commit/a4d33c4a3be0a7f9a1073cbe5de537aa733a9842))


### Miscellaneous Chores

* update login template ([e1da760](https://github.com/for-keycloak/email-otp-authenticator/commit/e1da760f98be89b4a4e33b6677c5821385dffab7))
* update translations ([86e3868](https://github.com/for-keycloak/email-otp-authenticator/commit/86e38685ca32dba69b382cacb87a815b40ecaa90))

## 1.0.0 (2025-04-16)


### Features

* **Email OTP Authenticator:** first release ([95b68fa](https://github.com/for-keycloak/email-otp-authenticator/commit/95b68fa05209e3cf48463043fb39d5e57f62157e))
