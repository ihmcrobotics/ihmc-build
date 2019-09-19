plugins {
   `java-library`
}

repositories {
   jcenter()
}

println("hello")

dependencies {
   api("org.apache.commons:commons-lang3:3.9")
   implementation("org.apache.commons:commons-lang3:3.9")

   testCompile("org.junit.jupiter:junit-jupiter-api:5.4.0")
}