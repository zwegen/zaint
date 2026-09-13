package de.zwegen.zpaint.command.serialization

import com.esotericsoftware.kryo.Kryo
import com.esotericsoftware.kryo.KryoException
import com.esotericsoftware.kryo.Serializer
import com.esotericsoftware.kryo.io.Input

abstract class ZaintVersionedSerializer<T>(val version: Int) : Serializer<T>() {

    companion object {
        private const val V1 = 1
        private const val V2 = 2
        private const val V3 = 3
        private const val V4 = 4
        private const val V5 = 5
        private const val V6 = 6
        private const val V7 = 7
        private const val V8 = 8
        private const val V9 = 9
        private const val V10 = 10
        private const val V11 = 11
        private const val V12 = 12
        private const val V13 = 13
        private const val V14 = 14
        private const val V15 = 15
    }

    protected fun handleVersions(serializer: ZaintVersionedSerializer<T>, kryo: Kryo, input: Input, type: Class<out T>): T {
        return when (version) {
            // Currently just here to see the intended pattern
            V1 -> serializer.readV1(serializer, kryo, input, type)
            V2 -> serializer.readV2(serializer, kryo, input, type)
            V3 -> serializer.readV3(serializer, kryo, input, type)
            V4 -> serializer.readV4(serializer, kryo, input, type)
            V5 -> serializer.readV5(serializer, kryo, input, type)
            V6 -> serializer.readV6(serializer, kryo, input, type)
            V7 -> serializer.readV7(serializer, kryo, input, type)
            V8 -> serializer.readV8(serializer, kryo, input, type)
            V9 -> serializer.readV9(serializer, kryo, input, type)
            V10 -> serializer.readV10(serializer, kryo, input, type)
            V11 -> serializer.readV11(serializer, kryo, input, type)
            V12 -> serializer.readV12(serializer, kryo, input, type)
            V13 -> serializer.readV13(serializer, kryo, input, type)
            V14 -> serializer.readV14(serializer, kryo, input, type)
            V15 -> serializer.readV15(serializer, kryo, input, type)
            else -> throw KryoException()
        }
    }

    protected open fun readV1(serializer: ZaintVersionedSerializer<T>, kryo: Kryo, input: Input, type: Class<out T>): T =
        serializer.readV2(serializer, kryo, input, type)

    protected open fun readV2(serializer: ZaintVersionedSerializer<T>, kryo: Kryo, input: Input, type: Class<out T>): T =
        serializer.readV3(serializer, kryo, input, type)

    protected open fun readV3(serializer: ZaintVersionedSerializer<T>, kryo: Kryo, input: Input, type: Class<out T>): T =
        serializer.readV4(serializer, kryo, input, type)

    protected open fun readV4(serializer: ZaintVersionedSerializer<T>, kryo: Kryo, input: Input, type: Class<out T>): T =
        serializer.readV5(serializer, kryo, input, type)

    protected open fun readV5(serializer: ZaintVersionedSerializer<T>, kryo: Kryo, input: Input, type: Class<out T>): T =
        serializer.readV6(serializer, kryo, input, type)

    protected open fun readV6(serializer: ZaintVersionedSerializer<T>, kryo: Kryo, input: Input, type: Class<out T>): T =
        serializer.readV7(serializer, kryo, input, type)

    protected open fun readV7(serializer: ZaintVersionedSerializer<T>, kryo: Kryo, input: Input, type: Class<out T>): T =
        serializer.readV8(serializer, kryo, input, type)

    protected open fun readV8(serializer: ZaintVersionedSerializer<T>, kryo: Kryo, input: Input, type: Class<out T>): T =
        serializer.readV9(serializer, kryo, input, type)

    protected open fun readV9(serializer: ZaintVersionedSerializer<T>, kryo: Kryo, input: Input, type: Class<out T>): T =
        serializer.readV10(serializer, kryo, input, type)

    protected open fun readV10(serializer: ZaintVersionedSerializer<T>, kryo: Kryo, input: Input, type: Class<out T>): T =
        serializer.readV11(serializer, kryo, input, type)

    protected open fun readV11(serializer: ZaintVersionedSerializer<T>, kryo: Kryo, input: Input, type: Class<out T>): T =
        serializer.readV12(serializer, kryo, input, type)

    protected open fun readV12(serializer: ZaintVersionedSerializer<T>, kryo: Kryo, input: Input, type: Class<out T>): T =
        serializer.readV13(serializer, kryo, input, type)

    protected open fun readV13(serializer: ZaintVersionedSerializer<T>, kryo: Kryo, input: Input, type: Class<out T>): T =
        serializer.readV14(serializer, kryo, input, type)

    protected open fun readV14(serializer: ZaintVersionedSerializer<T>, kryo: Kryo, input: Input, type: Class<out T>): T =
        serializer.readV15(serializer, kryo, input, type)

    protected open fun readV15(serializer: ZaintVersionedSerializer<T>, kryo: Kryo, input: Input, type: Class<out T>): T =
        serializer.readCurrentVersion(kryo, input, type)

    abstract fun readCurrentVersion(kryo: Kryo, input: Input, type: Class<out T>): T
}
