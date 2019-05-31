package dainslef

import org.springframework.cloud.stream.annotation.Input
import org.springframework.cloud.stream.annotation.Output
import org.springframework.messaging.MessageChannel
import org.springframework.messaging.SubscribableChannel

interface Logger {
    val logger get() = org.slf4j.LoggerFactory.getLogger(javaClass)
}

interface CustomSinkSource {

    // Receive message use input1 channel
    @Input(inChannel1)
    fun input1(): SubscribableChannel

    // Receive message use input2 channel
    @Input(inChannel2)
    fun input2(): SubscribableChannel

    // Send message use output1 channel
    @Output(outChannel1)
    fun output1(): MessageChannel

    // Send message use output2 channel
    @Output(outChannel2)
    fun output2(): MessageChannel

    companion object {
        const val outChannel1 = "customOutChannel1"
        const val outChannel2 = "customOutChannel2"
        const val inChannel1 = "customInChannel1"
        const val inChannel2 = "customInChannel2"
    }

}

data class CustomMessage(val index: Int = 0, val type: String = "", val content: String = "")