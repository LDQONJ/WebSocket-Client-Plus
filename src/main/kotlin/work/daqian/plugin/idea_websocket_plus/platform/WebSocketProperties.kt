package work.daqian.plugin.idea_websocket_plus.platform

import com.intellij.ide.util.PropertiesComponent

class WebSocketProperties {
    private val propertiesComponent = PropertiesComponent.getInstance()

    companion object {
        private const val KEY_URL = "work.daqian.plugin.idea_websocket_plus.KEY_URL"
    }

    fun getUrls(): Array<String> = propertiesComponent.getValues(KEY_URL) ?: emptyArray()

    fun setUrl(url: String) {
        val urls = getUrls().toMutableList()
        urls.remove(url)
        urls.add(0, url)
        propertiesComponent.setValues(KEY_URL, urls.toTypedArray())
    }
}
