// Copyright 2016 Google Inc.
// 
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
// 
//      http://www.apache.org/licenses/LICENSE-2.0
// 
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.codelabs.appauth;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.annotation.UiThread;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.AppCompatButton;
import android.support.v7.widget.AppCompatTextView;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import com.squareup.picasso.Picasso;

import net.openid.appauth.AuthState;
import net.openid.appauth.AuthorizationException;
import net.openid.appauth.AuthorizationRequest;
import net.openid.appauth.AuthorizationResponse;
import net.openid.appauth.AuthorizationService;
import net.openid.appauth.AuthorizationServiceConfiguration;
import net.openid.appauth.ResponseTypeValues;
import net.openid.appauth.TokenResponse;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.ref.WeakReference;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

import okio.Okio;

import static com.google.codelabs.appauth.MainApplication.LOG_TAG;

public class MainActivity extends AppCompatActivity {
    private static final int CODE_REQ = 1;
    private static final String SHARED_PREFERENCES_NAME = "AuthStatePreference";
    private static final String AUTH_STATE = "AUTH_STATE";
    private static final String USED_INTENT = "USED_INTENT";

    MainApplication mMainApplication;

    // state
    AuthState mAuthState;

    // views
    AppCompatButton mAuthorize;
    AppCompatButton mMakeApiCall;
    AppCompatButton mSignOut;
    AppCompatTextView mGivenName;
    AppCompatTextView mFamilyName;
    AppCompatTextView mFullName;
    ImageView mProfileView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(LOG_TAG, "onCreate");
        setContentView(R.layout.activity_main);
        mMainApplication = (MainApplication) getApplication();
        mAuthorize = (AppCompatButton) findViewById(R.id.authorize);
        mMakeApiCall = (AppCompatButton) findViewById(R.id.makeApiCall);
        mSignOut = (AppCompatButton) findViewById(R.id.signOut);
        mGivenName = (AppCompatTextView) findViewById(R.id.givenName);
        mFamilyName = (AppCompatTextView) findViewById(R.id.familyName);
        mFullName = (AppCompatTextView) findViewById(R.id.fullName);
        mProfileView = (ImageView) findViewById(R.id.profileImage);

        enablePostAuthorizationFlows();

        // wire click listeners
        mAuthorize.setOnClickListener(new AuthorizeListener(this));
    }

    private void upateUserInfo(final JSONObject object) {
        this.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                try {
                    String fullName = object.getString("name");
                    mFullName.setText(fullName);

                    String givenName = object.getString("given_name");
                    mGivenName.setText(givenName);

                    String familyName = object.getString("family_name");
                    mFamilyName.setText(familyName);

                    String imageUrl = object.getString("picture");
                    Picasso.with(MainActivity.this).load(imageUrl).into(mProfileView);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    private void enablePostAuthorizationFlows() {
        Log.i(LOG_TAG, "enablePostAuthorizationFlows");
        mAuthState = restoreAuthState();
        if (mAuthState != null && mAuthState.isAuthorized()) {
            if (mMakeApiCall.getVisibility() == View.GONE) {
                mMakeApiCall.setVisibility(View.VISIBLE);
                mMakeApiCall.setOnClickListener(new MakeApiCallListener(this, mAuthState, new AuthorizationService(this)));
            }
            if (mSignOut.getVisibility() == View.GONE) {
                mSignOut.setVisibility(View.VISIBLE);
                mSignOut.setOnClickListener(new SignOutListener(this));
            }
        } else {
            mMakeApiCall.setVisibility(View.GONE);
            mSignOut.setVisibility(View.GONE);
        }
    }

    /**
     * Exchanges the code, for the {@link TokenResponse}.
     *
     * @param intent represents the {@link Intent} from the Custom Tabs or the System Browser.
     */
    private void handleAuthorizationResponse(@NonNull Intent intent) {
        Log.i(LOG_TAG, "handleAuthorizationResponse intent: " + intent);
        // code from the step 'Handle the Authorization Response' goes here.
        AuthorizationResponse response = AuthorizationResponse.fromIntent(intent);
        AuthorizationException error = AuthorizationException.fromIntent(intent);
        final AuthState authState = new AuthState(response, error);

        if (response != null) {
            Log.i(LOG_TAG, String.format("Handled Authorization Response %s ", authState.jsonSerializeString()));
            AuthorizationService service = new AuthorizationService(this);
            service.performTokenRequest(response.createTokenExchangeRequest(), new AuthorizationService.TokenResponseCallback() {
                @Override
                public void onTokenRequestCompleted(@Nullable TokenResponse tokenResponse, @Nullable AuthorizationException exception) {
                    if (exception != null) {
                        Log.w(LOG_TAG, "Token Exchange failed", exception);
                    } else {
                        if (tokenResponse != null) {
                            authState.update(tokenResponse, exception);
                            persistAuthState(authState);
                            Log.i(LOG_TAG, String.format("Token Response [ Access Token: %s, ID Token: %s ]", tokenResponse.accessToken, tokenResponse.idToken));
                        }
                    }
                }
            });
        }

    }

    private void persistAuthState(@NonNull AuthState authState) {
        getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE).edit()
                .putString(AUTH_STATE, authState.jsonSerializeString())
                .commit();
        enablePostAuthorizationFlows();
    }

    private void clearAuthState() {
        getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove(AUTH_STATE)
                .apply();
    }

    @Nullable
    private AuthState restoreAuthState() {
        String jsonString = getSharedPreferences(SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
                .getString(AUTH_STATE, null);
        if (!TextUtils.isEmpty(jsonString)) {
            try {
                return AuthState.jsonDeserialize(jsonString);
            } catch (JSONException jsonException) {
                // should never happen
            }
        }
        return null;
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.i(LOG_TAG, "onStart");
        //checkIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.i(LOG_TAG, "onNewIntent");
        //checkIntent(intent);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        Log.i(LOG_TAG, "onActivityResult requestCode=" + requestCode + ", resultCode=" + resultCode);
        if (CODE_REQ == requestCode) {
            checkIntent(data);
            enablePostAuthorizationFlows();
        }
    }

    private void checkIntent(@Nullable Intent intent) {
        if (intent != null) {
            if (!intent.hasExtra(USED_INTENT)) {
                handleAuthorizationResponse(intent);
                intent.putExtra(USED_INTENT, true);
            }
        }
    }

    private static class GetUserInfoThread extends Thread {
        private MainActivity mActivity;
        private String mAccessToken;
        public GetUserInfoThread(MainActivity activity, String accessToken) {
            mActivity = activity;
            mAccessToken = accessToken;
        }

        @Override
        public void run() {

            try {
                URL url = new URL("https://openidconnect.googleapis.com/v1/userinfo");
                HttpsURLConnection conn = (HttpsURLConnection) url.openConnection();
                conn.setRequestProperty("Authorization", "Bearer " + mAccessToken);
                conn.setInstanceFollowRedirects(false);
                String response = Okio.buffer(Okio.source(conn.getInputStream()))
                        .readString(Charset.forName("UTF-8"));
                JSONObject userInfo = new JSONObject(response);

                if (null != userInfo) {
                    Log.i(LOG_TAG, "userInfo: " + userInfo.toString());
                    mActivity.upateUserInfo(userInfo);
                }
            } catch (UnsupportedEncodingException e) {
                Log.e(LOG_TAG, "getUserInfo UnsupportedEncodingException: " + e.toString());
            } catch (IOException e) {
                Log.e(LOG_TAG, "getUserInfo IOException: " + e.toString());
            } catch (JSONException e) {
                Log.e(LOG_TAG, "getUserInfo JSONException: " + e.toString());
            }
        }
    }
    /**
     * Kicks off the authorization flow.
     */
    public static class AuthorizeListener implements Button.OnClickListener {
        private WeakReference<Activity> mActivity;

        public AuthorizeListener(Activity activity) {
            mActivity = new WeakReference<Activity>(activity);
        }

        @Override
        public void onClick(View view) {
            // code from the step 'Create the Authorization Request',
            // and the step 'Perform the Authorization Request' goes here.
            authorize(view, buildAuthorizationRequest());
        }

        private AuthorizationRequest buildAuthorizationRequest() {
            String clientId = "511828570984-fuprh0cm7665emlne3rnf9pk34kkn86s.apps.googleusercontent.com";
            Uri redirectUri = Uri.parse("com.google.codelabs.appauth:/oauth2callback");

            AuthorizationServiceConfiguration serviceConfiguration = new AuthorizationServiceConfiguration(
                    Uri.parse("https://accounts.google.com/o/oauth2/v2/auth") /* auth endpoint */,
                    Uri.parse("https://www.googleapis.com/oauth2/v4/token") /* token endpoint */
            );

            AuthorizationRequest.Builder builder = new AuthorizationRequest.Builder(
                    serviceConfiguration,
                    clientId,
                    ResponseTypeValues.CODE,
                    redirectUri
            );
            builder.setScopes(AuthorizationRequest.Scope.OPENID,
                    AuthorizationRequest.Scope.EMAIL,
                    AuthorizationRequest.Scope.PROFILE);
            AuthorizationRequest request = builder.build();
            return request;
        }

        private void authorize(View view, AuthorizationRequest request) {
            Log.i(LOG_TAG, "authorize begin");
            Context context = view.getContext();
            AuthorizationService authorizationService = new AuthorizationService(context);
            Intent authIntent = authorizationService.getAuthorizationRequestIntent(request);
            Activity activity = mActivity.get();
            if (null != activity) {
                activity.startActivityForResult(authIntent, CODE_REQ);
            }
            Log.i(LOG_TAG, "authorize end");
        }
    }

    public static class SignOutListener implements Button.OnClickListener {

        private final MainActivity mMainActivity;

        public SignOutListener(@NonNull MainActivity mainActivity) {
            mMainActivity = mainActivity;
        }

        @Override
        public void onClick(View view) {
            mMainActivity.mAuthState = null;
            mMainActivity.clearAuthState();
            mMainActivity.enablePostAuthorizationFlows();
        }
    }

    public static class MakeApiCallListener implements Button.OnClickListener {

        private final MainActivity mMainActivity;
        private AuthState mAuthState;
        private AuthorizationService mAuthorizationService;

        public MakeApiCallListener(@NonNull MainActivity mainActivity, @NonNull AuthState authState, @NonNull AuthorizationService authorizationService) {
            mMainActivity = mainActivity;
            mAuthState = authState;
            mAuthorizationService = authorizationService;
        }

        @Override
        public void onClick(View view) {
            // code from the section 'Making API Calls' goes here
            if (null != mAuthState) {
                Log.i(LOG_TAG, "getUserInfo accessToken=" + mAuthState.getAccessToken());
                new GetUserInfoThread(mMainActivity, mAuthState.getAccessToken()).start();
            }
        }
    }

}
