plugins {
   `java-library`
}

repositories {
   jcenter()
}

dependencies {
   api("commons-io:commons-io:2.8.0")

   testCompile("junit:junit:4.13.1")
}