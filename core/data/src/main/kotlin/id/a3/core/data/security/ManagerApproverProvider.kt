package id.a3.core.data.security

import id.a3.core.domain.security.ManagerApprover

/**
 * Titik pasang tunggal [ManagerApprover] untuk satu proses aplikasi. Cangkang aplikasi memasangnya sekali
 * saat start (butuh kredensial perangkat dari core:auth); pabrik ViewModel mengambilnya.
 * Tanpa pemasangan, [get] mengembalikan null dan semua aksi yang butuh PIN manager ditolak (aman secara bawaan).
 */
object ManagerApproverProvider {
    @Volatile
    private var approver: ManagerApprover? = null

    fun install(approver: ManagerApprover) {
        this.approver = approver
    }

    fun get(): ManagerApprover? = approver
}
