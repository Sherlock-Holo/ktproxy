package ktproxy.coroutineBuffer

import java.io.IOException

class OnlyReadable(override val message: String?) : IOException()