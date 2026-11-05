package id.a3.core.domain.security

/**
 * Siapa yang boleh membuka kunci layar selama sebuah shift berjalan.
 * Transaksi dalam shift tercatat atas nama pembuka shift, sehingga hanya pembuka shift
 * atau manager/owner yang boleh masuk. Tanpa shift aktif, semua akun boleh.
 */
object ShiftLoginPolicy {
    fun isAllowed(
        userId: String,
        role: String,
        shiftOwnerId: String?,
        approverRoles: Set<String> = ManagerApprover.DEFAULT_ROLES
    ): Boolean =
        shiftOwnerId == null || userId == shiftOwnerId || role.lowercase() in approverRoles

    /**
     * Pembatasan hanya dipakai bila daftar akun datang dari server. Saat offline, daftar hanya berisi
     * akun cadangan yang PIN-nya bisa dicocokkan secara lokal; menyaringnya akan membuat tak seorang pun
     * bisa masuk, bahkan untuk menutup shift.
     */
    fun shouldRestrict(accountsFromServer: Boolean, shiftOwnerId: String?): Boolean =
        accountsFromServer && shiftOwnerId != null

    /** Pesan untuk kasir bila tidak ada akun yang boleh dipilih. */
    fun blockedMessage(shiftOwnerName: String?): String =
        "Shift sedang dibuka oleh ${shiftOwnerName ?: "kasir lain"}. " +
            "Hanya pembuka shift atau manager yang bisa membuka kunci. Tutup shift dulu untuk berganti kasir."
}
