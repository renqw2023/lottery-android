class ResultDetailFragment : Fragment() {
    private lateinit var binding: FragmentResultDetailBinding
    private val viewModel: ResultDetailViewModel by viewModels()
    private lateinit var chartManager: AdvancedChartManager

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentResultDetailBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // 获取传入的开奖结果
        arguments?.getLong("drawTime")?.let { drawTime ->
            viewModel.loadResult(drawTime)
        }
        
        setupViews()
        observeData()
    }

    private fun setupViews() {
        binding.apply {
            // 设置工具栏
            toolbar.setNavigationOnClickListener {
                findNavController().navigateUp()
            }
            
            // 设置图表切换
            chartTypeChips.setOnCheckedChangeListener { group, checkedId ->
                when (checkedId) {
                    R.id.chip_history -> showHistoryAnalysis()
                    R.id.chip_pattern -> showPatternAnalysis()
                    R.id.chip_prediction -> showPredictionAnalysis()
                }
            }
            
            // 设置属性展开面板
            attributeExpandButton.setOnClickListener {
                toggleAttributePanel()
            }
            
            // 设置相关号码列表
            relatedNumbersList.apply {
                layoutManager = LinearLayoutManager(context)
                adapter = RelatedNumbersAdapter()
            }
        }
    }

    private fun observeData() {
        // 观察开奖结果
        viewModel.result.observe(viewLifecycleOwner) { result ->
            updateResultInfo(result)
        }
        
        // 观察历史分析
        viewModel.historyAnalysis.observe(viewLifecycleOwner) { analysis ->
            updateHistoryAnalysis(analysis)
        }
        
        // 观察规律分析
        viewModel.patternAnalysis.observe(viewLifecycleOwner) { analysis ->
            updatePatternAnalysis(analysis)
        }
        
        // 观察预测分析
        viewModel.predictionAnalysis.observe(viewLifecycleOwner) { analysis ->
            updatePredictionAnalysis(analysis)
        }
    }

    private fun updateResultInfo(result: LotteryResult) {
        binding.apply {
            // 更新基本信息
            drawDateText.text = formatDate(result.drawTime)
            typeText.text = when(result.type) {
                LotteryType.MACAU -> "澳门"
                LotteryType.HONGKONG -> "香港"
            }
            
            // 更新号码列表
            numbersList.removeAllViews()
            result.numbers.forEach { number ->
                addNumberView(number, false)
            }
            addNumberView(result.specialNumber, true)
            
            // 更新属性信息
            updateAttributes(result)
            
            // 更新统计信息
            updateStatistics(result)
        }
    }

    private fun updateAttributes(result: LotteryResult) {
        binding.attributesLayout.apply {
            // 生肖属性
            zodiacText.text = result.zodiac
            zodiacImage.setImageResource(getZodiacImage(result.zodiac))
            
            // 五行属性
            elementText.text = result.element
            elementImage.setImageResource(getElementImage(result.element))
            
            // 其他属性
            setupAttributeChips(result.attributes)
        }
    }

    private fun setupAttributeChips(attributes: List<String>) {
        binding.attributesLayout.attributeChipGroup.apply {
            removeAllViews()
            attributes.forEach { attribute ->
                addView(createAttributeChip(attribute))
            }
        }
    }

    private fun updateStatistics(result: LotteryResult) {
        binding.statisticsLayout.apply {
            // 基础统计
            sumText.text = result.numbers.sum().toString()
            val oddCount = result.numbers.count { it % 2 == 1 }
            oddEvenText.text = "$oddCount:${6 - oddCount}"
            val bigCount = result.numbers.count { it > 24 }
            bigSmallText.text = "$bigCount:${6 - bigCount}"
            
            // 高级统计
            val consecutive = findConsecutiveNumbers(result.numbers)
            consecutiveText.text = consecutive.joinToString { it.joinToString(",") }
            
            val intervals = findIntervalNumbers(result.numbers)
            intervalText.text = intervals.joinToString { "${it.first}-${it.second}" }
            
            distanceText.text = calculateDistance(result.numbers).toString()
        }
    }

    private fun showHistoryAnalysis() {
        binding.analysisContainer.apply {
            // 显示历史走势图
            trendChart.visibility = View.VISIBLE
            patternChart.visibility = View.GONE
            predictionChart.visibility = View.GONE
            
            viewModel.historyAnalysis.value?.let { analysis ->
                setupTrendChart(analysis)
            }
        }
    }

    private fun showPatternAnalysis() {
        binding.analysisContainer.apply {
            // 显示规律分析图
            trendChart.visibility = View.GONE
            patternChart.visibility = View.VISIBLE
            predictionChart.visibility = View.GONE
            
            viewModel.patternAnalysis.value?.let { analysis ->
                setupPatternChart(analysis)
            }
        }
    }

    private fun showPredictionAnalysis() {
        binding.analysisContainer.apply {
            // 显示预测分析图
            trendChart.visibility = View.GONE
            patternChart.visibility = View.GONE
            predictionChart.visibility = View.VISIBLE
            
            viewModel.predictionAnalysis.value?.let { analysis ->
                setupPredictionChart(analysis)
            }
        }
    }

    private fun toggleAttributePanel() {
        binding.attributesLayout.root.apply {
            if (visibility == View.VISIBLE) {
                visibility = View.GONE
                binding.attributeExpandButton.setImageResource(R.drawable.ic_expand_more)
            } else {
                visibility = View.VISIBLE
                binding.attributeExpandButton.setImageResource(R.drawable.ic_expand_less)
            }
        }
    }

    companion object {
        fun newInstance(drawTime: Long) = ResultDetailFragment().apply {
            arguments = Bundle().apply {
                putLong("drawTime", drawTime)
            }
        }
    }
} 