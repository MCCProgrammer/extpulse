# 0001. Java 25 and Spring Boot 4.1

**Date:** 2026-10-21 · **Status:** accepted

## Context

The project is a back end meant to show the stack most Java jobs in Portugal ask
for. In October 2026 the Spring Initializr offers Spring Boot 4.0 and 4.1 only; the
3.x line is no longer offered for new projects. Java 25 is the latest LTS release
and is already installed on the development machine.

## Decision

Java 25 and Spring Boot 4.1, built with Maven through the Maven Wrapper.

## Consequences

- Current versions: long support ahead, and virtual threads are available for the
  calls to the stores.
- Many companies still run Java 17 or 21 and Spring Boot 3. The code written here
  (Spring MVC, JPA, Security) is almost the same in those versions, so the skills carry over.
- Spring Boot 4 split some starters (`spring-boot-starter-webmvc` instead of
  `spring-boot-starter-web`) and moved some test classes to new packages
  (`org.springframework.boot.webmvc.test.autoconfigure`). Older tutorials may show
  the old names.
- The Maven Wrapper (`mvnw`) pins the Maven version in the repository, so everyone
  and the CI build with the same one, without installing Maven.

## Alternatives considered

- **Java 21 and Spring Boot 3.5:** closer to what many companies run today, but not
  offered for new projects and with less support left.
- **Gradle:** faster builds, but Maven is more common in Portuguese companies.
