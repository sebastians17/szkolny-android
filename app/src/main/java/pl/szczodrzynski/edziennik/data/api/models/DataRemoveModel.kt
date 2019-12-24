/*
 * Copyright (c) Kuba Szczodrzyński 2019-10-2.
 */

package pl.szczodrzynski.edziennik.data.api.models

import pl.szczodrzynski.edziennik.data.db.modules.events.EventDao
import pl.szczodrzynski.edziennik.data.db.modules.grades.GradeDao
import pl.szczodrzynski.edziennik.data.db.modules.timetable.TimetableDao
import pl.szczodrzynski.edziennik.utils.models.Date

open class DataRemoveModel {
    class Timetable(private val dateFrom: Date?, private val dateTo: Date?) : DataRemoveModel() {
        companion object {
            fun from(dateFrom: Date) = Timetable(dateFrom, null)
            fun to(dateTo: Date) = Timetable(null, dateTo)
            fun between(dateFrom: Date, dateTo: Date) = Timetable(dateFrom, dateTo)
        }

        fun commit(profileId: Int, dao: TimetableDao) {
            if (dateFrom != null && dateTo != null) {
                dao.clearBetweenDates(profileId, dateFrom, dateTo)
            } else {
                dateFrom?.let { dateFrom -> dao.clearFromDate(profileId, dateFrom) }
                dateTo?.let { dateTo -> dao.clearToDate(profileId, dateTo) }
            }
        }
    }

    class Grades(private val all: Boolean, private val semester: Int?, private val type: Int?) : DataRemoveModel() {
        companion object {
            fun all() = Grades(true, null, null)
            fun allWithType(type: Int) = Grades(true, null, type)
            fun semester(semester: Int) = Grades(false, semester, null)
            fun semesterWithType(semester: Int, type: Int) = Grades(false, semester, type)
        }

        fun commit(profileId: Int, dao: GradeDao) {
            if (all) {
                if (type != null) dao.clearWithType(profileId, type)
                else dao.clear(profileId)
            }
            semester?.let {
                if (type != null) dao.clearForSemesterWithType(profileId, it, type)
                else dao.clearForSemester(profileId, it)
            }
        }
    }

    class Events(private val type: Int?, private val exceptType: Int?) : DataRemoveModel() {
        companion object {
            fun futureExceptType(exceptType: Int) = Events(null, exceptType)
            fun futureWithType(type: Int) = Events(type, null)
        }

        fun commit(profileId: Int, dao: EventDao) {
            type?.let { dao.removeFutureWithType(profileId, Date.getToday(), it) }
            exceptType?.let { dao.removeFutureExceptType(profileId, Date.getToday(), it) }
        }
    }
}