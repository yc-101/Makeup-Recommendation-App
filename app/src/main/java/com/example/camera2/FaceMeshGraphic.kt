/*
 * Copyright 2022 Google LLC. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.camera2

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import com.google.mlkit.vision.common.PointF3D
import com.example.camera2.GraphicOverlay
import com.google.mlkit.vision.facemesh.FaceMesh
import com.google.mlkit.vision.facemesh.FaceMeshDetectorOptions
import com.google.mlkit.vision.facemesh.FaceMeshPoint
import kotlin.math.*

/**
 * Graphic instance for rendering face position and mesh info within the associated graphic overlay
 * view.
 */
class FaceMeshGraphic(overlay: GraphicOverlay?, private val faceMesh: FaceMesh) :
	GraphicOverlay.Graphic(overlay) {

	private val positionPaint: Paint
	private var boxPaint: Paint
	private val useCase: Int
	private var zMin: Float
	private var zMax: Float
	var canDetect: Boolean
	private var particularPoints: List<FaceMeshPoint>

	/** Draws the face annotations for position on the supplied canvas. */
	override fun draw(canvas: Canvas) {
		// Draws the bounding box. 臉的框框
		val rect = RectF(faceMesh.boundingBox)
		// If the image is flipped, the left will be translated to right, and the right to left.
		val x0 = translateX(rect.left)
		val x1 = translateX(rect.right)
		rect.left = x0.coerceAtMost(x1) // 取min
		rect.right = x0.coerceAtLeast(x1) // 取max
		rect.top = translateY(rect.top)
		rect.bottom = translateY(rect.bottom)
		canDetect = faceIsSkewed(particularPoints) /** 要先判斷臉的歪斜程度，再決定要畫 紅/黃/綠 色 **/
		canvas.drawRect(rect, boxPaint)

		// Draw face mesh
		val points =
			if (useCase == USE_CASE_CONTOUR_ONLY) getContourPoints(faceMesh) else faceMesh.allPoints
		val triangles = faceMesh.allTriangles

		zMin = Float.MAX_VALUE
		zMax = Float.MIN_VALUE
		for (point in points) {
			zMin = zMin.coerceAtMost(point.position.z)  // 取min
			zMax = zMax.coerceAtLeast(point.position.z) // 取max
		}

		// Draw face mesh points
		for (point in points) {
//          Log.d("test", "index: ${point.index}")
			updatePaintColorByZValue(
				positionPaint,
				canvas,
				/* visualizeZ = */true,
				/* rescaleZForVisualization = */true,
				point.position.z,
				zMin,
				zMax)
			canvas.drawCircle(
				translateX(point.position.x),
				translateY(point.position.y),
				FACE_POSITION_RADIUS,
				positionPaint
			)
		}

		/** particular points to get face measure */
		for(point in particularPoints) {
			canvas.drawCircle(
				translateX(point.position.x),
				translateY(point.position.y),
				FACE_POSITION_RADIUS*3,
				positionPaint
			)
			canvas.drawText("${point.index}",
				translateX(point.position.x)+2,
				translateY(point.position.y),
				positionPaint)
		}

		if (useCase == FaceMeshDetectorOptions.FACE_MESH) {
			// Draw face mesh triangles
			for (triangle in triangles) {
				val point1 = triangle.allPoints[0].position
				val point2 = triangle.allPoints[1].position
				val point3 = triangle.allPoints[2].position
				drawLine(canvas, point1, point2)
				drawLine(canvas, point1, point3)
				drawLine(canvas, point2, point3)
			}
		}
	}

	fun getFaceShapeResult() :String {
		if (!faceIsSkewed(particularPoints)) {
			Log.d("test", "把臉擺正一點唷")
			return "把臉擺正一點唷!"
		}
		/** 判斷臉型 */
		val faceLength  = getDistance(particularPoints[0].position, particularPoints[1].position)
		val forehead    = getDistance(particularPoints[2].position, particularPoints[3].position)
		val cheekbones  = getDistance(particularPoints[4].position, particularPoints[5].position)
		val jaw         = getDistance(particularPoints[6].position, particularPoints[7].position)
		var longestPart = max(max(forehead, cheekbones), jaw)

		var result = ""

		// 1. 找出臉最寬的位置 ( 1前額 2頰骨 3下顎 )
		result += when(longestPart) {
			forehead -> '1'
			cheekbones -> '2'
			else -> '3'
		}

		// 2. 比較臉部長寬比 ( 1長>寬 2長=寬 )
		result += if (faceLength / longestPart > 1.1) '1' else '2' //TODO: 差距要大多少?

		// 3. 比較前額與下頜寬度 ( 1前額=下頜 2前額>下頜 3前額<下頜 )
		result += if (forehead == jaw) '1' else if (forehead > jaw) '2' else '3'

		// 4. 找出下巴形狀 ( 1尖 2方 3圓 )
		val up = getDistance(particularPoints[8].position, particularPoints[9].position)
		val left = getDistance(particularPoints[1].position, particularPoints[8].position)
		val right = getDistance(particularPoints[1].position, particularPoints[9].position)
		val cosValue = (left.pow(2) + right.pow(2) - up.pow(2)) / (2 * left * right)
		result += if (cosValue > 0) '1' else if (cosValue > -1) '3' else '2' //TODO: 差距要大多少?

		// 判斷臉型
		var value = "你是"
		value +=
			if (result == "2111") "菱形臉" // 也是長形
			else if (result == "1221" || result == "1222") "心形臉"
			// "1***" or "3***"
			else if (result[0] == '1' || result[0] == '3') {
				if (result[1] == '1' || (result[2] == '1' && result[3] == '1')) "長形臉"
				else "方形臉"
			}
			// "2***"
			else if (result[1] == '1') "長形臉"
			else if (result[2] == '3' || (result[2] == '1' && result[3] == '2')) "方形臉"
			else if (result[3] == '3') "圓形臉"
			else "橢圓形臉"
		Log.d("test", value)
		return value
	}

	private fun getDistance(point1: PointF3D, point2: PointF3D) :Float {
		return sqrt((point1.x - point2.x).pow(2) + (point1.y - point2.y).pow(2))
	}

	private fun drawLine(canvas: Canvas, point1: PointF3D, point2: PointF3D) {
		updatePaintColorByZValue(
			positionPaint,
			canvas,
			/* visualizeZ= */true,
			/* rescaleZForVisualization= */true,
			(point1.z + point2.z) / 2,
			zMin,
			zMax)
		canvas.drawLine(
			translateX(point1.x),
			translateY(point1.y),
			translateX(point2.x),
			translateY(point2.y),
			positionPaint
		)
	}

	private fun getContourPoints(faceMesh: FaceMesh): List<FaceMeshPoint> {
		val contourPoints: MutableList<FaceMeshPoint> = ArrayList()
		for (type in DISPLAY_CONTOURS) {
			contourPoints.addAll(faceMesh.getPoints(type))
		}
		return contourPoints
	}

	/** 若臉部太歪斜，要求把臉擺好 (Bounding Box變成黃色) **/
	private fun faceIsSkewed(points: List<FaceMeshPoint>): Boolean {
		// list陣列: 兩兩一對，分別是左右邊
		val skew = kotlin.math.max(
			abs(points[0].position.z - points[1].position.z + 55),
			abs(points[2].position.z - points[3].position.z)
		)
		boxPaint.color =
			if (skew <= 30 ) Color.GREEN
			else if (skew <= 100) Color.YELLOW
			else Color.RED
//		Log.d("test","上下: ${points[0].position.z - points[1].position.z + 55}, 左右: ${points[2].position.z - points[3].position.z}")

		return (skew <= 30)
	}

	companion object {
		private const val USE_CASE_CONTOUR_ONLY = 999
		private const val FACE_POSITION_RADIUS = 2.0f // 畫網格的點粗細
		private const val BOX_STROKE_WIDTH = 5.0f

		private val DISPLAY_CONTOURS =
			intArrayOf(
				FaceMesh.FACE_OVAL,
				FaceMesh.LEFT_EYEBROW_TOP,
				FaceMesh.LEFT_EYEBROW_BOTTOM,
				FaceMesh.RIGHT_EYEBROW_TOP,
				FaceMesh.RIGHT_EYEBROW_BOTTOM,
				FaceMesh.LEFT_EYE,
				FaceMesh.RIGHT_EYE,
				FaceMesh.UPPER_LIP_TOP,
				FaceMesh.UPPER_LIP_BOTTOM,
				FaceMesh.LOWER_LIP_TOP,
				FaceMesh.LOWER_LIP_BOTTOM,
				FaceMesh.NOSE_BRIDGE
			)
	}

	init {
		val selectedColor = Color.WHITE
		positionPaint = Paint()
		positionPaint.color = selectedColor

		boxPaint = Paint()
		boxPaint.color = selectedColor
		boxPaint.style = Paint.Style.STROKE
		boxPaint.strokeWidth = BOX_STROKE_WIDTH

		useCase =
			FaceMeshDetectorOptions.FACE_MESH
		// USE_CASE_CONTOUR_ONLY
		// PreferenceUtils.getFaceMeshUseCase(applicationContext)
		zMin = java.lang.Float.MAX_VALUE
		zMax = java.lang.Float.MIN_VALUE
		canDetect = false
		particularPoints = listOf(
			faceMesh.allPoints[5], faceMesh.allPoints[152],    // 臉中心 底部 (用來算臉長)
			faceMesh.allPoints[54], faceMesh.allPoints[284],  // 額頭
			faceMesh.allPoints[454], faceMesh.allPoints[234], // 顴骨
			faceMesh.allPoints[172], faceMesh.allPoints[397], // 下顎
			faceMesh.allPoints[150], faceMesh.allPoints[379], // 下巴
		)
	}
}
