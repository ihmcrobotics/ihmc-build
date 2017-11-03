# IHMC Gradle Build Plugin in Kotlin

Conversion of our build plugin to Kotlin. Very active development.

Gradle Plugin Site: https://plugins.gradle.org/plugin/us.ihmc.ihmc-build

Documentation on Confluence: https://confluence.ihmc.us/display/BUILD/New+Build+Configuration+Documentation

Presentation outlining the purpose of this project: https://docs.google.com/presentation/d/1xH8kKYqLaBkRXms_04nb_yyoV6MRchLO8EAtz9WqfZA/edit?usp=sharing

## Project Goals

- Seperate source set classpaths when building projects in IDEs
- Get strongly typed build scripts working in IntelliJ and Eclipse
- Utilize composite builds to make each project standalone by default
- Keep Bamboo CI configuration powerful, minimal, and flexible
