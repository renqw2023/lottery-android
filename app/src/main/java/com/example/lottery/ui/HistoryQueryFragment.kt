class HistoryQueryFragment : Fragment() {
    private lateinit var binding: FragmentHistoryQueryBinding
    private val viewModel: HistoryQueryViewModel by viewModels()
    private lateinit var chartManager: AdvancedChartManager
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentHistoryQueryBinding.inflate(inflater, container, false)
        return binding.root
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        setupDateRangePicker()
        setupNumberFilter()
        setupAttributeFilter()
        setupResultsList()
        setupCharts()
        observeData()
    }
    
    private fun setupDateRangePicker() {
        binding.dateRangeLayout.apply {
            startDatePicker.setOnClickListener {
                showDatePicker(true)
            }
            
            endDatePicker.setOnClickListener {
                showDatePicker(false)
            }
        }
    }
    
    private fun showDatePicker(isStartDate: Boolean) {
        val calendar = Calendar.getInstance()
        DatePickerDialog(
            requireContext(),
            { _, year, month, day ->
                val date = Calendar.getInstance().apply {
                    set(year, month, day)
                }.timeInMillis
                if (isStartDate) {
                    viewModel.setStartDate(date)
                } else {
                    viewModel.setEndDate(date)
                }
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }
    
    private fun setupNumberFilter() {
        binding.numberFilterLayout.apply {
            // 设置号码范围选择器
            numberRangeSlider.addOnChangeListener { slider, _, _ ->
                viewModel.setNumberRange(
                    slider.values[0].toInt(),
                    slider.values[1].toInt()
                )
            }
            
            // 设置特码选择器
            specialNumberChips.forEach { chip ->
                chip.setOnCheckedChangeListener { _, isChecked ->
                    if (isChecked) {
                        viewModel.addSpecialNumber(chip.text.toString().toInt())
                    } else {
                        viewModel.removeSpecialNumber(chip.text.toString().toInt())
                    }
                }
            }
        }
    }
    
    private fun setupAttributeFilter() {
        binding.attributeFilterLayout.apply {
            // 设置生肖选择
            zodiacChipGroup.setOnCheckedChangeListener { group, checkedId ->
                val chip = group.findViewById<Chip>(checkedId)
                chip?.let {
                    viewModel.setSelectedZodiac(it.text.toString())
                }
            }
            
            // 设置五行选择
            elementChipGroup.setOnCheckedChangeListener { group, checkedId ->
                val chip = group.findViewById<Chip>(checkedId)
                chip?.let {
                    viewModel.setSelectedElement(it.text.toString())
                }
            }
            
            // 设置其他属性选择
            setupAttributeChips()
        }
    }
    
    private fun setupAttributeChips() {
        val attributes = listOf(
            "单", "双", "大", "小",
            "金", "木", "水", "火", "土",
            "东", "南", "西", "北"
        )
        
        binding.attributeFilterLayout.attributeFlowLayout.apply {
            attributes.forEach { attribute ->
                addView(createAttributeChip(attribute))
            }
        }
    }
    
    private fun createAttributeChip(text: String): Chip {
        return Chip(requireContext()).apply {
            this.text = text
            isCheckable = true
            setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    viewModel.addAttribute(text)
                } else {
                    viewModel.removeAttribute(text)
                }
            }
        }
    }
    
    private fun setupResultsList() {
        val adapter = HistoryResultAdapter()
        binding.resultsList.apply {
            this.adapter = adapter
            layoutManager = LinearLayoutManager(context)
            addItemDecoration(DividerItemDecoration(context, DividerItemDecoration.VERTICAL))
        }
        
        // 设置分页加载
        binding.resultsList.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                
                val layoutManager = recyclerView.layoutManager as LinearLayoutManager
                val lastVisibleItem = layoutManager.findLastVisibleItemPosition()
                val totalItemCount = layoutManager.itemCount
                
                if (lastVisibleItem >= totalItemCount - 3) {
                    viewModel.loadMoreResults()
                }
            }
        })
    }
    
    private fun setupCharts() {
        chartManager = AdvancedChartManager()
        
        binding.chartsLayout.apply {
            // 设置图表类型选择器
            chartTypeChips.setOnCheckedChangeListener { group, checkedId ->
                when (checkedId) {
                    R.id.chip_trend -> showTrendChart()
                    R.id.chip_distribution -> showDistributionChart()
                    R.id.chip_pattern -> showPatternChart()
                }
            }
            
            // 设置图表交互监听
            setupChartInteraction()
        }
    }
    
    private fun setupChartInteraction() {
        binding.chartsLayout.chart.apply {
            setOnChartValueSelectedListener(object : OnChartValueSelectedListener {
                override fun onValueSelected(e: Entry?, h: Highlight?) {
                    e?.let {
                        showChartDetail(it)
                    }
                }
                
                override fun onNothingSelected() {
                    hideChartDetail()
                }
            })
        }
    }
    
    private fun observeData() {
        // 观察查询结果
        viewModel.queryResults.observe(viewLifecycleOwner) { results ->
            (binding.resultsList.adapter as HistoryResultAdapter).submitList(results)
        }
        
        // 观察图表数据
        viewModel.chartData.observe(viewLifecycleOwner) { data ->
            updateChart(data)
        }
        
        // 观察筛选状态
        viewModel.filterState.observe(viewLifecycleOwner) { state ->
            updateFilterUI(state)
        }
    }
    
    private fun updateChart(data: ChartData) {
        when (data) {
            is ChartData.TrendData -> showTrendChart(data)
            is ChartData.DistributionData -> showDistributionChart(data)
            is ChartData.PatternData -> showPatternChart(data)
        }
    }
    
    private fun updateFilterUI(state: FilterState) {
        // 更新日期范围显示
        binding.dateRangeLayout.apply {
            startDateText.text = formatDate(state.startDate)
            endDateText.text = formatDate(state.endDate)
        }
        
        // 更新号码范围显示
        binding.numberFilterLayout.numberRangeSlider.values = listOf(
            state.numberRange.first.toFloat(),
            state.numberRange.second.toFloat()
        )
        
        // 更新属性选择状态
        state.selectedAttributes.forEach { attribute ->
            // 更新相应的Chip选中状态
        }
    }
} 