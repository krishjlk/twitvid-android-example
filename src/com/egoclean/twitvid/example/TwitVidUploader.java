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
import android.view.View;
import android.view.Window;
import android.widget.Button;
import com.twitvid.android.sdk.UploadServiceHelper;
import com.twitvid.api.ApiException;
import com.twitvid.api.TwitVidApi;
import com.twitvid.api.bean.Session;
import com.twitvid.api.bean.TwitterAuthPack;
import com.twitvid.api.bean.Values;
import com.twitvid.api.net.HttpClientExecutor;
import org.json.JSONException;
import org.json.JSONObject;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.auth.AccessToken;
import twitter4j.auth.Authorization;
import twitter4j.auth.AuthorizationFactory;
import twitter4j.auth.RequestToken;
import twitter4j.conf.Configuration;
import twitter4j.conf.ConfigurationBuilder;

public class TwitVidUploader extends Activity implements View.OnClickListener, TwitterUtils.BaseAuthChecker.OnAuthCheckerResult {
    private static final int PICK_VIDEO = 8219;
    private Button mUploadButton;
    private ResultReceiver mReceiver;
    private Uri mCurrentVideo;
    private Twitter mTwitter;
    private RequestToken mRequestToken;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.main);

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
                TwitVidApi api = new TwitVidApi(values, HttpClientExecutor.getInstance());
                try {
                    SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
                    String token = prefs.getString(TwitterUtils.TOKEN_KEY, null);
                    String secret = prefs.getString(TwitterUtils.TOKEN_SECRET_KEY, null);
                    String authenticate = api.authenticate(new TwitterAuthPack.Builder()
                            .setConsumerKey(TwitterConstants.CONSUMER_KEY)
                            .setConsumerSecret(TwitterConstants.CONSUMER_SECRET)
                            .setOAuthToken(token)
                            .setOAuthTokenSecret(secret)
                            .build());

                    Session session = new Session();
                    try {
                        JSONObject response = new JSONObject(authenticate).getJSONObject("rsp");
                        session.setTwitvidUserId(response.getString("twitvid_user_id"));
                        session.setProfilePictureUrl(response.getString("profilepic"));
                        session.setToken(response.getString("token"));
                        session.setUserId(response.getString("user_id"));
                        session.setTimeToLive(response.getLong("ttl"));
                        session.setExpirationTimestamp(System.currentTimeMillis() + session.getTimeToLive());
                        Session.UserInfo userInfo = new Session.UserInfo();
                        userInfo.setFacebookId("facebook_id");
                        userInfo.setFacebookName("facebook_name");
                        userInfo.setTwitterId("twitter_id");
                        userInfo.setTwitterName("twitter_name");
                        userInfo.setTwitterScreenName("twitter_screenname");
                        session.setUserInfo(userInfo);

                        api.getValues().setSession(session);

                        UploadServiceHelper.startUpload(this, api, mReceiver, mCurrentVideo,
                                MediaHelper.getVideoType(TwitVidUploader.this, mCurrentVideo),
                                "Testing twitvid app", false, null, true, true);
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
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
                mUploadButton.setText("Upload " + MediaHelper.getVideoName(TwitVidUploader.this, mCurrentVideo));
                mUploadButton.setEnabled(true);
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
    public void onAuthChecker(int result, Twitter twitter) {
        if (result == TwitterUtils.LOGGED_IN) {
            findViewById(R.id.login).setEnabled(false);
            findViewById(R.id.choose).setEnabled(true);
        } else {
            findViewById(R.id.login).setEnabled(true);
            findViewById(R.id.choose).setEnabled(false);
            findViewById(R.id.upload).setEnabled(false);
        }
    }

    private class UploadReceiver extends ResultReceiver {
        public UploadReceiver(Handler handler) {
            super(handler);
        }

        @Override
        protected void onReceiveResult(int resultCode, Bundle resultData) {
            super.onReceiveResult(resultCode, resultData);
        }
    }

    private class TwitterAuthLauncher extends AsyncTask<Void, Void, Void> {

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            setProgressBarIndeterminate(true);
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
            setProgressBarIndeterminate(false);
        }
    }
}
