/*
 * Copyright (c) Kuba Szczodrzyński 2019-10-23.
 */

package pl.szczodrzynski.edziennik.api.v2.edziennik.librus.data.api

import pl.szczodrzynski.edziennik.*
import pl.szczodrzynski.edziennik.api.v2.edziennik.librus.DataLibrus
import pl.szczodrzynski.edziennik.api.v2.edziennik.librus.ENDPOINT_LIBRUS_API_UNITS
import pl.szczodrzynski.edziennik.api.v2.edziennik.librus.data.LibrusApi

class LibrusApiUnits(override val data: DataLibrus,
                       val onSuccess: () -> Unit) : LibrusApi(data) {
    companion object {
        const val TAG = "LibrusApiUnits"
    }

    init { run {
        if (data.unitId == 0L) {
            data.setSyncNext(ENDPOINT_LIBRUS_API_UNITS, 12 * DAY)
            onSuccess()
            return@run
        }

        apiGet(TAG, "Units") { json ->
            val units = json.getJsonArray("Units").asJsonObjectList()

            units?.singleOrNull { it.getLong("Id") == data.unitId }?.also { unit ->
                val startPoints = unit.getJsonObject("BehaviourGradesSettings")?.getJsonObject("StartPoints")
                startPoints?.apply {
                    data.startPointsSemester1 = getInt("Semester1", defaultValue = 0)
                    data.startPointsSemester2 = getInt("Semester2", defaultValue = data.startPointsSemester1)
                }
                unit.getJsonObject("GradesSettings")?.apply {
                    data.enablePointGrades = getBoolean("PointGradesEnabled", true)
                    data.enableDescriptiveGrades = getBoolean("DescriptiveGradesEnabled", true)
                }
            }

            data.setSyncNext(ENDPOINT_LIBRUS_API_UNITS, 7 * DAY)
            onSuccess()
        }
    }}
}