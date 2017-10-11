package com.prestoncinema.app;

import java.io.File;
import java.util.Map;

/**
 * Created by MATT on 2/15/2017.
 */

public interface DownloadCompleteListener {
    void downloadComplete(Map<String, Map<String, PCSReleaseParser.ProductInfo>> firmwareFilesMap);
}
