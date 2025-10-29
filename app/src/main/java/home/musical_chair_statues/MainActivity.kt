package home.musical_chair_statues

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.view.KeyEvent
import android.view.View
import android.widget.Button
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

    private lateinit var statusIndicator: TextView
    private lateinit var gameModeSelector: RadioGroup
    private lateinit var modeMusicalStatues: RadioButton
    private lateinit var modeMusicalChairs: RadioButton
    private lateinit var playTimeMin: SeekBar
    private lateinit var playTimeMax: SeekBar
    private lateinit var pauseTimeMin: SeekBar
    private lateinit var pauseTimeMax: SeekBar
    private lateinit var pauseTimeLabel: TextView
    private lateinit var hapticFeedback: SwitchCompat
    private lateinit var primaryActionButton: Button

    private lateinit var audioManager: AudioManager
    private var mediaSession: MediaSessionCompat? = null

    private var isGameRunning = false
    private var isMusicPlaying = false
    private var isPausedForMusicalChairs = false
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusIndicator = findViewById(R.id.statusIndicator)
        gameModeSelector = findViewById(R.id.gameModeSelector)
        modeMusicalStatues = findViewById(R.id.modeMusicalStatues)
        modeMusicalChairs = findViewById(R.id.modeMusicalChairs)
        playTimeMin = findViewById(R.id.playTimeMin)
        playTimeMax = findViewById(R.id.playTimeMax)
        pauseTimeMin = findViewById(R.id.pauseTimeMin)
        pauseTimeMax = findViewById(R.id.pauseTimeMax)
        pauseTimeLabel = findViewById(R.id.pauseTimeLabel)
        hapticFeedback = findViewById(R.id.hapticFeedback)
        primaryActionButton = findViewById(R.id.primaryActionButton)

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        initMediaSession()

        gameModeSelector.setOnCheckedChangeListener { _, checkedId ->
            if (checkedId == R.id.modeMusicalChairs) {
                pauseTimeMin.visibility = View.GONE
                pauseTimeMax.visibility = View.GONE
                pauseTimeLabel.visibility = View.GONE
            } else {
                pauseTimeMin.visibility = View.VISIBLE
                pauseTimeMax.visibility = View.VISIBLE
                pauseTimeLabel.visibility = View.VISIBLE
            }
        }

        primaryActionButton.setOnClickListener {
            handlePrimaryAction()
        }

        val prefs = getSharedPreferences("prefs", MODE_PRIVATE)
        if (prefs.getBoolean("firstrun", true)) {
            showOnboardingDialog()
        }
    }

    private fun initMediaSession() {
        mediaSession = MediaSessionCompat(this, "MusicalChairStatues").apply {
            setFlags(MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS or MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS)
            setPlaybackState(PlaybackStateCompat.Builder()
                .setState(PlaybackStateCompat.STATE_PLAYING, 0, 1.0f)
                .build())
            isActive = true
        }
    }

    private fun handlePrimaryAction() {
        if (isGameRunning) {
            if (isPausedForMusicalChairs) {
                isPausedForMusicalChairs = false
                playMusic()
                primaryActionButton.text = getString(R.string.stop_game)
                scheduleNextGameEvent()
            } else {
                stopGame()
            }
        } else {
            startGame()
        }
    }

    private fun startGame() {
        if (!audioManager.isMusicActive) {
            Toast.makeText(this, "Please start playing music in another app first!", Toast.LENGTH_SHORT).show()
            return
        }

        if (playTimeMin.progress > playTimeMax.progress) {
            Toast.makeText(this, "Min play time cannot be greater than max play time!", Toast.LENGTH_SHORT).show()
            return
        }

        if (modeMusicalStatues.isChecked && pauseTimeMin.progress > pauseTimeMax.progress) {
            Toast.makeText(this, "Min pause time cannot be greater than max pause time!", Toast.LENGTH_SHORT).show()
            return
        }

        isGameRunning = true
        primaryActionButton.text = getString(R.string.stop_game)
        handler.post(gameLoop)
    }

    private fun stopGame() {
        isGameRunning = false
        isPausedForMusicalChairs = false
        handler.removeCallbacksAndMessages(null)
        statusIndicator.text = getString(R.string.waiting_to_start)
        primaryActionButton.text = getString(R.string.start_game)
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
                        primaryActionButton.text = getString(R.string.start_next_round)
                    } else {
                        scheduleNextGameEvent()
                    }
                }
            }, playTime)
        } else {
            val pauseTime = (pauseTimeMin.progress..pauseTimeMax.progress).random() * 1000L
            handler.postDelayed({
                if (isGameRunning) {
                    playMusic()
                    scheduleNextGameEvent()
                }
            }, pauseTime)
        }
    }

    private val gameLoop = object : Runnable {
        override fun run() {
            if (!isGameRunning) return

            playMusic()
            scheduleNextGameEvent()
        }
    }

    private fun playMusic() {
        val event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY)
        audioManager.dispatchMediaKeyEvent(event)
        statusIndicator.text = getString(R.string.music_is_playing)
        isMusicPlaying = true
    }

    private fun pauseMusic() {
        val event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PAUSE)
        audioManager.dispatchMediaKeyEvent(event)
        statusIndicator.text = getString(R.string.music_paused)
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
