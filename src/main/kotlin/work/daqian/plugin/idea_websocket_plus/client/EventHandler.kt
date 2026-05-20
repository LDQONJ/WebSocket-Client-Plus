package work.daqian.plugin.idea_websocket_plus.client

data class EventHandler(
    val onOpen: () -> Unit,
    val onClose: () -> Unit,
    val onError: (e: Throwable) -> Unit,
    val onMessage: (message: String) -> Unit,
)
