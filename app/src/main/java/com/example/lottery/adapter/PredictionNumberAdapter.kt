class PredictionNumberAdapter : 
    RecyclerView.Adapter<PredictionNumberAdapter.NumberViewHolder>() {
    
    private var numbers: List<LotteryNumber> = emptyList()
    
    fun submitList(newNumbers: List<LotteryNumber>) {
        numbers = newNumbers
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NumberViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.number_ball, parent, false)
        return NumberViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: NumberViewHolder, position: Int) {
        holder.bind(numbers[position])
    }
    
    override fun getItemCount() = numbers.size
    
    class NumberViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val numberText: TextView = itemView.findViewById(R.id.numberText)
        
        fun bind(lotteryNumber: LotteryNumber) {
            numberText.text = lotteryNumber.number.toString()
            numberText.background = when(lotteryNumber.color) {
                Color.RED -> ContextCompat.getDrawable(
                    itemView.context, R.drawable.ball_background_red)
                Color.BLUE -> ContextCompat.getDrawable(
                    itemView.context, R.drawable.ball_background_blue)
                Color.GREEN -> ContextCompat.getDrawable(
                    itemView.context, R.drawable.ball_background_green)
            }
        }
    }
} 