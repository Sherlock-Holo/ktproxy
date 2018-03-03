package ktproxy.coroutineBuffer

import java.io.IOException

class OnlyWritable(override val message: String?) : IOException()