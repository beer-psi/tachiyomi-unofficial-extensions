package io.github.beerpsi.tachiyomi.extension.en.fakku

import kotlin.math.floor
import kotlin.math.truncate

private const val MASHER_INITIAL_STATE: Double = 4022871197.0
private const val UHEPRNG_ORDER = 48
private val controlCharacterRegex = Regex("""[\x00-\x1F\x7F-\x9F]""")

private class Mash {
    private var _n = MASHER_INITIAL_STATE

    fun reset() {
        _n = MASHER_INITIAL_STATE
    }

    @Suppress("MagicNumber")
    fun masher(data: String): Double {
        data.forEach {
            _n += it.code

            var h = 0.02519603282416938 * _n

            _n = floor(h)
            h -= _n
            h *= _n
            _n = floor(h)
            h -= _n
            _n += h * 0x100000000
        }

        return floor(_n) * 2.3283064365386963e-10
    }
}

class UltraHighEntropyPRNG {
    private val mash = Mash()
    private var carry: Double = 1.0
    private var phase = UHEPRNG_ORDER
    private val state = DoubleArray(UHEPRNG_ORDER)

    init {
        reset()
    }

    private fun reset() {
        mash.reset()
        for (i in state.indices) {
            state[i] = mash.masher(" ")
        }
        carry = 1.0
        phase = UHEPRNG_ORDER
    }

    private fun hashString(data: String) {
        val cleanData = data.replace(controlCharacterRegex, "").trim()

        mash.masher(cleanData)

        for (c in data) {
            val code = c.code.toString()

            for (i in state.indices) {
                state[i] -= mash.masher(code)

                while (state[i] < 0) {
                    state[i] += 1.0
                }
            }
        }
    }

    private fun rawPRNG(): Double {
        phase += 1

        if (phase >= UHEPRNG_ORDER) {
            phase = 0
        }

        val t = 1768863 * state[phase] + carry * 2.3283064365386963e-10

        carry = floor(t)
        state[phase] = t - carry

        return state[phase]
    }

    fun seed(data: String): UltraHighEntropyPRNG {
        reset()
        hashString(data)

        return this
    }

    @Suppress("MagicNumber")
    fun next(range: Long): Long {
        return floor(
            range * (rawPRNG() + truncate(rawPRNG() * 0x200000) * 1.1102230246251565e-16)
        )
            .toLong()
    }

    fun next(): Double {
        return next(Long.MAX_VALUE - 1).toDouble() / Long.MAX_VALUE
    }
}
