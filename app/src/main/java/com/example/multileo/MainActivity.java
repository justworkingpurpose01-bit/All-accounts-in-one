package com.example.multileo;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.PermissionRequest;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.webkit.Profile;
import androidx.webkit.ProfileStore;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import com.example.R;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.progressindicator.LinearProgressIndicator;

public class MainActivity extends AppCompatActivity {

    public static final String LEONARDO_URL = "https://app.leonardo.ai";
    private static final int NUM_PROFILES = 4;

    private final WebView[] webViews = new WebView[NUM_PROFILES];
    private final int[] profileColors = new int[NUM_PROFILES];
    private final String[] profileNames = new String[NUM_PROFILES];

    private int currentProfileIndex = 0;

    private FrameLayout webViewContainer;
    private BottomNavigationView bottomNav;
    private SwipeRefreshLayout swipeRefreshLayout;
    private LinearProgressIndicator progressBar;

    private ImageButton btnBack;
    private ImageButton btnForward;
    private ImageButton btnRefresh;
    private ImageButton btnClearData;

    private View viewProfileIndicator;
    private TextView tvCurrentProfile;
    private TextView tvIsolationStatus;

    private ValueCallback<Uri[]> mFilePathCallback;
    private final ActivityResultLauncher<Intent> fileChooserLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (mFilePathCallback != null) {
                    Uri[] results = null;
                    if (result.getResultCode() == Activity.RESULT_OK && result.getData() != null) {
                        Intent data = result.getData();
                        if (data.getClipData() != null) {
                            int count = data.getClipData().getItemCount();
                            results = new Uri[count];
                            for (int i = 0; i < count; i++) {
                                results[i] = data.getClipData().getItemAt(i).getUri();
                            }
                        } else if (data.getData() != null) {
                            results = new Uri[]{data.getData()};
                        }
                    }
                    mFilePathCallback.onReceiveValue(results);
                    mFilePathCallback = null;
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initColorsAndNames();
        initViews();
        setupNavigationControls();
        setupBackHandler();
        setupProfilesAndWebViews();
        switchToProfile(0);
    }

    private void initColorsAndNames() {
        profileColors[0] = ContextCompat.getColor(this, R.color.profile_1_color);
        profileColors[1] = ContextCompat.getColor(this, R.color.profile_2_color);
        profileColors[2] = ContextCompat.getColor(this, R.color.profile_3_color);
        profileColors[3] = ContextCompat.getColor(this, R.color.profile_4_color);

        profileNames[0] = getString(R.string.title_profile_1);
        profileNames[1] = getString(R.string.title_profile_2);
        profileNames[2] = getString(R.string.title_profile_3);
        profileNames[3] = getString(R.string.title_profile_4);
    }

    private void initViews() {
        webViewContainer = findViewById(R.id.webViewContainer);
        bottomNav = findViewById(R.id.bottomNav);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        progressBar = findViewById(R.id.progressBar);

        btnBack = findViewById(R.id.btnBack);
        btnForward = findViewById(R.id.btnForward);
        btnRefresh = findViewById(R.id.btnRefresh);
        btnClearData = findViewById(R.id.btnClearData);

        viewProfileIndicator = findViewById(R.id.viewProfileIndicator);
        tvCurrentProfile = findViewById(R.id.tvCurrentProfile);
        tvIsolationStatus = findViewById(R.id.tvIsolationStatus);

        swipeRefreshLayout.setColorSchemeColors(profileColors[0], profileColors[1], profileColors[2], profileColors[3]);
        swipeRefreshLayout.setOnRefreshListener(() -> {
            WebView activeWebView = getCurrentWebView();
            if (activeWebView != null) {
                activeWebView.reload();
            } else {
                swipeRefreshLayout.setRefreshing(false);
            }
        });
    }

    private void setupNavigationControls() {
        btnBack.setOnClickListener(v -> {
            WebView active = getCurrentWebView();
            if (active != null && active.canGoBack()) {
                active.goBack();
            }
        });

        btnForward.setOnClickListener(v -> {
            WebView active = getCurrentWebView();
            if (active != null && active.canGoForward()) {
                active.goForward();
            }
        });

        btnRefresh.setOnClickListener(v -> {
            WebView active = getCurrentWebView();
            if (active != null) {
                active.reload();
            }
        });

        btnClearData.setOnClickListener(v -> showClearDataConfirmationDialog());

        bottomNav.setOnItemSelectedListener(item -> {
            int itemId = item.getItemId();
            if (itemId == R.id.menu_profile_1) {
                switchToProfile(0);
                return true;
            } else if (itemId == R.id.menu_profile_2) {
                switchToProfile(1);
                return true;
            } else if (itemId == R.id.menu_profile_3) {
                switchToProfile(2);
                return true;
            } else if (itemId == R.id.menu_profile_4) {
                switchToProfile(3);
                return true;
            }
            return false;
        });
    }

    private void setupBackHandler() {
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                WebView active = getCurrentWebView();
                if (active != null && active.canGoBack()) {
                    active.goBack();
                } else {
                    setEnabled(false);
                    getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    /**
     * Initializes 4 isolated WebView instances.
     * Uses AndroidX WebKit ProfileStore (Multi-Profile API) to bind each WebView
     * to a unique profile ("profile_0", "profile_1", etc.) for strict sandbox isolation
     * of cookies, cache, localStorage, and IndexedDB.
     */
    private void setupProfilesAndWebViews() {
        boolean isMultiProfileSupported = WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE);
        ProfileStore profileStore = null;

        if (isMultiProfileSupported) {
            profileStore = ProfileStore.getInstance();
        }

        for (int i = 0; i < NUM_PROFILES; i++) {
            final int profileIndex = i;
            final String profileId = "profile_" + (i + 1);

            WebView webView = new WebView(this);
            webView.setLayoutParams(new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
            ));
            webView.setVisibility(View.GONE);

            // Configure multi-profile isolation before loading any URL
            if (isMultiProfileSupported && profileStore != null) {
                Profile profile = profileStore.getOrCreateProfile(profileId);
                WebViewCompat.setProfile(webView, profileId);
            }

            configureWebSettings(webView);
            configureClients(webView, profileIndex);

            // Load Leonardo.ai session for this profile
            webView.loadUrl(LEONARDO_URL);

            webViews[i] = webView;
            webViewContainer.addView(webView);
        }
    }

    private void configureWebSettings(WebView webView) {
        WebSettings settings = webView.getSettings();

        // 1. JavaScript & Storage
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);

        // 2. Hardware acceleration & rendering
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(true);

        // 3. File & Resource access for creative canvas uploads
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        settings.setMediaPlaybackRequiresUserGesture(false);

        // 4. Modern Chrome Mobile User-Agent
        // Stripping WebView tags ('; wv' and 'Version/4.0') prevents Google OAuth restrictions
        // and enables full Leonardo.ai WebGL / modern web features.
        String defaultUa = settings.getUserAgentString();
        String modernUa = defaultUa.replace("; wv", "").replace("Version/4.0 ", "");
        settings.setUserAgentString(modernUa);

        // 5. Cache settings
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        // 6. Cookies
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);
    }

    private void configureClients(WebView webView, int profileIndex) {
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                // Keep all navigation within this profile's isolated WebView
                return false;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                if (profileIndex == currentProfileIndex) {
                    progressBar.setVisibility(View.VISIBLE);
                    progressBar.setProgress(10);
                    updateNavigationButtonStates();
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (profileIndex == currentProfileIndex) {
                    progressBar.setVisibility(View.GONE);
                    swipeRefreshLayout.setRefreshing(false);
                    updateNavigationButtonStates();
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (profileIndex == currentProfileIndex) {
                    swipeRefreshLayout.setRefreshing(false);
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                super.onProgressChanged(view, newProgress);
                if (profileIndex == currentProfileIndex) {
                    progressBar.setProgress(newProgress);
                    if (newProgress >= 100) {
                        progressBar.setVisibility(View.GONE);
                        swipeRefreshLayout.setRefreshing(false);
                    } else {
                        progressBar.setVisibility(View.VISIBLE);
                    }
                    updateNavigationButtonStates();
                }
            }

            @Override
            public void onPermissionRequest(PermissionRequest request) {
                // Grant camera/microphone/canvas permissions requested by the web app
                request.grant(request.getResources());
            }

            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> filePathCallback, FileChooserParams fileChooserParams) {
                if (mFilePathCallback != null) {
                    mFilePathCallback.onReceiveValue(null);
                    mFilePathCallback = null;
                }
                mFilePathCallback = filePathCallback;

                try {
                    Intent intent = fileChooserParams.createIntent();
                    fileChooserLauncher.launch(intent);
                } catch (Exception e) {
                    Intent fallback = new Intent(Intent.ACTION_GET_CONTENT);
                    fallback.addCategory(Intent.CATEGORY_OPENABLE);
                    fallback.setType("image/*");
                    fileChooserLauncher.launch(Intent.createChooser(fallback, "Select Image"));
                }
                return true;
            }
        });
    }

    /**
     * Smoothly switches between isolated profiles without destroying active sessions or WebViews.
     */
    private void switchToProfile(int index) {
        if (index < 0 || index >= NUM_PROFILES) return;
        currentProfileIndex = index;

        for (int i = 0; i < NUM_PROFILES; i++) {
            if (webViews[i] != null) {
                webViews[i].setVisibility(i == index ? View.VISIBLE : View.GONE);
            }
        }

        // Update Top Bar & Profile Identity
        int activeColor = profileColors[index];
        viewProfileIndicator.setBackgroundColor(activeColor);
        tvCurrentProfile.setText(profileNames[index]);
        tvCurrentProfile.setTextColor(ContextCompat.getColor(this, R.color.text_primary));
        progressBar.setIndicatorColor(activeColor);

        updateNavigationButtonStates();
    }

    private void updateNavigationButtonStates() {
        WebView active = getCurrentWebView();
        if (active != null) {
            btnBack.setEnabled(active.canGoBack());
            btnBack.setAlpha(active.canGoBack() ? 1.0f : 0.4f);

            btnForward.setEnabled(active.canGoForward());
            btnForward.setAlpha(active.canGoForward() ? 1.0f : 0.4f);
        }
    }

    private WebView getCurrentWebView() {
        if (currentProfileIndex >= 0 && currentProfileIndex < NUM_PROFILES) {
            return webViews[currentProfileIndex];
        }
        return null;
    }

    /**
     * Prompts the user to clear cookies, cache, and local storage for the active profile only.
     */
    private void showClearDataConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle(profileNames[currentProfileIndex])
                .setMessage(getString(R.string.action_clear_confirm))
                .setPositiveButton(R.string.dialog_clear, (dialog, which) -> clearCurrentProfileData())
                .setNegativeButton(R.string.dialog_cancel, null)
                .show();
    }

    private void clearCurrentProfileData() {
        WebView active = getCurrentWebView();
        if (active == null) return;

        String profileId = "profile_" + (currentProfileIndex + 1);

        if (WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE)) {
            ProfileStore store = ProfileStore.getInstance();
            Profile profile = store.getProfile(profileId);
            if (profile != null) {
                profile.getCookieManager().removeAllCookies(null);
                profile.getWebStorage().deleteAllData();
            }
        }

        active.clearCache(true);
        active.clearFormData();
        active.clearHistory();
        active.loadUrl(LEONARDO_URL);

        Toast.makeText(this, getString(R.string.clear_success) + " (" + profileNames[currentProfileIndex] + ")", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        for (int i = 0; i < NUM_PROFILES; i++) {
            if (webViews[i] != null) {
                webViews[i].destroy();
            }
        }
        super.onDestroy();
    }
}
