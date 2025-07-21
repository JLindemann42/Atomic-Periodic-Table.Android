package com.jlindemann.science.extensions

object CrystalMath {
    fun rotate(
        x: Float, y: Float, z: Float,
        yaw: Float, pitch: Float, roll: Float
    ): FloatArray {
        // Roll around Z-axis
        val cosr = kotlin.math.cos(roll)
        val sinr = kotlin.math.sin(roll)
        var x0 = x * cosr - y * sinr
        var y0 = x * sinr + y * cosr
        var z0 = z

        // Yaw around Y-axis
        val cosy = kotlin.math.cos(yaw)
        val siny = kotlin.math.sin(yaw)
        val x1 = x0 * cosy - z0 * siny
        val z1 = x0 * siny + z0 * cosy
        val y1 = y0

        // Pitch around X-axis
        val cosp = kotlin.math.cos(pitch)
        val sinp = kotlin.math.sin(pitch)
        val y2 = y1 * cosp - z1 * sinp
        val z2 = y1 * sinp + z1 * cosp
        return floatArrayOf(x1, y2, z2)
    }
}