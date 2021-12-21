plugins {
   `java-library`
}

repositories {
   mavenCentral()
}

println("hello")

dependencies {
   compile("us.ihmc:sub-project-one-test:source")

   testCompile("junit:junit:4.13.2")
}