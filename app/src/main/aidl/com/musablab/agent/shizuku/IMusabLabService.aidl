package com.musablab.agent.shizuku;

import android.os.ParcelFileDescriptor;

interface IMusabLabService {
    String ping();
    String exec(String command, int timeoutMs);
    byte[] execBytes(String command, int timeoutMs);
    int installApk(in ParcelFileDescriptor apk, long sizeBytes, boolean replaceExisting);
    void destroy();
}
