package br.uff.hist.core.model

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import br.uff.hist.R

enum class Category(val id: String, @StringRes val label: Int, @DrawableRes val icon: Int) {
    IGREJA("igreja", R.string.cat_igreja, R.drawable.ic_cat_igreja),
    MUSEU("museu", R.string.cat_museu, R.drawable.ic_cat_museu),
    MONUMENTO("monumento", R.string.cat_monumento, R.drawable.ic_cat_monumento),
    EDIFICIO_HISTORICO("edificio_historico", R.string.cat_edificio_historico, R.drawable.ic_cat_edificio_historico),
    PRACA("praca", R.string.cat_praca, R.drawable.ic_cat_praca),
    FORTIFICACAO("fortificacao", R.string.cat_fortificacao, R.drawable.ic_cat_fortificacao),
    SITIO_ARQUEOLOGICO("sitio_arqueologico", R.string.cat_sitio_arqueologico, R.drawable.ic_cat_sitio_arqueologico),
    FERROVIA("ferrovia", R.string.cat_ferrovia, R.drawable.ic_cat_ferrovia),
    OUTRO("outro", R.string.cat_outro, R.drawable.ic_cat_outro);

    companion object {
        fun fromId(id: String?): Category =
            entries.firstOrNull { it.id.equals(id, ignoreCase = true) } ?: OUTRO
    }
}
