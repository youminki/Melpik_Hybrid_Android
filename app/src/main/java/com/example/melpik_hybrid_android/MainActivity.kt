package com.example.melpik_hybrid_android

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.ConnectivityManager
import android.net.Uri
import android.net.http.SslCertificate
import android.net.http.SslError
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import org.json.JSONObject
import java.security.cert.CertificateExpiredException
import java.security.cert.CertificateNotYetValidException
import java.security.cert.X509Certificate

class MainActivity : AppCompatActivity() {
    private lateinit var webView: WebView
    private lateinit var progressBar: ProgressBar
    private lateinit var sharedPreferences: SharedPreferences
    private val url = "https://me1pik.com"
    private val permissions = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // SharedPreferences 초기화
        sharedPreferences = getSharedPreferences("MelpikPrefs", Context.MODE_PRIVATE)

        // FrameLayout으로 WebView와 ProgressBar(스플래시) 겹치기
        val frameLayout = FrameLayout(this)
        webView = WebView(this)
        progressBar = ProgressBar(this)
        progressBar.isIndeterminate = true
        val params = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        )
        frameLayout.addView(webView, params)
        frameLayout.addView(progressBar, params)
        setContentView(frameLayout)

        // 네트워크 체크
        if (!isNetworkAvailable(this)) {
            Toast.makeText(this, "네트워크 연결이 필요합니다.", Toast.LENGTH_LONG).show()
            progressBar.visibility = FrameLayout.GONE
            return
        }

        // WebView 설정 강화
        val settings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.mediaPlaybackRequiresUserGesture = false
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            settings.safeBrowsingEnabled = true
        }
        CookieManager.getInstance().setAcceptCookie(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        }

        // WebViewClient 설정 (SSL, 외부 URL, 로딩 표시)
        webView.webViewClient = object : WebViewClient() {
            override fun onReceivedSslError(
                view: WebView?,
                handler: SslErrorHandler?,
                error: SslError?
            ) {
                handler?.cancel() // SSL 오류 발생 시 무조건 연결 차단
            }
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val url = request?.url.toString()
                return handleExternalUrl(url)
            }
            override fun onPageStarted(view: WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                progressBar.visibility = FrameLayout.VISIBLE
            }
            override fun onPageFinished(view: WebView?, url: String?) {
                progressBar.visibility = FrameLayout.GONE
                // 페이지 로딩 완료 시 로그인 상태 확인 및 전달
                checkLoginStatus()
            }
        }

        // WebChromeClient (권한 요청 등)
        webView.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest?) {
                runOnUiThread {
                    request?.grant(request.resources)
                }
            }
        }

        // JavaScript 인터페이스 추가 (웹뷰에서 네이티브 앱으로 메시지 전달)
        webView.addJavascriptInterface(object {
            @android.webkit.JavascriptInterface
            fun postMessage(message: String) {
                try {
                    val jsonMessage = JSONObject(message)
                    when (jsonMessage.optString("type")) {
                        "saveLoginInfo" -> {
                            val loginData = jsonMessage.optJSONObject("detail")
                            if (loginData != null) {
                                saveLoginInfo(loginData)
                            }
                        }
                        "clearLoginInfo" -> {
                            clearLoginInfo()
                        }
                    }
                } catch (e: Exception) {
                    println("메시지 처리 실패: $e")
                }
            }
        }, "Android")

        // 권한 요청
        requestPermissionsIfNeeded()

        // 웹사이트 로드
        webView.loadUrl(url)
    }

    // 로그인 정보 저장 (AsyncStorage 대신 SharedPreferences 사용)
    private fun saveLoginInfo(loginData: JSONObject) {
        try {
            val editor = sharedPreferences.edit()
            editor.putString("accessToken", loginData.optString("token"))
            editor.putString("refreshToken", loginData.optString("refreshToken"))
            editor.putString("userEmail", loginData.optString("email"))
            editor.apply()

            // 웹뷰에 로그인 정보 전달
            sendLoginInfoToWebView(true, loginData)
        } catch (error: Exception) {
            println("로그인 정보 저장 실패: $error")
        }
    }

    // 앱 시작 시 토큰 확인
    private fun checkLoginStatus() {
        try {
            val token = sharedPreferences.getString("accessToken", null)
            if (token != null) {
                val loginData = JSONObject().apply {
                    put("token", token)
                    put("refreshToken", sharedPreferences.getString("refreshToken", ""))
                    put("email", sharedPreferences.getString("userEmail", ""))
                }
                
                // 웹뷰에 로그인 상태 전달
                sendLoginInfoToWebView(true, loginData)
            }
        } catch (error: Exception) {
            println("로그인 상태 확인 실패: $error")
        }
    }

    // 웹뷰에 로그인 정보 전달
    private fun sendLoginInfoToWebView(isLoggedIn: Boolean, userInfo: JSONObject) {
        val message = JSONObject().apply {
            put("type", "loginInfoReceived")
            put("detail", JSONObject().apply {
                put("isLoggedIn", isLoggedIn)
                put("userInfo", userInfo)
            })
        }

        runOnUiThread {
            webView.evaluateJavascript(
                "window.postMessage(${message}, '*');",
                null
            )
        }
    }

    // 로그인 정보 삭제 (로그아웃 시)
    private fun clearLoginInfo() {
        val editor = sharedPreferences.edit()
        editor.remove("accessToken")
        editor.remove("refreshToken")
        editor.remove("userEmail")
        editor.apply()

        // 웹뷰에 로그아웃 상태 전달
        sendLoginInfoToWebView(false, JSONObject())
    }

    // 외부 URL 처리 (전화, 이메일, 외부 링크 등)
    private fun handleExternalUrl(url: String): Boolean {
        return if (url.startsWith("http://") || url.startsWith("https://")) {
            false // WebView에서 처리
        } else {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "외부 앱을 실행할 수 없습니다.", Toast.LENGTH_SHORT).show()
            }
            true
        }
    }

    // 네트워크 연결 체크
    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val activeNetwork = connectivityManager.activeNetworkInfo
        return activeNetwork != null && activeNetwork.isConnected
    }

    // 권한 요청
    private fun requestPermissionsIfNeeded() {
        val toRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (toRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, toRequest.toTypedArray(), 100)
        }
    }

    // 뒤로가기 WebView 내비게이션 지원
    override fun onBackPressed() {
        if (this::webView.isInitialized && webView.canGoBack()) {
            webView.goBack()
        } else {
            super.onBackPressed()
        }
    }
}