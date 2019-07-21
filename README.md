# IHMC Build Plugin

Composite build and IDE classpath separation support for JVM Gradle projects.

### Main features

- Seperate source set classpaths when building projects in IDEs
- Utilize composite builds to make each project standalone by default
- Keep Bamboo CI configuration powerful, minimal, and flexible

#### Contents
1. [Properties](#properties)
1. [Quick project setup](#quick-project-setup)
1. [Groups of projects](#groups-of-projects)
1. [Commands](#commands)
1. [Learn more](#learn-more)
1. [Advantages over standard Gradle](#advantages-over-standard-gradle)
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

(for project groups) `projectGroup` - If this project exists only to contain other `ihmc-build` projects. (i.e. a "glue" project)

(optional) `publishUrl` - See publishing commands below. Defaults to "local"

### Quick project setup

Create the following file structure:
```
your-project
├─ src/main/java
├─ src/test/java
├─ build.gradle
├─ settings.gradle
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

Fill out `build.gradle`:
```gradle
plugins {
   id("us.ihmc.ihmc-build") version "0.16.3"
}

ihmc {
   group = "us.ihmc"
   version = "0.1.0"
   vcsUrl = "https://your.vcs/url"
   openSource = false
   
   configureDependencyResolution()
   configurePublications()
}

mainDependencies {
   compile group: 'some', name: 'main-dependency', version: '0.1'
}

testDependencies {
}
```

Copy the following into `settings.gradle`:
```gradle
buildscript {
   repositories {
      maven { url "https://plugins.gradle.org/m2/" }
      mavenLocal()
   }
   dependencies {
      classpath "us.ihmc:ihmc-build:0.16.3"
   }
}

import us.ihmc.build.IHMCSettingsConfigurator

/** Browse source at https://github.com/ihmcrobotics/ihmc-build */
def ihmcSettingsConfigurator = new IHMCSettingsConfigurator(settings, logger, ext)
ihmcSettingsConfigurator.checkRequiredPropertiesAreSet()
ihmcSettingsConfigurator.configureExtraSourceSets()
ihmcSettingsConfigurator.findAndIncludeCompositeBuilds()
```

### Groups of projects

Handling groups of independently buildable projects is a core focus of this plugin. The [repository-group](https://github.com/ihmcrobotics/repository-group) project provides root level scripts so you can get started quickly and easily keep build scripts up to date with a periodic `git pull`. See the [repository-group README](https://github.com/ihmcrobotics/repository-group/blob/master/README.md) for more details.

Consider the following layout:

```
repository-group
├─ single-project-one
│  └─ src/main/java
│  └─ build.gradle
│  └─ gradle.properties
│  └─ settings.gradle
├─ single-project-two
│  └─ src/main/java
│  └─ build.gradle
│  └─ gradle.properties
│  └─ settings.gradle
├─ multi-project-one
│  └─ subproject-one
│     └─ src/main/java
│     └─ build.gradle
│     └─ gradle.properties
│     └─ settings.gradle
│  └─ subproject-two
│     └─ src/main/java
│     └─ build.gradle
│     └─ gradle.properties
│     └─ settings.gradle
│  └─ build.gradle
│  └─ gradle.properties
│  └─ settings.gradle
├─ build.gradle
├─ settings.gradle
└─ gradle.properties
```

The Gradle build files for the projects that do not contain `src/main/java` directories have a few differences from the ones above:

**gradle.properties (group)**
```ini
kebabCasedName = your-project
pascalCasedName = YourProject
isProjectGroup = true
compositeSearchHeight = 0
excludeFromCompositeBuild = false
```

**settings.gradle (group)**
```gradle
buildscript {
   repositories {
      maven { url "https://plugins.gradle.org/m2/" }
      mavenLocal()
   }
   dependencies {
      classpath "us.ihmc:ihmc-build:0.16.3"
   }
}

import us.ihmc.build.IHMCSettingsConfigurator

def ihmcSettingsConfigurator = new IHMCSettingsConfigurator(settings, logger, ext)
ihmcSettingsConfigurator.configureAsGroupOfProjects()
ihmcSettingsConfigurator.findAndIncludeCompositeBuilds()
```

**build.gradle (group)**
```gradle
buildscript {
   repositories {
      maven { url "https://plugins.gradle.org/m2/" }
      mavenLocal()
      jcenter()
   }
   dependencies {
      classpath "us.ihmc:ihmc-build:0.16.3"
   }
}
apply plugin: "us.ihmc.ihmc-build"
```

### Commands

The following covers running tasks over your builds, which may be composite builds.

##### Custom Commands

`gradle compositeTask -PtaskName=someTaskName`

For example, to run the Java plugin's `compileJava` task on your project plus all included Java projects, run:

`gradle compositeTask -PtaskName=compileJava`.

As always, pass any additional properties required for your task
via additional `-PsomeProperty=someValue`.

##### Publish release

Set `bintray_user` and `bintray_key` in `~/.gradle/gradle.properties` after generating an API key on Bintray.

`gradle publish -PpublishUrl=ihmcRelease`

Publishes `your-project-0.1.0.jar` to Bintray if openSource == true, else publish to Artifactory. Uses declared `version` in build.gradle

##### Publish locally

`gradle publish -PpublishUrl=local`

Publish `your-project-LOCAL.jar` to `$home/.m2`

##### Publish Custom URL

`gradle publish -PpublishUrl=https://my.site/repo -PpublishUsername=username -PpublishPassword=password`

or use the `addPublishUrl()` function:

**build.gradle**
```gradle
ihmc {
   ...
   
   configureDependencyResolution()  // Between here ->
   addPublishUrl("myVendor", "https://my.site/my-repo", publishUsername, publishPassword)
   configurePublications() // <- and here
}
```

##### Publish Over Composite Build

For convenience, an alias for `gradle compositeTask -PtaskName=publish`:

`gradle publishAll`

For example, publishing a project group:

`gradle publishAll -PpublishUrl=ihmcRelease`

##### Clean build directories

`gradle cleanBuild`

Cleans `build/` (Gradle), `bin/` (Eclipse), and `out/` (IntelliJ) build directories from all included projects.

##### Snapshots

Snapshots is an unsupported feature which is used internally in our CI.

`publish -PsnapshotMode=true -PpublishUrl=ihmcSnapshots`

Setting `snapshotMode=true` changes the version to `SNAPSHOT-$branchName-$integrationNumber` and enables parsing of versions declared as `SNAPSHOT-*`, matching
them to artifacts found to be available on IHMC's Artifactory snapshots repos.

##### Maven Repositories

To add a Maven repository, use the `repository` function in the `ihmc` extension. Note that projects that depend on this will also need to have these repositories declared, so use them sparingly.
```
ihmc {
   ...

   configureDependencyResolution()  // Between here ->
   repository("http://maven-eclipse.github.io/maven")
   repository("https://artifactory.ihmc.us/artifactory/proprietary-releases/", artifactoryUsername, artifactoryPassword)
   configurePublications() // <- and here
}
```

### Troubleshooting

##### Group property requirement

The [Gradle composite builds](https://docs.gradle.org/current/userguide/composite_builds.html) feature requires the `group` and `version` project properties
to be set on all of the included projects. This is a common reason why a dependency 
build will not be included. See more at https://docs.gradle.org/current/userguide/composite_builds.html.

##### Gradle file trio requirement

The `ihmc-build` plugin requires all projects to have a `build.gradle`, `settings.gradle`, and `gradle.properties` file to qualify for build inclusion. This
another common reason that projects don't make it into the build.

### Learn more

Gradle Plugin Site: https://plugins.gradle.org/plugin/us.ihmc.ihmc-build

Documentation on Confluence: https://confluence.ihmc.us/display/BUILD/New+Build+Configuration+Documentation

Presentation outlining the purpose of this project: https://docs.google.com/presentation/d/1xH8kKYqLaBkRXms_04nb_yyoV6MRchLO8EAtz9WqfZA/edit?usp=sharing

### Advantages over standard Gradle

- Seperate source set classpaths when building projects in IDEs
- Utilize composite builds to make each project standalone by default
- Keep Bamboo CI configuration powerful, minimal, and flexible

### Future plans

Get Kotlin build scripts working in IntelliJ and Eclipse.

Unfortunately, this project has hardcoded parameters specific to IHMC. making it hard for others to use. It's mostly open sourced for reference and ideas for other people. Feel free to fork it or copy code! In the next year, I would like to turn it into something easy to use for others. Furthermore, hopefully this project will shrink into non-existence as Gradle and Maven get better.
