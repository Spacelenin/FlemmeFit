package com.spacelenin.flemmefit.model

import java.time.LocalDate
import java.time.Month

/** L'écran surprise : une fois après la version qui l'apporte, puis chaque 1er avril. */
object Surprise {
    fun isAprilFirst(date: LocalDate) = date.month == Month.APRIL && date.dayOfMonth == 1

    fun due(seen: Boolean, lastAprilYear: Int, today: LocalDate): Boolean =
        !seen || (isAprilFirst(today) && lastAprilYear != today.year)
}
