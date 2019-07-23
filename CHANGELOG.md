# Changelog
All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.16.5] - 2019-07-23

### Bug fix

- Increase hardcoded minimum required Gradle version to 5.3.1

## [0.16.3] - 2019-07-14

### Major Features

- Switch to Gradle Java Library plugin [1]
- Gradle Kotlin script support
- Support `title` property
- Support Java 11+ and Gradle 5.5+

[1] https://docs.gradle.org/current/userguide/java_library_plugin.html

### Minor Enhancements

- Relax `publishUrl` keyword strictness
- Declaring `publishUrl` is no longer required (default value is "local")
- `bintrayUsername` and `bintrayApiKey` are now accepted (`bintay_user` and `bintray_key`
are now deprecated)

### Deprecation Removal

- `hyphenatedName` is no longer acknowledged
- Remove old `IHMCSettingsConfigurator` method signatures

### Examples

##### `title` property

You can now use `title` instead of `kebabCasedName` and `pascalCasedName`.

**gradle.properties**
```
title = IHMC Commons   // kebab: ihmc-commons, pascal: IHMCCommons
```

In `build.gradle` and `build.gradle.kts` files you can make use of the `api` and `implementation`
dependency types. `api` is a direct replacement for `compile`. `implementation` blocks consuming libraries
from accessing that library transitively.

```
mainDependencies {
   api("org.apache.commons:commons-lang3:3.8.1")
   implementation("commons-io:commons-io:2.6")
}
```

## [0.15.7] - 2019-03-05
- Apply `HelpTasksPlugin` to all projects to fix `compositeTask` for those tasks in Gradle 5.

## [0.15.6] - 2019-03-05
- Add support for composite building on projects that use `settings.gradle.kts` files.

## [0.15.5] - 2018-12-12
## Bug Fix

- Fix bug where snapshots would roll back to older versions without warning in child builds on Bamboo. Additionally, add log message for when that happens.

## [0.15.4] - 2018-11-26
### Bug Fix

- Remove overlap between java source and resources directories which broke IntelliJ 2018.3

## [0.15.3] - 2018-10-26
### UX Improvement

- Using only `-PsnapshotMode=true` will declare Artifactory repos
- More output during property loading
- `groupDependencyVersion` no longer needed, never pass `SNAPSHOT` as version. `source` will work
- Remove empty task log messages

### Regressions

- Require Gradle 4.9 or higher

## [0.15.1] - 2018-09-06
### API Removal

- `ihmc-ci` library and plugin is removed from this project and is not automatically applied to projects
- `ihmc.convertJobNameToHyphenatedName` has been removed and replaced by
`testSuites.convertJobNameToHyphenatedName` (now provided only by `ihmc-ci-plugin`)

### Migration

Please use the following code to get the old functionality:

```groovy
buildscript {
   repositories {
      maven { url "https://plugins.gradle.org/m2/" }
      mavenLocal()
   }
   dependencies {
      classpath "us.ihmc:ihmc-build:0.15.10" // new build plugin without ci
      classpath "us.ihmc:ihmc-ci-plugin:0.18.0" // apply ci separately now
   }
}
apply plugin: "us.ihmc.ihmc-build"
apply plugin: "us.ihmc.ihmc-ci-plugin"

println testSuites.convertJobNameToHyphenatedName("AtlasAFast") // replaces removed method

testDependencies {
   compile group: "us.ihmc", name: "ihmc-ci-core-api", version: "0.18.0" // these were auto included before
}
```

## [0.14.0] - 2018-07-26
### Bug fix

- Dependencies included in the build, but do not have "source" as their version, now keep their declared version instead of inheriting the parent's version

For example, if 
1. `project-a` has included the build `project-b`,
1. `project-a`'s version is `0.2`,
1. `project-b`'s version is `1.0`,
1. `project-a/build.gradle` contains `compile [...] name: project-b, version: 1.0`,
it is no longer the case that the IHMCDependenciesExtension would override `1.0` to `0.2` when passing the version to Gradle.

### Quality improvements

- Improve message for "source" version dependencies that aren't present
- Add unit tests for test suite generation using ihmc-ci
- Add binray properties to README

[Unreleased]: https://github.com/ihmcrobotics/ihmc-build/compare/0.16.5...HEAD
[0.16.3]: https://github.com/ihmcrobotics/ihmc-build/compare/0.16.3...0.16.5
[0.16.3]: https://github.com/ihmcrobotics/ihmc-build/compare/0.15.7...0.16.3
[0.15.7]: https://github.com/ihmcrobotics/ihmc-build/compare/0.15.6...0.15.7
[0.15.6]: https://github.com/ihmcrobotics/ihmc-build/compare/0.15.5...0.15.6
[0.15.5]: https://github.com/ihmcrobotics/ihmc-build/compare/0.15.4...0.15.5
[0.15.4]: https://github.com/ihmcrobotics/ihmc-build/compare/0.15.3...0.15.4
[0.15.3]: https://github.com/ihmcrobotics/ihmc-build/compare/0.15.1...0.15.3
[0.15.1]: https://github.com/ihmcrobotics/ihmc-build/compare/0.14.0...0.15.1
[0.14.0]: https://github.com/ihmcrobotics/ihmc-build/releases/tag/0.14.0