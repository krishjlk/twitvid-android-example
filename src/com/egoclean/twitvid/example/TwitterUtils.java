package com.egoclean.twitvid.example;

import android.app.ProgressDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.preference.PreferenceManager;
import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.auth.AccessToken;

public class TwitterUtils {
    public static final int LOGGED_IN = 0;
    public static final int NOT_LOGGED_IN = 1;
    public static final int NETWORK_PROBLEMS = 2;
    public static final int OAUTH_PROBLEMS = 3;

    public static final String TOKEN_KEY = "token";
    public static final String TOKEN_SECRET_KEY = "token_secret";

    public static int isAuthenticated(Context context, Twitter twitter) {
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        String token = prefs.getString(TOKEN_KEY, null);
        String secret = prefs.getString(TOKEN_SECRET_KEY, null);
        if (token == null || secret == null) {
            return NOT_LOGGED_IN;
        }
        try {
            AccessToken accessToken = new AccessToken(token, secret);
            if (twitter == null) {
                twitter = new TwitterFactory().getInstance();
            }
            twitter.setOAuthConsumer(TwitterConstants.CONSUMER_KEY, TwitterConstants.CONSUMER_SECRET);
            twitter.setOAuthAccessToken(accessToken);
        } catch (Exception e) {
            e.printStackTrace();
            return OAUTH_PROBLEMS;
        }

        try {
            if (twitter.getId() > 0) {
                return LOGGED_IN;
            }
        } catch (TwitterException e) {
            e.printStackTrace();
            if (e.isCausedByNetworkIssue()) {
                return NETWORK_PROBLEMS;
            }
        } catch (Exception ignored) {
            ignored.printStackTrace();
        }
        return NOT_LOGGED_IN;
    }

    public static class BaseAuthChecker extends AsyncTask<Void, Void, Integer> {
        private Context context;
        private OnAuthCheckerResult callback;
        private ProgressDialog progressDialog;
        private Twitter twitter;

        public BaseAuthChecker(Context context, OnAuthCheckerResult callback) {
            this.context = context;
            this.callback = callback;
            twitter = new TwitterFactory().getInstance();
            progressDialog = new ProgressDialog(context);
            progressDialog.setMessage(context.getResources().getString(R.string.checking_twitter_auth));
            progressDialog.setCancelable(false);
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            progressDialog.show();
        }

        @Override
        protected Integer doInBackground(Void... voids) {
            return isAuthenticated(context, twitter);
        }

        @Override
        protected void onPostExecute(Integer result) {
            super.onPostExecute(result);
            progressDialog.dismiss();
            if (callback != null) {
                callback.onAuthChecker(result, twitter);
            }
        }

        public interface OnAuthCheckerResult{
            void onAuthChecker(int result, Twitter twitter);
        }
    }
}
