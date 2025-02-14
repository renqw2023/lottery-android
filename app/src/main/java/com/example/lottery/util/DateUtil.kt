object DateUtil {
    fun formatDate(timestamp: Long, pattern: String = "yyyy-MM-dd HH:mm"): String {
        return SimpleDateFormat(pattern, Locale.getDefault()).format(Date(timestamp))
    }
} 