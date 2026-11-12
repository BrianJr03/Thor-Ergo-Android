package jr.brian.thorkbm.core.swipe

import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.max

/** A point of a swipe, in the same units as the key boxes. */
data class SwipePoint(val x: Float, val y: Float)

/** Where a letter key sits: its centre and size. */
data class KeyBox(val cx: Float, val cy: Float, val w: Float, val h: Float)

/**
 * Turns a finger trace over the letter keys into ranked words (a simplified SHARK2 decoder).
 * Each word's ideal path runs through its key centres; candidates must start and end near the trace,
 * then score by how far the trace strays from that path, how many letters it misses, and how common
 * the word is. Deterministic and never learns from typing. Must be used from one thread.
 */
class SwipeDecoder(private val lexicon: SwipeLexicon) {
    /** Words grouped by first and last letter, the cheapest filter for a trace. */
    private val byEnds: Map<Pair<Char, Char>, List<LexiconWord>> by lazy {
        lexicon.words.groupBy { it.letters.first() to it.letters.last() }
    }

    private var cachedKeys: Map<Char, KeyBox>? = null
    private val templates = HashMap<String, List<SwipePoint>?>()

    fun warmUp() {
        byEnds.size
    }

    fun decode(trace: List<SwipePoint>, keys: Map<Char, KeyBox>, limit: Int = 18): List<String> {
        if (trace.size < 2 || keys.isEmpty()) return emptyList()
        if (keys != cachedKeys) {
            cachedKeys = keys
            templates.clear()
        }
        val unit = keys.values.map { it.w }.average().toFloat()
        val path = resample(trace)
        val starts = nearKeys(trace.first(), keys, unit)
        val ends = nearKeys(trace.last(), keys, unit)
        val scored = ArrayList<Pair<String, Double>>()
        val seen = HashSet<String>()
        for (start in starts) {
            for (end in ends) {
                for (word in byEnds[start to end].orEmpty()) {
                    val template = templates.getOrPut(word.letters) { template(word.letters, keys) } ?: continue
                    if (!seen.add(word.text)) continue
                    scored += word.text to score(path, template, word, unit)
                }
            }
        }
        return scored.sortedBy { it.second }.take(limit).map { it.first }
    }

    private fun score(path: List<SwipePoint>, template: List<SwipePoint>, word: LexiconWord, unit: Float): Double {
        var location = 0.0
        for (i in path.indices) location += distance(path[i], template[i])
        location /= path.size * unit
        // A letter the trace never comes near is a strong sign of the wrong word.
        var missed = 0.0
        for (center in keyCenters(word.letters)) {
            val nearest = path.minOf { distance(it, center) }
            missed += max(0.0, nearest / unit - MISS_RADIUS)
        }
        return location + MISS_WEIGHT * missed - FREQUENCY_WEIGHT * ln(word.count.toDouble())
    }

    private fun keyCenters(letters: String): List<SwipePoint> {
        val keys = cachedKeys ?: return emptyList()
        return letters.mapNotNull { c -> keys[c]?.let { SwipePoint(it.cx, it.cy) } }
    }

    /** The resampled path through [letters]' key centres, or `null` if it never leaves one key. */
    private fun template(letters: String, keys: Map<Char, KeyBox>): List<SwipePoint>? {
        val points = ArrayList<SwipePoint>()
        for (c in letters) {
            val key = keys[c] ?: return null
            val point = SwipePoint(key.cx, key.cy)
            if (points.lastOrNull() != point) points += point
        }
        return if (points.size < 2) null else resample(points)
    }

    private fun nearKeys(point: SwipePoint, keys: Map<Char, KeyBox>, unit: Float): List<Char> {
        val ranked = keys.entries.sortedBy { distance(point, SwipePoint(it.value.cx, it.value.cy)) }
        return ranked.filterIndexed { i, (_, box) -> i == 0 || distance(point, SwipePoint(box.cx, box.cy)) <= unit * END_RADIUS }
            .map { it.key }
    }

    companion object {
        const val SAMPLES = 32
        private const val END_RADIUS = 0.9
        private const val MISS_RADIUS = 0.5
        private const val MISS_WEIGHT = 1.5
        private const val FREQUENCY_WEIGHT = 0.06

        private fun distance(a: SwipePoint, b: SwipePoint): Double = hypot((a.x - b.x).toDouble(), (a.y - b.y).toDouble())

        /** [SAMPLES] points spaced evenly along the polyline. */
        internal fun resample(points: List<SwipePoint>): List<SwipePoint> {
            val total = points.zipWithNext { a, b -> distance(a, b) }.sum()
            if (total == 0.0) return List(SAMPLES) { points.first() }
            val step = total / (SAMPLES - 1)
            val result = ArrayList<SwipePoint>(SAMPLES)
            result += points.first()
            var carried = 0.0
            for (i in 1 until points.size) {
                var a = points[i - 1]
                val b = points[i]
                var segment = distance(a, b)
                while (carried + segment >= step && result.size < SAMPLES) {
                    val t = ((step - carried) / segment).toFloat()
                    val p = SwipePoint(a.x + t * (b.x - a.x), a.y + t * (b.y - a.y))
                    result += p
                    segment -= step - carried
                    a = p
                    carried = 0.0
                }
                carried += segment
            }
            while (result.size < SAMPLES) result += points.last()
            return result
        }
    }
}
