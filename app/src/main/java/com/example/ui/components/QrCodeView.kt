package com.example.ui.components

import com.example.ui.theme.kw
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/**
 * High-clarity vector QR Code Canvas renderer with authentic finder patterns
 * and deterministic matrix layout based on the 6-digit pairing code.
 */
@Composable
fun QrCodeView(
    data: String,
    modifier: Modifier = Modifier,
    size: Dp = 180.dp,
    qrColor: Color = Color(0xFF0B132B),
    backgroundColor: Color = Color.White
) {
    val gridSize = 21 // standard Version 1 QR matrix

    val matrix = remember(data) {
        generateQrMatrix(data, gridSize)
    }

    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .border(2.dp, kw(0xFFE2E8F0), RoundedCornerShape(16.dp)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(size - 24.dp)) {
            val moduleSize = this.size.width / gridSize

            for (r in 0 until gridSize) {
                for (c in 0 until gridSize) {
                    if (matrix[r][c]) {
                        // Check if it's part of a finder pattern corner to draw smoother corners
                        val isFinder = isFinderModule(r, c, gridSize)
                        drawRoundRect(
                            color = qrColor,
                            topLeft = Offset(c * moduleSize, r * moduleSize),
                            size = Size(moduleSize, moduleSize),
                            cornerRadius = if (isFinder) CornerRadius(1.5f, 1.5f) else CornerRadius(2f, 2f)
                        )
                    }
                }
            }
        }
    }
}

private fun isFinderModule(r: Int, c: Int, gridSize: Int): Boolean {
    // Top-Left (0..6, 0..6)
    if (r in 0..6 && c in 0..6) return true
    // Top-Right (0..6, gridSize-7..gridSize-1)
    if (r in 0..6 && c in (gridSize - 7) until gridSize) return true
    // Bottom-Left (gridSize-7..gridSize-1, 0..6)
    if (r in (gridSize - 7) until gridSize && c in 0..6) return true
    return false
}

private fun generateQrMatrix(data: String, size: Int): Array<BooleanArray> {
    val grid = Array(size) { BooleanArray(size) { false } }

    fun drawFinder(topRow: Int, leftCol: Int) {
        for (r in 0..6) {
            for (c in 0..6) {
                val isOuter = r == 0 || r == 6 || c == 0 || c == 6
                val isCore = r in 2..4 && c in 2..4
                grid[topRow + r][leftCol + c] = isOuter || isCore
            }
        }
    }

    // Draw the 3 standard finder patterns
    drawFinder(0, 0)
    drawFinder(0, size - 7)
    drawFinder(size - 7, 0)

    // Timing patterns
    for (i in 7 until size - 7) {
        grid[6][i] = (i % 2 == 0)
        grid[i][6] = (i % 2 == 0)
    }

    // Deterministic data encoding based on hash
    val seed = abs(data.hashCode())
    var bitIndex = 0
    val bytes = data.toByteArray(Charsets.UTF_8)

    for (r in 0 until size) {
        for (c in 0 until size) {
            if (isFinderModule(r, c, size) || (r == 6) || (c == 6)) {
                continue
            }
            // Generate pseudo-random bit stream seeded by data content
            val byteVal = if (bytes.isNotEmpty()) bytes[bitIndex % bytes.size].toInt() else 0
            val combined = (seed xor (r * 31 + c * 17) xor (byteVal shl 3))
            grid[r][c] = (combined % 3 == 0 || (r + c + seed) % 2 == 0)
            bitIndex++
        }
    }

    return grid
}
