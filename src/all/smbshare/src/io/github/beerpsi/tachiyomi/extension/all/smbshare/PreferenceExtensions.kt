package io.github.beerpsi.tachiyomi.extension.all.smbshare

import android.content.SharedPreferences
import android.text.Editable
import android.text.TextWatcher
import android.widget.Button
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.PreferenceScreen

internal const val PREF_DOMAIN = "domain"
internal const val PREF_PORT = "port"
internal const val PREF_SHARE_NAME = "shareName"
internal const val PREF_ROOT_DIRECTORY = "rootDir"
internal const val PREF_USERNAME = "username"
internal const val PREF_PASSWORD = "password"
internal const val PREF_DISPLAY_NAME = "displayName"
internal const val PREF_EXTRA_SOURCES_COUNT = "extraSources"

internal val SharedPreferences.domain
    get() = getString(PREF_DOMAIN, "")!!

internal val SharedPreferences.port
    get() = getString(PREF_PORT, "445")!!.toInt()

internal val SharedPreferences.shareName
    get() = getString(PREF_SHARE_NAME, "")!!

internal val SharedPreferences.rootDirectory
    get() = getString(PREF_ROOT_DIRECTORY, "")!!

internal val SharedPreferences.username
    get() = getString(PREF_USERNAME, "")!!

internal val SharedPreferences.password
    get() = getString(PREF_PASSWORD, "")!!

internal val SharedPreferences.displayName
    get() = getString(PREF_DISPLAY_NAME, "")!!

internal val SharedPreferences.extraSources
    get() = getString(PREF_EXTRA_SOURCES_COUNT, "0")!!.toInt()

fun PreferenceScreen.addEditTextPreference(
    title: String,
    default: String,
    summary: String,
    dialogMessage: String? = null,
    inputType: Int? = null,
    validate: ((String) -> Boolean)? = null,
    validationMessage: String? = null,
    key: String = title,
    restartRequired: Boolean = false,
    onChange: ((String) -> Unit)? = null,
) {
    EditTextPreference(context).apply {
        this.key = key
        this.title = title
        this.summary = summary
        this.setDefaultValue(default)
        dialogTitle = title
        this.dialogMessage = dialogMessage

        setOnBindEditTextListener { editText ->
            if (inputType != null) {
                editText.inputType = inputType
            }

            if (validate != null) {
                editText.addTextChangedListener(
                    @Suppress("EmptyFunctionBlock")
                    object : TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

                        override fun afterTextChanged(editable: Editable?) {
                            requireNotNull(editable)

                            val text = editable.toString()

                            val isValid = text.isBlank() || validate(text)

                            editText.error = if (!isValid) validationMessage else null
                            editText.rootView.findViewById<Button>(android.R.id.button1)
                                ?.isEnabled = editText.error == null
                        }
                    },
                )
            }
        }

        setOnPreferenceChangeListener { _, newValue ->
            val text = newValue as String
            val result = text.isBlank() || validate?.invoke(text) ?: true

            if (result) {
                onChange?.invoke(text)

                if (restartRequired) {
                    Toast.makeText(context, "Restart Tachiyomi to apply new setting.", Toast.LENGTH_LONG).show()
                }
            }

            result
        }
    }.also(::addPreference)
}
