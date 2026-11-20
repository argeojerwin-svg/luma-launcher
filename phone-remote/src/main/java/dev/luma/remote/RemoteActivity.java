package dev.luma.remote;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.net.URI;

/** Native app shell for the projector's existing private remote page. */
public class RemoteActivity extends Activity {
    private SharedPreferences prefs;
    private LinearLayout root;
    private WebView web;
    private URI paired;
    private TextView status;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        prefs=getSharedPreferences("pairing",MODE_PRIVATE);
        paired=RemoteAddress.parse(prefs.getString("url",""));
        if(paired==null)pair();else remote();
    }
    private int dp(int value){return Math.round(value*getResources().getDisplayMetrics().density);}
    private void screen(){
        if(web!=null){web.stopLoading();web.destroy();web=null;}
        root=new LinearLayout(this);root.setOrientation(LinearLayout.VERTICAL);root.setBackgroundColor(Color.rgb(16,25,40));
        root.setPadding(dp(16),dp(16),dp(16),dp(16));
        root.setOnApplyWindowInsetsListener((view,insets)->{
            view.setPadding(dp(16)+insets.getSystemWindowInsetLeft(),dp(16)+insets.getSystemWindowInsetTop(),dp(16)+insets.getSystemWindowInsetRight(),dp(16)+insets.getSystemWindowInsetBottom());return insets;
        });
        setContentView(root);root.requestApplyInsets();
    }
    private TextView label(String value,int size){
        TextView text=new TextView(this);text.setText(value);text.setTextColor(Color.WHITE);text.setTextSize(size);text.setPadding(0,dp(8),0,dp(8));root.addView(text);return text;
    }
    private Button button(String title,Runnable action){
        Button button=new Button(this);button.setText(title);button.setOnClickListener(v->action.run());return button;
    }
    private void pair(){
        screen();label("Luma Remote",26);
        label("Paste the complete private QR link from your projector. Keep the http:// address and everything after #. Your phone and projector need the same Wi-Fi.",16);
        EditText input=new EditText(this);input.setSingleLine(false);input.setMinLines(3);input.setTextColor(Color.WHITE);input.setHintTextColor(Color.LTGRAY);
        input.setHint("http://projector-ip:19767/#private-token");input.setInputType(android.text.InputType.TYPE_CLASS_TEXT|android.text.InputType.TYPE_TEXT_VARIATION_URI|android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setText(prefs.getString("url",""));input.setAutofillHints((String[])null);root.addView(input,new LinearLayout.LayoutParams(-1,-2));
        TextView error=label("",14);
        root.addView(button("Connect",()->{
            URI candidate=RemoteAddress.parse(input.getText().toString());
            if(candidate==null){error.setText("Paste the full local link, including port 19767 and its private # token.");return;}
            paired=candidate;prefs.edit().putString("url",paired.toString()).apply();remote();
        }));
        if(paired!=null)root.addView(button("Back to remote",this::remote));
        root.addView(button("Forget projector",()->new AlertDialog.Builder(this).setMessage("Remove this phone's saved projector link?")
                .setNegativeButton("Cancel",null).setPositiveButton("Forget",(d,w)->{prefs.edit().clear().apply();paired=null;pair();}).show()));
    }
    @SuppressWarnings("SetJavaScriptEnabled")
    private void remote(){
        screen();
        LinearLayout toolbar=new LinearLayout(this);
        toolbar.addView(button("Reconnect",()->web.loadUrl(paired.toString())),new LinearLayout.LayoutParams(0,-2,1));
        toolbar.addView(button("Projector",this::pair),new LinearLayout.LayoutParams(0,-2,1));root.addView(toolbar);
        status=label("Connecting…",13);
        web=new WebView(this);web.setBackgroundColor(Color.rgb(16,25,40));
        WebSettings settings=web.getSettings();settings.setJavaScriptEnabled(true);settings.setAllowFileAccess(false);settings.setAllowContentAccess(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);settings.setSupportMultipleWindows(false);settings.setJavaScriptCanOpenWindowsAutomatically(false);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView view,WebResourceRequest request){return !RemoteAddress.samePage(paired,request.getUrl().toString());}
            @Override public void onPageFinished(WebView view,String url){if(RemoteAddress.samePage(paired,url))status.setText("Same Wi-Fi · private projector remote");}
            @Override public void onReceivedError(WebView view,WebResourceRequest request,WebResourceError error){
                if(request.isForMainFrame())status.setText("Can't connect. Check projector power, Wi-Fi and its IP address, then tap Reconnect.");
            }
        });
        root.addView(web,new LinearLayout.LayoutParams(-1,0,1));web.loadUrl(paired.toString());
    }
    @Override public void onBackPressed(){if(web!=null)pair();else super.onBackPressed();}
    @Override public void onDestroy(){if(web!=null){web.destroy();web=null;}super.onDestroy();}
}
