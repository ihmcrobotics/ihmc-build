plugins {
   `java-library`
}

repositories {
   jcenter()
}

dependencies {
   api("commons-io:commons-io:2.11.0")

   testCompile("junit:junit:4.13.2")
}