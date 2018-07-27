# IHMC Build Plugin

Composite build and IDE classpath seperation support for JVM Gradle projects. Currently for use only by IHMC projects.

### Table of Contents
1. [Quick project setup](#quick-project-setup)
1. [Groups of projects](#groups-of-projects)
1. [Commands](#commands)
1. [Learn more](#learn-more)
1. [Advantages over standard Gradle](#advantages-over-standard-gradle)
1. [Future plans](#future-plans)

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

**settings.gradle**
```gradle
buildscript {
   repositories {
      maven { url "https://plugins.gradle.org/m2/" }
      mavenLocal()
   }
   dependencies {
      classpath "us.ihmc:ihmc-build:0.14.0"
   }
}

import us.ihmc.build.IHMCSettingsConfigurator

/** Browse source at https://github.com/ihmcrobotics/ihmc-build */
def ihmcSettingsConfigurator = new IHMCSettingsConfigurator(settings, logger, ext)
ihmcSettingsConfigurator.checkRequiredPropertiesAreSet()
ihmcSettingsConfigurator.configureExtraSourceSets()
ihmcSettingsConfigurator.findAndIncludeCompositeBuilds()
```

**gradle.properties**
```ini
kebabCasedName = your-project
pascalCasedName = YourProject
extraSourceSets = ["test"]
publishUrl = local

# When building from this directory, set how many directories
# to go up and do a search for more builds to include.
compositeSearchHeight = 0

# When another build is searching for builds to include,
# tell it to leave you out.
excludeFromCompositeBuild = false
```

**build.gradle**
```gradle
buildscript {
   repositories {
      maven { url "https://plugins.gradle.org/m2/" }
      mavenLocal()
   }
   dependencies {
      classpath "us.ihmc:ihmc-build:0.14.0"
   }
}
apply plugin: "us.ihmc.ihmc-build"

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
   compile group: 'junit', name: 'junit', version: '4.11'
}
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
publishUrl = local

# Tells the build plugin to always include subprojects
isProjectGroup = true

# When building from this directory, set how many directories
# to go up and do a search for more builds to include.
compositeSearchHeight = 0

# When another build is searching for builds to include,
# tell it to leave this entire project group out.
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
      classpath "us.ihmc:ihmc-build:0.14.0"
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
      classpath "us.ihmc:ihmc-build:0.14.0"
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
