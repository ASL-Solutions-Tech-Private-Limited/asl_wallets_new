package com.asl.wallet.feature.aeps.domain

/**
 * PID options for the UIDAI RD service, and a parser for the PID block it returns.
 * The PID XML is biometric data: it is passed straight to the server and never logged or stored.
 */
object Pid {
    /** Standard UIDAI eKYC WADH, used by the old app for the bank biometric check. */
    const val EKYC_WADH = "E0jzJ/P8UopUHAieZn8CKqS4WPMi5ZSYXgfnlfkWjrc="

    private fun attr(v: String) = v.replace("&", "&amp;").replace("\"", "&quot;").replace("<", "&lt;").replace(">", "&gt;")

    /** Fingerprint, 1 finger, FMR+FIR (L1 devices), production. [otp] = customer OTP for withdrawals above ₹5,000. */
    fun fingerOptions(wadh: String = "", otp: String = ""): String =
        "<PidOptions ver=\"1.0\"><Opts fCount=\"1\" fType=\"2\" iCount=\"0\" iType=\"0\" pCount=\"0\" pType=\"0\" format=\"0\" " +
            "pidVer=\"2.0\" timeout=\"10000\" otp=\"${attr(otp.filter(Char::isDigit))}\" wadh=\"${attr(wadh)}\" posh=\"UNKNOWN\" env=\"P\" /><Demo></Demo>" +
            "<CustOpts><Param name=\"\" value=\"\" /></CustOpts></PidOptions>"

    /** Face RD (AadhaarFaceRD) request. [txnId] must be random per capture. */
    fun faceOptions(txnId: String, wadh: String = "", otp: String = ""): String =
        "<?xml version=\"1.0\" encoding=\"UTF-8\"?><PidOptions ver=\"1.0\" env=\"P\"><Opts fCount=\"\" fType=\"\" iCount=\"\" iType=\"\" " +
            "pCount=\"\" pType=\"\" format=\"\" pidVer=\"2.0\" timeout=\"\" otp=\"${attr(otp.filter(Char::isDigit))}\" wadh=\"${attr(wadh)}\" posh=\"\" /><CustOpts>" +
            "<Param name=\"txnId\" value=\"${attr(txnId)}\"/><Param name=\"purpose\" value=\"auth\"/><Param name=\"language\" value=\"en\"/>" +
            "</CustOpts></PidOptions>"

    sealed interface Parsed {
        data class Ok(val pidXml: String) : Parsed
        data class Error(val code: String, val info: String?) : Parsed
        data object Invalid : Parsed
    }

    private val respTag = Regex("<Resp\\b([^>]*)>", RegexOption.IGNORE_CASE)
    private fun attrOf(attrs: String, name: String) = Regex("\\b$name\\s*=\\s*\"([^\"]*)\"", RegexOption.IGNORE_CASE).find(attrs)?.groupValues?.get(1)

    /** errCode "0" means a good capture. Anything else is an error with the RD service's own text. */
    fun parse(xml: String?): Parsed {
        val x = xml?.trim().orEmpty()
        if (!x.contains("<PidData", ignoreCase = true)) return Parsed.Invalid
        val attrs = respTag.find(x)?.groupValues?.get(1) ?: return Parsed.Invalid
        val code = attrOf(attrs, "errCode") ?: return Parsed.Invalid
        return if (code == "0") Parsed.Ok(x) else Parsed.Error(code, attrOf(attrs, "errInfo")?.ifBlank { null })
    }

    /** Known RD services by the device names `aeps/init` returns. */
    val PACKAGES: Map<String, String> = mapOf(
        "mantra l1" to "com.mantra.mfs110.rdservice",
        "morpho l1" to "com.idemia.l1rdservice",
        "startek l1" to "com.acpl.registersdk_l1",
        "faceauth" to "in.gov.uidai.facerd",
        "face auth" to "in.gov.uidai.facerd",
    )
    const val FINGER_ACTION = "in.gov.uidai.rdservice.fp.CAPTURE"
    const val FACE_ACTION = "in.gov.uidai.rdservice.face.CAPTURE"
}
