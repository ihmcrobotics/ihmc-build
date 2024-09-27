package us.ihmc.ci

class AllocationInstrumenter(val version: String)
{
   fun instrumenter(): String = "com.google.code.java-allocation-instrumenter:java-allocation-instrumenter:$version"
}