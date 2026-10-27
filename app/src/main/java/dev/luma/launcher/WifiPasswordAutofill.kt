package dev.luma.launcher

/** Device-local saved password for the P6 Wi-Fi editor only. No bundled password. */
internal object WifiPasswordAutofill {
    fun valid(value:String)=value.length in 8..63 && value.all{it.code in 32..126} ||
        value.length==64 && value.all{it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F'}
    fun matches(packageName:String?,fieldName:String?)=
        packageName=="com.yhk.setting" && fieldName=="edit_password"
}
