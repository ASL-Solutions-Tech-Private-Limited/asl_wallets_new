package com.asl.wallet.feature.wallet.data

import com.asl.wallet.feature.wallet.domain.FundRequestItem
import javax.inject.Inject
import javax.inject.Singleton

/** In-memory hand-over between wallet screens (money data never goes into routes). Cleared on sign-out. */
@Singleton
class WalletSession @Inject constructor() {
    /** The pending fund request "Edit" was tapped on; read once by the form. */
    @Volatile var editing: FundRequestItem? = null

    fun takeEditing(): FundRequestItem? = editing.also { editing = null }

    fun clear() { editing = null }
}
