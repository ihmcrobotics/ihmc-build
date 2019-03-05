plugins {
   `java-library`
}

repositories {
   jcenter()
}

println("hello")

dependencies {
   compile("us.ihmc:sub-project-one-test:source")

   testCompile("junit:junit:4.12")
}