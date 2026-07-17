package com.match3.game

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val gameView = findViewById<GameView>(R.id.gameView)
        val scoreValue = findViewById<TextView>(R.id.scoreValue)
        val movesValue = findViewById<TextView>(R.id.movesValue)
        val overlay = findViewById<View>(R.id.gameOverOverlay)
        val finalScoreValue = findViewById<TextView>(R.id.finalScoreValue)
        val playAgain = findViewById<Button>(R.id.playAgainButton)

        gameView.onScoreChanged = { score -> scoreValue.text = score.toString() }
        gameView.onMovesChanged = { moves -> movesValue.text = moves.toString() }
        gameView.onGameOver = { score ->
            finalScoreValue.text = score.toString()
            overlay.visibility = View.VISIBLE
        }

        playAgain.setOnClickListener {
            overlay.visibility = View.GONE
            gameView.restart()
        }

        // Initialize HUD with starting values.
        scoreValue.text = "0"
        movesValue.text = GameEngine.MOVE_LIMIT.toString()
    }
}
