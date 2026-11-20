package br.uff.hist.core.network

import android.content.Context
import androidx.annotation.StringRes
import br.uff.hist.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject

/** Traduz [AppError] em texto para a UI (mensagens do servidor limitadas a [MAX_SERVER_MESSAGE] caracteres). */
class ErrorMessages @Inject constructor(@ApplicationContext private val context: Context) {
    fun of(error: AppError): String = when (error) {
        is AppError.Api -> error.message.take(MAX_SERVER_MESSAGE)
        AppError.Network -> context.getString(R.string.error_network)
        AppError.SessionExpired, AppError.Unknown -> context.getString(R.string.error_unknown)
    }

    fun string(@StringRes id: Int): String = context.getString(id)

    companion object {
        const val MAX_SERVER_MESSAGE = 200
    }
}
