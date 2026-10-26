package com.tdtu.ckmobile.ui.components

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import java.util.Calendar

/**
 * Utility to pick Date and Time sequentially and return timestamp in millis
 */
fun showDateTimePicker(
    context: Context,
    initialTimestamp: Long? = null,
    onDateTimeSelected: (Long) -> Unit
) {
    val calendar = Calendar.getInstance().apply {
        if (initialTimestamp != null && initialTimestamp > 0) {
            timeInMillis = initialTimestamp
        }
    }

    val year = calendar.get(Calendar.YEAR)
    val month = calendar.get(Calendar.MONTH)
    val day = calendar.get(Calendar.DAY_OF_MONTH)

    val datePickerDialog = DatePickerDialog(
        context,
        { _, selectedYear, selectedMonth, selectedDay ->
            calendar.set(Calendar.YEAR, selectedYear)
            calendar.set(Calendar.MONTH, selectedMonth)
            calendar.set(Calendar.DAY_OF_MONTH, selectedDay)

            val hour = calendar.get(Calendar.HOUR_OF_DAY)
            val minute = calendar.get(Calendar.MINUTE)

            val timePickerDialog = TimePickerDialog(
                context,
                { _, selectedHour, selectedMinute ->
                    calendar.set(Calendar.HOUR_OF_DAY, selectedHour)
                    calendar.set(Calendar.MINUTE, selectedMinute)
                    calendar.set(Calendar.SECOND, 0)
                    calendar.set(Calendar.MILLISECOND, 0)

                    onDateTimeSelected(calendar.timeInMillis)
                },
                hour,
                minute,
                true // 24-hour view
            )
            timePickerDialog.show()
        },
        year,
        month,
        day
    )

    datePickerDialog.show()
}
