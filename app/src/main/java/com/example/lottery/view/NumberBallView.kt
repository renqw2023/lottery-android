class NumberBallView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var number: Int = 0
    private var isSpecial: Boolean = false
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val size = resources.getDimensionPixelSize(R.dimen.ball_size)

    init {
        textPaint.apply {
            textSize = resources.getDimensionPixelSize(R.dimen.ball_text_size).toFloat()
            color = Color.WHITE
            textAlign = Paint.Align.CENTER
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(size, size)
    }

    override fun onDraw(canvas: Canvas) {
        val centerX = width / 2f
        val centerY = height / 2f
        val radius = width / 2f

        // 绘制背景圆
        paint.color = if (isSpecial) {
            ContextCompat.getColor(context, R.color.special_ball_color)
        } else {
            ContextCompat.getColor(context, R.color.normal_ball_color)
        }
        canvas.drawCircle(centerX, centerY, radius, paint)

        // 绘制数字
        val textHeight = textPaint.descent() - textPaint.ascent()
        val textOffset = textHeight / 2 - textPaint.descent()
        canvas.drawText(
            number.toString(),
            centerX,
            centerY + textOffset,
            textPaint
        )
    }

    fun setNumber(value: Int) {
        number = value
        invalidate()
    }

    fun setSpecial(special: Boolean) {
        isSpecial = special
        invalidate()
    }
} 