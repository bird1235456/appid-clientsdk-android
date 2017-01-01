package com.ibm.mobilefirstplatform.appid_clientsdk_android;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import com.ibm.mobilefirstplatform.clientsdk.android.core.api.BMSClient;
import com.ibm.mobilefirstplatform.clientsdk.android.core.api.Response;
import com.ibm.mobilefirstplatform.clientsdk.android.core.api.ResponseListener;
import com.ibm.mobilefirstplatform.clientsdk.android.security.api.UserIdentity;

import org.json.JSONObject;
import java.net.URL;
import java.util.InputMismatchException;
import java.util.Map;

/**
 * Created by rotembr on 04/12/2016.
 */

public class AppId {

    private static AppId instance;
    private String tenantId;
    private String bluemixRegionSuffix;
    private AppIdAuthorizationManager appIdAuthorizationManager;
    private AppIdPreferences preferences;
    private String facebookRealm = "wl_facebookRealm";
    private String googleRealm = "wl_googleRealm";

    protected static String redirectUri;
    public static String overrideServerHost = null;
    public final static String REGION_US_SOUTH = ".ng.bluemix.net";
    public final static String REGION_UK = ".eu-gb.bluemix.net";
    public final static String REGION_SYDNEY = ".au-syd.bluemix.net";

    private AppId() {}

    /**
     * Init singleton instance with context, tenantId and Bluemix region.
     * @param context Application context
     * @param tenantId the unique tenant id of the MCA service instance that the application connects to.
     * @param bluemixRegion Specifies the Bluemix deployment to use.
     * @return The singleton instance
     */
    public static synchronized AppId createInstance(Context context, String tenantId, String bluemixRegion) {
        if(null == instance ) {
            if (null == tenantId) {
                throw new InputMismatchException("tenantId can't be null");
            }
            if (null == bluemixRegion) {
                throw new InputMismatchException("bluemixRegion can't be null");
            }
            instance = new AppId();
            instance.tenantId = tenantId;
            instance.bluemixRegionSuffix = bluemixRegion;
            BMSClient.getInstance().initialize(context, bluemixRegion);
            instance.appIdAuthorizationManager = AppIdAuthorizationManager.createInstance(context);
            BMSClient.getInstance().setAuthorizationManager(instance.appIdAuthorizationManager);
            instance.preferences = instance.appIdAuthorizationManager.getPreferences();
            AppId.redirectUri = "https://" + instance.appIdAuthorizationManager.getAppIdentity().getId() + "/mobile/callback";
        }
        return instance;
    }

    /**
     * @return The singleton instance
     */
    public static AppId getInstance() {
        if (instance == null) {
            throw new IllegalStateException("getInstance can't be called before createInstance");
        }
        return instance;
    }

    /**
     * Pop out AppId login widget, to prompt user authentication.
     * @param context Application context
     * @param listener The listener whose onSuccess or onFailure methods will be called when the login ends
     *
     */
    public void login(final Context context, final ResponseListener listener) {
        this.appIdAuthorizationManager.setResponseListener(listener);
        if (preferences.clientId.get() == null || !preferences.tenantId.get().equals(tenantId)) {
            final AppIdRegistrationManager appIdRM = appIdAuthorizationManager.getAppIdRegistrationManager();
            appIdRM.invokeInstanceRegistrationRequest(context, new ResponseListener() {
                @Override
                public void onSuccess(Response response) {
                    preferences.tenantId.set(tenantId);
                    startWebViewActivity(context);
                }
                @Override
                public void onFailure(Response response, Throwable t, JSONObject extendedInfo) {
                    listener.onFailure(response, t, extendedInfo);
                }
            });
        } else {
              startWebViewActivity(context);
        }
    }

    private void startWebViewActivity(Context context) {
        try {
            Intent intent = new Intent(context, WebViewActivity.class);
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            context.startActivity(intent);
        }catch (ActivityNotFoundException e) {
            appIdAuthorizationManager.handleAuthorizationFailure(null, e, null);
        }
    }

    /**
     * @return The MCA instance tenantId
     */
    public String getTenantId(){
        return tenantId;
    }

    /**
     * @return Bluemix region suffix ,use to build URLs
     */
    public String getBluemixRegionSuffix(){
        return bluemixRegionSuffix;
    }

    public String getCachedAuthorizationHeader() {
        return appIdAuthorizationManager.getCachedAuthorizationHeader();
    }

    /**
     * @return authorized user identity. Will return null if user is not yet authorized
     */
    public UserIdentity getUserIdentity() {
        return appIdAuthorizationManager.getUserIdentity();
    }

    /**
     * @return The URL of the authenticated user profile picture or null in case there is no an authenticated user.
     */
    public URL getUserProfilePicture() {
        try {
        Map map = preferences.userIdentity.getAsMap();
        if(null != map){
                JSONObject attributes = (JSONObject) map.get("attributes");
                String authBy = (String) map.get(UserIdentity.AUTH_BY);
                if(null == authBy) {
                    return null;
                }
                if(authBy.equals(facebookRealm)) {
                    JSONObject picture = attributes.getJSONObject("picture");
                    JSONObject data = picture.getJSONObject("data");
                    String stringUrl = data.getString("url");
                    return new URL(stringUrl);
                }
                if(authBy.equals(googleRealm)) {
                    String picture = attributes.getString("picture");
                    return new URL(picture);
                }
                return null;
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

}
