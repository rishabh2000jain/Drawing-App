package com.example.drawingapp

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.util.TypedValue
import android.view.MotionEvent
import android.view.View
import java.util.*
import kotlin.collections.ArrayList

class DrawingView(context: Context,attr:AttributeSet): View(context,attr) {
    private var mDrawPath:CustomPath?=null
    private var mDrawPaint:Paint?=null
    private var mCanvasPaint:Paint?=null
    private  var mBitmap: Bitmap?= null
    private var brushSize:Float = 1f
    private var originalBrushSize:Float = 1f
    private var brushColor:Int = Color.BLACK

    private val pathList:ArrayList<CustomPath> = ArrayList()
    private val undoStack:Stack<CustomPath> = Stack()

    init {
        setupDrawing()
    }


    private fun setupDrawing(){
        mDrawPaint = Paint()
        mCanvasPaint = Paint(Paint.DITHER_FLAG)
        mDrawPath = CustomPath(brushColor, brushSize)
        mDrawPaint?.style = Paint.Style.STROKE
        mDrawPaint!!.color = brushColor
        setBrushSize(1f)
        mDrawPaint!!.strokeCap = Paint.Cap.ROUND
        mDrawPaint!!.strokeJoin = Paint.Join.ROUND
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        mBitmap = Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
    }

    override fun onDraw(canvas: Canvas?) {
        canvas?.drawBitmap(mBitmap!!,0f,0f,mCanvasPaint)
        for(path in pathList){
            mDrawPaint?.strokeWidth = path.brushSize
            mDrawPaint?.color = path.color
            canvas?.drawPath(path,mDrawPaint!!)
        }

        mDrawPath?.let {
            mDrawPaint?.strokeWidth = it.brushSize
            mDrawPaint?.color = it.color
            canvas?.drawPath(it, mDrawPaint!!)
        }
        super.onDraw(canvas)
    }

    override fun onTouchEvent(event: MotionEvent?): Boolean {
        event?.apply {
            val touchX = x
            val touchY = y
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    mDrawPath?.reset()
                    mDrawPath?.moveTo(
                        touchX,
                        touchY
                    )
                }
                MotionEvent.ACTION_MOVE -> {
                    mDrawPath?.lineTo(
                        touchX,
                        touchY
                    )
                }
                MotionEvent.ACTION_UP -> {
                    pathList.add(mDrawPath!!)
                    mDrawPath = CustomPath(brushColor, brushSize)
                }
                else -> return false
            }
        }
        invalidate()
        return true
    }

    fun undo(){
        if(pathList.isNotEmpty()) {
            undoStack.push(pathList.removeLast())
            invalidate()
        }
    }

    fun redo(){
        if(undoStack.isNotEmpty()){
            pathList.add(undoStack.pop())
            invalidate()
        }
    }

    fun clear(){
        undoStack.clear()
        pathList.clear()
        invalidate()
    }

    fun setBrushSize(size:Float?){
        if(size == null)return
        originalBrushSize = size
        brushSize = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,size,resources.displayMetrics)
        mDrawPath?.brushSize = brushSize
    }

    fun setBrushColor(color: Int?){
        if(color == null)return
        brushColor = color
        mDrawPath?.color = brushColor
    }

    fun pathCount() = pathList.size
    fun redoPathCount() = undoStack.size

    fun getBrushSize():Float = originalBrushSize
    fun getBrushColor():Int = brushColor
    fun bitmapFromView(view:View):Bitmap?{
         try {
             val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
             val canvas = Canvas(bitmap)

             val viewBackground = view.background
             if (viewBackground != null) {
                 viewBackground.draw(canvas)
             } else {
                 canvas.drawColor(Color.WHITE)
             }
             view.draw(canvas)
             return bitmap
         }catch (e:Exception){
             e.printStackTrace()
         }
         return null
    }

    internal inner class CustomPath(var color:Int,var brushSize:Float): Path()
}