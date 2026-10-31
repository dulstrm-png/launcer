# Dulz Fluu

Aplikasi panel melayang. Tampilan dibuat dengan HTML (`app/src/main/assets/index.html`),
dibungkus WebView, dan dihubungkan ke fitur Android lewat jembatan `Android`.

## Cara build APK dengan GitHub Actions

1. Buat repositori baru di GitHub.
2. Unggah seluruh isi folder ini, termasuk folder `.github` (folder berawalan titik sering
   terlewat saat unggah lewat browser; kalau terlewat, buat file
   `.github/workflows/build.yml` secara manual dan salin isinya).
3. Buka tab **Actions**. Build berjalan otomatis setiap push ke `main` atau `master`.
   Untuk menjalankan manual: pilih **Build APK** lalu **Run workflow**.
4. Setelah selesai (sekitar 3 sampai 6 menit), buka run tersebut dan unduh artifact
   **DulzFluu-APK**. Di dalamnya ada `DulzFluu.apk`.
5. Pasang di HP. Izinkan pemasangan dari sumber tidak dikenal bila diminta.

APK yang dihasilkan adalah build debug yang sudah ditandatangani kunci debug, jadi langsung
bisa dipasang.

## Mengubah tampilan

Edit `app/src/main/assets/index.html`, commit, dan Actions akan membangun ulang APK.

## Fitur native

| Fitur | Cara kerja |
| --- | --- |
| Tampil di atas aplikasi lain | Layanan foreground `OverlayService` dengan jendela overlay |
| Izin | Tombol Izinkan membuka layar izin sistem yang sebenarnya |
| Buka aplikasi lain | `getLaunchIntentForPackage` berdasarkan nama paket |
| Pembersih | Hapus cache aplikasi dan hentikan proses latar belakang aplikasi lain |
| Mode Game | Layar tetap menyala selama panel aktif |

## Batasan

- FPS game yang sedang dimainkan tidak bisa dibaca aplikasi biasa tanpa akses root atau Shizuku.
  Panel native menampilkan Hz layar, RAM bebas, dan baterai. FPS di halaman utama adalah
  kelancaran tampilan aplikasi ini sendiri.
- Pembersih hanya bisa menghentikan proses latar belakang. Sistem Android bisa menjalankan
  ulang sebagian proses tersebut.
