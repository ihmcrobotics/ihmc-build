plugins {
   `java-library`
}

repositories {
   mavenCentral()
}

println("hello")

dependencies {
   api("org.apache.commons:commons-lang3:3.12.0")
   implementation("org.apache.commons:commons-lang3:3.12.0")

   testCompile("org.junit.jupiter:junit-jupiter-api:5.8.2")
}