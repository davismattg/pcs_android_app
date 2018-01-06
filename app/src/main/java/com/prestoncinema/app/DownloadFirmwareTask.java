package com.prestoncinema.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import timber.log.Timber;

import static android.R.attr.value;
import static android.content.Context.MODE_PRIVATE;

/**
 * Created by MATT on 2/15/2017.
 */

public class DownloadFirmwareTask extends AsyncTask<String, Void, Map<String, Map<String, PCSReleaseParser.ProductInfo>>> {
    private final static String TAG = DownloadFirmwareTask.class.getSimpleName();
    DownloadCompleteListener mDownloadCompleteListener;
    private Context mContext;
    private boolean fwPrefsWritten = false;

    public DownloadFirmwareTask(Context context, DownloadCompleteListener downloadCompleteListener) {
        this.mDownloadCompleteListener = downloadCompleteListener;
        this.mContext = context;
    }

    @Override
    protected Map<String, Map<String, PCSReleaseParser.ProductInfo>> doInBackground(String... params) {
        try {
            return downloadData(params[0]);
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    protected void onPostExecute(Map<String, Map<String, PCSReleaseParser.ProductInfo>> result) {
        Log.d(TAG, "onPostExecute");
        fwPrefsWritten = addToFirmwarePref(result);
        if (fwPrefsWritten) {
            Log.d(TAG, "firmware written to preferences successfully");
            mDownloadCompleteListener.downloadComplete(result);
        }
        else {
            Log.d(TAG, "prefs not written successfully");
        }
    }

    // main function to do the heavy lifting of the download
    private Map<String, Map<String, PCSReleaseParser.ProductInfo>> downloadData(String urlString) throws IOException {
        Map<String, Map<String, PCSReleaseParser.ProductInfo>> latestFirmwareFiles = new HashMap<>();
        SharedPreferences firmwarePrefs = mContext.getSharedPreferences("firmwareURLs", MODE_PRIVATE);
        try {
            URL url = new URL(urlString);

            Map<String, PCSReleaseParser.ProductInfo> productMap = PCSReleaseParser.parseReleasesXml(url);

            for (Map.Entry<String, PCSReleaseParser.ProductInfo> entry : productMap.entrySet()) {           // entry.getKey() = "Hand3", version: latestRelease.version
                PCSReleaseParser.FirmwareInfo latestRelease;
                PCSReleaseParser.ProductInfo productInfo = productMap.get(entry.getKey());
                if (productInfo != null) {
//                    Map<String, File> versionInfo = new HashMap<>();
                    Map<String, PCSReleaseParser.ProductInfo> versionInfo = new HashMap<>();
//                    Map<String, File> changesAndFile = new HashMap<>();

                    List<PCSReleaseParser.FirmwareInfo> modelReleases = productInfo.firmwareReleases;

                    latestRelease = modelReleases.get(0);

                    String currentFirmwareVersion = "";
                    String currentProductString = firmwarePrefs.getString(entry.getKey(), "None");

                    Log.d(TAG, "currentProductString: " + currentProductString);

                    if (!currentProductString.contains("None")) {
                        currentFirmwareVersion = currentProductString.split("=")[0].replaceAll("[{}]", "");
                    }

                    Log.d(TAG, entry.getKey() + ":\n");
                    Log.d(TAG, "current version on device: " + currentFirmwareVersion);
                    Log.d(TAG, "latest version from web: " + latestRelease.version);

                    boolean updateNeeded = !hasLatestFirmware(currentFirmwareVersion, latestRelease.version);
                    Log.d(TAG, "Update this device? " + updateNeeded);

                    if (updateNeeded) {
                        // create the file name using the product version, string and append .s19 to it
                        String productFileName = entry.getKey() + "-" + latestRelease.version.replaceAll("[.]", "_") + ".s19";
                        Log.d(TAG, "Filename for " + entry.getKey() + ": " + productFileName);

                        // the folder where the internal s19 file will be stored after downloading
                        File productFile = new File(mContext.getFilesDir() + "/" + productFileName);

                        // the location of the s19 file on the server
                        URL latestURL = new URL(latestRelease.hexFileUrl);
                        BufferedReader in = new BufferedReader(new InputStreamReader((latestURL.openStream())));
                        String line;

                        try {
                            FileOutputStream fos = new FileOutputStream(productFile);
                            while ((line = in.readLine()) != null) {
                                fos.write(line.getBytes());
                                fos.write(0x0A);
                            }

                            fos.close();
                            Log.d(TAG, "file written successfully @ " + productFile.toString());

                            latestRelease.internalFileLocation = productFile.toString();
                            versionInfo.put(latestRelease.version, productInfo);
                            latestFirmwareFiles.put(entry.getKey(), versionInfo);
                        } catch (IOException e) {
                            Log.d(TAG, "IOException: " + e);
                        }
                    }
                }
            }
        } catch(IOException e) {
            Log.d(TAG, "downloadData: " + e);
        }

        Log.d(TAG, "Number of files that were downloaded: " + latestFirmwareFiles.size());
        return latestFirmwareFiles;
    }

    private boolean hasLatestFirmware(String deviceVersion, String webVersion) {
        return deviceVersion.equals(webVersion);
    }

    private boolean addToFirmwarePref(Map<String, Map<String, PCSReleaseParser.ProductInfo>> firmwareMap) {
        boolean urlOK = false;
        boolean changesOK = false;

        SharedPreferences sharedURLPref = mContext.getSharedPreferences("firmwareURLs", MODE_PRIVATE);
        SharedPreferences sharedChangesPref = mContext.getSharedPreferences("firmwareChanges", MODE_PRIVATE);

        PCSReleaseParser.ProductInfo prodInfo; // = new PCSReleaseParser.ProductInfo();

        // handle the file URLs first
        SharedPreferences.Editor urlEditor = sharedURLPref.edit();
        SharedPreferences.Editor changesEditor = sharedChangesPref.edit();

        for (Map.Entry<String, Map<String, PCSReleaseParser.ProductInfo>> entry : firmwareMap.entrySet()) {
            Map<String, PCSReleaseParser.ProductInfo> productMap = entry.getValue();
            Collection<PCSReleaseParser.ProductInfo> versionValues = productMap.values();
            Set<String> versionKeys = productMap.keySet();
            String productName = "";
            String versionNum = "";
            String versionHexFile = "";
            String internalHexFile = "";

            String changes = "";

            Log.d(TAG, "productInfo Map: " + productMap.toString());

            for (Object key : versionKeys) {
                versionNum = (String) key;
            }

            for (Object val : versionValues) {
                prodInfo = (PCSReleaseParser.ProductInfo) val;
                productName = prodInfo.firmwareReleases.get(0).description;
                internalHexFile = prodInfo.firmwareReleases.get(0).internalFileLocation;
                changes = stringifyFirmwareChanges(prodInfo.firmwareReleases.get(0).changes);
                Timber.d("changes: " + changes);
            }

            String hexValue = versionNum + "=" + internalHexFile + "=" + changes;
//            String changeValue = productName + "=" + changes;

            urlEditor.putString(entry.getKey(), hexValue);
//            changesEditor.putString(entry.getKey(), changes);
        }
        if (urlEditor.commit()) {
            Log.d(TAG, "URL pref written to successfully");
            urlOK = true;
        }

        if (changesEditor.commit()) {
            Timber.d("Changes prefs written to successfully");
            changesOK = true;
        }

        if (urlOK && changesOK) {
            return true;
        }

        return false;

    }

    private String stringifyFirmwareChanges(ArrayList<String> changes) {
        StringBuilder sb = new StringBuilder();

        for (String change : changes) {
            sb.append(change).append("&&");
        }

        return sb.toString();
    }
}