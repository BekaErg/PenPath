import android.graphics.*
import kotlin.math.absoluteValue
import kotlin.math.sqrt

class PenPath (var contourType : Type = Type.CIRCLE_SEQUENCE){
    /**
     * Minimum distance between two path points is
     * minGapFactor * radius
     */
    var minGapFactor = 0.1f

    /**
     * Only applicable when contourType is CIRCLE_SEQUENCE
     * If the value is too high, lines look like collection of circles
     * Maximum distance between two consecutive path points is
     * minMaxFactor * radius
     */
    val maxGapFactor = 0.15f

    /**
     * The constructed path
     */
    val contourPath = Path()

    /**
     * Part of contourPath beginning from previous last point
     */
    val lastSegment = Path()

    /**
     * Rectangle bounding path (with 1px margin)
     */
    val boundaryRectF = RectF()

    /**
     * The path thickness depends on direction of the movement
     * It is thickest when moving across to the directionBiasVector
     */
    var directionBiasVector: Pair<Float, Float> = Pair(1f / sqrt(2f), -1f / sqrt(2f))
        set(value) {
            val norm = sqrt(value.first * value.first + value.second * value.second)
            field = if (norm == 0f) {
                Pair(1f, 0f)
            } else {
                Pair(value.first / norm, value.second / norm)
            }
        }

    /**
     * Variation between thickest and thinnest lines due to moving direction
     * (This is independent from pressure level)
     */
    var directionBiasLevel = 0.7f
        set(value) {
            field = value.coerceAtLeast(0f).coerceAtMost(1f)
        }

    /**
     * RealTime smoothing by averaging last inputBufferSize points into one
     * Useful to reduce input jitter and smoothen strokes
     * Values lower than 5 do not have significant disadvantages
     */
    var inputBufferSize = 3
        set(value) {
            field = value.coerceAtLeast(1).coerceAtMost(100)
        }

    private var curX = 0f
    private var curY = 0f
    private var prevX = 0f
    private var prevY = 0f
    private var norm = 1f
    private var mRadius = 0f
    private var mPrevRadius = 0f
    private val pos = floatArrayOf(0f, 0f)

    //private var smoothLevel = 1
    private var mPathMeasure = PathMeasure()
    private var arr = mutableListOf<Triple<Float, Float, Float>>()
    private var inputBuffer : ArrayDeque<Triple<Float, Float, Float>> = ArrayDeque()

    /**
     * similar to Path.moveTo()
     */
    fun moveTo(x : Float, y : Float, radius : Float) {
        moveToImpl(x, y, radius, true)
    }

    /**
     * similar to Path.lineTo()
     */
    fun lineTo(x : Float, y : Float, radius : Float) {
        inputBuffer.addLast(Triple(x, y, radius))
        if (inputBuffer.size > inputBufferSize) inputBuffer.removeFirst()

        //calculate average of all points in buffer and pass it to lineTo implementation
        val (sumX, sumY, sumRad) = inputBuffer.fold(Triple(0f, 0f, 0f)) { acc, value ->
            Triple(
                acc.first + value.first,
                acc.second + value.second,
                acc.third + value.third
            )
        }
        lineToImpl(
            sumX / inputBuffer.size,
            sumY / inputBuffer.size,
            sumRad / inputBuffer.size,
            true
        )
    }

    /**
     * Unloads buffer and adds to the path.
     * Only needed if inputBufferSize > 1
     */
    fun finish() {
        var (sumX, sumY, sumRad) = inputBuffer.fold(Triple(0f, 0f, 0f)) { acc, value ->
            Triple(
                acc.first + value.first,
                acc.second + value.second,
                acc.third + value.third
            )
        }
        while (inputBuffer.size > 1) {
            val (x, y, rad) = inputBuffer.removeFirst()
            sumX -= x
            sumY -= y
            sumRad -= rad
            lineToImpl(
                sumX / inputBuffer.size,
                sumY / inputBuffer.size,
                sumRad / inputBuffer.size,
                true
            )
        }
        inputBuffer.clear()
    }

    /**
     * Draws contour path on the canvas
     * Use fill paint to get normal brush effect
     * use smoothEffect makes drawing of the path feel more responsive
     * Only recommended to turn of smoothEffect when the paint is translucent
     */
    fun draw(canvas : Canvas, paint : Paint) {
        canvas.drawPath(contourPath, paint)
        //for ((x, y, _) in inputBuffer) {
        //    canvas.drawCircle(x, y, mRadius, paint)
        //}

    }

    /**
     * Draws last added segment of the path
     * Can be used to draw infinitely large paths without performance increase
     */
    fun drawLastSegment(canvas : Canvas, paint : Paint) {
        canvas.drawPath(lastSegment, paint)
        lastSegment.rewind()
    }

    /**
     * similar to Path.rewind()
     */
    fun rewind() {
        contourPath.rewind()
        lastSegment.rewind()
        arr.clear()
        mPrevRadius = 0f
        inputBuffer.clear()
    }

    /**
     * reverts path to the starting point
     * useful while drawing a straight line
     */
    fun restart() {
        if (arr.isEmpty()) return
        val (firstX, firstY, firstRad) = arr[0]
        rewind()
        this.moveTo(firstX, firstY, firstRad)
    }

    /**
     * Transforms the path according to the transformation Matrix
     * Does not change thickness of path
     */
    fun transform(matrix : Matrix) {
        if (arr.isEmpty()) return
        val src : FloatArray = arr.flatMap { listOf(it.first, it.second) }.toFloatArray()
        matrix.mapPoints(src)
        src.zip(src.drop(1)).filterIndexed { i, _ ->
            i % 2 == 0
        }.forEachIndexed { i, (x, y) ->
            arr[i] = Triple(x, y, arr[i].third)
        }
        updateContour()
    }


    /**
     * Smooths line by quadratic curves.
     * higher level will smooth more.
     * The resulting path will contain upscaleFactor times more vertices
     * Use-case of upscaleFactor > 1 is when the path is have been enlarged by scaling
     */
    fun quadSmooth(level : Int, upscaleFactor : Int = 1) {
        if (level <= 0) {
            return
        }
        mPrevRadius = arr[0].third
        prevX = arr[0].first
        prevY = arr[0].second
        val newArr = mutableListOf<Triple<Float, Float, Float>>()
        newArr.add(Triple(prevX, prevY, mPrevRadius))
        for (i in 0 until arr.size step level) {
            val (l, r) = if (i + level < arr.size) Pair(i, i + level) else Pair(
                arr.size - 1,
                arr.size - 1
            )

            mRadius = (arr[l].third + arr[r].third) / 2f
            curX = (arr[l].first + arr[r].first) / 2f
            curY = (arr[l].second + arr[r].second) / 2f
            val anchorX = arr[i].first
            val anchorY = arr[i].second

            val curArc = Path()
            curArc.moveTo(prevX, prevY)
            curArc.quadTo(anchorX, anchorY, curX, curY)
            mPathMeasure.setPath(curArc, false)
            subdivide(upscaleFactor * level, mPrevRadius, mRadius, newArr)

            mPrevRadius = mRadius
            prevX = curX
            prevY = curY
        }
        arr = newArr
        updateContour()
    }

    /**
     * If the array was modified, updateContour has to be used in order to
     * obtain new corresponding contour Path
     */
    private fun updateContour() {
        if (arr.isEmpty()) return
        this.moveToImpl(arr[0].first, arr[0].second, arr[0].third, false)
        for ((x, y, rad) in arr.drop(1).dropLast(0)) {
            this.lineToImpl(x, y, rad, false, 0f)
        }
    }

    //subdivide path that is set to mPathMeasure into n parts.
    private fun subdivide(
        n : Int,
        prevRadius : Float,
        nextRadius : Float,
        targetArray : MutableList<Triple<Float, Float, Float>>
    ) {
        for (j in 1 .. n) {
            val d = j * mPathMeasure.length / n
            val radius = prevRadius + (nextRadius - prevRadius) * j / n
            mPathMeasure.getPosTan(d, pos, null)
            targetArray.add(Triple(pos[0], pos[1], radius))
        }
    }

    //helper function for moveTo()
    private fun moveToImpl(x : Float, y : Float, radius : Float, addToArr : Boolean) {
        mRadius = (1f - directionBiasLevel) * radius
        contourPath.rewind()
        contourPath.addCircle(x, y, mRadius, Path.Direction.CCW)
        lastSegment.rewind()
        lastSegment.addCircle(x, y, mRadius, Path.Direction.CCW)

        if (addToArr) {
            arr.add(Triple(x, y, mRadius))
        }
        boundaryRectF.set(RectF(x - mRadius, y - mRadius, x + mRadius, y + mRadius))

        prevX = x
        prevY = y
        mPrevRadius = mRadius
    }

    private fun lineToImpl(x : Float, y : Float, radius : Float, addToArr : Boolean, gapFactor: Float = minGapFactor) {
        norm = sqrt((x - prevX) * (x - prevX) + (y - prevY) * (y - prevY))

        if (norm == 0f) {
            return
        }

        mRadius = if (addToArr) {
            radius * calculateBias((x - prevX) / norm, (y - prevY) / norm)
        } else {
            radius
        }

        if (norm.coerceAtLeast(mRadius - mPrevRadius) < gapFactor * mRadius + 0.1f) {
            return
        }

        if (addToArr) {
            arr.add(Triple(x, y, mRadius))
        }

        curX = x
        curY = y

        lastSegment.rewind()
        extendBoundaryRect(x,y,mRadius)
        extendContour()

        prevX = curX
        prevY = curY
        mPrevRadius = mRadius
    }

    private fun extendBoundaryRect(x: Float, y: Float, radius: Float) {
        boundaryRectF.left = boundaryRectF.left.coerceAtMost(x - radius - 1f)
        boundaryRectF.right = boundaryRectF.right.coerceAtLeast(x + radius + 1f)
        boundaryRectF.top = boundaryRectF.top.coerceAtMost(y - radius - 1f)
        boundaryRectF.bottom = boundaryRectF.bottom.coerceAtLeast(y + radius + 1f)
    }

    //Helper function for lineToImpl
    private fun extendContour() {
        when (contourType) {
            Type.CIRCLE_SEQUENCE -> {
                val n = (
                        norm / (maxGapFactor * mRadius.coerceAtMost(mPrevRadius))
                        ).toInt() + 1
                for (i in 1 .. n) {
                    val x = prevX + (curX - prevX) * i / n
                    val y = prevY + (curY - prevY) * i / n
                    val radius = mPrevRadius + (mRadius - mPrevRadius) * i / n
                    lastSegment.addCircle(x, y, radius, Path.Direction.CCW)
                    contourPath.addCircle(x, y, radius, Path.Direction.CCW)
                }
            }
            Type.JOIN_WITH_TANGENTS -> {
                 //If one circle is inside another, skip the process
                if (norm > (mRadius - mPrevRadius).absoluteValue) {
                    val dx = (curX - prevX) / norm
                    val dy = (curY - prevY) / norm
                    val cosTheta = -(mRadius - mPrevRadius) / norm
                    val sinTheta = sqrt(1 - cosTheta * cosTheta)

                    val leftUnitX = dx * cosTheta + dy * sinTheta
                    val leftUnitY = -dx * sinTheta + dy * cosTheta
                    val rightUnitX = dx * cosTheta - dy * sinTheta
                    val rightUnitY = dx * sinTheta + dy * cosTheta

                    lastSegment.moveTo(prevX + leftUnitX * mPrevRadius, prevY + leftUnitY * mPrevRadius)
                    lastSegment.lineTo(prevX + rightUnitX * mPrevRadius, prevY + rightUnitY * mPrevRadius)
                    lastSegment.lineTo(curX + rightUnitX * mRadius, curY + rightUnitY * mRadius)
                    lastSegment.lineTo(curX + leftUnitX * mRadius, curY + leftUnitY * mRadius)
                    lastSegment.close()
                }

                lastSegment.addCircle(curX, curY, mRadius, Path.Direction.CCW)
                contourPath.addPath(lastSegment)
            }
        }
    }

    private fun calculateBias(x : Float, y : Float) : Float {
        return 0.5f* ((x * directionBiasVector.first + y * directionBiasVector.second) * directionBiasLevel + 2f - directionBiasLevel)
    }

    enum class Type {
        JOIN_WITH_TANGENTS,
        CIRCLE_SEQUENCE,
    }
}
