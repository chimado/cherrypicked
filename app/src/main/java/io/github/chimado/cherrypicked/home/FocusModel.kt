package io.github.chimado.cherrypicked.home

/** Generic over N rows; key codes match android.view.KeyEvent so JVM tests need no Android. */
class FocusModel(private val rowSizes: List<Int>, private val centersX: List<List<Float>>) {
    var row = 0
        private set
    var col = 0
        private set

    fun move(keyCode: Int): Boolean {
        val (r, c) = when (keyCode) {
            UP -> if (row > 0) row - 1 to nearest(row - 1) else return false
            DOWN -> if (row < rowSizes.size - 1) row + 1 to nearest(row + 1) else return false
            LEFT -> if (col > 0) row to col - 1 else return false
            RIGHT -> if (col < rowSizes[row] - 1) row to col + 1 else return false
            else -> return false
        }
        row = r; col = c
        return true
    }

    fun reset() { row = 0; col = 0 }

    private fun nearest(target: Int): Int {
        val x = centersX[row][col]
        return centersX[target].indices.minByOrNull { Math.abs(centersX[target][it] - x) } ?: 0
    }

    companion object {
        const val UP = 19
        const val DOWN = 20
        const val LEFT = 21
        const val RIGHT = 22
    }
}
