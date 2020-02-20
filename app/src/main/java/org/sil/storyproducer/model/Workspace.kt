package org.sil.storyproducer.model

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Bundle
import android.preference.PreferenceManager
import android.provider.Settings.Secure
import android.support.v4.provider.DocumentFile
import android.util.Log
import android.widget.Toast
import com.google.firebase.analytics.FirebaseAnalytics
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.channels.BroadcastChannel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import org.json.JSONObject
import org.sil.storyproducer.BuildConfig
import org.sil.storyproducer.R
import org.sil.storyproducer.model.messaging.Message
import org.sil.storyproducer.tools.file.deleteWorkspaceFile
import java.io.File
import java.net.URI
import java.net.URISyntaxException
import java.util.*

internal const val SLIDE_NUM = "CurrentSlideNum"
internal const val PHASE_TYPE = "CurrentPhaseType"

object Workspace {
    var workspace: DocumentFile = DocumentFile.fromFile(File(""))
        set(value) {
            field = value
            prefs?.edit()?.putString("workspace", field.uri.toString())?.apply()
            storiesUpdated = false
        }
    val Stories: MutableList<Story> = mutableListOf()
    var storiesUpdated = false
    var registration: Registration = Registration()
    var phases: List<PhaseType> = ArrayList()
    var activePhaseIndex: Int = 0
    var isInitialized = false
    var prefs: SharedPreferences? = null

    var activeStory: Story = emptyStory()
        set(value) {
            field = value
            //You are switching the active story.  Recall the last phase and slide.
            activePhase = value.lastPhaseType
            activeSlideNum = value.lastSlideNum
        }
    var activePhase: PhaseType
        get() = phases[activePhaseIndex]
        set(value) {
            activePhaseIndex = phases.indexOf(value)
        }

    val activeDirRoot: String
        get() {
            return activeStory.title
        }

    val activeDir: String = PROJECT_DIR
    val activeFilenameRoot: String
        get() {
            return "${activePhase.getShortName()}$activeSlideNum"
        }

    var activeSlideNum: Int = 0
        set(x) {
            field = if (x >= 0 && x < activeStory.slides.size && activePhase.checkValidDisplaySlideNum(x)) {
                x
            } else {
                0
            }
        }

    val activeSlide: Slide?
        get() {
            if (activeStory.title == "") return null
            return activeStory.slides[activeSlideNum]
        }

    val messages = ArrayList<Message>()
    val queuedMessages = ArrayList<JSONObject>()
    val messageChannel = BroadcastChannel<Message>(30)
    val toSendMessageChannel = Channel<Message>(100)
    var messageClient: MessageWebSocketClient? = null
    var nextMessageId = 0

    fun getRoccUrlPrefix(context: Context): String {
        return if (BuildConfig.ENABLE_IN_APP_ROCC_URL_SETTING) {
            PreferenceManager.getDefaultSharedPreferences(context).getString("ROCC_URL_PREFIX", BuildConfig.ROCC_URL_PREFIX)
                    ?: BuildConfig.ROCC_URL_PREFIX
        } else {
            BuildConfig.ROCC_URL_PREFIX
        }
    }


    fun getRoccWebSocketsUrl(context: Context): String {
        var baseUrl = if (BuildConfig.ENABLE_IN_APP_ROCC_URL_SETTING) {
            PreferenceManager.getDefaultSharedPreferences(context).getString("WEBSOCKETS_URL", BuildConfig.ROCC_WEBSOCKETS_PREFIX)
                    ?: BuildConfig.ROCC_WEBSOCKETS_PREFIX
        } else {
            BuildConfig.ROCC_WEBSOCKETS_PREFIX
        }
        val projectId = Secure.getString(context.contentResolver, Secure.ANDROID_ID)
        return "$baseUrl/phone/$projectId"
    }

    private lateinit var firebaseAnalytics: FirebaseAnalytics

    private const val WORKSPACE_KEY = "org.sil.storyproducer.model.workspace"

    fun initializeWorkspace(context: Context) {
        //first, see if there is already a workspace in shared preferences
        prefs = context.getSharedPreferences(WORKSPACE_KEY, Context.MODE_PRIVATE)
        setupWorkspacePath(context, Uri.parse(prefs!!.getString("workspace", "")))
        isInitialized = true
        firebaseAnalytics = FirebaseAnalytics.getInstance(context)
        Log.e("@pwhite", "about to create socket client ${getRoccWebSocketsUrl(context)}")
        val client = MessageWebSocketClient(URI(getRoccWebSocketsUrl(context)))
        client.connect()
        messageClient = client
        GlobalScope.launch {
            for (message in messageChannel.openSubscription()) {
                messages.add(message)
            }
        }
        GlobalScope.launch {
            for (message in toSendMessageChannel) {
                val js = messageToJson(message)
                if (messageClient?.isOpen == true) {
                    messageClient?.send(js.toString(2))
                } else {
                    queuedMessages.add(js)
                }
            }
        }
        GlobalScope.launch {
            while (true) {
                Log.e("@pwhite", "checking websocket status: ${messageClient?.isOpen}")
                if (messageClient?.isOpen != true) {
                    val client =  MessageWebSocketClient(URI(getRoccWebSocketsUrl(context)))
                    client.connectBlocking()
                    messageClient = client
                } else {
                    for (js in queuedMessages) {
                        messageClient?.send(js.toString(2))
                    }
                    queuedMessages.clear()
                }
                delay(5000)
            }
        }
    }

    fun ensureWebSocketConnection(context: Context): Boolean {
        return ensureWebSocketConnection(URI(getRoccWebSocketsUrl(context)))
    }

    fun ensureWebSocketConnection(uri: URI): Boolean {
        var client = messageClient
        if (client == null || client.isClosed) {
            try {
                client = MessageWebSocketClient(uri)
                client.connectBlocking()
                messageClient = client
                return true
            } catch (e: URISyntaxException) {
                return false
            }
        }
        return true
    }

    fun messageToJson(m: Message): JSONObject {
        val js = JSONObject()
        js.put("type", "text")
        js.put("isTranscript", m.isTranscript)
        js.put("slideNumber", m.slideNumber)
        js.put("storyId", m.storyId)
        js.put("text", m.message)
        return js
    }

    fun logEvent(context: Context, eventName: String, params: Bundle = Bundle()) {
        params.putString("phone_id", Secure.getString(context.contentResolver,
                Secure.ANDROID_ID))
        params.putString("story_number", activeStory.titleNumber)
        params.putString("ethnolog", registration.projectEthnoCode)
        params.putString("lwc", registration.projectMajorityLanguage)
        params.putString("translator_email", registration.translatorEmail)
        params.putString("trainer_email", registration.trainerEmail)
        params.putString("consultant_email", registration.consultantEmail)
        firebaseAnalytics.logEvent(eventName, params)
    }

    fun setupWorkspacePath(context: Context, uri: Uri) {
        try {
            workspace = DocumentFile.fromTreeUri(context, uri)!!
            registration.load(context)
        } catch (e: Exception) {
        }
        updateStories(context)
    }

    fun clearWorkspace() {
        workspace = DocumentFile.fromFile(File(""))

    }

    private fun updateStories(context: Context) {
        //Iterate external files directories.
        //for all files in the workspace, see if they are folders that have templates.
        if (storiesUpdated) return
        if (workspace.isDirectory) {
            //find all stories
            Stories.removeAll(Stories)
            for (storyPath in workspace.listFiles()) {
                //TODO - check storyPath.name against titles.
                if (storyPath.isDirectory) {
                    val story = parseStoryIfPresent(context, storyPath)
                    if (story != null) {
                        Stories.add(story)
                    }
                }
            }
        }
        //sort by title.
        Stories.sortBy { it.title }
        //update phases based upon registration selection
        phases = when (registration.consultantLocationType) {
            "Remote" -> PhaseType.getRemotePhases()
            else -> PhaseType.getLocalPhases()
        }
        activePhaseIndex = 0
        updateStoryLocalCredits(context)
        storiesUpdated = true
    }

    fun deleteVideo(context: Context, path: String) {
        activeStory.outputVideos.remove(path)
        deleteWorkspaceFile(context, "$VIDEO_DIR/$path")
    }

    fun updateStoryLocalCredits(context: Context) {
        for (story in Stories) {
            for (slide in story.slides) {
                if (slide.slideType == SlideType.LOCALCREDITS) { //local credits
                    if (slide.translatedContent == "") {
                        slide.translatedContent = context.getString(R.string.LC_starting_text)
                    }
                }
            }
        }
    }

    fun isLocalCreditsChanged(context: Context): Boolean {
        var isChanged = false
        val orgLCText = context.getString(R.string.LC_starting_text)
        for (slide in activeStory.slides) {
            if (slide.slideType == SlideType.LOCALCREDITS) { //local credits
                if (slide.translatedContent != orgLCText) {
                    isChanged = true
                }
            }
        }
        return isChanged
    }

    fun getSongFilename(): String? {
        val songSlide = activeStory.slides.firstOrNull { it.slideType == SlideType.LOCALSONG }
        return (songSlide?.dramatizationRecordings?.selectedFile
                ?: songSlide?.draftRecordings?.selectedFile)?.fileName
    }
}


