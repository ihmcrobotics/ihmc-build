plugins {
   `java-library`
}

repositories {
   jcenter()
}

println("hello")

dependencies {
   api("org.apache.commons:commons-lang3:3.11")
   implementation("org.apache.commons:commons-lang3:3.11")

   testCompile("org.junit.jupiter:junit-jupiter-api:5.7.0")
}