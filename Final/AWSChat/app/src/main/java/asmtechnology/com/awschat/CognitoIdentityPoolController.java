package asmtechnology.com.awschat;

import android.content.Context;
import android.os.AsyncTask;

import com.amazonaws.auth.CognitoCachingCredentialsProvider;
import com.amazonaws.mobileconnectors.cognito.CognitoSyncManager;
import com.amazonaws.mobileconnectors.cognito.Dataset;
import com.amazonaws.mobileconnectors.cognito.Record;
import com.amazonaws.mobileconnectors.cognito.SyncConflict;
import com.amazonaws.mobileconnectors.cognito.exceptions.DataStorageException;
import com.amazonaws.regions.Regions;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import asmtechnology.com.awschat.interfaces.CognitoIdentityPoolControllerGenericHandler;


public class CognitoIdentityPoolController {

    //TO DO: Insert your Cognito identity pool settings here
    private String identityPoolID = "your identity pool id";
    private Regions identityPoolRegion = Regions.US_EAST_1;

    public CognitoCachingCredentialsProvider mCredentialsProvider;

    private Context mContext;
    private CognitoIdentityPoolControllerGenericHandler facebookCompletionHandler;

    private static CognitoIdentityPoolController instance = null;
    private CognitoIdentityPoolController() {}

    public static CognitoIdentityPoolController getInstance(Context context) {
        if(instance == null) {
            instance = new CognitoIdentityPoolController();
        }

        if (context != null) {
            instance.setupCredentialsProvider(context);
        }

        return instance;
    }

    private void  setupCredentialsProvider(Context context) {

        if (mCredentialsProvider == null) {
            mContext = context;
            mCredentialsProvider = new CognitoCachingCredentialsProvider(mContext, identityPoolID, identityPoolRegion);
            return;
        }

        if (mContext != context) {
            mCredentialsProvider = new CognitoCachingCredentialsProvider(mContext, identityPoolID, identityPoolRegion);
        }
    }

    public void getFederatedIdentityForFacebook(String idToken,
                                                String username,
                                                String emailAddress,
                                                final CognitoIdentityPoolControllerGenericHandler completion) {

        this.facebookCompletionHandler = completion;
        new FacebookIdentityFederationTask().execute(idToken, username, emailAddress);
    }




    class FacebookIdentityFederationTask extends AsyncTask<String, Void, Long> {

        private String idToken;
        private String username;
        private String emailAddress;

        protected Long doInBackground(String... strings) {

            idToken = strings[0];
            username = strings[1];
            emailAddress = strings[2];

            Map<String, String> logins = new HashMap<String, String>();
            logins.put("graph.facebook.com", idToken);
            mCredentialsProvider.setLogins(logins);
            mCredentialsProvider.refresh();

            return 1L;
        }

            protected void onPostExecute(Long result) {

                CognitoSyncManager client = new CognitoSyncManager(mContext,  identityPoolRegion,  mCredentialsProvider);

                Dataset dataset = client.openOrCreateDataset("facebookUserData");
                dataset.put("name", username);
                dataset.put("email", emailAddress);

                dataset.synchronize(new Dataset.SyncCallback() {
                    @Override
                    public void onSuccess(Dataset dataset, List<Record> updatedRecords) {
                        facebookCompletionHandler.didSucceed();
                    }

                    @Override
                    public boolean onConflict(Dataset dataset, List<SyncConflict> conflicts) {
                        List<Record> resolved = new ArrayList<Record>();
                        for (SyncConflict conflict : conflicts) {
                            resolved.add(conflict.resolveWithRemoteRecord());
                        }
                        dataset.resolve(resolved);
                        return true;
                    }

                    @Override
                    public boolean onDatasetDeleted(Dataset dataset, String datasetName) {
                        return true;
                    }

                    @Override
                    public boolean onDatasetsMerged(Dataset dataset, List<String> datasetNames) {
                        return false;
                    }

                    @Override
                    public void onFailure(DataStorageException dse) {
                        facebookCompletionHandler.didFail(dse);
                    }
                });

            }
        }
}
