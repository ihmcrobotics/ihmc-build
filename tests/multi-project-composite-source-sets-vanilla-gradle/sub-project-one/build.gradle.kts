plugins {
   `java-library`
}

repositories {
   jcenter()
}

println("hello")

dependencies {
   api("org.apache.commons:commons-lang3:3.8.1")
   implementation("org.apache.commons:commons-lang3:3.6.1")

   testCompile("org.junit.jupiter:junit-jupiter-api:5.4.0")
}