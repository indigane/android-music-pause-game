package home.musical_chair_statues

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.media.session.MediaSessionManager
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaControllerCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.text.TextUtils
import android.view.KeyEvent
import android.view.View
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.edit

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TIMER_INTERVAL_MS = 16L
    }

    private lateinit var albumArt: ImageView
    private lateinit var trackTitle: TextView
    private lateinit var trackArtist: TextView
    private lateinit var gameModeSelector: RadioGroup
    private lateinit var modeMusicalStatues: RadioButton
    private lateinit var modeMusicalChairs: RadioButton
    private lateinit var playTimeMin: SeekBar
    private lateinit var playTimeMax: SeekBar
    private lateinit var pauseTimeMin: SeekBar
    private lateinit var pauseTimeMax: SeekBar
    private lateinit var pauseTimeLabel: TextView
    private lateinit var playTimeMinLabel: TextView
    private lateinit var playTimeMaxLabel: TextView
    private lateinit var pauseTimeMinLabel: TextView
    private lateinit var pauseTimeMaxLabel: TextView
    private lateinit var hapticFeedback: SwitchCompat
    private lateinit var primaryActionButton: ImageButton
    private lateinit var timerView: TimerView
    private var timer: CountDownTimer? = null

    private lateinit var audioManager: AudioManager
    private var mediaSession: MediaSessionCompat? = null

    private var isGameRunning = false
    private var mediaController: MediaControllerCompat? = null
    private var isMusicPlaying = false
    private var isPausedForMusicalChairs = false
    private val handler = Handler(Looper.getMainLooper())

    private val mediaControllerCallback = object : MediaControllerCompat.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadataCompat?) {
            updateMediaInfo(metadata)
        }

        override fun onPlaybackStateChanged(state: PlaybackStateCompat?) {
            if (state?.state == PlaybackStateCompat.STATE_PLAYING) {
                if (trackTitle.text.toString() == getString(R.string.no_music_playing)) {
                    trackTitle.text = getString(R.string.music_is_playing)
                    trackArtist.text = ""
                    albumArt.setImageResource(R.drawable.ic_play)
                }
            } else {
                trackTitle.text = getString(R.string.no_music_playing)
                trackArtist.text = ""
                albumArt.setImageResource(R.drawable.ic_play)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        albumArt = findViewById(R.id.albumArt)
        trackTitle = findViewById(R.id.trackTitle)
        trackArtist = findViewById(R.id.trackArtist)
        gameModeSelector = findViewById(R.id.gameModeSelector)
        modeMusicalStatues = findViewById(R.id.modeMusicalStatues)
        modeMusicalChairs = findViewById(R.id.modeMusicalChairs)
        playTimeMin = findViewById(R.id.playTimeMin)
        playTimeMax = findViewById(R.id.playTimeMax)
        pauseTimeMin = findViewById(R.id.pauseTimeMin)
        pauseTimeMax = findViewById(R.id.pauseTimeMax)
        pauseTimeLabel = findViewById(R.id.pauseTimeLabel)
        playTimeMinLabel = findViewById(R.id.playTimeMinLabel)
        playTimeMaxLabel = findViewById(R.id.playTimeMaxLabel)
        pauseTimeMinLabel = findViewById(R.id.pauseTimeMinLabel)
        pauseTimeMaxLabel = findViewById(R.id.pauseTimeMaxLabel)
        hapticFeedback = findViewById(R.id.hapticFeedback)
        primaryActionButton = findViewById(R.id.primaryActionButton)
        timerView = findViewById(R.id.timerView)

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        initMediaSession()
        loadSettings()

        setupSliderWithValue(playTimeMin, playTimeMinLabel, "playTimeMin")
        setupSliderWithValue(playTimeMax, playTimeMaxLabel, "playTimeMax")
        setupSliderWithValue(pauseTimeMin, pauseTimeMinLabel, "pauseTimeMin")
        setupSliderWithValue(pauseTimeMax, pauseTimeMaxLabel, "pauseTimeMax")

        gameModeSelector.setOnCheckedChangeListener { _, checkedId ->
            saveGameMode(checkedId)
            if (checkedId == R.id.modeMusicalChairs) {
                pauseTimeMin.visibility = View.GONE
                pauseTimeMax.visibility = View.GONE
                pauseTimeLabel.visibility = View.GONE
                pauseTimeMinLabel.visibility = View.GONE
                pauseTimeMaxLabel.visibility = View.GONE
            } else {
                pauseTimeMin.visibility = View.VISIBLE
                pauseTimeMax.visibility = View.VISIBLE
                pauseTimeLabel.visibility = View.VISIBLE
                pauseTimeMinLabel.visibility = View.VISIBLE
                pauseTimeMaxLabel.visibility = View.VISIBLE
            }
        }

        hapticFeedback.setOnCheckedChangeListener { _, isChecked ->
            saveHapticFeedback(isChecked)
        }

        primaryActionButton.setOnClickListener {
            handlePrimaryAction()
        }

        val prefs = getSharedPreferences("prefs", MODE_PRIVATE)
        if (prefs.getBoolean("firstrun", true)) {
            showOnboardingDialog()
        }
    }

    private fun setupSliderWithValue(slider: SeekBar, label: TextView, prefKey: String) {
        label.text = "${slider.progress}s"
        slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                label.text = "${progress}s"
                saveSliderValue(prefKey, progress)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
    }

    private fun saveSliderValue(key: String, value: Int) {
        getSharedPreferences("prefs", MODE_PRIVATE).edit {
            putInt(key, value)
        }
    }

    private fun saveGameMode(checkedId: Int) {
        getSharedPreferences("prefs", MODE_PRIVATE).edit {
            putInt("gameMode", checkedId)
        }
    }

    private fun saveHapticFeedback(isChecked: Boolean) {
        getSharedPreferences("prefs", MODE_PRIVATE).edit {
            putBoolean("hapticFeedback", isChecked)
        }
    }

    private fun loadSettings() {
        val prefs = getSharedPreferences("prefs", MODE_PRIVATE)
        playTimeMin.progress = prefs.getInt("playTimeMin", 10)
        playTimeMax.progress = prefs.getInt("playTimeMax", 40)
        pauseTimeMin.progress = prefs.getInt("pauseTimeMin", 3)
        pauseTimeMax.progress = prefs.getInt("pauseTimeMax", 8)
        gameModeSelector.check(prefs.getInt("gameMode", R.id.modeMusicalStatues))
        hapticFeedback.isChecked = prefs.getBoolean("hapticFeedback", false)
    }

    override fun onResume() {
        super.onResume()
        if (!isNotificationListenerEnabled()) {
            showNotificationListenerPermissionDialog()
        } else {
            initMediaController()
        }
    }

    override fun onPause() {
        super.onPause()
        mediaController?.unregisterCallback(mediaControllerCallback)
    }

    private fun isNotificationListenerEnabled(): Boolean {
        val enabledListeners = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        val componentName = ComponentName(this, MediaNotificationListenerService::class.java)
        return enabledListeners?.contains(componentName.flattenToString()) == true
    }

    private fun showNotificationListenerPermissionDialog() {
        AlertDialog.Builder(this)
            .setTitle("Permission Required")
            .setMessage("This app needs Notification Listener permission to control other music apps. Please grant the permission in the next screen.")
            .setPositiveButton("Open Settings") { _, _ ->
                startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
            }
            .setNegativeButton("Cancel") { dialog, _ ->
                dialog.dismiss()
                finish()
            }
            .create()
            .show()
    }

    private fun initMediaController() {
        val mediaSessionManager = getSystemService(Context.MEDIA_SESSION_SERVICE) as MediaSessionManager
        val componentName = ComponentName(this, MediaNotificationListenerService::class.java)
        val controllers = mediaSessionManager.getActiveSessions(componentName)

        if (controllers.isNotEmpty()) {
            mediaController = MediaControllerCompat(this, controllers[0].sessionToken)
            mediaController?.registerCallback(mediaControllerCallback)
            updateMediaInfo(mediaController?.metadata)
        } else {
            trackTitle.text = getString(R.string.no_music_playing)
            trackArtist.text = ""
        }
    }

    private fun updateMediaInfo(metadata: MediaMetadataCompat?) {
        if (metadata != null) {
            trackTitle.text = metadata.getString(MediaMetadataCompat.METADATA_KEY_TITLE)
            trackArtist.text = metadata.getString(MediaMetadataCompat.METADATA_KEY_ARTIST)
            albumArt.setImageBitmap(metadata.getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART))
        } else {
            trackTitle.text = getString(R.string.no_music_playing)
            trackArtist.text = ""
            albumArt.setImageResource(R.drawable.ic_play)
        }
    }

    private fun handlePrimaryAction() {
        if (isGameRunning) {
            if (isPausedForMusicalChairs) {
                isPausedForMusicalChairs = false
                playMusic()
                scheduleNextGameEvent()
            } else {
                stopGame()
            }
        } else {
            startGame()
        }
        updateButtonIcon()
    }

    private fun updateButtonIcon() {
        if (isGameRunning) {
            primaryActionButton.setImageResource(R.drawable.ic_stop)
        } else {
            primaryActionButton.setImageResource(R.drawable.ic_play)
        }
    }

    private fun startGame() {
        // Try to resume music if it's paused.
        val event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY)
        audioManager.dispatchMediaKeyEvent(event)

        // Add a delay to give the music player time to update its state.
        handler.postDelayed({
            if (!audioManager.isMusicActive) {
                Toast.makeText(this, getString(R.string.no_music_playing), Toast.LENGTH_SHORT).show()
                return@postDelayed
            }

            if (playTimeMin.progress > playTimeMax.progress) {
                Toast.makeText(this, "Min play time cannot be greater than max play time!", Toast.LENGTH_SHORT).show()
                return@postDelayed
            }

            if (modeMusicalStatues.isChecked && pauseTimeMin.progress > pauseTimeMax.progress) {
                Toast.makeText(this, "Min pause time cannot be greater than max pause time!", Toast.LENGTH_SHORT).show()
                return@postDelayed
            }

            isGameRunning = true
            updateButtonIcon()
            handler.post(gameLoop)
        }, 500) // 500ms delay
    }

    private fun stopGame() {
        isGameRunning = false
        isPausedForMusicalChairs = false
        handler.removeCallbacksAndMessages(null)
        updateMediaInfo(mediaController?.metadata)
        updateButtonIcon()
        timer?.cancel()
        timerView.setProgress(0f)
        pauseMusic()
    }

    private fun scheduleNextGameEvent() {
        if (!isGameRunning) return

        if (isMusicPlaying) {
            val playTime = (playTimeMin.progress..playTimeMax.progress).random() * 1000L
            handler.postDelayed({
                if (isGameRunning) {
                    pauseMusic()
                    if (modeMusicalChairs.isChecked) {
                        isPausedForMusicalChairs = true
                        timer?.cancel()
                        timerView.setProgress(0f)
                    } else {
                        scheduleNextGameEvent()
                    }
                }
            }, playTime)
            startTimer(playTime)
        } else {
            val pauseTime = (pauseTimeMin.progress..pauseTimeMax.progress).random() * 1000L
            handler.postDelayed({
                if (isGameRunning) {
                    playMusic()
                    scheduleNextGameEvent()
                }
            }, pauseTime)
            startTimer(pauseTime)
        }
    }

    private val gameLoop = object : Runnable {
        override fun run() {
            if (!isGameRunning) return

            playMusic()
            scheduleNextGameEvent()
        }
    }

    private fun initMediaSession() {
        mediaSession = MediaSessionCompat(this, "MusicalChairStatues").apply {
            setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS
            )
            setPlaybackState(PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE)
                .setState(PlaybackStateCompat.STATE_PLAYING, 0, 1.0f)
                .build())
            isActive = true
        }
    }

    private fun playMusic() {
        val event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY)
        audioManager.dispatchMediaKeyEvent(event)
        isMusicPlaying = true
    }

    private fun pauseMusic() {
        val event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE)
        audioManager.dispatchMediaKeyEvent(event)
        isMusicPlaying = false
        if (hapticFeedback.isChecked) {
            val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager
                vibratorManager.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(VIBRATOR_SERVICE) as Vibrator
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vibrator.vibrate(100)
            }
        }
    }

    private fun startTimer(duration: Long) {
        timer?.cancel()
        timer = object : CountDownTimer(duration, TIMER_INTERVAL_MS) {
            override fun onTick(millisUntilFinished: Long) {
                val progress = (duration - millisUntilFinished).toFloat() / duration
                timerView.setProgress(progress)
            }

            override fun onFinish() {
                timerView.setProgress(0f)
            }
        }.start()
    }

    private fun showOnboardingDialog() {
        AlertDialog.Builder(this)
            .setTitle("Welcome!")
            .setMessage("To use this app:\n\n1. Open your favorite music app (Spotify, etc.) and start playing a playlist.\n2. Come back here, choose your settings, and tap 'Start Game'.\n\nWe'll handle the pausing and playing for you!")
            .setPositiveButton("Got it!") { dialog, _ ->
                dialog.dismiss()
                getSharedPreferences("prefs", MODE_PRIVATE).edit {
                    putBoolean("firstrun", false)
                }
            }
            .create()
            .show()
    }
}
