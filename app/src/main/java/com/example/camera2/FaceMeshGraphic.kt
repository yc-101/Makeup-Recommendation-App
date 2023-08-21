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
import kotlin.math.abs

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
	private var particularPoints: List<FaceMeshPoint>

	@FaceMesh.ContourType
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

	/** Draws the face annotations for position on the supplied canvas. */
	override fun draw(canvas: Canvas) {

		// Draws the bounding box. 臉的框框
		val rect = RectF(faceMesh.boundingBox)
		// If the image is flipped, the left will be translated to right, and the right to left.
		val x0 = translateX(rect.left)
		val x1 = translateX(rect.right)
		rect.left = Math.min(x0, x1)
		rect.right = Math.max(x0, x1)
		rect.top = translateY(rect.top)
		rect.bottom = translateY(rect.bottom)
		faceIsSkewed(particularPoints)
		canvas.drawRect(rect, boxPaint) /** 要先判斷臉的歪斜程度，再決定要畫 紅/黃/綠 色 **/

		// Draw face mesh
		val points =
			if (useCase == USE_CASE_CONTOUR_ONLY) getContourPoints(faceMesh) else faceMesh.allPoints
		val triangles = faceMesh.allTriangles

		zMin = Float.MAX_VALUE
		zMax = Float.MIN_VALUE
		for (point in points) {
			zMin = Math.min(zMin, point.position.z)
			zMax = Math.max(zMax, point.position.z)
		}

		// Draw face mesh points
		for ((i, point) in points.withIndex()) {
//          Log.d("test", "index: ${point.index}")
			updatePaintColorByZValue(
				positionPaint,
				canvas,
				/* visualizeZ= */true,
				/* rescaleZForVisualization= */true,
				point.position.z,
				zMin,
				zMax)
			canvas.drawCircle(
				translateX(point.position.x),
				translateY(point.position.y),
				FACE_POSITION_RADIUS,
				positionPaint
			)
//          canvas.drawText("${i}",
//              translateX(point.position.x)+2,
//              translateY(point.position.y),
//              positionPaint)
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
		var skew = kotlin.math.max(
			abs(points[0].position.z - points[1].position.z + 55),
			abs(points[2].position.z - points[3].position.z)
		)
		boxPaint.color =
			if (skew <= 30 ) Color.GREEN
			else if (skew <= 100) Color.YELLOW
			else Color.RED
		Log.d("test","上下: ${points[0].position.z - points[1].position.z + 55}, 左右: ${points[2].position.z - points[3].position.z}")

		return (skew <= 30)
	}

	companion object {
		private const val USE_CASE_CONTOUR_ONLY = 999
		private const val FACE_POSITION_RADIUS = 2.0f // 畫網格的點
		private const val BOX_STROKE_WIDTH = 5.0f
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
		particularPoints = listOf(
			faceMesh.allPoints[5], faceMesh.allPoints[152],    // 臉中心 底部 (用來算臉長)
			faceMesh.allPoints[54], faceMesh.allPoints[284],  // 額頭
			faceMesh.allPoints[454], faceMesh.allPoints[234], // 顴骨
			faceMesh.allPoints[172], faceMesh.allPoints[397], // 下顎
		)
	}
}
