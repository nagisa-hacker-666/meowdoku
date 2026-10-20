package com.meow.helper

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs

class Board(val n: Int, val left: Float, val top: Float, val cw: Float, val ch: Float, val cols: IntArray)

object Solver {
    /** cols[r] = colonne du chat de la ligne r ; null si pas de solution */
    fun solve(reg: IntArray, n: Int): Pair<IntArray?, Int> {
        val cols = IntArray(n); val used = BooleanArray(n); val usedC = BooleanArray(n)
        var count = 0; var first: IntArray? = null
        fun rec(r: Int): Boolean {
            if (r == n) { count++; if (first == null) first = cols.clone(); return count >= 2 }
            for (c in 0 until n) {
                if (usedC[c]) continue
                if (r > 0 && abs(cols[r - 1] - c) < 2) continue
                val k = reg[r * n + c]; if (used[k]) continue
                cols[r] = c; used[k] = true; usedC[c] = true
                val stop = rec(r + 1)
                used[k] = false; usedC[c] = false
                if (stop) return true
            }
            return false
        }
        rec(0)
        return Pair(first, count)
    }

    /** Détecte la grille (zone colorée), devine N (5..10) et résout. */
    fun detect(bmp: Bitmap): Board? {
        val w = bmp.width; val h = bmp.height
        val px = IntArray(w * h); bmp.getPixels(px, 0, w, 0, 0, w, h)
        fun sat(p: Int): Boolean {
            val r = Color.red(p); val g = Color.green(p); val b = Color.blue(p)
            return maxOf(r, g, b) - minOf(r, g, b) > 28
        }
        val rowC = IntArray(h); val colC = IntArray(w)
        for (y in 0 until h step 2) for (x in 0 until w step 2) if (sat(px[y * w + x])) { rowC[y]++; colC[x]++ }
        fun span(a: IntArray): Pair<Int, Int>? {
            val mx = a.max(); if (mx < 10) return null
            val t = mx * 0.6
            return Pair(a.indexOfFirst { it >= t }, a.indexOfLast { it >= t })
        }
        val (top, bot) = span(rowC) ?: return null
        val (left, right) = span(colC) ?: return null
        val bw = (right - left + 1).toFloat(); val bh = (bot - top + 1).toFloat()
        if (bw < 200 || bh < 200) return null
        var fallback: Board? = null
        for (n in 5..10) {
            val cw = bw / n; val ch = bh / n
            val reps = ArrayList<IntArray>(); val reg = IntArray(n * n); var ok = true
            loop@ for (r in 0 until n) for (c in 0 until n) {
                val cx = (left + (c + 0.5f) * cw).toInt(); val cy = (top + (r + 0.5f) * ch).toInt()
                var sr = 0; var sg = 0; var sb = 0; var k = 0
                for (dy in -3..3 step 3) for (dx in -3..3 step 3) {
                    val p = px[(cy + dy).coerceIn(0, h - 1) * w + (cx + dx).coerceIn(0, w - 1)]
                    sr += Color.red(p); sg += Color.green(p); sb += Color.blue(p); k++
                }
                val rgb = intArrayOf(sr / k, sg / k, sb / k)
                var id = reps.indexOfFirst { abs(it[0] - rgb[0]) + abs(it[1] - rgb[1]) + abs(it[2] - rgb[2]) < 36 }
                if (id < 0) { reps.add(rgb); id = reps.size - 1 }
                if (reps.size > n) { ok = false; break@loop }
                reg[r * n + c] = id
            }
            if (!ok || reps.size != n) continue
            val (sol, cnt) = solve(reg, n)
            if (sol != null) {
                val b = Board(n, left.toFloat(), top.toFloat(), cw, ch, sol)
                if (cnt == 1) return b
                if (fallback == null) fallback = b
            }
        }
        return fallback
    }
}
