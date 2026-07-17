package com.match3.game

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class GameView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    var onScoreChanged: ((Int) -> Unit)? = null
    var onMovesChanged: ((Int) -> Unit)? = null
    var onGameOver: ((Int) -> Unit)? = null

    private val engine = GameEngine()

    private var gemSize = 0f
    private var boardLeft = 0f
    private var boardTop = 0f
    private val gridSize get() = engine.size

    // Gem colors
    private val gemColors = intArrayOf(
        Color.parseColor("#FF5A5F"), // 0 red     - circle
        Color.parseColor("#FFC93C"), // 1 yellow  - rounded square
        Color.parseColor("#4CD964"), // 2 green   - diamond
        Color.parseColor("#4A90E2"), // 3 blue    - triangle
        Color.parseColor("#B14AE2"), // 4 purple  - hexagon
        Color.parseColor("#FF7AC6")  // 5 pink    - star
    )

    private val gemPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#22000000")
    }
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#55FFFFFF")
    }
    private val selectPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        color = Color.parseColor("#FFFFFFFF")
    }
    private val cellBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#22000000")
    }
    private val specialGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFFFFFFF")
    }
    private val specialStarPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
    }

    private enum class State { IDLE, SWAP, INVALID_SWAP, CLEAR, FALL }

    private var state = State.IDLE
    private var animStartMs = 0L
    private var animDurationMs = 0L

    private var swapA: Pos? = null
    private var swapB: Pos? = null
    private var cascadeLevel = 1

    private var clearCells: List<Pair<Pos, Cell>> = emptyList()
    private var newSpecials: Set<Pos> = emptySet()

    private var fallMoves: List<FallMove> = emptyList()

    private var selected: Pos? = null
    private var downPos: Pos? = null
    private var downX = 0f
    private var downY = 0f

    init {
        engine.newGame()
    }

    fun restart() {
        engine.newGame()
        state = State.IDLE
        selected = null
        swapA = null
        swapB = null
        cascadeLevel = 1
        clearCells = emptyList()
        fallMoves = emptyList()
        onScoreChanged?.invoke(engine.score)
        onMovesChanged?.invoke(engine.movesLeft)
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        val boardPixels = min(w, h).toFloat()
        gemSize = boardPixels / gridSize
        boardLeft = (w - gemSize * gridSize) / 2f
        boardTop = (h - gemSize * gridSize) / 2f
        borderPaint.strokeWidth = gemSize * 0.04f
        selectPaint.strokeWidth = gemSize * 0.09f
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (state != State.IDLE || engine.isGameOver) return true

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                downPos = cellAt(event.x, event.y)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val start = downPos ?: return true
                val dx = event.x - downX
                val dy = event.y - downY
                val threshold = gemSize * 0.4f
                if (abs(dx) > threshold || abs(dy) > threshold) {
                    val target = if (abs(dx) > abs(dy)) {
                        Pos(start.row, start.col + if (dx > 0) 1 else -1)
                    } else {
                        Pos(start.row + if (dy > 0) 1 else -1, start.col)
                    }
                    downPos = null
                    selected = null
                    attemptSwap(start, target)
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                val tapped = downPos ?: cellAt(event.x, event.y)
                downPos = null
                if (tapped != null && inGrid(tapped)) {
                    val prev = selected
                    when {
                        prev == null -> {
                            selected = tapped
                            invalidate()
                        }
                        prev == tapped -> {
                            selected = null
                            invalidate()
                        }
                        engine.isAdjacent(prev, tapped) -> {
                            selected = null
                            attemptSwap(prev, tapped)
                        }
                        else -> {
                            selected = tapped
                            invalidate()
                        }
                    }
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun attemptSwap(a: Pos, b: Pos) {
        if (!inGrid(a) || !inGrid(b) || !engine.isAdjacent(a, b)) return
        swapA = a
        swapB = b
        val valid = engine.trySwap(a, b)
        if (valid) {
            onMovesChanged?.invoke(engine.movesLeft)
            cascadeLevel = 1
            startAnimation(State.SWAP, 150)
        } else {
            startAnimation(State.INVALID_SWAP, 260)
        }
    }

    private fun cellAt(x: Float, y: Float): Pos? {
        if (gemSize <= 0f) return null
        val col = ((x - boardLeft) / gemSize).toInt()
        val row = ((y - boardTop) / gemSize).toInt()
        val p = Pos(row, col)
        return if (inGrid(p)) p else null
    }

    private fun inGrid(p: Pos) = p.row in 0 until gridSize && p.col in 0 until gridSize

    private fun startAnimation(next: State, durationMs: Long) {
        state = next
        animDurationMs = durationMs
        animStartMs = System.currentTimeMillis()
        postInvalidateOnAnimation()
    }

    private fun progress(): Float {
        if (animDurationMs <= 0) return 1f
        val t = (System.currentTimeMillis() - animStartMs).toFloat() / animDurationMs
        return t.coerceIn(0f, 1f)
    }

    private fun advanceState() {
        when (state) {
            State.SWAP -> beginClearPhase()
            State.INVALID_SWAP -> {
                swapA = null
                swapB = null
                state = State.IDLE
            }
            State.CLEAR -> beginFallPhase()
            State.FALL -> {
                // Cascade: check for more matches created by the fall.
                cascadeLevel++
                beginClearPhase()
            }
            State.IDLE -> {}
        }
    }

    private fun beginClearPhase() {
        val matches = engine.findResolvableMatches()
        if (matches.isEmpty()) {
            settle()
            return
        }
        val swapped = if (cascadeLevel == 1) listOfNotNull(swapA, swapB) else emptyList()
        val before = HashMap<Pos, Cell>()
        for (r in 0 until gridSize) {
            for (c in 0 until gridSize) {
                engine.board[r][c]?.let { before[Pos(r, c)] = Cell(it.color, it.special) }
            }
        }
        val result = engine.resolveMatches(cascadeLevel, swapped)
        onScoreChanged?.invoke(engine.score)

        clearCells = result.clearedCells.mapNotNull { p -> before[p]?.let { p to it } }
        newSpecials = result.createdSpecials
        swapA = null
        swapB = null
        startAnimation(State.CLEAR, 200)
    }

    private fun beginFallPhase() {
        fallMoves = engine.applyGravity()
        newSpecials = emptySet()
        startAnimation(State.FALL, 260)
    }

    private fun settle() {
        state = State.IDLE
        clearCells = emptyList()
        fallMoves = emptyList()
        if (engine.isGameOver) {
            onGameOver?.invoke(engine.score)
        } else if (!engine.hasPossibleMove()) {
            engine.reshuffle()
            invalidate()
        } else {
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        // Cell background grid.
        for (r in 0 until gridSize) {
            for (c in 0 until gridSize) {
                val left = boardLeft + c * gemSize
                val top = boardTop + r * gemSize
                canvas.drawRoundRect(
                    left + gemSize * 0.03f, top + gemSize * 0.03f,
                    left + gemSize * 0.97f, top + gemSize * 0.97f,
                    gemSize * 0.18f, gemSize * 0.18f, cellBgPaint
                )
            }
        }

        when (state) {
            State.IDLE -> drawStaticBoard(canvas)
            State.SWAP -> drawSwap(canvas, invalid = false)
            State.INVALID_SWAP -> drawSwap(canvas, invalid = true)
            State.CLEAR -> drawClear(canvas)
            State.FALL -> drawFall(canvas)
        }

        // Advance the state machine after drawing the frame.
        if (state != State.IDLE) {
            if (progress() >= 1f) {
                advanceState()
                if (state != State.IDLE) postInvalidateOnAnimation()
                else invalidate()
            } else {
                postInvalidateOnAnimation()
            }
        }
    }

    private fun drawStaticBoard(canvas: Canvas) {
        for (r in 0 until gridSize) {
            for (c in 0 until gridSize) {
                val cell = engine.board[r][c] ?: continue
                drawGem(canvas, centerX(c), centerY(r), gemSize, cell.color, cell.special, 1f)
            }
        }
        // Highlight the tap-selected gem.
        selected?.let { sel ->
            val left = boardLeft + sel.col * gemSize
            val top = boardTop + sel.row * gemSize
            canvas.drawRoundRect(
                left + gemSize * 0.06f, top + gemSize * 0.06f,
                left + gemSize * 0.94f, top + gemSize * 0.94f,
                gemSize * 0.2f, gemSize * 0.2f, selectPaint
            )
        }
    }

    private fun drawSwap(canvas: Canvas, invalid: Boolean) {
        val a = swapA
        val b = swapB
        val t = progress()
        // For an invalid swap, go out and back with a triangle wave.
        val f = if (invalid) (1f - abs(1f - 2f * t)) else t

        for (r in 0 until gridSize) {
            for (c in 0 until gridSize) {
                val cell = engine.board[r][c] ?: continue
                val here = Pos(r, c)
                if (here == a || here == b) continue
                drawGem(canvas, centerX(c), centerY(r), gemSize, cell.color, cell.special, 1f)
            }
        }
        if (a != null && b != null) {
            // During INVALID_SWAP the engine did not commit, so board still has originals at a/b.
            // During SWAP the engine already swapped, so the gem now at 'a' visually travels from b.
            val cellA = engine.board[a.row][a.col]
            val cellB = engine.board[b.row][b.col]
            if (cellA != null) {
                val x = lerp(centerX(b.col), centerX(a.col), f)
                val y = lerp(centerY(b.row), centerY(a.row), f)
                drawGem(canvas, x, y, gemSize, cellA.color, cellA.special, 1f)
            }
            if (cellB != null) {
                val x = lerp(centerX(a.col), centerX(b.col), f)
                val y = lerp(centerY(a.row), centerY(b.row), f)
                drawGem(canvas, x, y, gemSize, cellB.color, cellB.special, 1f)
            }
        }
    }

    private fun drawClear(canvas: Canvas) {
        val t = progress()
        val clearing = clearCells.associate { it.first to it.second }
        // Draw the settled board (cells already nulled won't draw).
        for (r in 0 until gridSize) {
            for (c in 0 until gridSize) {
                val cell = engine.board[r][c] ?: continue
                val here = Pos(r, c)
                val scale = if (here in newSpecials) 1f + 0.25f * sin(t * Math.PI).toFloat() else 1f
                drawGem(canvas, centerX(c), centerY(r), gemSize, cell.color, cell.special, scale)
            }
        }
        // Draw the disappearing gems shrinking & fading.
        for ((p, cell) in clearing) {
            val scale = (1f - t)
            if (scale <= 0.02f) continue
            drawGem(canvas, centerX(p.col), centerY(p.row), gemSize, cell.color, cell.special, scale)
        }
    }

    private fun drawFall(canvas: Canvas) {
        val t = easeOut(progress())
        for (m in fallMoves) {
            val cell = engine.board[m.finalRow][m.col] ?: continue
            val fromY = centerYForRow(m.startRow)
            val toY = centerY(m.finalRow)
            val y = lerp(fromY, toY, t)
            drawGem(canvas, centerX(m.col), y, gemSize, cell.color, cell.special, 1f)
        }
    }

    private val tmpPath = Path()

    private fun drawGem(canvas: Canvas, cx: Float, cy: Float, size: Float, color: Int, special: Boolean, scale: Float) {
        val r = (size * 0.38f) * scale
        if (r <= 0f) return

        if (special) {
            specialGlowPaint.alpha = 90
            canvas.drawCircle(cx, cy, r * 1.25f, specialGlowPaint)
        }

        gemPaint.color = gemColors[color]
        when (color) {
            0 -> canvas.drawCircle(cx, cy, r, gemPaint)                       // circle
            1 -> {                                                             // rounded square
                val rect = RectF(cx - r, cy - r, cx + r, cy + r)
                canvas.drawRoundRect(rect, r * 0.35f, r * 0.35f, gemPaint)
            }
            2 -> drawPolygon(canvas, cx, cy, r, 4, 0.0, gemPaint)              // diamond
            3 -> drawPolygon(canvas, cx, cy, r, 3, -Math.PI / 2, gemPaint)     // triangle
            4 -> drawPolygon(canvas, cx, cy, r, 6, 0.0, gemPaint)             // hexagon
            5 -> drawStar(canvas, cx, cy, r, gemPaint)                         // star
        }

        // Subtle border and top highlight for depth.
        borderPaint.color = darken(gemColors[color])
        when (color) {
            0 -> canvas.drawCircle(cx, cy, r, borderPaint)
            1 -> {
                val rect = RectF(cx - r, cy - r, cx + r, cy + r)
                canvas.drawRoundRect(rect, r * 0.35f, r * 0.35f, borderPaint)
            }
            2 -> drawPolygon(canvas, cx, cy, r, 4, 0.0, borderPaint)
            3 -> drawPolygon(canvas, cx, cy, r, 3, -Math.PI / 2, borderPaint)
            4 -> drawPolygon(canvas, cx, cy, r, 6, 0.0, borderPaint)
            5 -> drawStar(canvas, cx, cy, r, borderPaint)
        }
        canvas.drawCircle(cx - r * 0.3f, cy - r * 0.35f, r * 0.22f, highlightPaint)

        if (special) {
            // White 4-point sparkle marks a Special Gem.
            drawFourPointStar(canvas, cx, cy, r * 0.55f, specialStarPaint)
        }
    }

    private fun drawPolygon(canvas: Canvas, cx: Float, cy: Float, radius: Float, sides: Int, startAngle: Double, paint: Paint) {
        tmpPath.reset()
        for (i in 0 until sides) {
            val angle = startAngle + 2.0 * Math.PI * i / sides
            val x = cx + radius * cos(angle).toFloat()
            val y = cy + radius * sin(angle).toFloat()
            if (i == 0) tmpPath.moveTo(x, y) else tmpPath.lineTo(x, y)
        }
        tmpPath.close()
        canvas.drawPath(tmpPath, paint)
    }

    private fun drawStar(canvas: Canvas, cx: Float, cy: Float, radius: Float, paint: Paint) {
        tmpPath.reset()
        val points = 5
        val inner = radius * 0.45f
        for (i in 0 until points * 2) {
            val rr = if (i % 2 == 0) radius else inner
            val angle = -Math.PI / 2 + Math.PI * i / points
            val x = cx + rr * cos(angle).toFloat()
            val y = cy + rr * sin(angle).toFloat()
            if (i == 0) tmpPath.moveTo(x, y) else tmpPath.lineTo(x, y)
        }
        tmpPath.close()
        canvas.drawPath(tmpPath, paint)
    }

    private fun drawFourPointStar(canvas: Canvas, cx: Float, cy: Float, radius: Float, paint: Paint) {
        tmpPath.reset()
        val inner = radius * 0.35f
        for (i in 0 until 8) {
            val rr = if (i % 2 == 0) radius else inner
            val angle = -Math.PI / 2 + Math.PI * i / 4
            val x = cx + rr * cos(angle).toFloat()
            val y = cy + rr * sin(angle).toFloat()
            if (i == 0) tmpPath.moveTo(x, y) else tmpPath.lineTo(x, y)
        }
        tmpPath.close()
        canvas.drawPath(tmpPath, paint)
    }

    private fun centerX(col: Int) = boardLeft + col * gemSize + gemSize / 2f
    private fun centerY(row: Int) = boardTop + row * gemSize + gemSize / 2f
    private fun centerYForRow(row: Float) = boardTop + row * gemSize + gemSize / 2f

    private fun lerp(from: Float, to: Float, t: Float) = from + (to - from) * t
    private fun easeOut(t: Float) = 1f - (1f - t) * (1f - t)

    private fun darken(color: Int): Int {
        val factor = 0.7f
        val r = (Color.red(color) * factor).toInt()
        val g = (Color.green(color) * factor).toInt()
        val b = (Color.blue(color) * factor).toInt()
        return Color.argb(120, r, g, b)
    }
}
