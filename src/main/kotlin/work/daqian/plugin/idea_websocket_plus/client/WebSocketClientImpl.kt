package work.daqian.plugin.idea_websocket_plus.client

import org.java_websocket.client.WebSocketClient
import org.java_websocket.handshake.ServerHandshake
import java.net.URI

class WebSocketClientImpl(
    serverUri: ServerUri,
    headers: Headers,
    private val handler: EventHandler
): WebSocketClient(URI(serverUri.value), headers) {

    override fun onOpen(handshakedata: ServerHandshake?) {
        handler.onOpen()
    }

    override fun onMessage(message: String?) {
        message?.let { handler.onMessage(it)}
    }

    override fun onError(ex: Exception?) {
        ex?.let {
            handler.onError(ex)
        }
    }

    override fun onClose(code: Int, reason: String?, remote: Boolean) {
        handler.onClose()
    }
}
