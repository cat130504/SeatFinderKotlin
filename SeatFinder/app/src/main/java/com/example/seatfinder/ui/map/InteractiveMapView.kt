package com.example.seatfinder.ui.map

import android.content.Context
import android.graphics.*
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.Log
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import com.example.seatfinder.R
import com.example.seatfinder.data.remote.dto.toDomain
import com.example.seatfinder.data.repository.TavoloRepository
import com.example.seatfinder.domain.model.Tavolo
import kotlinx.coroutines.*

//View Android classica della mappa: disegna la planimetria e sopra i tavoli come pallini (zoom basso) o icone (zoom alto)

class InteractiveMapView(context: Context, attrs: AttributeSet? = null) : View(context, attrs) {

    //Planimetria corrente (cambia quando si cambia edificio/piano)
    private var planimetria: Bitmap? = BitmapFactory.decodeResource(resources, R.drawable.q155)

    private val drawMatrix = Matrix()

    //Icone per i tre stati del tavolo
    private var drawableLibero: Drawable? = null
    private var drawableOccupato: Drawable? = null
    private var drawablePieno: Drawable? = null

    //Limiti zoom: sotto minScale non si va (calcolato per riempire la view)
    private var minScale = 1.0f
    private val maxScale = 5.0f

    //Ultime coordinate del tocco (usate per il pan)
    private var lastTouchX = 0f
    private var lastTouchY = 0f

    //Tavoli da disegnare
    private val listaTavoli = mutableListOf<Tavolo>()

    //Bitmap già scalate (per non fare toBitmap ad ogni frame)
    private var iconaLibera: Bitmap? = null
    private var iconaPiena: Bitmap? = null
    private var iconaOccupata: Bitmap? = null

    //Quota corrente
    private var pianoCorrente: String? = null

    //Paint riusato per i pallini (evita allocazioni in onDraw)
    private val dotPaint = Paint().apply { isAntiAlias = true }

    //Scope per il caricamento asincrono dei tavoli
    private val viewScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    //Callback verso MapScreen quando l'utente tappa un tavolo
    var onTableClick: ((Tavolo) -> Unit)? = null

    init {
        drawableLibero = ContextCompat.getDrawable(context, R.drawable.tavolo_libero)
        drawableOccupato = ContextCompat.getDrawable(context, R.drawable.tavolo_occupato)
        drawablePieno = ContextCompat.getDrawable(context, R.drawable.tavolo_pieno)
        aggiornaIconeScalate()
    }

    //Dimensione del pallino/icona a schermo. indipendente dallo zoom
    private val ICON_SIZE_DP = 24f

    private fun dpToPx(dp: Float): Float {
        return dp * resources.displayMetrics.density
    }

    //Le icone tavolo vengono scalate in base alla larghezza della planimetria
    private val ICON_WIDTH_PERCENT = 0.025f
    private fun aggiornaIconeScalate() {
        val bitmap = planimetria ?: return
        val targetSize = (bitmap.width * ICON_WIDTH_PERCENT).toInt().coerceAtLeast(24)

        iconaLibera = drawableLibero?.toBitmap(targetSize, targetSize)
        iconaOccupata = drawableOccupato?.toBitmap(targetSize, targetSize)
        iconaPiena = drawablePieno?.toBitmap(targetSize, targetSize)
    }

    //Evita di ricaricare la stessa planimetria due volte di fila
    private var currentDrawableRes: Int = 0

    //Cambia planimetria (chiamato da MapScreen quando cambia edificio/piano)
    fun cambiaImmagine(drawableRes: Int) {
        if (currentDrawableRes == drawableRes && planimetria != null) return

        currentDrawableRes = drawableRes
        planimetria = BitmapFactory.decodeResource(resources, drawableRes)
        aggiornaIconeScalate()
        if (width > 0 && height > 0) {
            onSizeChanged(width, height, width, height)
        }
        invalidate()
    }

    //Carica i tavoli dal repository per la quota indicata
    //force=false evita ricarichi inutili se la quota è la stessa e ho già dati
    //force=true (usato dal refresh periodico)
    fun caricaTavoli(quota: String? = null, force: Boolean = false) {
        if (!force && pianoCorrente == quota && listaTavoli.isNotEmpty()) return

        pianoCorrente = quota

        viewScope.launch {
            try {
                val result = TavoloRepository.getTavoli(quota)
                listaTavoli.clear()
                listaTavoli.addAll(result.map { it.toDomain() })
                invalidate()
            } catch (e: Exception) {
                Log.e("SUPABASE", "Errore caricamento: ${e.message}")
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        viewScope.cancel()
    }

    //Pinch zoom con due dita. Il fattore di scala viene bloccato fra minScale e maxScale
    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val values = FloatArray(9)
            drawMatrix.getValues(values)
            val currentScale = values[Matrix.MSCALE_X]
            val targetScale = currentScale * detector.scaleFactor
            val finalScale = when {
                targetScale < minScale -> minScale / currentScale
                targetScale > maxScale -> maxScale / currentScale
                else -> detector.scaleFactor
            }
            //Lo zoom è ancorato al punto fra le due dita (focusX/focusY)
            drawMatrix.postScale(finalScale, finalScale, detector.focusX, detector.focusY)
            invalidate()
            return true
        }
    })

    //Al primo layout: calcola minScale per far stare la planimetria nella view e la centra orizzontalmente e verticalmente
    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        planimetria?.let { bitmap ->
            val scaleX = w.toFloat() / bitmap.width
            val scaleY = h.toFloat() / bitmap.height
            minScale = maxOf(scaleX, scaleY)
            drawMatrix.reset()
            drawMatrix.postScale(minScale, minScale)
            val dx = (w - bitmap.width * minScale) / 2f
            val dy = (h - bitmap.height * minScale) / 2f
            drawMatrix.postTranslate(dx, dy)
            invalidate()
        }
    }

    //Disegno: prima la planimetria trasformata, poi i tavoli
    //Sotto una certa soglia di zoom uso pallini colorati sopra uso le icone vere  ruotate secondo il tavolo
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val bitmapPlanimetria = planimetria ?: return
        canvas.drawBitmap(bitmapPlanimetria, drawMatrix, null)

        val values = FloatArray(9)
        drawMatrix.getValues(values)
        val currentScale = values[Matrix.MSCALE_X]
        val pts = FloatArray(2)

        val bWidth = bitmapPlanimetria.width.toFloat()
        val bHeight = bitmapPlanimetria.height.toFloat()

        val targetScreenPx = dpToPx(ICON_SIZE_DP)

        for (tavolo in listaTavoli) {
            //Percentuale → pixel nella planimetria originale
            pts[0] = (tavolo.x / 100f) * bWidth
            pts[1] = (tavolo.y / 100f) * bHeight

            drawMatrix.mapPoints(pts)

            if (currentScale > 1.2f) {
                //Zoom alto: icone bitmap
                val bitmap = when {
                    tavolo.isPieno -> iconaPiena
                    tavolo.isLibero -> iconaLibera
                    else -> iconaOccupata
                }

                bitmap?.let {
                    val scaleIcon = targetScreenPx / it.width.toFloat()

                    canvas.save()
                    canvas.translate(pts[0], pts[1])
                    canvas.rotate(tavolo.rotazione)
                    canvas.scale(scaleIcon, scaleIcon)
                    //Disegna centrato sull'origine (che è il centro del tavolo)
                    canvas.drawBitmap(it, -it.width / 2f, -it.height / 2f, null)
                    canvas.restore()
                }
            } else {
                //Zoom basso: pallini colorati (verde/arancio/rosso)
                dotPaint.color = when {
                    tavolo.isPieno -> Color.RED
                    tavolo.isLibero -> Color.GREEN
                    else -> Color.rgb(255, 153, 0)
                }
                canvas.drawCircle(pts[0], pts[1], dpToPx(6f), dotPaint)
            }
        }
    }

    //Gestione gesti
    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        val x = event.x
        val y = event.y

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = x
                lastTouchY = y
            }
            MotionEvent.ACTION_MOVE -> {
                if (!scaleDetector.isInProgress && event.pointerCount == 1) {
                    drawMatrix.postTranslate(x - lastTouchX, y - lastTouchY)
                    limitBounds()
                    invalidate()
                }
                lastTouchX = x
                lastTouchY = y
            }
            MotionEvent.ACTION_POINTER_UP -> {
                //Quando sollevi un dito, riparti dall'altro: altrimenti il pan "salta"
                val idx = if (event.actionIndex == 0) 1 else 0
                lastTouchX = event.getX(idx)
                lastTouchY = event.getY(idx)
            }
            MotionEvent.ACTION_UP -> {
                if (!scaleDetector.isInProgress) detectTableClick(x, y)
                limitBounds()
                invalidate()
                performClick()
            }
        }
        return true
    }

    //Tap detection sui tavoli
    private fun detectTableClick(touchX: Float, touchY: Float) {
        val bitmapPlanimetria = planimetria ?: return
        val bWidth = bitmapPlanimetria.width.toFloat()
        val bHeight = bitmapPlanimetria.height.toFloat()

        val inverse = Matrix()
        drawMatrix.invert(inverse)
        val coords = floatArrayOf(touchX, touchY)
        inverse.mapPoints(coords)

        //Tolleranza: 30% della larghezza icona
        val touchRadius = (bWidth * ICON_WIDTH_PERCENT) * 0.30f
        for (tavolo in listaTavoli) {
            val tavoloPixelX = (tavolo.x / 100f) * bWidth
            val tavoloPixelY = (tavolo.y / 100f) * bHeight
            val dx = coords[0] - tavoloPixelX
            val dy = coords[1] - tavoloPixelY
            if (kotlin.math.sqrt(dx * dx + dy * dy) < touchRadius) {
                onTableClick?.invoke(tavolo)
                break
            }
        }
    }

    //Corregge la Matrix quando pan/zoom porterebbero la planimetria fuori dai bordi
    private fun limitBounds() {
        val values = FloatArray(9)
        drawMatrix.getValues(values)
        val transX = values[Matrix.MTRANS_X]
        val transY = values[Matrix.MTRANS_Y]
        val scale = values[Matrix.MSCALE_X]
        val bitmapW = (planimetria?.width ?: 0) * scale
        val bitmapH = (planimetria?.height ?: 0) * scale
        val viewW = width.toFloat()
        val viewH = height.toFloat()

        val fixX = when {
            bitmapW > viewW -> when {
                transX > 0 -> -transX
                transX < viewW - bitmapW -> viewW - bitmapW - transX
                else -> 0f
            }
            else -> (viewW - bitmapW) / 2f - transX
        }

        val fixY = when {
            bitmapH > viewH -> when {
                transY > 0 -> -transY
                transY < viewH - bitmapH -> viewH - bitmapH - transY
                else -> 0f
            }
            else -> (viewH - bitmapH) / 2f - transY
        }

        drawMatrix.postTranslate(fixX, fixY)
    }

    override fun performClick() = super.performClick()
}