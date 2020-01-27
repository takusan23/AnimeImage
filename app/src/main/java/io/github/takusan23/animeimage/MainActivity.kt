package io.github.takusan23.animeimage

import android.app.Activity
import android.content.Intent
import android.database.Cursor
import android.media.ToneGenerator
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import com.arthenica.mobileffmpeg.FFmpeg
import com.google.android.material.snackbar.Snackbar
import kotlinx.android.synthetic.main.activity_main.*
import java.io.File
import java.nio.channels.InterruptedByTimeoutException
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    // 動画を取り出すときの識別コード
    val READ_REQUEST_CODE = 157

    // 変換前動画
    lateinit var inputFile: File
    // 変換前Uri
    lateinit var uri: Uri

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // ライセンス画面
        license_button.setOnClickListener {
            val intent = Intent(this, LicenseActivity::class.java)
            startActivity(intent)
        }

        // SAFで動画を取り出す
        video_picker_button.setOnClickListener {
            // 間違えちゃった用
            if (::inputFile.isInitialized) {
                inputFile.delete()
            }
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                type = "video/mp4"
            }
            startActivityForResult(intent, READ_REQUEST_CODE)
        }

        // FFmpegを実行する
        ffmpeg_run_button.setOnClickListener {
            // 選択済みか
            if (::inputFile.isInitialized && ::uri.isInitialized) {
                // 多分メインスレッドで実行されるので別スレッドで
                thread {
                    // ファイル名
                    val name = getContentFileName(uri)
                    // 保存GIF画像を生成する
                    val outputFile = File("${externalMediaDirs[0]?.path}/${name}.gif")
                    // コマンド
                    // -i 入力.mp4 出力.gif
                    FFmpeg.execute("-i ${inputFile.path} ${outputFile.path}")
                    val returnCode = FFmpeg.getLastReturnCode()
                    val output = FFmpeg.getLastCommandOutput()
                    when (returnCode) {
                        FFmpeg.RETURN_CODE_SUCCESS -> {
                            runOnUiThread {
                                Toast.makeText(
                                    this,
                                    getString(R.string.ffmpeg_successful),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        }
                        FFmpeg.RETURN_CODE_CANCEL -> {
                            runOnUiThread {
                                Toast.makeText(
                                    this,
                                    getString(R.string.ffmpeg_error),
                                    Toast.LENGTH_SHORT
                                ).show()
                                // 失敗してもさくじょで
                                inputFile.delete()
                            }
                        }
                    }
                }
            }
        }

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == READ_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            // リクエストコードが同じで成功した場合
            //　一旦ScopedStorageにコピーする。
            val uri = data?.data
            if (uri != null) {
                this.uri = uri
                // ファイル名取得
                val name = getContentFileName(uri)
                // 出力先
                inputFile = File("${getExternalFilesDir(null)?.path}/${name}.mp4")
                inputFile.createNewFile()
                // なぞ。拡張関数を使ってなんとか
                // 参考：https://stackoverflow.com/questions/35528409/write-a-large-inputstream-to-file-in-kotlin
                inputFile.outputStream().use { fileOut ->
                    contentResolver.openInputStream(uri)?.copyTo(fileOut)
                }
            } else {
                Toast.makeText(this, getString(R.string.uri_error), Toast.LENGTH_SHORT).show()
            }
        }
    }

    /**
     * content://なUriからファイルの名前を取得する
     * @param uri StorageAccessFrameworkから取得したUri
     * @return 名前。もしなかった場合はSystem.currentTimeMillisの文字列になります。
     * */
    fun getContentFileName(uri: Uri): String {
        val cursor = contentResolver.query(
            uri,
            arrayOf(MediaStore.MediaColumns.DISPLAY_NAME),
            null,
            null,
            null
        )
        cursor?.moveToFirst()
        val name = cursor?.getString(0) ?: "${System.currentTimeMillis()}"
        cursor?.close()
        return name
    }

}
