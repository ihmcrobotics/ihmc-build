# IHMC Build Plugin

Composite build and IDE classpath separation support for JVM Gradle projects.

### Main features

- Seperate source set classpaths when building projects in IDEs
- Utilize composite builds to make each project standalone by default
- Keep Bamboo CI configuration powerful, minimal, and flexible

#### Contents
1. [Properties](#properties)
1. [Commands](#commands)
1. [Creating a new project](#creating-a-new-project)
1. [Groups of projects](#groups-of-projects)
1. [Troubleshooting](#troubleshooting)
1. [Learn more](#learn-more)
1. [Future plans](#future-plans)

### Properties

`title` - The "Title Cased Name" of your project. This will be converted to kebab and Pascal casing in various build phases.
(i.e. "title-cased-name" and "TitleCasedName")

`extraSourceSets` - Classpath separated "source sets" that have their own dependencies and are published in separate artifacts. 
(i.e. ["test", "visualizers"]) 
These will be represented by Gradle subprojects
under the hood and located where Gradle would normally place its concept of source sets. `ihmc-build` takes care of setting up the "test"
source set project as if it were a normal source set.

`compositeSearchHeight` - When building from this directory, set how many directories to go up and search for builds to include.
If this is not the root project, this setting is ignored.

`excludeFromCompositeBuild` - "Opt out" of being included in other composite builds. This setting is ignored if this project
is the build root.

(optional) `publishUrl` - See publishing commands below. Defaults to "local"

(optional) `projectGroup` - If this project exists only to contain other `ihmc-build` projects. (i.e. a "glue" project)

### Commands

##### Running tasks over all projects in the build

Most of the time, you will be in an environment with more that one included build. If you want to run a Gradle task on all of
them at once, use:

`gradle compositeTask -PtaskName=someTaskName`

For example, to run the Java plugin's `compileJava` task on your project plus all included Java projects, run:

`gradle compositeTask -PtaskName=compileJava`.

As always, pass any additional properties required for your task
via additional `-PsomeProperty=someValue`.

To run multiple tasks over all builds you can separate them with commas. `taskName` and `taskNames` both work.

`gradle compositeTask -PtaskNames=task1,task2,task3`

##### Running a task on only a extra source set

You can run a task on an extra source set, like the `test` source set. In `ihmc-build` extra source sets are included as "multi projects". 
So you can use the `:` character to sepcify the extra source set project and the task to run, like:

`gradle ihmc-java-toolkit-test:publish -PsomeProperty=someValue`

##### Publishing releases

Set `publishUsername` and `publishPassword` in `~/.gradle/gradle.properties` to your Sonatype JIRA credentials.

`gradle publish -PpublishUrl=ihmcRelease`

The above command publishes `your-project-0.1.0.jar` to Maven Central if the `openSource` option is set to "true" in the `ihmc` block. 
Otherwise, it will publish to Artifactory `proprietary-releases`.

##### Publishing locally

To publish `your-project-LOCAL.jar` to `$home/.m2`:

`gradle publish -PpublishUrl=local`

##### Publishing to custom repositories/URLs

Use the `publishUrl` property to pass the URL and `publishUsername` and `publishPassword` to authenticate:

`gradle publish -PpublishUrl=https://my.site/repo -PpublishUsername=username -PpublishPassword=password`

You can also code this in the `build.gradle.kts` to a keyword with the `addPublishUrl()` function:

`build.gradle.kts`:
```gradle
ihmc {
   ...
   
   configureDependencyResolution()  // Between here ->
   addPublishUrl("myVendor", "https://my.site/my-repo", publishUsername, publishPassword)
   configurePublications() // <- and here
}
```

##### Publishing all projects at once

The `compositePublish` task is provided as an alias for `gradle compositeTask -PtaskName=publish`:

`gradle compositePublish`

For example, to publish a group of projects:

`gradle compositePublish -PpublishUrl=ihmcRelease`

##### Clean build directories

The `clean` task is extended to also clean `bin/` and `out/`. `clean` normally only deletes `build/`.

A `cleanBuildship` task has been added to clean `.settings`.

A `cleanIDE` task has been added to group `cleanEclipse`, `cleanIdea`, and `cleanBuildship`.

The `compositeClean` task is provided as an alias for `gradle compositeTask -PtaskName=clean`.

The `compositeCleanIDE` task is provided as an alias for `gradle compositeTask -PtaskNames=cleanEclipse,cleanIdea,cleanBuildship`.

##### Maven Repositories

To add a Maven repository, use the `repository` function in the `ihmc` extension. Note that projects that depend on this will 
also need to have these repositories declared, so use them sparingly.
```
ihmc {
   ...

   configureDependencyResolution()  // Between here ->
   repository("http://maven-eclipse.github.io/maven")
   repository("https://artifactory.ihmc.us/artifactory/proprietary-releases/", artifactoryUsername, artifactoryPassword)
   configurePublications() // <- and here
}
```

### Creating a new project

Create the following file structure:
```
your-project
├─ src/main/java
├─ src/test/java
├─ build.gradle.kts
├─ settings.gradle.kts
├─ gradle.properties
└─ ...
```

Fill out `gradle.properties`:
```ini
title = Your Project Title
extraSourceSets = ["test"]
compositeSearchHeight = 0
excludeFromCompositeBuild = false
```

Fill out `build.gradle.kts`:
```gradle
plugins {
   id("us.ihmc.ihmc-build")
}

ihmc {
   group = "us.ihmc"
   version = "0.1.0"
   vcsUrl = "https://your.vcs/url"
   openSource = false
   maintainer = "Your Name"

   configureDependencyResolution()
   configurePublications()
}

mainDependencies {
   api("some:main-dependency:0.1")
}

testDependencies {
}

```

Copy the following into `settings.gradle.kts`:
```gradle
pluginManagement {
   plugins {
      id("us.ihmc.ihmc-build") version "0.29.3"
   }
}

buildscript {
   repositories {
      maven { url = uri("https://plugins.gradle.org/m2/") }
      mavenLocal()
   }
   dependencies {
      classpath("us.ihmc:ihmc-build:0.29.3")
   }
}

val ihmcSettingsConfigurator = us.ihmc.build.IHMCSettingsConfigurator(settings, logger, extra)
ihmcSettingsConfigurator.checkRequiredPropertiesAreSet()
ihmcSettingsConfigurator.configureExtraSourceSets()
ihmcSettingsConfigurator.findAndIncludeCompositeBuilds()
```

### Groups of projects

Handling groups of independently buildable projects is a core focus of this plugin. 
The [repository-group](https://github.com/ihmcrobotics/repository-group) project provides root level scripts 
so you can get started quickly and easily keep build scripts up to date with a periodic `git pull`. 
See the [repository-group README](https://github.com/ihmcrobotics/repository-group/blob/master/README.md) for more details.

Consider the following layout:

```
repository-group
├─ single-project-one
│  └─ src/main/java
│  └─ build.gradle.kts
│  └─ gradle.properties
│  └─ settings.gradle.kts
├─ single-project-two
│  └─ src/main/java
│  └─ build.gradle.kts
│  └─ gradle.properties
│  └─ settings.gradle.kts
├─ multi-project-one
│  └─ subproject-one
│     └─ src/main/java
│     └─ build.gradle.kts
│     └─ gradle.properties
│     └─ settings.gradle.kts
│  └─ subproject-two
│     └─ src/main/java
│     └─ build.gradle.kts
│     └─ gradle.properties
│     └─ settings.gradle.kts
│  └─ build.gradle.kts
│  └─ gradle.properties
│  └─ settings.gradle.kts
├─ build.gradle.kts
├─ settings.gradle.kts
└─ gradle.properties
```

The Gradle build files for the projects that do not contain `src/main/java` directories have a few differences from the ones above:

`gradle.properties` for project group:
```ini
title = Your Project
isProjectGroup = true
compositeSearchHeight = 0
excludeFromCompositeBuild = false
```

`build.gradle.kts` for project group:
```gradle
plugins {
   id("us.ihmc.ihmc-build")
}
```

`settings.gradle.kts` for project group:
```gradle
pluginManagement {
   plugins {
      id("us.ihmc.ihmc-build") version "0.29.3"
   }
}

buildscript {
   repositories {
      maven { url = uri("https://plugins.gradle.org/m2/") }
      mavenLocal()
   }
   dependencies {
      classpath("us.ihmc:ihmc-build:0.29.3")
   }
}

val ihmcSettingsConfigurator = us.ihmc.build.IHMCSettingsConfigurator(settings, logger, extra)
ihmcSettingsConfigurator.configureAsGroupOfProjects()
ihmcSettingsConfigurator.findAndIncludeCompositeBuilds()
```

### Troubleshooting

##### Group property requirement

The [Gradle composite builds](https://docs.gradle.org/current/userguide/composite_builds.html) feature requires the `group` and `version` project properties
to be set on all of the included projects. This is a common reason why a dependency 
build will not be included. See more at https://docs.gradle.org/current/userguide/composite_builds.html.

##### Gradle file trio requirement

The `ihmc-build` plugin requires all projects to have a `build.gradle.kts`, `settings.gradle.kts`, and `gradle.properties` file to qualify for build inclusion. This
another common reason that projects don't make it into the build.

### Learn more

##### Snapshots

Snapshots is an unsupported feature which is used internally in our CI.

`publish -PsnapshotMode=true -PpublishUrl=ihmcSnapshots`

Setting `snapshotMode=true` changes the version to `SNAPSHOT-$branchName-$integrationNumber` and enables parsing of versions 
declared as `SNAPSHOT-*`, matching
them to artifacts found to be available on IHMC's Artifactory snapshots repos.

Gradle Plugin Site: https://plugins.gradle.org/plugin/us.ihmc.ihmc-build

Documentation on Confluence: https://confluence.ihmc.us/display/BUILD/New+Build+Configuration+Documentation

Presentation outlining the purpose of this project: https://docs.google.com/presentation/d/1xH8kKYqLaBkRXms_04nb_yyoV6MRchLO8EAtz9WqfZA/edit?usp=sharing

#### Testing Without Publishing to the Gradle Plugins Site

Use `gradle publishToMavenLocal`
and the in the depending project, use in the `settings.gradle.kts`:
```kotlin
pluginManagement {
   repositories {
      mavenLocal()
      gradlePluginPortal()
   }
}
```

### Future plans

Get Kotlin build scripts working in IntelliJ and Eclipse.

Unfortunately, this project has hardcoded parameters specific to IHMC. making it hard for others to use. It's mostly open sourced for reference and ideas for other people. Feel free to fork it or copy code! In the next year, I would like to turn it into something easy to use for others. Furthermore, hopefully this project will shrink into non-existence as Gradle and Maven get better.
