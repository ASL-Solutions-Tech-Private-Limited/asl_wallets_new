package com.asl.wallet.feature.aeps.domain

import com.asl.wallet.core.common.validation.Validators

/** Step 1 of onboarding. Validation mirrors the backend's zod schema, plus Verhoeff for Aadhaar. */
data class OnboardingForm(
    val firstName: String = "", val lastName: String = "", val email: String = "", val phone: String = "",
    /** dd/MM/yyyy as typed; sent as yyyy-MM-dd. */
    val dob: String = "",
    val address: String = "", val city: String = "", val state: String = "", val pincode: String = "",
    val aadhaar: String = "", val pan: String = "",
    val bankAccount: String = "", val ifsc: String = "", val bankName: String = "", val holderName: String = "",
    val shopName: String = "", val shopPan: String = "", val shopAddress: String = "", val shopCity: String = "",
    val shopDistrict: String = "", val shopState: String = "", val shopPincode: String = "", val gstin: String = "",
) {
    enum class Field { FirstName, LastName, Email, Phone, Dob, Address, City, State, Pincode, Aadhaar, Pan, BankAccount, Ifsc, BankName, HolderName, ShopName, ShopPan, ShopAddress, ShopCity, ShopDistrict, ShopState, ShopPincode }

    fun errors(): Set<Field> = buildSet {
        val nameRx = Regex("^[A-Za-z][A-Za-z .'-]{0,49}$")
        if (!nameRx.matches(firstName.trim())) add(Field.FirstName)
        if (!nameRx.matches(lastName.trim())) add(Field.LastName)
        if (!Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$").matches(email.trim())) add(Field.Email)
        if (!Validators.isMobile(phone)) add(Field.Phone)
        if (isoDob() == null) add(Field.Dob)
        if (address.trim().length < 5) add(Field.Address)
        if (city.isBlank()) add(Field.City)
        if (state.isBlank()) add(Field.State)
        if (!Validators.isPincode(pincode)) add(Field.Pincode)
        if (!Validators.isAadhaar(aadhaar.filter(Char::isDigit))) add(Field.Aadhaar)
        if (!Validators.isPan(pan)) add(Field.Pan)
        if (!Validators.isAccountNumber(bankAccount)) add(Field.BankAccount)
        if (!Validators.isIfsc(ifsc)) add(Field.Ifsc)
        if (bankName.isBlank()) add(Field.BankName)
        if (holderName.isBlank()) add(Field.HolderName)
        if (shopName.isBlank()) add(Field.ShopName)
        if (!Validators.isPan(shopPan)) add(Field.ShopPan)
        if (shopAddress.isBlank()) add(Field.ShopAddress)
        if (shopCity.isBlank()) add(Field.ShopCity)
        if (shopDistrict.isBlank()) add(Field.ShopDistrict)
        if (shopState.isBlank()) add(Field.ShopState)
        if (!Validators.isPincode(shopPincode)) add(Field.ShopPincode)
    }

    val isValid: Boolean get() = errors().isEmpty()

    /** "12/05/1990" → "1990-05-12"; null when not a real date or the person is under 18 (minSdk 24: no java.time). */
    fun isoDob(nowMillis: Long = System.currentTimeMillis()): String? {
        val m = Regex("^(\\d{2})/(\\d{2})/(\\d{4})$").matchEntire(dob.trim()) ?: return null
        val (d, mo, y) = m.destructured
        val cal = java.util.GregorianCalendar().apply { isLenient = false; clear(); set(y.toInt(), mo.toInt() - 1, d.toInt()) }
        val time = runCatching { cal.timeInMillis }.getOrNull() ?: return null
        val now = java.util.GregorianCalendar().apply { timeInMillis = nowMillis }
        val adult = (now.clone() as java.util.GregorianCalendar).apply { add(java.util.Calendar.YEAR, -18) }
        val oldest = (now.clone() as java.util.GregorianCalendar).apply { add(java.util.Calendar.YEAR, -100) }
        if (time > adult.timeInMillis || time < oldest.timeInMillis) return null
        return "%04d-%02d-%02d".format(java.util.Locale.US, y.toInt(), mo.toInt(), d.toInt())
    }

    fun fields(provider: AepsProvider, location: GeoPoint): Map<String, String> = mapOf(
        "firstName" to firstName.trim(), "lastName" to lastName.trim(), "email" to email.trim(), "phone" to phone.trim(),
        "dob" to isoDob().orEmpty(), "address" to address.trim(), "city" to city.trim(), "state" to state.trim(), "pincode" to pincode.trim(),
        "aadharNo" to aadhaar.filter(Char::isDigit), "panNo" to pan.trim().uppercase(),
        "bankAccountNo" to bankAccount.trim(), "bankIfsc" to ifsc.trim().uppercase(), "bankName" to bankName.trim(), "bankAccHolderName" to holderName.trim(),
        "shopName" to shopName.trim(), "shopPan" to shopPan.trim().uppercase(), "shopAddress" to shopAddress.trim(), "shopCity" to shopCity.trim(),
        "shopDistrict" to shopDistrict.trim(), "shopState" to shopState.trim(), "shopPincode" to shopPincode.trim(),
        "gstinNumber" to gstin.trim().uppercase(),
        "providerId" to provider.id.toString(), "latitude" to location.lat, "longitude" to location.lon,
    )

    companion object {
        /** Bank's data first; the retailer's ASL profile fills the gaps. */
        fun from(p: AgentPrefill, profile: ProfilePrefill) = OnboardingForm(
            firstName = p.firstName.ifBlank { profile.firstName }, lastName = p.lastName.ifBlank { profile.lastName },
            email = p.email.ifBlank { profile.email }, phone = p.phone.ifBlank { profile.phone },
            address = p.address.ifBlank { profile.address }, city = p.city.ifBlank { profile.city }, state = "", pincode = p.pincode.ifBlank { profile.pincode },
            pan = profile.pan,
            bankAccount = p.accountNo, ifsc = p.ifsc, bankName = p.bankName, holderName = p.holderName.ifBlank { listOf(profile.firstName, profile.lastName).filter { it.isNotBlank() }.joinToString(" ") },
            shopName = p.shopName.ifBlank { profile.shopName }, shopPan = p.shopPan, shopAddress = p.shopAddress.ifBlank { profile.address }, shopCity = p.shopCity.ifBlank { profile.city },
            shopDistrict = p.shopDistrict, shopState = p.shopState, shopPincode = p.shopPincode,
        )
    }
}

data class ProfilePrefill(
    val firstName: String = "", val lastName: String = "", val email: String = "", val phone: String = "",
    val pan: String = "", val address: String = "", val city: String = "", val pincode: String = "", val shopName: String = "",
)
