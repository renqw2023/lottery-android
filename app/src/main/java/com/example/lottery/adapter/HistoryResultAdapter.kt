class HistoryResultAdapter : ListAdapter<LotteryResult, HistoryResultAdapter.ViewHolder>(DiffCallback()) {

    private var onItemClickListener: ((LotteryResult) -> Unit)? = null
    private var onAnalyzeClickListener: ((LotteryResult) -> Unit)? = null

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemHistoryResultBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class ViewHolder(
        private val binding: ItemHistoryResultBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.root.setOnClickListener {
                onItemClickListener?.invoke(getItem(adapterPosition))
            }
            
            binding.analyzeButton.setOnClickListener {
                onAnalyzeClickListener?.invoke(getItem(adapterPosition))
            }
        }

        fun bind(result: LotteryResult) {
            binding.apply {
                // 设置开奖日期
                drawDateText.text = formatDate(result.drawTime)
                
                // 设置开奖号码
                numbersList.removeAllViews()
                result.numbers.forEach { number ->
                    addNumberView(number, false)
                }
                addNumberView(result.specialNumber, true)
                
                // 设置属性信息
                attributesChipGroup.removeAllViews()
                setupAttributeChips(result)
                
                // 设置统计信息
                setupStatistics(result)
            }
        }

        private fun addNumberView(number: Int, isSpecial: Boolean) {
            val numberView = NumberBallView(itemView.context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    resources.getDimensionPixelSize(R.dimen.ball_size),
                    resources.getDimensionPixelSize(R.dimen.ball_size)
                ).apply {
                    marginStart = resources.getDimensionPixelSize(R.dimen.ball_margin)
                }
                setNumber(number)
                setSpecial(isSpecial)
            }
            binding.numbersList.addView(numberView)
        }

        private fun setupAttributeChips(result: LotteryResult) {
            // 添加生肖属性
            addAttributeChip(result.zodiac, R.color.zodiac_color)
            
            // 添加五行属性
            addAttributeChip(result.element, R.color.element_color)
            
            // 添加其他属性
            result.attributes.forEach { attribute ->
                addAttributeChip(attribute, getAttributeColor(attribute))
            }
        }

        private fun addAttributeChip(text: String, @ColorRes colorRes: Int) {
            val chip = Chip(itemView.context).apply {
                this.text = text
                isCheckable = false
                chipBackgroundColor = ColorStateList.valueOf(
                    ContextCompat.getColor(context, colorRes)
                )
                setTextColor(Color.WHITE)
            }
            binding.attributesChipGroup.addView(chip)
        }

        private fun setupStatistics(result: LotteryResult) {
            binding.apply {
                // 设置和值
                sumText.text = result.numbers.sum().toString()
                
                // 设置奇偶比
                val oddCount = result.numbers.count { it % 2 == 1 }
                oddEvenText.text = "$oddCount:${6 - oddCount}"
                
                // 设置大小比
                val bigCount = result.numbers.count { it > 24 }
                bigSmallText.text = "$bigCount:${6 - bigCount}"
                
                // 设置距离值
                distanceText.text = calculateDistance(result.numbers).toString()
            }
        }

        private fun calculateDistance(numbers: List<Int>): Int {
            return numbers.zipWithNext { a, b -> abs(b - a) }.sum()
        }

        @ColorRes
        private fun getAttributeColor(attribute: String): Int {
            return when (attribute) {
                in listOf("单", "双") -> R.color.odd_even_color
                in listOf("大", "小") -> R.color.big_small_color
                in listOf("金", "木", "水", "火", "土") -> R.color.element_color
                in listOf("东", "南", "西", "北") -> R.color.direction_color
                else -> R.color.default_attribute_color
            }
        }
    }

    fun setOnItemClickListener(listener: (LotteryResult) -> Unit) {
        onItemClickListener = listener
    }

    fun setOnAnalyzeClickListener(listener: (LotteryResult) -> Unit) {
        onAnalyzeClickListener = listener
    }

    private class DiffCallback : DiffUtil.ItemCallback<LotteryResult>() {
        override fun areItemsTheSame(oldItem: LotteryResult, newItem: LotteryResult): Boolean {
            return oldItem.drawTime == newItem.drawTime
        }

        override fun areContentsTheSame(oldItem: LotteryResult, newItem: LotteryResult): Boolean {
            return oldItem == newItem
        }
    }
} 