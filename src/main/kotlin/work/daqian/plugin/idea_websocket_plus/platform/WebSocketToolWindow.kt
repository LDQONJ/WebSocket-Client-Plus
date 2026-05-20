package work.daqian.plugin.idea_websocket_plus.platform

import work.daqian.plugin.idea_websocket_plus.client.EventHandler
import work.daqian.plugin.idea_websocket_plus.client.Headers
import work.daqian.plugin.idea_websocket_plus.client.ServerUri
import work.daqian.plugin.idea_websocket_plus.client.WebSocketClientImpl
import com.google.gson.GsonBuilder
import com.google.gson.JsonParser
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Font
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class WebSocketToolWindow {

    private val logger = Logger.getInstance(javaClass)
    private val properties = WebSocketProperties()

    private var client: WebSocketClientImpl? = null

    private val urlHistories = ComboBox(arrayOf("") + properties.getUrls()).apply {
        isEditable = true
    }

    private val connectButton = JButton("Connect").apply {
        addActionListener { handleConnectButtonClicked() }
    }
    private val disconnectButton = JButton("Disconnect").apply {
        isEnabled = false
        addActionListener { handleDisconnectButtonClicked() }
    }

    private val isConnectedLabel = JLabel()

    private val formatJsonCheckbox = JBCheckBox("Auto Format JSON", true)

    private val heartbeatEnabledCheckbox = JBCheckBox("Enable Heartbeat", false).apply {
        addActionListener {
            if (client?.isOpen == true) {
                if (isSelected) startHeartbeat() else stopHeartbeat()
            }
        }
    }
    private val heartbeatIntervalField = JBTextField("30", 3)
    private val heartbeatMessageField = JBTextField("ping", 10)
    private var heartbeatTask: ScheduledFuture<*>? = null

    private val responseArea = JBTextArea(15, 50).apply {
        isEditable = false
        val scheme = EditorColorsManager.getInstance().globalScheme
        font = UIUtil.getFontWithFallback(scheme.consoleFontName, Font.PLAIN, scheme.consoleFontSize)
        margin = JBUI.insets(5)
    }
    private val responseAreaScrollPane = JBScrollPane(responseArea)


    private val requestArea = JBTextArea().apply {
        val scheme = EditorColorsManager.getInstance().globalScheme
        font = UIUtil.getFontWithFallback(scheme.consoleFontName, Font.PLAIN, scheme.consoleFontSize)
        margin = JBUI.insets(5)
    }
    private val requestAreaScrollPane = JBScrollPane(requestArea)

    private val sendMessageButton = JButton("Send").apply {
        isEnabled = false
        addActionListener { handleSendMessageButtonClicked() }
    }

    private fun handleConnectButtonClicked() {
        val url = try {
            ServerUri(urlHistories.item)
        } catch (e: Exception) {
            handleConnectionFailed("Invalid URL")
            return
        }

        connectButton.isEnabled = false
        isConnectedLabel.text = "Connecting..."
        isConnectedLabel.foreground = Color.ORANGE

        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                this.client?.close()
                val headers = Headers(emptyMap())
                val handler = EventHandler(
                    onOpen = {},
                    onClose = { handleConnectionClosed() },
                    onError = { e ->
                        logger.warn(e)
                        handleConnectionFailed(e.message ?: "Unknown Error")
                    },
                    onMessage = { message ->
                        val displayMessage = if (formatJsonCheckbox.isSelected) formatIfJson(message) else message
                        responseArea + "Response =>\n$displayMessage"
                    }
                )
                val newClient = WebSocketClientImpl(url, headers, handler)
                this.client = newClient

                if (newClient.connectBlocking()) {
                    handleConnectionSuccess()
                } else {
                    handleConnectionFailed("Connection failed")
                }
            } catch (e: Throwable) {
                logger.warn(e)
                handleConnectionFailed(e.message ?: "Connection error")
            }
        }
    }

    private fun formatIfJson(text: String): String {
        return try {
            val jsonElement = JsonParser.parseString(text)
            val gson = GsonBuilder().setPrettyPrinting().create()
            gson.toJson(jsonElement)
        } catch (e: Exception) {
            text
        }
    }

    private fun handleDisconnectButtonClicked() {
        this.client?.let {
            it.close()
            handleConnectionClosed()
        }
    }

    private fun handleSendMessageButtonClicked() {
        val message = requestArea.text
        send(message)
    }

    private fun send(message: String) {
        val displayMessage = if (formatJsonCheckbox.isSelected) formatIfJson(message) else message
        responseArea + "Request =>\n$displayMessage"
        this.client?.send(message)
    }

    private fun startHeartbeat() {
        stopHeartbeat()
        if (!heartbeatEnabledCheckbox.isSelected) return

        val interval = heartbeatIntervalField.text.toLongOrNull() ?: 30L
        val message = heartbeatMessageField.text

        heartbeatTask = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay({
            if (this.client?.isOpen == true) {
                send(message)
            } else {
                stopHeartbeat()
            }
        }, interval, interval, TimeUnit.SECONDS)
    }

    private fun stopHeartbeat() {
        heartbeatTask?.cancel(true)
        heartbeatTask = null
    }

    private fun handleConnectionSuccess() {
        startHeartbeat()
        ApplicationManager.getApplication().invokeLater {
            properties.setUrl(urlHistories.item)
            isConnectedLabel.text = "Connected to ${urlHistories.item}"
            isConnectedLabel.foreground = Color.GREEN
            connectButton.isEnabled = false
            disconnectButton.isEnabled = true
            sendMessageButton.isEnabled = true
        }
    }

    private fun handleConnectionFailed(reason: String = "Connection Failed") {
        stopHeartbeat()
        ApplicationManager.getApplication().invokeLater {
            this.client = null
            isConnectedLabel.text = reason
            isConnectedLabel.foreground = Color.RED
            connectButton.isEnabled = true
            disconnectButton.isEnabled = false
            sendMessageButton.isEnabled = false
        }
    }

    private fun handleConnectionClosed() {
        stopHeartbeat()
        ApplicationManager.getApplication().invokeLater {
            this.client = null
            isConnectedLabel.text = "Connection Closed"
            isConnectedLabel.foreground = Color.YELLOW
            connectButton.isEnabled = true
            disconnectButton.isEnabled = false
            sendMessageButton.isEnabled = false
        }
    }

    fun getComponent(): JComponent {
        val splitter = OnePixelSplitter(false, 0.5f).apply {
            firstComponent = createResponsePanel()
            secondComponent = createRequestPanel()
            dividerWidth = 2
        }

        return panel {
            row("URL: ") {
                cell(urlHistories).align(Align.FILL).resizableColumn()
                cell(connectButton)
                cell(disconnectButton)
            }
            row {
                cell(isConnectedLabel)
            }
            row {
                cell(heartbeatEnabledCheckbox)
                label("Interval (s):")
                cell(heartbeatIntervalField).widthGroup("hb")
                label("Message:")
                cell(heartbeatMessageField).align(Align.FILL)
            }
            row {
                cell(splitter).align(Align.FILL)
            }.resizableRow()
        }.apply {
            border = JBUI.Borders.empty(10, 10, 5, 10)
        }
    }

    private fun createResponsePanel(): JPanel {
        return JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(5, 0, 5, 5)
            val header = JPanel(BorderLayout()).apply {
                border = JBUI.Borders.emptyBottom(5)
                add(JBLabel("Responses from server"), BorderLayout.WEST)
                val controls = JPanel(BorderLayout()).apply {
                    add(formatJsonCheckbox, BorderLayout.WEST)
                    add(JButton("Clear").apply {
                        addActionListener { responseArea.truncateMessages() }
                    }, BorderLayout.EAST)
                }
                add(controls, BorderLayout.EAST)
            }
            add(header, BorderLayout.NORTH)
            add(responseAreaScrollPane, BorderLayout.CENTER)
        }
    }

    private fun createRequestPanel(): JPanel {
        return JPanel(BorderLayout()).apply {
            border = JBUI.Borders.empty(5, 5, 5, 0)
            val header = JPanel(BorderLayout()).apply {
                border = JBUI.Borders.emptyBottom(5)
                add(JBLabel("Request to server"), BorderLayout.WEST)
                val buttons = JPanel(BorderLayout()).apply {
                    add(JButton("Format JSON").apply {
                        addActionListener {
                            requestArea.text = formatIfJson(requestArea.text)
                        }
                    }, BorderLayout.WEST)
                    val rightButtons = JPanel(BorderLayout()).apply {
                        add(JButton("Clear").apply {
                            addActionListener { requestArea.truncateMessages() }
                        }, BorderLayout.WEST)
                        add(sendMessageButton, BorderLayout.EAST)
                    }
                    add(rightButtons, BorderLayout.EAST)
                }
                add(buttons, BorderLayout.EAST)
            }
            add(header, BorderLayout.NORTH)
            add(requestAreaScrollPane, BorderLayout.CENTER)
        }
    }
}

private operator fun JBTextArea.plus(message: String) {
    val formatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")
    val now = LocalDateTime.now()
    val fullMessage = "(${now.format(formatter)}) $message\n\n"

    ApplicationManager.getApplication().invokeLater {
        this.append(fullMessage)
        // 性能优化：当文本过长时自动截断（保留最近的 50000 字符）
        if (this.text.length > 100000) {
            this.text = this.text.substring(this.text.length - 50000)
        }
        // 自动滚动到底部
        this.caretPosition = this.document.length
    }
}

private fun JBTextArea.truncateMessages() {
    this.text = ""
}
