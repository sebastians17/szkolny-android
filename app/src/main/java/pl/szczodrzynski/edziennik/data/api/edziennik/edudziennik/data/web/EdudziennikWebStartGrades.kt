/*
 * Copyright (c) Kacper Ziubryniewicz 2019-12-24
 */

package pl.szczodrzynski.edziennik.data.api.edziennik.edudziennik.data.web

import android.graphics.Color
import org.jsoup.nodes.Document
import pl.szczodrzynski.edziennik.colorFromCssName
import pl.szczodrzynski.edziennik.crc32
import pl.szczodrzynski.edziennik.data.api.Regexes
import pl.szczodrzynski.edziennik.data.api.edziennik.edudziennik.DataEdudziennik
import pl.szczodrzynski.edziennik.data.api.models.DataRemoveModel
import pl.szczodrzynski.edziennik.data.db.modules.grades.Grade
import pl.szczodrzynski.edziennik.data.db.modules.grades.Grade.*
import pl.szczodrzynski.edziennik.data.db.modules.metadata.Metadata
import pl.szczodrzynski.edziennik.get
import pl.szczodrzynski.edziennik.utils.Utils
import pl.szczodrzynski.edziennik.utils.models.Date

class EdudziennikWebStartGrades(val data: DataEdudziennik, val doc: Document) {
    companion object {
        const val TAG = "EdudziennikWebStartGrades"
    }

    init {data.profile?.also { profile ->
        val subjects = doc.select("#student_grades tbody").firstOrNull()?.children()

        if (subjects.isNullOrEmpty()) return@also

        subjects.forEach { subjectElement ->
            if (subjectElement.id().isBlank()) return@forEach

            val subjectId = subjectElement.id().trim()
            val subjectName = subjectElement.child(0).text().trim()
            val subject = data.getSubject(subjectId, subjectName)

            val grades = subjectElement.select(".grade")
            val gradesInfo = subjectElement.select(".grade-tip")

            val gradeValues = subjects.select(".avg-$subjectId .grade-tip > p").first()
                    .text().split('+').map {
                        val split = it.split('*')
                        val weight = split[0].trim().toFloat()
                        val value = split[1].trim().toFloat()

                        Pair(value, weight)
                    }

            grades.forEachIndexed { index, gradeElement ->
                val id = Regexes.EDUDZIENNIK_GRADE_ID.find(gradeElement.attr("href"))?.get(1)?.crc32()
                        ?: return@forEachIndexed
                val (value, weight) = gradeValues[index]
                val name = gradeElement.text().trim().let {
                    if (it.contains(',') || it.contains('.')) {
                        val replaced = it.replace(',', '.')
                        val float = replaced.toFloatOrNull()

                        if (float != null && float % 1 == 0f) float.toInt().toString()
                        else it
                    } else it
                }

                val info = gradesInfo[index]
                val category = info.child(4).text().trim()

                val (teacherLastName, teacherFirstName) = info.child(1).text().split(' ')
                val teacher = data.getTeacher(teacherFirstName, teacherLastName)

                val addedDate = info.child(2).text().split(' ').let {
                    val day = it[0].toInt()
                    val month = Utils.monthFromName(it[1])
                    val year = it[2].toInt()

                    Date(year, month, day).inMillis
                }

                val color = Regexes.STYLE_CSS_COLOR.find(gradeElement.attr("style"))?.get(1)?.let {
                    if (it.startsWith('#')) Color.parseColor(it)
                    else colorFromCssName(it)
                } ?: -1

                val gradeObject = Grade(
                        data.profileId,
                        id,
                        category,
                        color,
                        "",
                        name,
                        value,
                        weight,
                        profile.currentSemester,
                        teacher.id,
                        subject.id
                )

                data.gradeList.add(gradeObject)
                data.metadataList.add(Metadata(
                        data.profileId,
                        Metadata.TYPE_GRADE,
                        id,
                        profile.empty,
                        profile.empty,
                        addedDate
                ))
            }

            val proposed = subjectElement.select(".proposal").firstOrNull()?.text()?.trim()

            if (proposed != null && proposed.isNotBlank()) {
                val proposedGradeObject = Grade(
                        data.profileId,
                        (-1 * subject.id) - 1,
                        "",
                        -1,
                        "",
                        proposed,
                        proposed.toFloatOrNull() ?: 0f,
                        0f,
                        profile.currentSemester,
                        -1,
                        subject.id
                ).apply {
                    type = when (semester) {
                        1 -> TYPE_SEMESTER1_PROPOSED
                        else -> TYPE_SEMESTER2_PROPOSED
                    }
                }

                data.gradeList.add(proposedGradeObject)
                data.metadataList.add(Metadata(
                        data.profileId,
                        Metadata.TYPE_GRADE,
                        proposedGradeObject.id,
                        profile.empty,
                        profile.empty,
                        System.currentTimeMillis()
                ))
            }

            val final = subjectElement.select(".final").firstOrNull()?.text()?.trim()

            if (final != null && final.isNotBlank()) {
                val finalGradeObject = Grade(
                        data.profileId,
                        (-1 * subject.id) - 2,
                        "",
                        -1,
                        "",
                        final,
                        final.toFloatOrNull() ?: 0f,
                        0f,
                        profile.currentSemester,
                        -1,
                        subject.id
                ).apply {
                    type = when (semester) {
                        1 -> TYPE_SEMESTER1_FINAL
                        else -> TYPE_SEMESTER2_FINAL
                    }
                }

                data.gradeList.add(finalGradeObject)
                data.metadataList.add(Metadata(
                        data.profileId,
                        Metadata.TYPE_GRADE,
                        finalGradeObject.id,
                        profile.empty,
                        profile.empty,
                        System.currentTimeMillis()
                ))
            }
        }

        data.toRemove.addAll(listOf(
                TYPE_NORMAL,
                TYPE_SEMESTER1_PROPOSED,
                TYPE_SEMESTER2_PROPOSED,
                TYPE_SEMESTER1_FINAL,
                TYPE_SEMESTER2_FINAL
        ).map {
            DataRemoveModel.Grades.semesterWithType(profile.currentSemester, it)
        })
    }}
}