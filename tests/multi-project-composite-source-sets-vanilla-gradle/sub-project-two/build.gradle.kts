plugins {
   `java-library`
}

repositories {
   jcenter()
}

dependencies {
   api("commons-io:commons-io:2.6")

   testCompile("junit:junit:4.12")
}