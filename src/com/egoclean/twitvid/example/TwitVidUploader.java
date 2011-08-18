package com.egoclean.twitvid.example;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.ResultReceiver;
import android.preference.PreferenceManager;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import com.twitvid.android.sdk.SessionPersister;
import com.twitvid.android.sdk.UploadServiceHelper;
import com.twitvid.api.ApiException;
import com.twitvid.api.TwitvidApi;
import com.twitvid.api.bean.Session;
import com.twitvid.api.bean.TwitterAuthPack;
import com.twitvid.api.bean.Values;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.auth.AccessToken;
import twitter4j.auth.Authorization;
import twitter4j.auth.AuthorizationFactory;
import twitter4j.auth.RequestToken;
import twitter4j.conf.Configuration;
import twitter4j.conf.ConfigurationBuilder;

import static com.twitvid.android.sdk.ResultReceiverConstants.*;

public class TwitvidUploader extends Activity implements View.OnClickListener, TwitterUtils.BaseAuthChecker.OnAuthCheckerResult {
    private static final int PICK_VIDEO = 8219;
    private static final int CHOOSE_CHILD = 0;
    private static final int UPLOAD_CHILD = 1;
    private Button mUploadButton;
    private ResultReceiver mReceiver;
    private Uri mCurrentVideo;
    private Twitter mTwitter;
    private RequestToken mRequestToken;
    private ProgressBar mProgressBar;
    private ViewSwitcher mViewSwitcher;
    private EditText mMessageBox;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.main);

        mViewSwitcher = (ViewSwitcher) findViewById(R.id.switcher);
        mMessageBox = (EditText) findViewById(R.id.message);
        mProgressBar = (ProgressBar) findViewById(R.id.progressbar);
        mUploadButton = (Button) findViewById(R.id.upload);
        mReceiver = new UploadReceiver(new Handler());

        new TwitterUtils.BaseAuthChecker(this, this).execute();
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.login:
                new TwitterAuthLauncher().execute();
                break;
            case R.id.choose:
                Intent intent = new Intent(Intent.ACTION_PICK);
                intent.setType("video/*");
                startActivityForResult(intent, PICK_VIDEO);
                break;
            case R.id.upload:
                Values values = new Values();
                values.setSession(new Session());
                try {
                    TwitvidApi api = SessionPersister.getApi(this);
                    if (!api.getValues().getSession().isValid()) {
                        setProgressBarIndeterminateVisibility(true);
                        api = new TwitvidApi(values);
                        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
                        String token = prefs.getString(TwitterUtils.TOKEN_KEY, null);
                        String secret = prefs.getString(TwitterUtils.TOKEN_SECRET_KEY, null);
                        Session session = api.authenticate(new TwitterAuthPack.Builder()
                                .setConsumerKey(TwitterConstants.CONSUMER_KEY)
                                .setConsumerSecret(TwitterConstants.CONSUMER_SECRET)
                                .setOAuthToken(token)
                                .setOAuthTokenSecret(secret)
                                .build());

                        api.getValues().setSession(session);
                        SessionPersister.persist(this, api);
                    }
                    setProgressBarIndeterminateVisibility(true);
                    InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
                    imm.hideSoftInputFromWindow(mMessageBox.getWindowToken(), 0);
                    UploadServiceHelper.startUpload(this, mReceiver, mCurrentVideo,
                            mMessageBox.getText().toString(), null, false, false);
                } catch (ApiException e) {
                    e.printStackTrace();
                }
                break;
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK) {
            return;
        }
        switch (requestCode) {
            case PICK_VIDEO:
                mCurrentVideo = data.getData();
                mViewSwitcher.setDisplayedChild(UPLOAD_CHILD);
                break;
        }
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        final Uri uri = intent.getData();
        if (uri != null && uri.getScheme().equals(TwitterConstants.CALLBACK_SCHEME)) {
            final String oauthVerifier = uri.getQueryParameter("oauth_verifier");
            try {
                AccessToken oAuthAccessToken = mTwitter.getOAuthAccessToken(mRequestToken, oauthVerifier);
                SharedPreferences.Editor editor = PreferenceManager.getDefaultSharedPreferences(this).edit();
                editor.putString(TwitterUtils.TOKEN_KEY, oAuthAccessToken.getToken());
                editor.putString(TwitterUtils.TOKEN_SECRET_KEY, oAuthAccessToken.getTokenSecret());
                editor.commit();
                new TwitterUtils.BaseAuthChecker(this, this).execute();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void onAuthChecker(int result) {
        if (result == TwitterUtils.LOGGED_IN) {
            findViewById(R.id.login).setEnabled(false);
            findViewById(R.id.choose).setEnabled(true);
        } else {
            findViewById(R.id.login).setEnabled(true);
            findViewById(R.id.choose).setEnabled(false);
            findViewById(R.id.upload).setEnabled(false);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && mViewSwitcher.getDisplayedChild() == UPLOAD_CHILD) {
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && mViewSwitcher.getDisplayedChild() == UPLOAD_CHILD) {
            mViewSwitcher.setDisplayedChild(CHOOSE_CHILD);
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    private class UploadReceiver extends ResultReceiver {
        private long totalBytes;

        private long size;

        public UploadReceiver(Handler handler) {
            super(handler);
        }

        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {
            super.onReceiveResult(resultCode, resultData);
            switch (resultCode) {
                case ON_UPLOAD_START:
                    setProgressBarIndeterminateVisibility(true);
                    break;
                case ON_BYTES_UPLOADED:
                    long totalBytesSoFar = resultData.getLong(TOTAL_BYTES_SO_FAR);
                    size = resultData.getLong(SIZE);
                    mProgressBar.setProgress((int) ((totalBytesSoFar + totalBytes) * 100 / size));
                    break;
                case ON_UPLOAD_FINISH:
                    totalBytes = resultData.getLong(TOTAL_BYTES);
                    if (totalBytes == size) {
                        Toast.makeText(TwitvidUploader.this, R.string.upload_finished, Toast.LENGTH_LONG).show();
                        mProgressBar.setProgress(0);
                        setProgressBarIndeterminateVisibility(false);
                        mViewSwitcher.setDisplayedChild(CHOOSE_CHILD);
                    }
                    break;
            }
        }

    }

    private class TwitterAuthLauncher extends AsyncTask<Void, Void, Void> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            setProgressBarIndeterminateVisibility(true);
        }

        @Override
        protected Void doInBackground(Void... voids) {
            Configuration conf = new ConfigurationBuilder()
                    .setOAuthConsumerKey(TwitterConstants.CONSUMER_KEY)
                    .setOAuthConsumerSecret(TwitterConstants.CONSUMER_SECRET)
                    .build();
            Authorization authorization = AuthorizationFactory.getInstance(conf);
            mTwitter = new TwitterFactory().getInstance(authorization);
            try {
                mRequestToken = mTwitter.getOAuthRequestToken(TwitterConstants.CALLBACK_URL);
                String url = mRequestToken.getAuthenticationURL();
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP | Intent.FLAG_ACTIVITY_NO_HISTORY | Intent.FLAG_FROM_BACKGROUND);
                startActivity(intent);
            } catch (TwitterException e) {
                e.printStackTrace();
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void aVoid) {
            super.onPostExecute(aVoid);
            setProgressBarIndeterminateVisibility(false);
        }

    }
}
