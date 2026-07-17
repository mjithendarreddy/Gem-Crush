package com.match3.game

import kotlin.random.Random

class Cell(var color: Int, var special: Boolean = false)

data class Pos(val row: Int, val col: Int)

data class FallMove(val finalRow: Int, val col: Int, val startRow: Float)

data class ResolveResult(
    val clearedCells: Set<Pos>,
    val createdSpecials: Set<Pos>,
    val pointsAwarded: Int
)

class GameEngine(
    val size: Int = 8,
    private val random: Random = Random.Default
) {
    companion object {
        const val COLORS = 6
        const val MOVE_LIMIT = 20
        const val POINTS_PER_GEM = 10
    }

    // board[row][col]; may hold nulls transiently while cells are being cleared.
    val board: Array<Array<Cell?>> = Array(size) { arrayOfNulls<Cell>(size) }

    var score: Int = 0
        private set
    var movesLeft: Int = MOVE_LIMIT
        private set

    val isGameOver: Boolean
        get() = movesLeft <= 0

    fun newGame() {
        score = 0
        movesLeft = MOVE_LIMIT
        for (r in 0 until size) {
            for (c in 0 until size) {
                board[r][c] = Cell(randomColor())
            }
        }
        // Re-roll any cell that forms an initial match so the player starts on a stable board.
        while (findMatchedCells().isNotEmpty()) {
            for (p in findMatchedCells()) {
                board[p.row][p.col] = Cell(randomColor())
            }
        }
    }

    private fun randomColor() = random.nextInt(COLORS)

    fun cell(r: Int, c: Int): Cell? = if (inBounds(r, c)) board[r][c] else null

    private fun inBounds(r: Int, c: Int) = r in 0 until size && c in 0 until size

    fun isAdjacent(a: Pos, b: Pos): Boolean {
        val dr = kotlin.math.abs(a.row - b.row)
        val dc = kotlin.math.abs(a.col - b.col)
        return (dr + dc) == 1
    }

    private fun swapCells(a: Pos, b: Pos) {
        val tmp = board[a.row][a.col]
        board[a.row][a.col] = board[b.row][b.col]
        board[b.row][b.col] = tmp
    }

    fun trySwap(a: Pos, b: Pos): Boolean {
        if (isGameOver) return false
        if (!inBounds(a.row, a.col) || !inBounds(b.row, b.col)) return false
        if (!isAdjacent(a, b)) return false

        swapCells(a, b)
        if (findMatchedCells().isNotEmpty()) {
            movesLeft--
            return true
        }
        // No match: revert.
        swapCells(a, b)
        return false
    }

    /** All cells that are part of any horizontal or vertical run of length >= 3. */
    fun findMatchedCells(): Set<Pos> {
        val matched = HashSet<Pos>()
        // Horizontal runs
        for (r in 0 until size) {
            var runStart = 0
            while (runStart < size) {
                val color = board[r][runStart]?.color
                var runEnd = runStart
                while (color != null && runEnd + 1 < size && board[r][runEnd + 1]?.color == color) {
                    runEnd++
                }
                if (color != null && runEnd - runStart + 1 >= 3) {
                    for (c in runStart..runEnd) matched.add(Pos(r, c))
                }
                runStart = runEnd + 1
            }
        }
        // Vertical runs
        for (c in 0 until size) {
            var runStart = 0
            while (runStart < size) {
                val color = board[runStart][c]?.color
                var runEnd = runStart
                while (color != null && runEnd + 1 < size && board[runEnd + 1][c]?.color == color) {
                    runEnd++
                }
                if (color != null && runEnd - runStart + 1 >= 3) {
                    for (r in runStart..runEnd) matched.add(Pos(r, c))
                }
                runStart = runEnd + 1
            }
        }
        return matched
    }

    fun findResolvableMatches(): Set<Pos> = findMatchedCells()

    fun resolveMatches(cascadeLevel: Int, swapped: List<Pos> = emptyList()): ResolveResult {
        val runs = findAllRuns()
        val matched = HashSet<Pos>()
        for (run in runs) matched.addAll(run)

        // Decide which cells become Special Gems (runs of exactly 4).
        val createdSpecials = HashSet<Pos>()
        for (run in runs) {
            if (run.size == 4) {
                val keep = run.firstOrNull { it in swapped } ?: run[run.size / 2]
                createdSpecials.add(keep)
            }
        }

        // Cells to clear = matched minus the ones we keep as new specials.
        val toClear = HashSet<Pos>()
        for (p in matched) if (p !in createdSpecials) toClear.add(p)

        // Expand for any EXISTING special gems caught in the clear (row + column blast, chaining).
        val queue = ArrayDeque(toClear)
        while (queue.isNotEmpty()) {
            val p = queue.removeFirst()
            val existing = board[p.row][p.col]
            if (existing != null && existing.special && p !in createdSpecials) {
                for (c in 0 until size) {
                    val q = Pos(p.row, c)
                    if (q !in createdSpecials && toClear.add(q)) queue.add(q)
                }
                for (r in 0 until size) {
                    val q = Pos(r, p.col)
                    if (q !in createdSpecials && toClear.add(q)) queue.add(q)
                }
            }
        }

        // Award score and clear.
        val points = toClear.size * POINTS_PER_GEM * cascadeLevel
        score += points
        for (p in toClear) board[p.row][p.col] = null

        // Promote kept cells to specials.
        for (p in createdSpecials) board[p.row][p.col]?.special = true

        return ResolveResult(toClear, createdSpecials, points)
    }

    /** Returns every horizontal/vertical run (length >= 3) as an ordered list of positions. */
    private fun findAllRuns(): List<List<Pos>> {
        val runs = ArrayList<List<Pos>>()
        for (r in 0 until size) {
            var start = 0
            while (start < size) {
                val color = board[r][start]?.color
                var end = start
                while (color != null && end + 1 < size && board[r][end + 1]?.color == color) end++
                if (color != null && end - start + 1 >= 3) {
                    runs.add((start..end).map { Pos(r, it) })
                }
                start = end + 1
            }
        }
        for (c in 0 until size) {
            var start = 0
            while (start < size) {
                val color = board[start][c]?.color
                var end = start
                while (color != null && end + 1 < size && board[end + 1][c]?.color == color) end++
                if (color != null && end - start + 1 >= 3) {
                    runs.add((start..end).map { Pos(it, c) })
                }
                start = end + 1
            }
        }
        return runs
    }

    fun applyGravity(): List<FallMove> {
        val moves = ArrayList<FallMove>()
        for (c in 0 until size) {
            // Survivors, top -> bottom.
            val survivors = ArrayList<Pair<Int, Cell>>() // originalRow to cell
            for (r in 0 until size) {
                val cell = board[r][c]
                if (cell != null) survivors.add(r to cell)
            }
            val spawnCount = size - survivors.size

            // Clear column.
            for (r in 0 until size) board[r][c] = null

            // New gems occupy the top rows; they animate in from above the board.
            for (i in 0 until spawnCount) {
                val cell = Cell(randomColor())
                board[i][c] = cell
                moves.add(FallMove(finalRow = i, col = c, startRow = (i - spawnCount).toFloat()))
            }
            // Survivors keep their relative order below the new gems.
            for ((idx, pair) in survivors.withIndex()) {
                val finalRow = spawnCount + idx
                board[finalRow][c] = pair.second
                moves.add(FallMove(finalRow = finalRow, col = c, startRow = pair.first.toFloat()))
            }
        }
        return moves
    }

    fun hasPossibleMove(): Boolean {
        for (r in 0 until size) {
            for (c in 0 until size) {
                // Try swapping right and down; that covers all adjacent pairs.
                if (c + 1 < size && swapCreatesMatch(Pos(r, c), Pos(r, c + 1))) return true
                if (r + 1 < size && swapCreatesMatch(Pos(r, c), Pos(r + 1, c))) return true
            }
        }
        return false
    }

    private fun swapCreatesMatch(a: Pos, b: Pos): Boolean {
        swapCells(a, b)
        val hit = findMatchedCells().isNotEmpty()
        swapCells(a, b)
        return hit
    }

    fun reshuffle() {
        do {
            for (r in 0 until size) {
                for (c in 0 until size) {
                    board[r][c] = Cell(randomColor())
                }
            }
        } while (findMatchedCells().isNotEmpty() || !hasPossibleMove())
    }
}
