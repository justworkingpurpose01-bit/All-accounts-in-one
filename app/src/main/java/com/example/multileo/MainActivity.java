package com.example.multileo;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
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
import android.widget.HorizontalScrollView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.webkit.Profile;
import androidx.webkit.ProfileStore;
import androidx.webkit.WebViewCompat;
import androidx.webkit.WebViewFeature;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.progressindicator.LinearProgressIndicator;

import java.io.File;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MultiLeo";
    public static final String LEONARDO_URL = "https://app.leonardo.ai";
    public static final int NUM_PROFILES = 16;

    private final WebView[] webViews = new WebView[NUM_PROFILES];
    private final boolean[] isProfileLoaded = new boolean[NUM_PROFILES];
    private final int[] profileColors = new int[NUM_PROFILES];
    private final String[] profileNames = new String[NUM_PROFILES];
    private final TextView[] tabChipViews = new TextView[NUM_PROFILES];

    private int currentProfileIndex = 0;
    private boolean isMultiProfileSupported = false;
    private ProfileStore profileStore = null;

    private FrameLayout webViewContainer;
    private HorizontalScrollView hsvTabs;
    private LinearLayout llTabStrip;
    private SwipeRefreshLayout swipeRefreshLayout;
    private LinearProgressIndicator progressBar;

    private LinearLayout layoutLoading;
    private ProgressBar loadingSpinner;
    private TextView tvLoadingTitle;
    private TextView tvLoadingSubtitle;

    private LinearLayout layoutError;
    private MaterialButton btnRetry;

    private ImageButton btnBack;
    private ImageButton btnForward;
    private ImageButton btnRefresh;
    private ImageButton btnClearData;
    private FrameLayout btnTabOverview;
    private TextView tvTabBadge;

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
        cleanCorruptedVariationsSeed();
        setupProfilesAndWebViews();
        buildHorizontalTabStrip();

        switchToProfile(0);
    }

    private void initColorsAndNames() {
        int[] hexColors = {
                0xFF6366F1, 0xFF06B6D4, 0xFF10B981, 0xFFF59E0B,
                0xFFEC4899, 0xFF8B5CF6, 0xFF3B82F6, 0xFF14B8A6,
                0xFF84CC16, 0xFFEAB308, 0xFFF97316, 0xFFEF4444,
                0xFFD946EF, 0xFFA855F7, 0xFF0284C7, 0xFF059669
        };

        for (int i = 0; i < NUM_PROFILES; i++) {
            profileColors[i] = hexColors[i];
            profileNames[i] = "Profile " + (i + 1);
        }
    }

    private void initViews() {
        webViewContainer = findViewById(R.id.webViewContainer);
        hsvTabs = findViewById(R.id.hsvTabs);
        llTabStrip = findViewById(R.id.llTabStrip);
        swipeRefreshLayout = findViewById(R.id.swipeRefreshLayout);
        progressBar = findViewById(R.id.progressBar);

        layoutLoading = findViewById(R.id.layoutLoading);
        loadingSpinner = findViewById(R.id.loadingSpinner);
        tvLoadingTitle = findViewById(R.id.tvLoadingTitle);
        tvLoadingSubtitle = findViewById(R.id.tvLoadingSubtitle);

        layoutError = findViewById(R.id.layoutError);
        btnRetry = findViewById(R.id.btnRetry);

        btnBack = findViewById(R.id.btnBack);
        btnForward = findViewById(R.id.btnForward);
        btnRefresh = findViewById(R.id.btnRefresh);
        btnClearData = findViewById(R.id.btnClearData);
        btnTabOverview = findViewById(R.id.btnTabOverview);
        tvTabBadge = findViewById(R.id.tvTabBadge);

        viewProfileIndicator = findViewById(R.id.viewProfileIndicator);
        tvCurrentProfile = findViewById(R.id.tvCurrentProfile);
        tvIsolationStatus = findViewById(R.id.tvIsolationStatus);

        tvTabBadge.setText(String.valueOf(NUM_PROFILES));

        swipeRefreshLayout.setColorSchemeColors(profileColors[0], profileColors[1], profileColors[2], profileColors[3]);
        swipeRefreshLayout.setOnRefreshListener(() -> {
            WebView activeWebView = getCurrentWebView();
            if (activeWebView != null) {
                activeWebView.reload();
            } else {
                swipeRefreshLayout.setRefreshing(false);
            }
        });

        swipeRefreshLayout.setOnChildScrollUpCallback((parent, child) -> {
            WebView active = getCurrentWebView();
            return active != null && active.getScrollY() > 0;
        });

        btnRetry.setOnClickListener(v -> {
            layoutError.setVisibility(View.GONE);
            layoutLoading.setVisibility(View.VISIBLE);
            WebView active = getCurrentWebView();
            if (active != null) {
                active.reload();
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
        btnTabOverview.setOnClickListener(v -> showTabSwitcherDialog());
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

    private void cleanCorruptedVariationsSeed() {
        try {
            File dataDir = getApplicationContext().getDataDir();
            if (dataDir != null && dataDir.exists()) {
                File webViewDir = new File(dataDir, "app_webview");
                if (webViewDir.exists()) {
                    File seed1 = new File(webViewDir, "variations_seed");
                    if (seed1.exists()) seed1.delete();
                    File seed2 = new File(webViewDir, "variations_seed_new");
                    if (seed2.exists()) seed2.delete();
                }
            }
        } catch (Throwable t) {
            Log.d(TAG, "Seed cleanup skipped", t);
        }
    }

    private void setupProfilesAndWebViews() {
        try {
            isMultiProfileSupported = WebViewFeature.isFeatureSupported(WebViewFeature.MULTI_PROFILE);
            if (isMultiProfileSupported) {
                profileStore = ProfileStore.getInstance();
            }
        } catch (Throwable t) {
            Log.w(TAG, "Multi-profile feature check warning", t);
            isMultiProfileSupported = false;
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
            webView.setLayerType(View.LAYER_TYPE_HARDWARE, null);

            if (isMultiProfileSupported && profileStore != null) {
                try {
                    Profile profile = profileStore.getOrCreateProfile(profileId);
                    WebViewCompat.setProfile(webView, profileId);
                } catch (Throwable t) {
                    Log.w(TAG, "Could not set profile for " + profileId, t);
                }
            }

            configureWebSettings(webView);
            configureClients(webView, profileIndex);

            webViews[i] = webView;
            isProfileLoaded[i] = false;
            webViewContainer.addView(webView);
        }
    }

    private void configureWebSettings(WebView webView) {
        WebSettings settings = webView.getSettings();

        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setJavaScriptCanOpenWindowsAutomatically(true);
        settings.setSupportMultipleWindows(false);

        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(true);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(true);

        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        settings.setMediaPlaybackRequiresUserGesture(false);

        String defaultUa = settings.getUserAgentString();
        String modernUa = defaultUa.replace("; wv", "").replace("Version/4.0 ", "");
        settings.setUserAgentString(modernUa);

        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        try {
            CookieManager cookieManager = CookieManager.getInstance();
            cookieManager.setAcceptCookie(true);
            cookieManager.setAcceptThirdPartyCookies(webView, true);
        } catch (Throwable t) {
            Log.w(TAG, "CookieManager setup warning", t);
        }
    }

    private void configureClients(WebView webView, int profileIndex) {
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (request == null || request.getUrl() == null) return false;
                Uri uri = request.getUrl();
                String scheme = uri.getScheme();

                if (scheme != null && !scheme.equalsIgnoreCase("http") && !scheme.equalsIgnoreCase("https")
                        && !scheme.equalsIgnoreCase("about") && !scheme.equalsIgnoreCase("data")
                        && !scheme.equalsIgnoreCase("javascript")) {
                    try {
                        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
                        startActivity(intent);
                    } catch (ActivityNotFoundException ignored) {
                    }
                    return true;
                }
                return false;
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                if (profileIndex == currentProfileIndex) {
                    progressBar.setVisibility(View.VISIBLE);
                    progressBar.setProgress(15);
                    layoutError.setVisibility(View.GONE);
                    updateNavigationButtonStates();
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                if (profileIndex == currentProfileIndex) {
                    progressBar.setVisibility(View.GONE);
                    layoutLoading.setVisibility(View.GONE);
                    layoutError.setVisibility(View.GONE);
                    swipeRefreshLayout.setRefreshing(false);
                    updateNavigationButtonStates();
                }
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                super.onReceivedError(view, request, error);
                if (profileIndex == currentProfileIndex && request != null && request.isForMainFrame()) {
                    swipeRefreshLayout.setRefreshing(false);
                    progressBar.setVisibility(View.GONE);
                    layoutLoading.setVisibility(View.GONE);
                    layoutError.setVisibility(View.VISIBLE);
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
                        layoutLoading.setVisibility(View.GONE);
                        swipeRefreshLayout.setRefreshing(false);
                    } else if (newProgress > 60) {
                        layoutLoading.setVisibility(View.GONE);
                    }
                    updateNavigationButtonStates();
                }
            }

            @Override
            public void onPermissionRequest(PermissionRequest request) {
                try {
                    request.grant(request.getResources());
                } catch (Throwable t) {
                    Log.w(TAG, "Permission request grant failed", t);
                }
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
                    try {
                        fileChooserLauncher.launch(Intent.createChooser(fallback, "Select Image"));
                    } catch (Exception ex) {
                        mFilePathCallback.onReceiveValue(null);
                        mFilePathCallback = null;
                    }
                }
                return true;
            }
        });
    }

    private void buildHorizontalTabStrip() {
        llTabStrip.removeAllViews();
        int padH = dpToPx(14);
        int padV = dpToPx(6);
        int marginH = dpToPx(4);

        for (int i = 0; i < NUM_PROFILES; i++) {
            final int index = i;
            TextView chip = new TextView(this);
            chip.setText("P" + (i + 1));
            chip.setTextSize(13f);
            chip.setPadding(padH, padV, padH, padV);
            chip.setGravity(android.view.Gravity.CENTER);

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
            );
            lp.setMargins(marginH, 0, marginH, 0);
            chip.setLayoutParams(lp);

            chip.setOnClickListener(v -> switchToProfile(index));

            tabChipViews[i] = chip;
            llTabStrip.addView(chip);
        }
    }

    public void switchToProfile(int index) {
        if (index < 0 || index >= NUM_PROFILES) return;
        int prevIndex = currentProfileIndex;
        currentProfileIndex = index;

        if (prevIndex != index && webViews[prevIndex] != null) {
            webViews[prevIndex].onPause();
            webViews[prevIndex].setVisibility(View.GONE);
        }

        int activeColor = profileColors[index];

        viewProfileIndicator.setBackgroundColor(activeColor);
        tvCurrentProfile.setText(profileNames[index]);
        tvCurrentProfile.setTextColor(Color.WHITE);
        progressBar.setIndicatorColor(activeColor);
        loadingSpinner.setIndeterminateTintList(ColorStateList.valueOf(activeColor));
        btnRetry.setBackgroundColor(activeColor);

        tvLoadingTitle.setText("Connecting to Leonardo.ai…");
        tvLoadingSubtitle.setText(profileNames[index] + " • Isolated Session");

        for (int i = 0; i < NUM_PROFILES; i++) {
            TextView chip = tabChipViews[i];
            if (chip != null) {
                if (i == index) {
                    GradientDrawable activeBg = new GradientDrawable();
                    activeBg.setShape(GradientDrawable.RECTANGLE);
                    activeBg.setCornerRadius(dpToPx(16));
                    activeBg.setColor(activeColor);
                    chip.setBackground(activeBg);
                    chip.setTextColor(Color.WHITE);
                    chip.setTypeface(null, android.graphics.Typeface.BOLD);
                } else {
                    GradientDrawable inactiveBg = new GradientDrawable();
                    inactiveBg.setShape(GradientDrawable.RECTANGLE);
                    inactiveBg.setCornerRadius(dpToPx(16));
                    inactiveBg.setColor(0xFF24334C);
                    inactiveBg.setStroke(dpToPx(1), 0xFF334155);
                    chip.setBackground(inactiveBg);
                    chip.setTextColor(0xFF94A3B8);
                    chip.setTypeface(null, android.graphics.Typeface.NORMAL);
                }
            }
        }

        TextView selectedChip = tabChipViews[index];
        if (selectedChip != null) {
            hsvTabs.post(() -> {
                int screenWidth = hsvTabs.getWidth();
                int chipLeft = selectedChip.getLeft();
                int chipWidth = selectedChip.getWidth();
                int scrollPos = chipLeft - (screenWidth / 2) + (chipWidth / 2);
                hsvTabs.smoothScrollTo(Math.max(0, scrollPos), 0);
            });
        }

        WebView currentWebView = webViews[index];
        if (currentWebView != null) {
            currentWebView.setVisibility(View.VISIBLE);
            currentWebView.onResume();

            if (!isProfileLoaded[index]) {
                isProfileLoaded[index] = true;
                layoutLoading.setVisibility(View.VISIBLE);
                layoutError.setVisibility(View.GONE);
                currentWebView.loadUrl(LEONARDO_URL);
            } else {
                layoutLoading.setVisibility(View.GONE);
                layoutError.setVisibility(View.GONE);
            }
        }

        updateNavigationButtonStates();
    }

    private void showTabSwitcherDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Switch Profile (16 Sessions)");

        CharSequence[] items = new CharSequence[NUM_PROFILES];
        for (int i = 0; i < NUM_PROFILES; i++) {
            String status = (i == currentProfileIndex) ? "  [Active]" : (isProfileLoaded[i] ? "  [Ready]" : "  [Idle]");
            items[i] = "Profile " + (i + 1) + status;
        }

        builder.setItems(items, (dialog, which) -> switchToProfile(which));
        builder.setNegativeButton("Cancel", null);
        builder.show();
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

    private void showClearDataConfirmationDialog() {
        new AlertDialog.Builder(this)
                .setTitle(profileNames[currentProfileIndex])
                .setMessage("Are you sure you want to clear cookies, cache, and local storage for this profile?")
                .setPositiveButton("Clear", (dialog, which) -> clearCurrentProfileData())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void clearCurrentProfileData() {
        WebView active = getCurrentWebView();
        if (active == null) return;

        String profileId = "profile_" + (currentProfileIndex + 1);

        if (isMultiProfileSupported && profileStore != null) {
            try {
                Profile profile = profileStore.getProfile(profileId);
                if (profile != null) {
                    profile.getCookieManager().removeAllCookies(null);
                    profile.getWebStorage().deleteAllData();
                }
            } catch (Throwable t) {
                Log.w(TAG, "Error clearing profile store", t);
            }
        }

        active.clearCache(true);
        active.clearFormData();
        active.clearHistory();
        layoutLoading.setVisibility(View.VISIBLE);
        active.loadUrl(LEONARDO_URL);

        Toast.makeText(this, "Profile data cleared (" + profileNames[currentProfileIndex] + ")", Toast.LENGTH_SHORT).show();
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    @Override
    protected void onPause() {
        super.onPause();
        WebView active = getCurrentWebView();
        if (active != null) {
            active.onPause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        WebView active = getCurrentWebView();
        if (active != null) {
            active.onResume();
        }
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
