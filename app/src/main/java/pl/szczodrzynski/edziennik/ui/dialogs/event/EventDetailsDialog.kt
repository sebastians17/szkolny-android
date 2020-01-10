/*
 * Copyright (c) Kuba Szczodrzyński 2019-12-18.
 */

package pl.szczodrzynski.edziennik.ui.dialogs.event

import android.content.Intent
import android.provider.CalendarContract
import android.provider.CalendarContract.Events
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.*
import pl.szczodrzynski.edziennik.*
import pl.szczodrzynski.edziennik.data.api.szkolny.SzkolnyApi
import pl.szczodrzynski.edziennik.data.db.full.EventFull
import pl.szczodrzynski.edziennik.databinding.DialogEventDetailsBinding
import pl.szczodrzynski.edziennik.utils.models.Date
import kotlin.coroutines.CoroutineContext

class EventDetailsDialog(
        val activity: AppCompatActivity,
        val event: EventFull,
        val onShowListener: ((tag: String) -> Unit)? = null,
        val onDismissListener: ((tag: String) -> Unit)? = null
) : CoroutineScope {
    companion object {
        private const val TAG = "EventDetailsDialog"
    }

    private lateinit var app: App
    private lateinit var b: DialogEventDetailsBinding
    private lateinit var dialog: AlertDialog
    private var removeEventDialog: AlertDialog? = null
    private val eventShared = event.sharedBy != null
    private val eventOwn = event.sharedBy == "self"

    private val job = Job()
    override val coroutineContext: CoroutineContext
        get() = job + Dispatchers.Main

    private val api by lazy {
        SzkolnyApi(app)
    }

    init { run {
        if (activity.isFinishing)
            return@run
        onShowListener?.invoke(TAG)
        app = activity.applicationContext as App
        b = DialogEventDetailsBinding.inflate(activity.layoutInflater)
        dialog = MaterialAlertDialogBuilder(activity)
                .setView(b.root)
                .setPositiveButton(R.string.close) { dialog, _ ->
                    dialog.dismiss()
                }
                .apply {
                    if (event.addedManually)
                        setNeutralButton(R.string.remove, null)
                }
                .setOnDismissListener {
                    onDismissListener?.invoke(TAG)
                }
                .show()

        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.onClick {
            showRemoveEventDialog()
        }

        update()
    }}

    private fun update() {
        b.event = event
        b.eventShared = eventShared
        b.eventOwn = eventOwn

        val bullet = " • "
        val colorSecondary = android.R.attr.textColorSecondary.resolveAttr(activity)

        try {
            b.monthName = app.resources.getStringArray(R.array.months_day_of_array)[event.eventDate.month - 1]
        }
        catch (_: Exception) {}

        b.typeColor.background?.setTintColor(event.getColor())

        b.details = mutableListOf(
                event.subjectLongName,
                event.teamName?.asColoredSpannable(colorSecondary)
        ).concat(bullet)

        b.addedBy.setText(
                when (event.sharedBy) {
                    null -> when {
                        event.addedManually -> R.string.event_details_added_by_self_format
                        event.teacherFullName == null -> R.string.event_details_added_by_unknown_format
                        else -> R.string.event_details_added_by_format
                    }
                    "self" -> R.string.event_details_shared_by_self_format
                    else -> R.string.event_details_shared_by_format
                },
                Date.fromMillis(event.addedDate).formattedString,
                event.sharedByName ?: event.teacherFullName ?: ""
        )

        b.editButton.visibility = if (event.addedManually) View.VISIBLE else View.GONE
        b.editButton.setOnClickListener {
            EventManualDialog(
                    activity,
                    event.profileId,
                    editingEvent = event,
                    onShowListener = onShowListener,
                    onDismissListener = onDismissListener
            )
        }

        b.saveInCalendarButton.setOnClickListener {
            openInCalendar()
        }
    }

    private fun showRemoveEventDialog() {
        val shareNotice = when {
            eventShared && eventOwn -> "\n\n"+activity.getString(R.string.dialog_event_manual_remove_shared_self)
            eventShared && !eventOwn -> "\n\n"+activity.getString(R.string.dialog_event_manual_remove_shared)
            else -> ""
        }
        removeEventDialog = MaterialAlertDialogBuilder(activity)
                .setTitle(R.string.are_you_sure)
                .setMessage(activity.getString(R.string.dialog_register_event_manual_remove_confirmation)+shareNotice)
                .setPositiveButton(R.string.yes, null)
                .setNegativeButton(R.string.no) { dialog, _ -> dialog.dismiss() }
                .create()
                .apply {
                    setOnShowListener { dialog ->
                        val positiveButton = (dialog as AlertDialog).getButton(AlertDialog.BUTTON_POSITIVE)
                        positiveButton?.setOnClickListener {
                            removeEvent()
                        }
                    }

                    show()
                }
    }

    private fun removeEvent() {
        launch {
            if (eventShared && eventOwn) {
                Toast.makeText(activity, "Unshare + remove own event", Toast.LENGTH_SHORT).show()

                val response = withContext(Dispatchers.Default) {
                    api.unshareEvent(event)
                }

                response?.errors?.ifNotEmpty {
                    Toast.makeText(activity, "Error: "+it[0].reason, Toast.LENGTH_SHORT).show()
                    return@launch
                }

                finishRemoving()
            } else if (eventShared && !eventOwn) {
                Toast.makeText(activity, "Remove + blacklist somebody's event", Toast.LENGTH_SHORT).show()
                // TODO
            } else {
                Toast.makeText(activity, "Remove event", Toast.LENGTH_SHORT).show()
                finishRemoving()
            }
        }
    }

    private fun finishRemoving() {
        launch {
            withContext(Dispatchers.Default) {
                app.db.eventDao().remove(event)
            }
        }

        removeEventDialog?.dismiss()
        dialog.dismiss()
        Toast.makeText(activity, R.string.removed, Toast.LENGTH_SHORT).show()
        if (activity is MainActivity && activity.navTargetId == MainActivity.DRAWER_ITEM_AGENDA)
            activity.reloadTarget()
    }

    private fun openInCalendar() { launch {
        val title = (event.typeName ?: "") +
                (if (event.typeName.isNotNullNorBlank() && event.subjectLongName.isNotNullNorBlank()) " - " else " ") +
                (event.subjectLongName ?: "")

        val intent = Intent(Intent.ACTION_EDIT).apply {
            data = Events.CONTENT_URI
            putExtra(Events.TITLE, title)
            putExtra(Events.DESCRIPTION, event.topic)

            if (event.startTime == null) {
                putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, true)
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, event.eventDate.inMillis)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, event.eventDate.inMillis)
            } else {
                val startTime = event.eventDate.combineWith(event.startTime)
                val endTime = startTime + 45 * 60 * 1000 /* 45 min */

                putExtra(CalendarContract.EXTRA_EVENT_ALL_DAY, false)
                putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, startTime)
                putExtra(CalendarContract.EXTRA_EVENT_END_TIME, endTime)
            }
        }

        activity.startActivity(intent)
    }}
}