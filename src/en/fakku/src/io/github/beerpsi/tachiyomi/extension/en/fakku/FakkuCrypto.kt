package io.github.beerpsi.tachiyomi.extension.en.fakku

import kotlin.math.floor

internal fun calculateDecryptionKey(zid: String, keyHash: String): ByteArray {
    return if (zid == "13fafbe11a72969c2464696efd553940f6a45c1c4801b19c3445e033f38b0e7e") {
        zid + keyHash + "b3d90ea3cc794be5e74013880c4519aae1b8fbe3108f2bbe60c5dc3f6e807ff1"
    } else {
        zid + keyHash + "0a10f3bd42587ad70fc96886d8e5e7b3614ce69529b238a1c690cb9b51d4868f"
    }
        .toByteArray(Charsets.UTF_8)
}

internal fun ByteArray.decryptXorCipher(key: ByteArray) = this.mapIndexed { i, b ->
    (b.toInt() xor key[i % key.size].toInt()).toByte()
}
    .toByteArray()

internal fun MutableList<Int>.randomize(seed: Int): MutableList<Int> {
    val rand = UltraHighEntropyPRNG().seed(seed.toString())

    for (i in this.indices.reversed()) {
        val newLoc = floor(rand.next() * (i + 1)).toInt()
        val tmp = this[i]
        this[i] = this[newLoc]
        this[newLoc] = tmp
    }

    return this
}

internal fun List<Int>.shuffle(seed: Int): List<Int> {
    val order = indices.toMutableList().randomize(seed)
    val newList = this.toMutableList()

    order.forEachIndexed { i, j ->
        newList[j] = this[i]
    }

    return newList
}
