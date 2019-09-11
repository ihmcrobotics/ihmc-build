package us.ihmc.build

class SemanticVersionNumber(private val rawVersion: String) : Comparable<SemanticVersionNumber>
{
   private val version = rawVersion.trim().split("-").get(0)

   init {
      if (version.isBlank() || !version.matches("[0-9]+(\\.[0-9]+)*".toRegex()))
         throw IllegalArgumentException("Invalid version format: $version")
   }

   fun get(): String
   {
      return this.version
   }

   override operator fun compareTo(other: SemanticVersionNumber): Int
   {
      val thisParts = this.get().split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
      val thatParts = other.get().split("\\.".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
      val length = Math.max(thisParts.size, thatParts.size)
      for (i in 0 until length)
      {
         val thisPart = if (i < thisParts.size)
            Integer.parseInt(thisParts[i])
         else
            0
         val thatPart = if (i < thatParts.size)
            Integer.parseInt(thatParts[i])
         else
            0
         if (thisPart < thatPart)
            return -1
         if (thisPart > thatPart)
            return 1
      }
      return 0
   }

   override fun equals(other: Any?): Boolean
   {
      if (this === other)
         return true
      if (other == null)
         return false
      return if (this.javaClass != other.javaClass) false else this.compareTo(other as SemanticVersionNumber) == 0
   }

   override fun hashCode(): Int
   {
      return super.hashCode()
   }
}