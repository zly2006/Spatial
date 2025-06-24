package com.github.zly2006.spatial

import kotlin.math.sqrt
import kotlin.math.max
import kotlin.math.pow

// --- 向量和矩阵基本操作 ---

typealias Vec3 = FloatArray
typealias Mat3 = Array<FloatArray>

fun Vec3(x: Float, y: Float, z: Float) = floatArrayOf(x, y, z)
fun Mat3(
    r1c1: Float, r1c2: Float, r1c3: Float,
    r2c1: Float, r2c2: Float, r2c3: Float,
    r3c1: Float, r3c2: Float, r3c3: Float
) = arrayOf(
    floatArrayOf(r1c1, r1c2, r1c3),
    floatArrayOf(r2c1, r2c2, r2c3),
    floatArrayOf(r3c1, r3c2, r3c3)
)

fun Vec3.magnitude(): Float {
    return sqrt(this[0].pow(2) + this[1].pow(2) + this[2].pow(2))
}

fun Vec3.normalize(): Vec3 {
    val mag = magnitude()
    if (mag == 0f) return Vec3(0f, 0f, 0f)
    return Vec3(this[0] / mag, this[1] / mag, this[2] / mag)
}

fun Vec3.dot(other: Vec3): Float {
    return this[0] * other[0] + this[1] * other[1] + this[2] * other[2]
}

fun Vec3.cross(other: Vec3): Vec3 {
    return Vec3(
        this[1] * other[2] - this[2] * other[1],
        this[2] * other[0] - this[0] * other[2],
        this[0] * other[1] - this[1] * other[0]
    )
}

fun Vec3.times(scalar: Float): Vec3 {
    return Vec3(this[0] * scalar, this[1] * scalar, this[2] * scalar)
}

operator fun Vec3.plus(other: Vec3): Vec3 {
    return Vec3(this[0] + other[0], this[1] + other[1], this[2] + other[2])
}

operator fun Vec3.minus(other: Vec3): Vec3 {
    return Vec3(this[0] - other[0], this[1] - other[1], this[2] - other[2])
}

fun Mat3.times(vec: Vec3): Vec3 {
    return Vec3(
        this[0][0] * vec[0] + this[0][1] * vec[1] + this[0][2] * vec[2],
        this[1][0] * vec[0] + this[1][1] * vec[1] + this[1][2] * vec[2],
        this[2][0] * vec[0] + this[2][1] * vec[1] + this[2][2] * vec[2]
    )
}

operator fun Mat3.plus(other: Mat3): Mat3 {
    return Mat3(
        this[0][0] + other[0][0], this[0][1] + other[0][1], this[0][2] + other[0][2],
        this[1][0] + other[1][0], this[1][1] + other[1][1], this[1][2] + other[1][2],
        this[2][0] + other[2][0], this[2][1] + other[2][1], this[2][2] + other[2][2]
    )
}

operator fun Mat3.minus(other: Mat3): Mat3 {
    return Mat3(
        this[0][0] - other[0][0], this[0][1] - other[0][1], this[0][2] - other[0][2],
        this[1][0] - other[1][0], this[1][1] - other[1][1], this[1][2] - other[1][2],
        this[2][0] - other[2][0], this[2][1] - other[2][1], this[2][2] - other[2][2]
    )
}

fun Mat3.times(scalar: Float): Mat3 {
    return Mat3(
        this[0][0] * scalar, this[0][1] * scalar, this[0][2] * scalar,
        this[1][0] * scalar, this[1][1] * scalar, this[1][2] * scalar,
        this[2][0] * scalar, this[2][1] * scalar, this[2][2] * scalar
    )
}

fun Mat3.times(other: Mat3): Mat3 {
    val result = Array(3) { FloatArray(3) }
    for (i in 0 until 3) {
        for (j in 0 until 3) {
            for (k in 0 until 3) {
                result[i][j] += this[i][k] * other[k][j]
            }
        }
    }
    return result
}

// Skew-symmetric matrix from a vector for cross product: [v]x * w = v x w
fun skewSymmetricMatrix(v: Vec3): Mat3 {
    return Mat3(
        0f, -v[2], v[1],
        v[2], 0f, -v[0],
        -v[1], v[0], 0f
    )
}

// Identity matrix
val IDENTITY_MAT3: Mat3 = Mat3(
    1f, 0f, 0f,
    0f, 1f, 0f,
    0f, 0f, 1f
)

// --- 主要计算函数 ---

data class ExpectedVectors(
    val expectedRight: Vec3,
    val expectedUp: Vec3,
    val expectedNormal: Vec3
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as ExpectedVectors

        if (!expectedRight.contentEquals(other.expectedRight)) return false
        if (!expectedUp.contentEquals(other.expectedUp)) return false
        if (!expectedNormal.contentEquals(other.expectedNormal)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = expectedRight.contentHashCode()
        result = 31 * result + expectedUp.contentHashCode()
        result = 31 * result + expectedNormal.contentHashCode()
        return result
    }
}

fun calculateExpectedVectors(
    currentScreenNormal: Vec3,
    expectedGyroRight: Float,
    expectedGyroUp: Float
): ExpectedVectors {
    val C_mag_sq = currentScreenNormal.dot(currentScreenNormal)
    val GR_sq = expectedGyroRight.pow(2)
    val GU_sq = expectedGyroUp.pow(2)

    var calculated_CN: Float
    var scaled_GR = expectedGyroRight
    var scaled_GU = expectedGyroUp

    // 处理无解情况：如果 (GR^2 + GU^2) > |C|^2，则需要缩放 GR 和 GU
    val gr_gu_sum_sq = GR_sq + GU_sq
    val epsilon_sq = 1e-12f // 使用更小的容差进行平方值比较
    if (gr_gu_sum_sq > C_mag_sq + epsilon_sq) {
        val scale_factor = sqrt(C_mag_sq / gr_gu_sum_sq)
        scaled_GR *= scale_factor
        scaled_GU *= scale_factor
        calculated_CN = 0f // 此时 CN 理论上为0
        println("警告: expectedGyroRight 和 expectedGyroUp 的组合过大，已按比例缩放。")
    } else {
        calculated_CN = sqrt(C_mag_sq - gr_gu_sum_sq)
    }

    // 目标向量 A 在 R,U,N 坐标系中表示 currentScreenNormal 的分量
    val A_target_in_RUN_frame = Vec3(scaled_GR, scaled_GU, calculated_CN)

    // 实际向量 B 在世界坐标系中表示 currentScreenNormal
    val B_current_actual = currentScreenNormal

    // 将 A 和 B 归一化 (它们的长度已经相同，为 currentScreenNormal 的长度)
    // 这里可以直接使用 A_target_in_RUN_frame 和 B_current_actual，因为它们的模长是相同的 (currentScreenNormal.magnitude())
    // 归一化是为了让 v 和 c 的计算更符合单位向量的 Rodrigues 公式
    val A_unit = A_target_in_RUN_frame.normalize()
    val B_unit = B_current_actual.normalize()

    // 计算旋转矩阵 M，使得 M * A_unit = B_unit
    val v = A_unit.cross(B_unit)
    val c = A_unit.dot(B_unit) // cos(theta)

    val rotationMatrix: Mat3

    val epsilon_rot = 1e-6f // 用于判断向量是否共线
    val v_magnitude = v.magnitude()

    if (v_magnitude < epsilon_rot) { // A_unit 和 B_unit 近似共线
        if (c > 0) { // 完全同向
            rotationMatrix = IDENTITY_MAT3
        } else { // 完全反向 (180度旋转)
            // 选择一个与 A_unit 垂直的任意轴
            val axis: Vec3
            if (A_unit[0].pow(2) + A_unit[1].pow(2) > epsilon_rot) { // A_unit 不在 Z 轴上
                axis = Vec3(0f, 0f, 1f).cross(A_unit).normalize() // 绕与 Z 轴和 A_unit 都垂直的轴旋转
            } else { // A_unit 接近 Z 轴 (或其反向)
                axis = Vec3(1f, 0f, 0f).cross(A_unit).normalize() // 绕与 X 轴和 A_unit 都垂直的轴旋转
            }
            // 180度旋转矩阵公式: R = 2 * k * k^T - I (当 k 是单位向量)
            val k_k_T = Mat3(
                axis[0]*axis[0], axis[0]*axis[1], axis[0]*axis[2],
                axis[1]*axis[0], axis[1]*axis[1], axis[1]*axis[2],
                axis[2]*axis[0], axis[2]*axis[1], axis[2]*axis[2]
            )
            rotationMatrix = (k_k_T.times(2f)) - IDENTITY_MAT3
        }
    } else { // 向量 A_unit 和 B_unit 不共线
        val v_skew = skewSymmetricMatrix(v)
        val v_skew_sq = v_skew.times(v_skew)
        rotationMatrix = IDENTITY_MAT3 + v_skew + v_skew_sq.times(1f / (1f + c))
    }

    // rotationMatrix 是将 A_target_in_RUN_frame (在 R,U,N 坐标系中) 旋转到 B_current_actual (在世界坐标系中) 的矩阵。
    // 它的列向量就是 R, U, N 在世界坐标系中的表示。
    val expectedRight = rotationMatrix.times(Vec3(1f, 0f, 0f)).normalize()
    val expectedUp = rotationMatrix.times(Vec3(0f, 1f, 0f)).normalize()
    val expectedNormal = rotationMatrix.times(Vec3(0f, 0f, 1f)).normalize()

    return ExpectedVectors(expectedRight, expectedUp, expectedNormal)
}

// --- 测试案例 ---

fun main() {
    println("--- 测试案例 1: currentScreenNormal 沿 Z 轴，有微小陀螺仪偏置 ---")
    val currentScreenNormal1 = floatArrayOf(0f, 0f, 1f) // Z 轴
    val expectedGyroRight1 = 0.1f
    val expectedGyroUp1 = 0.2f

    val result1 = calculateExpectedVectors(currentScreenNormal1, expectedGyroRight1, expectedGyroUp1)
    println("currentScreenNormal: ${currentScreenNormal1.contentToString()}")
    println("expectedGyroRight: $expectedGyroRight1, expectedGyroUp: $expectedGyroUp1")
    println("expectedRight: ${result1.expectedRight.contentToString()}")
    println("expectedUp: ${result1.expectedUp.contentToString()}")
    println("expectedNormal: ${result1.expectedNormal.contentToString()}")
    println("--- 验证 ---")
    println("expectedRight . currentScreenNormal: ${result1.expectedRight.dot(currentScreenNormal1)}")
    println("expectedUp . currentScreenNormal: ${result1.expectedUp.dot(currentScreenNormal1)}")
    println("expectedNormal . currentScreenNormal: ${result1.expectedNormal.dot(currentScreenNormal1)}")
    println("expectedRight x expectedUp: ${result1.expectedRight.cross(result1.expectedUp).contentToString()}")
    println("expectedNormal (验证): ${result1.expectedNormal.contentToString()}")
    println("-------------------------------------------------------------------\n")

    println("--- 测试案例 2: currentScreenNormal 沿 X 轴，无陀螺仪偏置 ---")
    val currentScreenNormal2 = floatArrayOf(1f, 0f, 0f) // X 轴
    val expectedGyroRight2 = 0f
    val expectedGyroUp2 = 0f

    val result2 = calculateExpectedVectors(currentScreenNormal2, expectedGyroRight2, expectedGyroUp2)
    println("currentScreenNormal: ${currentScreenNormal2.contentToString()}")
    println("expectedGyroRight: $expectedGyroRight2, expectedGyroUp: $expectedGyroUp2")
    println("expectedRight: ${result2.expectedRight.contentToString()}")
    println("expectedUp: ${result2.expectedUp.contentToString()}")
    println("expectedNormal: ${result2.expectedNormal.contentToString()}")
    println("--- 验证 ---")
    println("expectedRight . currentScreenNormal: ${result2.expectedRight.dot(currentScreenNormal2)}")
    println("expectedUp . currentScreenNormal: ${result2.expectedUp.dot(currentScreenNormal2)}")
    println("expectedNormal . currentScreenNormal: ${result2.expectedNormal.dot(currentScreenNormal2)}")
    println("expectedRight x expectedUp: ${result2.expectedRight.cross(result2.expectedUp).contentToString()}")
    println("expectedNormal (验证): ${result2.expectedNormal.contentToString()}")
    println("-------------------------------------------------------------------\n")

    println("--- 测试案例 3: currentScreenNormal 任意方向，较大的陀螺仪偏置 (可能触发缩放) ---")
    val currentScreenNormal3 = floatArrayOf(0.5f, 0.5f, 0.707f) // 大致单位向量
    val expectedGyroRight3 = 0.6f
    val expectedGyroUp3 = 0.7f

    val result3 = calculateExpectedVectors(currentScreenNormal3, expectedGyroRight3, expectedGyroUp3)
    println("currentScreenNormal: ${currentScreenNormal3.contentToString()}")
    println("expectedGyroRight: $expectedGyroRight3, expectedGyroUp: $expectedGyroUp3")
    println("expectedRight: ${result3.expectedRight.contentToString()}")
    println("expectedUp: ${result3.expectedUp.contentToString()}")
    println("expectedNormal: ${result3.expectedNormal.contentToString()}")
    println("--- 验证 ---")
    println("expectedRight . currentScreenNormal: ${result3.expectedRight.dot(currentScreenNormal3)}")
    println("expectedUp . currentScreenNormal: ${result3.expectedUp.dot(currentScreenNormal3)}")
    println("expectedNormal . currentScreenNormal: ${result3.expectedNormal.dot(currentScreenNormal3)}")
    println("expectedRight x expectedUp: ${result3.expectedRight.cross(result3.expectedUp).contentToString()}")
    println("expectedNormal (验证): ${result3.expectedNormal.contentToString()}")
    println("-------------------------------------------------------------------\n")

    println("--- 测试案例 4: currentScreenNormal 与目标向量完全反向 (180度旋转特殊情况) ---")
    // 为了触发180度旋转，需要让 A_unit 和 B_unit 几乎反向。
    // 这意味着 A_target_in_RUN_frame 应该与 currentScreenNormal 几乎反向。
    // 我们可以通过设置 expectedGyroRight 和 expectedGyroUp 为 0，并让 currentScreenNormal 的 Z 分量与 CN 反向。
    val currentScreenNormal4 = floatArrayOf(0f, 0f, -1f) // 实际方向是 -Z
    val expectedGyroRight4 = 0f
    val expectedGyroUp4 = 0f // 导致 calculated_CN 会是正的，即 [0,0,1]
    // 那么 A_unit = [0,0,1]，B_unit = [0,0,-1]
    // 这将导致 A_unit 和 B_unit 反向，触发 180 度旋转。

    val result4 = calculateExpectedVectors(currentScreenNormal4, expectedGyroRight4, expectedGyroUp4)
    println("currentScreenNormal: ${currentScreenNormal4.contentToString()}")
    println("expectedGyroRight: $expectedGyroRight4, expectedGyroUp: $expectedGyroUp4")
    println("expectedRight: ${result4.expectedRight.contentToString()}")
    println("expectedUp: ${result4.expectedUp.contentToString()}")
    println("expectedNormal: ${result4.expectedNormal.contentToString()}")
    println("--- 验证 ---")
    println("expectedRight . currentScreenNormal: ${result4.expectedRight.dot(currentScreenNormal4)}")
    println("expectedUp . currentScreenNormal: ${result4.expectedUp.dot(currentScreenNormal4)}")
    println("expectedNormal . currentScreenNormal: ${result4.expectedNormal.dot(currentScreenNormal4)}")
    println("expectedRight x expectedUp: ${result4.expectedRight.cross(result4.expectedUp).contentToString()}")
    println("expectedNormal (验证): ${result4.expectedNormal.contentToString()}")
    println("-------------------------------------------------------------------\n")
}
