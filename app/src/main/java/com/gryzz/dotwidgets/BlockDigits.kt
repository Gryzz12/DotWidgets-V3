package com.gryzz.dotwidgets

import android.graphics.Canvas
import android.graphics.CornerPathEffect
import android.graphics.Paint
import android.graphics.Path

/**
 * Cifras de bloques gruesos con esquinas suavizadas (estilo reloj digital pixelado).
 * Rejilla de 7×12 celdas por cifra; el trazo ocupa 2 celdas y los trazos verticales
 * se cortan en el centro, como un display de segmentos pixelado.
 */
object BlockDigits {
    private const val COLS = 7
    private const val ROWS = 12
    private const val GAP = 1.6f      // celdas entre cifras
    private const val MID_GAP = 0f    // separación extra entre horas y minutos
    private const val ASPECT = 0.94f  // alto de celda / ancho de celda

    private val g: Map<Char, Array<String>> = mapOf(
        '0' to arrayOf("..###..", "#######", "##...##", "##...##", "##...##", ".......", ".......", "##...##", "##...##", "##...##", "#######", "..###.."),
        '1' to arrayOf("...##..", "...##..", "...##..", "...##..", "...##..", ".......", ".......", "...##..", "...##..", "...##..", "...##..", "...##.."),
        '2' to arrayOf("#####..", "#######", ".....##", ".....##", ".....##", "..####.", ".####..", "##.....", "##.....", "##.....", "#######", "..#####"),
        '3' to arrayOf("#####..", "#######", ".....##", ".....##", ".....##", "..####.", "..####.", ".....##", ".....##", ".....##", "#######", "#####.."),
        '4' to arrayOf("##...##", "##...##", "##...##", "##...##", "##...##", ".#####.", "..####.", ".....##", ".....##", ".....##", ".....##", ".....##"),
        '5' to arrayOf("#######", "#######", "##.....", "##.....", "##.....", ".####..", "..####.", ".....##", ".....##", ".....##", "#######", "#####.."),
        '6' to arrayOf("..####.", "######.", "##.....", "##.....", "##.....", ".####..", ".#####.", "##...##", "##...##", "##...##", "#######", "..###.."),
        '7' to arrayOf("#####..", "#######", ".....##", ".....##", ".....##", ".......", ".......", "....##.", "....##.", "....##.", "....##.", "....##."),
        '8' to arrayOf("..###..", "#######", "##...##", "##...##", "##...##", ".#####.", ".#####.", "##...##", "##...##", "##...##", "#######", "..###.."),
        '9' to arrayOf("..###..", "#######", "##...##", "##...##", "##...##", ".#####.", "..#####", ".....##", ".....##", ".....##", ".######", ".####.."),
    )

    /** Ancho total en celdas para "HHMM". */
    private const val TOTAL_COLS = 4 * COLS + 3 * GAP + MID_GAP

    /** Tamaño de celda que hace caber "HHMM" en w × h. */
    fun cellFor(w: Float, h: Float): Float = minOf(w / TOTAL_COLS, h / (ROWS * ASPECT))

    fun width(cell: Float): Float = TOTAL_COLS * cell
    fun height(cell: Float): Float = ROWS * ASPECT * cell

    /** Dibuja "HHMM" (4 cifras) con la esquina superior izquierda en (x, y). */
    fun draw(c: Canvas, hhmm: String, x: Float, y: Float, cell: Float, color: Int) {
        val ch = cell * ASPECT
        val eps = cell * 0.02f
        val path = Path()
        var gx = x
        hhmm.forEachIndexed { i, digit ->
            val glyph = g[digit]
            if (glyph != null) {
                for (row in 0 until ROWS) {
                    val line = glyph[row]
                    var col = 0
                    while (col < COLS) {
                        if (line[col] == '#') {
                            val start = col
                            while (col < COLS && line[col] == '#') col++
                            val r = Path()
                            r.addRect(
                                gx + start * cell, y + row * ch - eps,
                                gx + col * cell, y + (row + 1) * ch + eps,
                                Path.Direction.CW,
                            )
                            path.op(r, Path.Op.UNION)
                        } else {
                            col++
                        }
                    }
                }
            }
            gx += (COLS + GAP) * cell + if (i == 1) MID_GAP * cell else 0f
        }
        // Suaviza esquinas y fuerza par-impar para que los huecos (0, 6, 8, 9) queden vacíos
        val rounder = Paint().apply {
            style = Paint.Style.FILL
            pathEffect = CornerPathEffect(cell * 0.26f)
        }
        val soft = Path()
        @Suppress("DEPRECATION")
        rounder.getFillPath(path, soft)
        soft.fillType = Path.FillType.EVEN_ODD
        c.drawPath(soft, Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color; style = Paint.Style.FILL })
    }
}
