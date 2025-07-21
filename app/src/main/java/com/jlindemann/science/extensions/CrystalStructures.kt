package com.jlindemann.science.extensions

object CrystalStructures {
    data class Structure(
        val vertices: Array<FloatArray>, // [x, y, z]
        val edges: Array<IntArray>,      // [i, j]
        val displayName: String
    )

    val data = mapOf(
        "Cubic" to Structure(
            vertices = arrayOf(
                floatArrayOf(-1f, -1f, -1f), floatArrayOf(1f, -1f, -1f),
                floatArrayOf(1f, 1f, -1f), floatArrayOf(-1f, 1f, -1f),
                floatArrayOf(-1f, -1f, 1f), floatArrayOf(1f, -1f, 1f),
                floatArrayOf(1f, 1f, 1f), floatArrayOf(-1f, 1f, 1f)
            ),
            edges = arrayOf(
                intArrayOf(0, 1), intArrayOf(1, 2), intArrayOf(2, 3), intArrayOf(3, 0),
                intArrayOf(4, 5), intArrayOf(5, 6), intArrayOf(6, 7), intArrayOf(7, 4),
                intArrayOf(0, 4), intArrayOf(1, 5), intArrayOf(2, 6), intArrayOf(3, 7)
            ),
            displayName = "Cubic"
        ),
        "Body-centered cubic (bcc)" to Structure(
            vertices = arrayOf(
                floatArrayOf(-1f, -1f, -1f), floatArrayOf(1f, -1f, -1f),
                floatArrayOf(1f, 1f, -1f), floatArrayOf(-1f, 1f, -1f),
                floatArrayOf(-1f, -1f, 1f), floatArrayOf(1f, -1f, 1f),
                floatArrayOf(1f, 1f, 1f), floatArrayOf(-1f, 1f, 1f),
                floatArrayOf(0f, 0f, 0f)
            ),
            edges = arrayOf(
                intArrayOf(0, 1), intArrayOf(1, 2), intArrayOf(2, 3), intArrayOf(3, 0),
                intArrayOf(4, 5), intArrayOf(5, 6), intArrayOf(6, 7), intArrayOf(7, 4),
                intArrayOf(0, 4), intArrayOf(1, 5), intArrayOf(2, 6), intArrayOf(3, 7),
                intArrayOf(8, 0), intArrayOf(8, 1), intArrayOf(8, 2), intArrayOf(8, 3),
                intArrayOf(8, 4), intArrayOf(8, 5), intArrayOf(8, 6), intArrayOf(8, 7)
            ),
            displayName = "Body-centered cubic (bcc)"
        ),
        "Face-centered cubic (fcc)" to Structure(
            vertices = arrayOf(
                floatArrayOf(-1f, -1f, -1f), floatArrayOf(1f, -1f, -1f),
                floatArrayOf(1f, 1f, -1f), floatArrayOf(-1f, 1f, -1f),
                floatArrayOf(-1f, -1f, 1f), floatArrayOf(1f, -1f, 1f),
                floatArrayOf(1f, 1f, 1f), floatArrayOf(-1f, 1f, 1f),
                floatArrayOf(0f, 0f, -1f), floatArrayOf(0f, 0f, 1f),
                floatArrayOf(0f, -1f, 0f), floatArrayOf(0f, 1f, 0f),
                floatArrayOf(-1f, 0f, 0f), floatArrayOf(1f, 0f, 0f)
            ),
            edges = arrayOf(
                intArrayOf(0, 1), intArrayOf(1, 2), intArrayOf(2, 3), intArrayOf(3, 0),
                intArrayOf(4, 5), intArrayOf(5, 6), intArrayOf(6, 7), intArrayOf(7, 4),
                intArrayOf(0, 4), intArrayOf(1, 5), intArrayOf(2, 6), intArrayOf(3, 7),
                intArrayOf(8, 0), intArrayOf(8, 1), intArrayOf(8, 2), intArrayOf(8, 3),
                intArrayOf(9, 4), intArrayOf(9, 5), intArrayOf(9, 6), intArrayOf(9, 7),
                intArrayOf(10, 0), intArrayOf(10, 1), intArrayOf(10, 4), intArrayOf(10, 5),
                intArrayOf(11, 2), intArrayOf(11, 3), intArrayOf(11, 6), intArrayOf(11, 7),
                intArrayOf(12, 0), intArrayOf(12, 3), intArrayOf(12, 4), intArrayOf(12, 7),
                intArrayOf(13, 1), intArrayOf(13, 2), intArrayOf(13, 5), intArrayOf(13, 6)
            ),
            displayName = "Face-centered cubic (fcc)"
        ),
        "Tetragonal" to Structure(
            vertices = arrayOf(
                floatArrayOf(-1f, -1f, -1.5f), floatArrayOf(1f, -1f, -1.5f),
                floatArrayOf(1f, 1f, -1.5f), floatArrayOf(-1f, 1f, -1.5f),
                floatArrayOf(-1f, -1f, 1.5f), floatArrayOf(1f, -1f, 1.5f),
                floatArrayOf(1f, 1f, 1.5f), floatArrayOf(-1f, 1f, 1.5f)
            ),
            edges = arrayOf(
                intArrayOf(0, 1), intArrayOf(1, 2), intArrayOf(2, 3), intArrayOf(3, 0),
                intArrayOf(4, 5), intArrayOf(5, 6), intArrayOf(6, 7), intArrayOf(7, 4),
                intArrayOf(0, 4), intArrayOf(1, 5), intArrayOf(2, 6), intArrayOf(3, 7)
            ),
            displayName = "Tetragonal"
        ),
        "Orthorhombic" to Structure(
            vertices = arrayOf(
                floatArrayOf(-1.5f, -1f, -0.5f), floatArrayOf(1.5f, -1f, -0.5f),
                floatArrayOf(1.5f, 1f, -0.5f), floatArrayOf(-1.5f, 1f, -0.5f),
                floatArrayOf(-1.5f, -1f, 0.5f), floatArrayOf(1.5f, -1f, 0.5f),
                floatArrayOf(1.5f, 1f, 0.5f), floatArrayOf(-1.5f, 1f, 0.5f)
            ),
            edges = arrayOf(
                intArrayOf(0, 1), intArrayOf(1, 2), intArrayOf(2, 3), intArrayOf(3, 0),
                intArrayOf(4, 5), intArrayOf(5, 6), intArrayOf(6, 7), intArrayOf(7, 4),
                intArrayOf(0, 4), intArrayOf(1, 5), intArrayOf(2, 6), intArrayOf(3, 7)
            ),
            displayName = "Orthorhombic"
        ),
        "Rhombohedral" to Structure(
            vertices = arrayOf(
                floatArrayOf(-1f, -1f, -1f), floatArrayOf(1f, -1f, -1f),
                floatArrayOf(1f, 1f, -1f), floatArrayOf(-1f, 1f, -1f),
                floatArrayOf(-0.5f, -0.5f, 1f), floatArrayOf(1.5f, -0.5f, 1f),
                floatArrayOf(1.5f, 1.5f, 1f), floatArrayOf(-0.5f, 1.5f, 1f)
            ),
            edges = arrayOf(
                intArrayOf(0, 1), intArrayOf(1, 2), intArrayOf(2, 3), intArrayOf(3, 0),
                intArrayOf(4, 5), intArrayOf(5, 6), intArrayOf(6, 7), intArrayOf(7, 4),
                intArrayOf(0, 4), intArrayOf(1, 5), intArrayOf(2, 6), intArrayOf(3, 7)
            ),
            displayName = "Rhombohedral"
        ),
        "Hexagonal" to Structure(
            vertices = arrayOf(
                floatArrayOf(1f, 0f, -1f), floatArrayOf(0.5f, (Math.sqrt(3.0) / 2).toFloat(), -1f),
                floatArrayOf(-0.5f, (Math.sqrt(3.0) / 2).toFloat(), -1f), floatArrayOf(-1f, 0f, -1f),
                floatArrayOf(-0.5f, (-Math.sqrt(3.0) / 2).toFloat(), -1f), floatArrayOf(0.5f, (-Math.sqrt(3.0) / 2).toFloat(), -1f),
                floatArrayOf(1f, 0f, 1f), floatArrayOf(0.5f, (Math.sqrt(3.0) / 2).toFloat(), 1f),
                floatArrayOf(-0.5f, (Math.sqrt(3.0) / 2).toFloat(), 1f), floatArrayOf(-1f, 0f, 1f),
                floatArrayOf(-0.5f, (-Math.sqrt(3.0) / 2).toFloat(), 1f), floatArrayOf(0.5f, (-Math.sqrt(3.0) / 2).toFloat(), 1f)
            ),
            edges = arrayOf(
                intArrayOf(0, 1), intArrayOf(1, 2), intArrayOf(2, 3), intArrayOf(3, 4), intArrayOf(4, 5), intArrayOf(5, 0),
                intArrayOf(6, 7), intArrayOf(7, 8), intArrayOf(8, 9), intArrayOf(9, 10), intArrayOf(10, 11), intArrayOf(11, 6),
                intArrayOf(0, 6), intArrayOf(1, 7), intArrayOf(2, 8), intArrayOf(3, 9), intArrayOf(4, 10), intArrayOf(5, 11)
            ),
            displayName = "Hexagonal"
        ),
        "Trigonal" to Structure(
            vertices = arrayOf(
                floatArrayOf(-1f, -1f, -1f), floatArrayOf(1f, -1f, -1f),
                floatArrayOf(1f, 1f, -1f), floatArrayOf(-1f, 1f, -1f),
                floatArrayOf(-0.5f, -0.5f, 1f), floatArrayOf(1.5f, -0.5f, 1f),
                floatArrayOf(1.5f, 1.5f, 1f), floatArrayOf(-0.5f, 1.5f, 1f)
            ),
            edges = arrayOf(
                intArrayOf(0, 1), intArrayOf(1, 2), intArrayOf(2, 3), intArrayOf(3, 0),
                intArrayOf(4, 5), intArrayOf(5, 6), intArrayOf(6, 7), intArrayOf(7, 4),
                intArrayOf(0, 4), intArrayOf(1, 5), intArrayOf(2, 6), intArrayOf(3, 7)
            ),
            displayName = "Trigonal"
        ),
        "Monoclinic" to Structure(
            vertices = arrayOf(
                floatArrayOf(-1f, -1f, -1f), floatArrayOf(1f, -1f, -1f),
                floatArrayOf(1.2f, 1f, -1f), floatArrayOf(-0.8f, 1f, -1f),
                floatArrayOf(-1f, -1f, 1f), floatArrayOf(1f, -1f, 1f),
                floatArrayOf(1.2f, 1f, 1f), floatArrayOf(-0.8f, 1f, 1f)
            ),
            edges = arrayOf(
                intArrayOf(0, 1), intArrayOf(1, 2), intArrayOf(2, 3), intArrayOf(3, 0),
                intArrayOf(4, 5), intArrayOf(5, 6), intArrayOf(6, 7), intArrayOf(7, 4),
                intArrayOf(0, 4), intArrayOf(1, 5), intArrayOf(2, 6), intArrayOf(3, 7)
            ),
            displayName = "Monoclinic"
        ),
        "Triclinic" to Structure(
            vertices = arrayOf(
                floatArrayOf(-1f, -1f, -1f), floatArrayOf(1.2f, -0.8f, -1f),
                floatArrayOf(1f, 1f, -0.7f), floatArrayOf(-1.1f, 1.1f, -0.5f),
                floatArrayOf(-0.8f, -1.2f, 1f), floatArrayOf(1f, -1f, 1.2f),
                floatArrayOf(1.3f, 1f, 1f), floatArrayOf(-1f, 1.2f, 1.1f)
            ),
            edges = arrayOf(
                intArrayOf(0, 1), intArrayOf(1, 2), intArrayOf(2, 3), intArrayOf(3, 0),
                intArrayOf(4, 5), intArrayOf(5, 6), intArrayOf(6, 7), intArrayOf(7, 4),
                intArrayOf(0, 4), intArrayOf(1, 5), intArrayOf(2, 6), intArrayOf(3, 7)
            ),
            displayName = "Triclinic"
        ),
    )
}