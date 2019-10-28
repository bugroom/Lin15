package com.xp.legend.lin17.utils;

import android.content.Context;
import android.net.Uri;

import androidx.core.content.FileProvider;

import java.io.File;

public class LinProvider extends FileProvider {

    public static Uri convertToUri(Context context,File file){
        return getUriForFile(context,"com.legend.lin17_provider",file);
    }

}
