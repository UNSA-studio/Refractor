package unsa.rfr.com

import java.util.UUID

object RfrIdGenerator {
    fun generate(): String {
        val uuid = UUID.randomUUID().toString().replace("-", "").take(12)
        val chunked = uuid.chunked(4).joinToString("-")
        val suffix = (100..999).random()
        return "$chunked-user$suffix"
    }
}
