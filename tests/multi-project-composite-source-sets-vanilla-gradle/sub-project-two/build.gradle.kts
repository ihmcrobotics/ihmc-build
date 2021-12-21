plugins {
   `java-library`
}

repositories {
   mavenCentral()
}

dependencies {
   api("commons-io:commons-io:2.11.0")

   testCompile("junit:junit:4.13.2")
}