package com.meow.helper

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.abs

class Board(val n: Int, val left: Float, val top: Float, val cw: Float, val ch: Float, val cols: IntArray, val placed: BooleanArray)

object Solver {
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

    private fun close(a: IntArray, b: IntArray) = abs(a[0] - b[0]) + abs(a[1] - b[1]) + abs(a[2] - b[2]) < 36

    private fun sample(px: IntArray, w: Int, h: Int, x: Int, y: Int): IntArray {
        var sr = 0; var sg = 0; var sb = 0; var k = 0
        for (dy in -2..2 step 2) for (dx in -2..2 step 2) {
            val p = px[(y + dy).coerceIn(0, h - 1) * w + (x + dx).coerceIn(0, w - 1)]
            sr += Color.red(p); sg += Color.green(p); sb += Color.blue(p); k++
        }
        return intArrayOf(sr / k, sg / k, sb / k)
    }

    // nombre de blocs colorés (cases) le long d'un axe, entre from et to
    private fun runs(a: IntArray, from: Int, to: Int, thr: Double): Int {
        var n = 0; var inRun = false
        for (i in from..to) {
            val on = a[i - i % 2] >= thr
            if (on) { if (!inRun) { inRun = true; n++ } } else inRun = false
        }
        return n
    }

    // chaque couleur doit former UNE zone d'un seul tenant, sinon la taille devinée est fausse
    private fun connected(reg: IntArray, n: Int): Boolean {
        val seen = BooleanArray(n * n); var comps = 0
        for (i in 0 until n * n) {
            if (seen[i]) continue
            comps++; seen[i] = true
            val st = ArrayDeque<Int>(); st.add(i)
            while (st.isNotEmpty()) {
                val j = st.removeLast(); val r = j / n; val c = j % n
                for ((dr, dc) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) {
                    val rr = r + dr; val cc = c + dc
                    if (rr in 0 until n && cc in 0 until n && !seen[rr * n + cc] && reg[rr * n + cc] == reg[j]) { seen[rr * n + cc] = true; st.add(rr * n + cc) }
                }
            }
        }
        return comps == n
    }

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
            val t = mx * 0.8
            return Pair(a.indexOfFirst { it >= t }, a.indexOfLast { it >= t })
        }
        val (top, bot) = span(rowC) ?: return null
        val (left, right) = span(colC) ?: return null
        val bw = (right - left + 1).toFloat(); val bh = (bot - top + 1).toFloat()
        if (bw < 200 || bh < 200) return null
        if (abs(bw - bh) > 0.1f * maxOf(bw, bh)) return null
        val offs = arrayOf(
            doubleArrayOf(-.4, -.4), doubleArrayOf(.4, -.4), doubleArrayOf(-.4, .4), doubleArrayOf(.4, .4),
            doubleArrayOf(0.0, -.42), doubleArrayOf(0.0, .42), doubleArrayOf(-.42, 0.0), doubleArrayOf(.42, 0.0)
        )
        val nr = runs(rowC, top, bot, rowC.max() * 0.5)
        val nc = runs(colC, left, right, colC.max() * 0.5)
        val sure = if (nr == nc && nr in 4..14) nr else -1
        val order = if (sure > 0) listOf(sure) + (5..10).filter { it != sure } else (5..10).toList()
        var fallback: Board? = null
        for (n in order) {
            val cw = bw / n; val ch = bh / n
            val reps = ArrayList<IntArray>(); val reg = IntArray(n * n); val hasCat = BooleanArray(n * n); var ok = true
            loop@ for (r in 0 until n) for (c in 0 until n) {
                val cx = left + (c + 0.5) * cw; val cy = top + (r + 0.5) * ch
                val ss = offs.map { o -> sample(px, w, h, (cx + o[0] * cw).toInt(), (cy + o[1] * ch).toInt()) }
                val rgb = ss.maxByOrNull { s -> ss.count { close(it, s) } }!!
                hasCat[r * n + c] = !close(sample(px, w, h, cx.toInt(), cy.toInt()), rgb)
                var id = reps.indexOfFirst { close(it, rgb) }
                if (id < 0) { reps.add(rgb); id = reps.size - 1 }
                if (reps.size > n) { ok = false; break@loop }
                reg[r * n + c] = id
            }
            if (!ok || reps.size != n) continue
            if (!connected(reg, n)) continue
            val (sol, cnt) = solve(reg, n)
            if (sol != null) {
                val b = Board(n, left.toFloat(), top.toFloat(), cw, ch, sol, BooleanArray(n) { hasCat[it * n + sol[it]] })
                if (cnt == 1 || n == sure) return b
                if (fallback == null) fallback = b
            }
        }
        return fallback
    }
}
