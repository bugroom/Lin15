package com.xp.legend.lin17.hooks;

import android.app.AndroidAppHelper;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.Toast;

import com.xp.legend.lin17.bean.Full;
import com.xp.legend.lin17.bean.Result;
import com.xp.legend.lin17.utils.BaseHook;
import com.xp.legend.lin17.utils.Conf;
import com.xp.legend.lin17.utils.ReceiverAction;
import com.xp.legend.lin17.utils.ReflectUtil;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.lang.reflect.Field;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;


/**
 * lin16工作原理
 * 首先P的快速设置面板（QSSettingPanel）不同于7.0或是8.0
 * 其并非是将一整块的QSContainerImpl作为快速设置面板背景，而是使用了一个View来专门作为背景，那就是白色的区域，并且还有圆角
 * lin15无法直接将这块View作为背景来继续使用，因为它只是一块纯粹的View，无法强转为Viewgroup放入自定义的背景
 * 所以在适配lin16上，将原本的白色背景给设置为透明，然后以它的尺寸来new一个自定义的view，用来代替它
 * 至于圆角，则是将自定义view切成圆角
 * lin16舍弃滚动模式，舍弃普通模式，只有卷轴模式
 * lin16头部也是自定义View，只需要设置好位置即可，大小则是参考0.0时背景view的大小来定，设定其位于全部背景的前面，避免被全部遮挡
 * 另外，需要居中显示，所以要设置好margin，margin计算是整个(快速设置面板的宽度-背景view的宽度)/2，margin是给左右两边设置的。
 * 距离顶部也有一定的距离，所以顶部也需要设置一个margin，此margin是android:dimen/quick_qs_offset_height，经过反射获取其值，最后再设置上即可。
 */
public class Q_FullHook extends BaseHook implements IXposedHookLoadPackage {

    private static final String METHOD = "onFinishInflate";
    private static final String CLASS2 = "com.android.systemui.qs.QSContainerImpl";
    private static final String METHOD2 = "updateExpansion";
    private static final String METHOD3 = "onConfigurationChanged";
    private static final String METHOD4 = "onLayout";

    private ViewGroup fullView;
    private FullReceiver receiver;
    private SharedPreferences sharedPreferences;
    private int alphaValue;
    private int height = -1;
    private int defaultDrawable;
    private int quality = Conf.LOW_QUALITY;
    private boolean isGaoSi = false;
    private int gaoValue = 25;
    private View header;

    private ImageView bgView, hengBgView;

    private SlitImageView shuSlit, hengSlit;

    private boolean isSlit = true;//是否卷轴背景

    private View bg;

    private int radius = 0;

    private View mBackgroundGradient, mStatusBarBackground;

    private boolean isFirst = true;


    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {

        if (!isQ()) {
            return;
        }

        if (!lpparam.packageName.equals("com.android.systemui")) {

            return;
        }


        XposedHelpers.findAndHookMethod(CLASS2, lpparam.classLoader, METHOD, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {

                //注册广播、
                register();

//                sharedPreferences = AndroidAppHelper.currentApplication().getSharedPreferences(Conf.SHARE, Context.MODE_PRIVATE);

                initPs();

                Class s=lpparam.classLoader.loadClass(CLASS2);

                if(isFiledExist("mBackground",s)){

                    bg = (View) XposedHelpers.getObjectField(param.thisObject, "mBackground");

//                    bg.setBackgroundTintList(null);

                }

                if (isFiledExist("mBackgroundGradient",s)){

                    mBackgroundGradient= (View) XposedHelpers.getObjectField(param.thisObject,"mBackgroundGradient");

                }

                if (isFiledExist("mStatusBarBackground",s)){

                    mStatusBarBackground= (View) XposedHelpers.getObjectField(param.thisObject,"mStatusBarBackground");


                }


                fullView = (ViewGroup) param.thisObject;


                //三星
                if (isRom("samsung")){

                    int id=ReflectUtil.getDimenId(AndroidAppHelper.currentApplication(),"notification_panel_background_radius");

                    radius= (int) AndroidAppHelper.currentApplication().getResources().getDimension(id);

                }else {//其他ROM


                    TypedValue typedValue = new TypedValue();
                    AndroidAppHelper.currentApplication().getTheme().resolveAttribute(android.R.attr.dialogCornerRadius, typedValue, true);


                    /**
                     * 获取圆角度数
                     */
                    radius = TypedValue.complexToDimensionPixelSize(typedValue.data,
                            AndroidAppHelper.currentApplication().getResources().getDisplayMetrics());

                }


                alphaValue = sharedPreferences.getInt(Conf.FULL_ALPHA_VALUE, 100);

                isSlit = sharedPreferences.getBoolean(Conf.SLIT, true);


                defaultDrawable = AndroidAppHelper
                        .currentApplication()
                        .getResources()
                        .getIdentifier("qs_background_primary", "drawable", lpparam.packageName);

                quality = sharedPreferences.getInt(Conf.FULL_QUALITY, Conf.LOW_QUALITY);//初始化

                isGaoSi = sharedPreferences.getBoolean(Conf.FULL_GAO, false);

                gaoValue = sharedPreferences.getInt(Conf.FULL_GAO_VALUE, 25);

//                saveFullWidthInfo();

                hideBackView();

                //开个线程，睡眠500毫秒后再设置背景，专治一加
                new Thread(){

                    @Override
                    public void run() {
                        super.run();
                        try {
                            sleep(500);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }

                        fullView.post(Q_FullHook.this::autoSetBg);

                    }
                }.start();

            }

        });


        XposedHelpers.findAndHookMethod(CLASS2, lpparam.classLoader, METHOD2, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                super.afterHookedMethod(param);

                float f = XposedHelpers.getFloatField(param.thisObject, "mQsExpansion");

                autoChangeAlpha(f);

            }
        });


        XposedHelpers.findAndHookMethod(CLASS2, lpparam.classLoader, METHOD3, Configuration.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
//                super.afterHookedMethod(param);

                Configuration configuration = (Configuration) param.args[0];

                autoSetPosition(configuration.orientation);

            }
        });

//        XposedHelpers.findAndHookMethod(CLASS2, lpparam.classLoader, METHOD4, boolean.class, int.class, int.class, int.class, int.class, new XC_MethodHook() {
//            @Override
//            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
//                super.afterHookedMethod(param);
//
//                if (isFirst) {
//                    isFirst = false;
//                    autoSetBg();
//
//                }
//
//            }
//        });


    }



    private void register() {

        if (this.receiver == null) {
            this.receiver = new FullReceiver();

            IntentFilter intentFilter = new IntentFilter();

            intentFilter.addAction(ReceiverAction.SET_N_FULL_VERTICAL_IMAGE);

            intentFilter.addAction(ReceiverAction.SET_N_FULL_HORIZONTAL_IMAGE);

            intentFilter.addAction(ReceiverAction.SEND_O_FLOAT);

            intentFilter.addAction(ReceiverAction.SET_N_FULL_GAO_SI);

            intentFilter.addAction(ReceiverAction.SET_N_FULL_GAO_VALUE);

            intentFilter.addAction(ReceiverAction.N_FULL_ALPHA_VALUE);

            intentFilter.addAction(ReceiverAction.DELETE_FULL_BG);

            intentFilter.addAction(ReceiverAction.GET_FULL_INFO);
            intentFilter.addAction(ReceiverAction.SET_FULL_QUALITY);
            intentFilter.addAction(ReceiverAction.UI_GET_FULL_INFO);
            intentFilter.addAction(ReceiverAction.SEND_FULL_SCROLL);
            intentFilter.addAction(ReceiverAction.SEND_CLEAN_ACTION);
            intentFilter.addAction(ReceiverAction.SEND_CUSTOM_HEIGHT);
            intentFilter.addAction(ReceiverAction.SEND_ORI);
            intentFilter.addAction(ReceiverAction.SEND_SLIT_INFO);

            intentFilter.addAction(ReceiverAction.SEND_LOGS);

            intentFilter.addAction(ReceiverAction.SEND_INFO_TO_ACTIVITY);

            AndroidAppHelper.currentApplication().registerReceiver(receiver, intentFilter);


        }

    }


    class FullReceiver extends BroadcastReceiver {


        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();

            if (action == null) {
                return;
            }


            switch (action) {

                case ReceiverAction.SET_N_FULL_VERTICAL_IMAGE:

                    setFullVerticalImage(intent, context);

                    break;

                case ReceiverAction.SET_N_FULL_HORIZONTAL_IMAGE:

                    setFullHorizontalImage(context, intent);


                    break;
                case ReceiverAction.N_FULL_ALPHA_VALUE:
                    getAlpha(intent);

                    break;
                case ReceiverAction.SET_N_FULL_GAO_SI:

                    getGaoSi(intent);

                    break;
                case ReceiverAction.SET_N_FULL_GAO_VALUE:

                    getGaoValue(intent);

                    break;
                case ReceiverAction.DELETE_FULL_BG:

                    deleteBg(intent, context);

                    break;
                case ReceiverAction.GET_FULL_INFO:

                    sendInfo(intent, context);


                    break;
                case ReceiverAction.SET_FULL_QUALITY:

                    getQuality(intent);

                    break;

                case ReceiverAction.SEND_O_FLOAT:

//                    autoChangeAlpha(intent);

                    break;


                case ReceiverAction.UI_GET_FULL_INFO:

                    sendAllInfo(intent, context);

                    break;

                case ReceiverAction.SEND_FULL_SCROLL:

//                    setScroll(intent);

                    break;

                case ReceiverAction.SEND_CLEAN_ACTION:

                    resetAll();

                    break;

                case ReceiverAction.SEND_CUSTOM_HEIGHT://自定义高度


                    setCustomHeight(intent);

                    break;

                case ReceiverAction.SEND_ORI://接收屏幕旋转信息

//                    autoSetPosition(intent);

                    break;


                case ReceiverAction.SEND_SLIT_INFO://接收是否使用卷轴模式

                    getSlitInfo(intent);

                    break;

                case ReceiverAction.SEND_LOGS:

                    openLogs(intent);

                    break;

                case ReceiverAction.SEND_INFO_TO_ACTIVITY://获取面板全部信息

                    sendInfoToActivity();

                    break;


            }

        }
    }

    private void resetAll() {

        String path = "/data/user_de/0/" + AndroidAppHelper.currentApplication().getPackageName() + "/shared_prefs/" + Conf.SHARE + ".xml";

        File file = new File(path);

        initPs();

        sharedPreferences.edit().putInt(Conf.FULL_SHU_HEIGHT,-1).apply();
        sharedPreferences.edit().putInt(Conf.FULL_SHU_WIDTH,-1).apply();
        sharedPreferences.edit().putInt(Conf.FULL_HENG_HEIGHT,-1).apply();
        sharedPreferences.edit().putInt(Conf.FULL_HENG_WIDTH,-1).apply();

        if (file.exists()) {
            if (file.delete())
            Toast.makeText(AndroidAppHelper.currentApplication(), "重置设置(reset success)", Toast.LENGTH_SHORT).show();
        }


        if (getHeaderFile(Conf.HORIZONTAL).exists()) {

            getHeaderFile(Conf.HORIZONTAL).delete();

        }

        if (getHeaderFile(Conf.VERTICAL).exists()) {
            getHeaderFile(Conf.VERTICAL).delete();
        }

        if (getNFullFile(Conf.HORIZONTAL).exists()) {
            getNFullFile(Conf.HORIZONTAL).delete();
        }

        if (getNFullFile(Conf.VERTICAL).exists()) {
            getNFullFile(Conf.VERTICAL).delete();
        }


//        Toast.makeText(AndroidAppHelper.currentApplication(), "重置成功(reset success)", Toast.LENGTH_SHORT).show();

    }


    /**
     * 设置竖屏图片
     *
     * @param intent
     */
    private void setFullVerticalImage(Intent intent, Context context) {

        String s = intent.getStringExtra(Conf.N_FULL_VERTICAL_FILE);

        if (s == null || s.isEmpty()) {

            logs("s为null或是空，图片信息未传递过来");

            return;
        }

        Uri uri = Uri.parse(s);

        if (uri == null) {
            logs("文件不存在");
            return;
        }

        try {
            Bitmap bitmap = BitmapFactory.decodeStream(context.getContentResolver().openInputStream(uri));

            if (bitmap != null) {

                int result = saveBitmap(bitmap, Conf.VERTICAL);

                if (result > 0) {

                    if (isVertical) {

                        autoSetBg();

                    }

                    Toast.makeText(context, "设置成功", Toast.LENGTH_SHORT).show();

                    deleteUriFile(uri,context);

                    Intent intent1=new Intent(ReceiverAction.DELETE_IMG_CALL);

                    AndroidAppHelper.currentApplication().sendBroadcast(intent1);
                } else {

                    logs("文件或许不是图片格式");
                    Toast.makeText(context, "设置失败，保存失败", Toast.LENGTH_SHORT).show();
                }

            }


        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

    }

    private void setFullHorizontalImage(Context context, Intent intent) {

        String s = intent.getStringExtra(Conf.N_FULL_HORIZONTAL_FILE);

        if (s == null || s.isEmpty()) {
            return;
        }

        Uri uri = Uri.parse(s);

        if (uri == null) {
            return;
        }

        try {
            Bitmap bitmap = BitmapFactory.decodeStream(context.getContentResolver().openInputStream(uri));

            if (bitmap != null) {

                int result = saveBitmap(bitmap, Conf.HORIZONTAL);

                if (result > 0) {

                    if (!isVertical) {

                        autoSetBg();


                    }

                    Toast.makeText(context, "设置成功", Toast.LENGTH_SHORT).show();

                    deleteUriFile(uri,context);

                    Intent intent1=new Intent(ReceiverAction.DELETE_IMG_CALL);

                    AndroidAppHelper.currentApplication().sendBroadcast(intent1);


                } else {

                    Toast.makeText(context, "设置失败，保存失败", Toast.LENGTH_SHORT).show();
                }

            }


        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }


    }

    //自动设置背景
    private void autoSetBg() {

        setSlitImage();

    }


    /**
     * 保存竖屏宽度
     */
    private void saveVerticalWidth(){

        if (sharedPreferences==null){
            initPs();
        }

        int vertical_width = sharedPreferences.getInt(Conf.FULL_SHU_WIDTH, -1);

        if (vertical_width<=0) {

            if (fullView==null){
                return;
            }

            if (bg==null){
                return;
            }

            vertical_width = bg.getWidth();

            sharedPreferences.edit().putInt(Conf.FULL_SHU_WIDTH, vertical_width).apply();

            int full_v_width = fullView.getWidth();

            sharedPreferences.edit().putInt(Conf.F_V_W, full_v_width).apply();

            logs("保存竖屏宽度---->>>" + vertical_width);
        }

    }

    /**
     * 保存横屏宽度
     */
    private void saveHorizontalWidth(){

        if (sharedPreferences==null){
            initPs();
        }

        int horizontal_width = sharedPreferences.getInt(Conf.FULL_HENG_WIDTH, -1);

        if (horizontal_width<=0) {

            if (fullView==null){
                return;
            }

            if (bg==null){
                return;
            }

            horizontal_width = bg.getWidth();

            sharedPreferences.edit().putInt(Conf.FULL_HENG_WIDTH, horizontal_width).apply();

            int full_h_width = fullView.getWidth();
            sharedPreferences.edit().putInt(Conf.F_H_W, full_h_width).apply();

            logs("保存横屏宽度---->>>" + horizontal_width);

            Toast.makeText(AndroidAppHelper.currentApplication(), "已保存横屏宽度", Toast.LENGTH_SHORT).show();
        }

    }


    /**
     * 保存高度
     */
    private void saveFullHeightInfo() {

        if (sharedPreferences == null) {
            initPs();
//            return;
        }

        int horizontal_height = sharedPreferences.getInt(Conf.FULL_HENG_HEIGHT, -1);
        int vertical_height = sharedPreferences.getInt(Conf.FULL_SHU_HEIGHT, -1);

        if (!isVertical) {//保存横屏高度

            horizontal_height = bg.getHeight();

            sharedPreferences.edit().putInt(Conf.FULL_HENG_HEIGHT, horizontal_height).apply();

            saveHorizontalWidth();

            logs("保存横屏高度---->>>"+horizontal_height);

        }

        if (isVertical) {//保存竖屏高度

            vertical_height = bg.getHeight();

            sharedPreferences.edit().putInt(Conf.FULL_SHU_HEIGHT, vertical_height).apply();

            saveVerticalWidth();

            logs("保存竖屏高度---->>>"+vertical_height);

        }
    }


    //保存头部高度
    private void saveHeaderHeight() {

        if (sharedPreferences == null) {
            initPs();
//            return;
        }

//        int header_vertical_height = sharedPreferences.getInt(Conf.N_HEADER_VERTICAL_HEIGHT, -1);
//
//        int header_horizontal_height = sharedPreferences.getInt(Conf.N_HEADER_HORIZONTAL_HEIGHT, -1);

//        if (header_vertical_height <= 0 && isVertical) {

        int header_vertical_height = bg.getHeight();

            sharedPreferences.edit().putInt(Conf.N_HEADER_VERTICAL_HEIGHT, header_vertical_height).apply();

//        }

//        if (header_horizontal_height <= 0 && !isVertical) {

        int header_horizontal_height = bg.getHeight();

            sharedPreferences.edit().putInt(Conf.N_HEADER_HORIZONTAL_HEIGHT, header_horizontal_height).apply();
//        }


    }

    /**
     * 获取图片文件
     *
     * @param type 类型
     * @return 返回文件
     */
    private File getNFullFile(int type) {

        String path = AndroidAppHelper.currentApplication().getFilesDir().getAbsolutePath();

        File file = null;

        switch (type) {
            case Conf.VERTICAL://竖屏图

                file = new File(path + "/" + Conf.N_FULL_VERTICAL_FILE);

                break;

            case Conf.HORIZONTAL://横屏图

                file = new File(path + "/" + Conf.N_FULL_HORIZONTAL_FILE);

                break;
        }

        return file;
    }


    /**
     * 判断头部是否存在，如果不存在，则给头部设置上背景
     * 舍弃，P这里用不上这个头部
     */
    @Deprecated
    private void autoSetHeaderBg() {

        if (header == null) {

            return;
        }


        if (isVertical) {

            if (!getHeaderFile(Conf.VERTICAL).exists()) {

                header.setBackground(getDefaultDrawable());

            }

        } else {

            if (!getHeaderFile(Conf.VERTICAL).exists()) {

                header.setBackground(getDefaultDrawable());
            }

        }


    }

    /**
     * 获取默认背景
     *
     * @return
     */
    private Drawable getDefaultDrawable() {

        if (defaultDrawable == 0) {
            return null;
        }

        return AndroidAppHelper.currentApplication().getDrawable(defaultDrawable);

    }

    private File getHeaderFile(int type) {

        String path = AndroidAppHelper.currentApplication().getFilesDir().getAbsolutePath();

        File file = null;

        switch (type) {
            case Conf.VERTICAL://竖屏图

                file = new File(path + "/" + Conf.N_HEADER_VERTICAL_FILE);

                break;

            case Conf.HORIZONTAL://横屏图

                file = new File(path + "/" + Conf.N_HEADER_HORIZONTAL_FILE);

                break;
        }

        return file;

    }

    /**
     * 保存背景图
     *
     * @param bitmap bitmap
     * @param type   保存类型
     * @return 保存后的结果
     */
    private int saveBitmap(Bitmap bitmap, int type) {

        String path = AndroidAppHelper.currentApplication().getFilesDir().getAbsolutePath();

        int result = -1;


        File file = null;


        switch (type) {
            case Conf.VERTICAL:

                try {
                    AndroidAppHelper.currentApplication().openFileOutput(Conf.N_FULL_VERTICAL_FILE,Context.MODE_PRIVATE);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }

                file = new File(path + "/" + Conf.N_FULL_VERTICAL_FILE);

                break;

            case Conf.HORIZONTAL:

                try {
                    AndroidAppHelper.currentApplication().openFileOutput(Conf.N_FULL_HORIZONTAL_FILE,Context.MODE_PRIVATE);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }


                file = new File(path + "/" + Conf.N_FULL_HORIZONTAL_FILE);

                break;
        }

        FileOutputStream outputStream = null;
        try {
            outputStream = new FileOutputStream(file,false);
            bitmap.compress(Bitmap.CompressFormat.WEBP, 100, outputStream);

            result = 1;
        } catch (FileNotFoundException e) {

            result = -1;
            e.printStackTrace();
        }

        return result;
    }

    private boolean deleteFile(int type) {

        File file = getNFullFile(type);

        return file.delete();

    }


    /**
     * 设置透明度
     *
     * @param intent
     */
    private void getAlpha(Intent intent) {


        int value = intent.getIntExtra(Conf.FULL_ALPHA_VALUE, -1);

        if (value == -1) {
            return;
        }

        value = (int) (2.55 * value);

        if (value < 0) {
            value = 0;
        }

        if (value > 255) {
            value = 255;
        }

        alphaValue = value;

        setViewAlpha(alphaValue);


        sharedPreferences.edit().putInt(Conf.FULL_ALPHA_VALUE, alphaValue).apply();


    }


    //统一设置背景透明度
    private void setViewAlpha(int alpha) {

        if (shuSlit != null) {

            shuSlit.setImageAlpha(alpha);

        }

        if (hengSlit != null) {

            hengSlit.setImageAlpha(alpha);

        }

    }


    /**
     * 设置是否高斯模糊
     *
     * @param intent
     */
    private void getGaoSi(Intent intent) {

        int b = intent.getIntExtra(Conf.FULL_GAO, -100);

        if (b == -100) {
            return;
        } else if (b > 0) {

            this.isGaoSi = true;
        } else if (b < 0) {

            this.isGaoSi = false;
        }

        sharedPreferences.edit().putBoolean(Conf.FULL_GAO, isGaoSi).apply();

        autoSetBg();


    }

    /**
     * 接收高斯模糊半径
     *
     * @param intent
     */
    private void getGaoValue(Intent intent) {

        this.gaoValue = intent.getIntExtra(Conf.FULL_GAO_VALUE, 25);

        sharedPreferences.edit().putInt(Conf.FULL_GAO_VALUE, gaoValue).apply();

        if (isGaoSi) {

            autoSetBg();

        }


    }

    private void getQuality(Intent intent) {

        this.quality = intent.getIntExtra(Conf.IMAGE_QUALITY, Conf.LOW_QUALITY);


        if (!isGaoSi) {

            autoSetBg();
        }

        sharedPreferences.edit().putInt(Conf.FULL_QUALITY, quality).apply();

    }


    private void deleteBg(Intent intent, Context context) {

        int type = intent.getIntExtra(Conf.FULL_DELETE_TYPE, -1);

        if (type == -1) {
            return;
        }

        if (deleteFile(type)) {

            Toast.makeText(context, "清除成功", Toast.LENGTH_SHORT).show();

//            setBg();

            autoSetBg();

        }

    }


    /**
     * 发送信息到UI
     *
     * @param intent
     * @param context
     */
    private void sendInfo(Intent intent, Context context) {

        int type = intent.getIntExtra(Conf.FULL_INFO, -1);

        if (type == -1) {
            return;
        }

        Full full = new Full();
        Intent intent1 = new Intent(ReceiverAction.SEND_FULL_INFO);

        switch (type) {

            case Conf.VERTICAL:

                int w = sharedPreferences.getInt(Conf.FULL_SHU_WIDTH, -1);

                int h = sharedPreferences.getInt(Conf.FULL_SHU_HEIGHT, -1);


                full.setWidth(w);
                full.setHeight(h);

                logs("发送全部竖屏 w--->>" + w);
                logs("发送全部竖屏 h--->>" + h);

                break;


            case Conf.HORIZONTAL:

                int w1 = sharedPreferences.getInt(Conf.FULL_HENG_WIDTH, -1);

                int h1 = sharedPreferences.getInt(Conf.FULL_HENG_HEIGHT, -1);


                full.setWidth(w1);
                full.setHeight(h1);

                logs("发送全部横屏 w--->>" + w1);
                logs("发送全部横屏 h--->>" + h1);

                break;

        }

        intent1.putExtra(Conf.FULL_RESULT, full);

        intent1.putExtra(Conf.FULL_INFO, type);

        context.sendBroadcast(intent1);


    }


    /**
     * 接收下拉程度，无下拉为0，下拉到底部为1，float
     */
    private void autoChangeAlpha(float f) {


        if (shuSlit != null && shuSlit.getVisibility() == View.VISIBLE) {

            shuSlit.change(bg.getHeight());
        }

        if (hengSlit != null && hengSlit.getVisibility() == View.VISIBLE) {
            hengSlit.change(bg.getHeight());
        }

        if (f == 1.0) {
            //下拉到最底下的时候保存信息
            //完全下拉状态，保存高度

            saveFullHeightInfo();



        }


        if (f == 0) {//没下拉状态


            saveHeaderHeight();//保存头部的高度

        }


    }


    private void sendAllInfo(Intent intent, Context context) {

        int sdk = intent.getIntExtra(Conf.SDK, -1);

        //取9.0
        if (sdk == Build.VERSION_CODES.P) {

            int alpha = sharedPreferences.getInt(Conf.FULL_ALPHA_VALUE, 255);

            int quality = sharedPreferences.getInt(Conf.FULL_QUALITY, Conf.LOW_QUALITY);

            boolean gao = sharedPreferences.getBoolean(Conf.FULL_GAO, false);

            int gaoValue = sharedPreferences.getInt(Conf.FULL_GAO_VALUE, 25);

            boolean scroll = sharedPreferences.getBoolean(Conf.FULL_SCROLL, true);

            boolean slit = sharedPreferences.getBoolean(Conf.SLIT, true);

            Result result = new Result();

            result.setAlpha(alpha);
            result.setGao(gao);
            result.setQuality(quality);
            result.setGaoValue(gaoValue);
            result.setScroll(scroll);
            result.setSlit(slit);

            Intent intent1 = new Intent(ReceiverAction.FULL_TO_UI_INFO);

            intent1.putExtra(Conf.FULL_TO_UI_RESULT, result);

            context.sendBroadcast(intent1);

        }


    }


    private int record = -1;//记录者，避免重复设置浪费资源


    /**
     * 自定义高度
     *
     * @param intent intent对象
     */
    private void setCustomHeight(Intent intent) {


        int type = intent.getIntExtra("type", -1);

        if (type == -1) {
            return;
        }

        String h = intent.getStringExtra("height");

        int height = Integer.parseInt(h);

        switch (type) {
            case 10://竖屏

                sharedPreferences.edit().putInt(Conf.FULL_SHU_HEIGHT, height).apply();

                break;

            case 20://横屏

                sharedPreferences.edit().putInt(Conf.FULL_HENG_HEIGHT, height).apply();

                break;
        }

        Toast.makeText(AndroidAppHelper.currentApplication(), "自定义高度成功", Toast.LENGTH_SHORT).show();

    }


    /**
     * 设置卷轴背景
     */
    private void setSlitImage() {

        logs("开始设置卷轴背景");

        File file = null;

        BitmapFactory.Options options = new BitmapFactory.Options();

        if (!isGaoSi) {//如果非高斯模糊，则读取配置

            switch (this.quality) {

                case Conf.HEIGHT_QUALITY:
                    options.inPreferredConfig = Bitmap.Config.ARGB_8888;
                    break;


                case Conf.LOW_QUALITY:

                    options.inPreferredConfig = Bitmap.Config.RGB_565;

                    break;


            }
        }


        if (isVertical) {//竖屏
            if (hengSlit != null && hengSlit.getVisibility() == View.VISIBLE) {//隐藏横向图
                hengSlit.setVisibility(View.GONE);
                fullView.removeView(hengSlit);
            }

            file = getNFullFile(Conf.VERTICAL);

            if (!file.exists()) {//文件不存在

                cleanSlitImage();
                cleanBg();

                logs("文件不存在");

                return;
            }


            if (shuSlit == null) {
//                shuSlit = new SlitImageView(AndroidAppHelper.currentApplication());

                initShuSlitView();

                if (shuSlit == null) {

                    logs("初始化卷轴背景失败，有可能是宽高获取不正确");

                    return;
                }

            } else if (shuSlit.getVisibility() == View.GONE) {//存在但是被隐藏
                shuSlit.setVisibility(View.VISIBLE);
            }


//            cleanScrollImage();
            cleanBg();


            fullView.removeView(shuSlit);

            ViewGroup viewGroup= (ViewGroup) shuSlit.getParent();


            if (viewGroup!=null) {
                viewGroup.removeView(shuSlit);
            }

            fullView.addView(shuSlit,0);


            if (isGaoSi) {//是否高斯模糊

                Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());
                bitmap = getBitmap(AndroidAppHelper.currentApplication(), bitmap, gaoValue);

                shuSlit.setBitmap(bitmap, radius);

                logs("高斯模糊");

            } else {

                Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);

                shuSlit.setBitmap(bitmap, radius);
//                shuSlit.setImageAlpha(alphaValue);

                logs("bitmap--width-->>"+bitmap.getWidth()+"\nheight--->>"+bitmap.getHeight());


                logs("非高斯模糊");
            }

//            shuSlit.setBackgroundColor(Color.TRANSPARENT);//设置透明背景

            setAlpha(shuSlit);


            logs("设置竖屏卷轴背景成功");

        } else {//横屏

            logs("设置横屏卷轴背景");

            if (shuSlit != null && shuSlit.getVisibility() == View.VISIBLE) {
                shuSlit.setVisibility(View.GONE);
                fullView.removeView(shuSlit);
            }

            file = getNFullFile(Conf.HORIZONTAL);

            if (!file.exists()) {//文件不存在


                cleanSlitImage();

                logs("文件不存在");

                return;
            }


            if (hengSlit == null) {
//                hengSlit=new SlitImageView(AndroidAppHelper.currentApplication());

                initHengSlitView();

                if (hengSlit == null) {

                    logs("横屏卷轴背景初始化失败");

                    return;
                }

            } else if (hengSlit.getVisibility() == View.GONE) {
                hengSlit.setVisibility(View.VISIBLE);
            }

            cleanBg();

            fullView.removeView(hengSlit);

            ViewGroup viewGroup= (ViewGroup) hengSlit.getParent();

            if (viewGroup!=null) {

                viewGroup.removeView(hengSlit);
            }


            fullView.addView(hengSlit,0);


            //设置高斯模糊
            if (isGaoSi) {

                Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath());

                bitmap = getBitmap(AndroidAppHelper.currentApplication(), bitmap, gaoValue);
                hengSlit.setBitmap(bitmap, radius);

            } else {

                Bitmap bitmap = BitmapFactory.decodeFile(file.getAbsolutePath(), options);
                hengSlit.setBitmap(bitmap, radius);


            }

//            hengSlit.setBackgroundColor(Color.TRANSPARENT);

            setAlpha(hengSlit);

        }

        setSlitHeader();//设置卷轴头部


//        bg.setBackgroundColor(Color.TRANSPARENT);//设置成功，将原先的背景给设置为透明，避免遮挡

        bg.setVisibility(View.INVISIBLE);

        autoChangePosition();//设置背景居中，避免歪掉，可以的话尽量搞个线程，睡个几秒钟再居中

    }


    /**
     * 自动根据当前屏幕设置背景，代替之前的监听
     *
     * @param p
     */
    private void autoSetPosition(int p) {

//        hideBackView();

        switch (p) {

            case Configuration.ORIENTATION_LANDSCAPE://横屏

                if (!isVertical) {//避免重复
                    return;
                }

                isVertical = false;

                autoSetBg();

                break;

            case Configuration.ORIENTATION_PORTRAIT://竖屏
            default:

                if (isVertical) {//避免重复
                    return;
                }

                isVertical = true;

                autoSetBg();

                break;

        }


    }


    //清除卷轴背景
    private void cleanSlitImage() {

        if (shuSlit != null) {

            fullView.removeView(shuSlit);
            shuSlit = null;
        }

        if (hengSlit != null) {

            fullView.removeView(hengSlit);
            hengSlit = null;

        }

        if (bg!=null){
            bg.setVisibility(View.VISIBLE);
        }

    }

    private void getSlitInfo(Intent intent) {

        this.isSlit = intent.getBooleanExtra(Conf.SLIT, true);

        sharedPreferences.edit().putBoolean(Conf.SLIT, isSlit).apply();//保存

        autoSetBg();

    }

    /**
     * 在卷轴模式下，可选对头部进行显示
     */
    private void setSlitHeader() {

        if (header == null) {
            return;
        }

        if (isVertical) {//

            if (!getHeaderFile(Conf.VERTICAL).exists()) {

                header.setBackgroundColor(Color.TRANSPARENT);

            }

        } else {

            if (!getHeaderFile(Conf.HORIZONTAL).exists()) {
                header.setBackgroundColor(Color.TRANSPARENT);
            }


        }

    }

    /**
     * 初始化
     */
    private void initShuSlitView() {

        if (shuSlit == null) {

            int height = sharedPreferences.getInt(Conf.FULL_SHU_HEIGHT, -1);

            int width = sharedPreferences.getInt(Conf.FULL_SHU_WIDTH, -1);


            if (height <= 0 || width <= 0) {

                logs("获取竖屏height--->>" + height);
                logs("获取竖屏width--->>" + width);
                logs("由此返回，初始化卷轴背景失败");

                return;
            }


            shuSlit = new SlitImageView(AndroidAppHelper.currentApplication());

            ViewGroup.LayoutParams layoutParams = new FrameLayout.LayoutParams(width, height);

            shuSlit.setLayoutParams(layoutParams);

        }

    }


    private void initHengSlitView() {

        if (hengSlit == null) {

            int height = sharedPreferences.getInt(Conf.FULL_HENG_HEIGHT, -1);

            int width = sharedPreferences.getInt(Conf.FULL_HENG_WIDTH, -1);

            if (height <= 0 || width <= 0) {

                logs("获取横屏的height--->>" + height);
                logs("获取横屏width--->>" + width);
                logs("由此返回，初始化卷轴背景失败");

                return;
            }

            hengSlit = new SlitImageView(AndroidAppHelper.currentApplication());

            ViewGroup.LayoutParams layoutParams = new FrameLayout.LayoutParams(width, height);

            hengSlit.setLayoutParams(layoutParams);

        }

    }

    //清除背景，还原
    private void cleanBg() {

        showBackView();

    }

    /**
     * 自动恢复背景位置，避免背景歪掉
     * 另外，背景图在重启后可能会歪掉，原因是重启时初始化速度太快，程序无法跟上节奏，所以这部分的代码直接失效，所以这一部分必须在之后才能调用
     */
    private void autoChangePosition() {


        int offset_height = 0;
        if (shuSlit != null && shuSlit.getVisibility() == View.VISIBLE) {

            int full_width = fullView.getWidth();

            int bg_width = bg.getWidth();

            int margin = (full_width - bg_width) / 2;//计算距离两边的距离

            int ii = AndroidAppHelper
                    .currentApplication()
                    .getResources()
                    .getIdentifier("android:dimen/quick_qs_offset_height", "dimen", AndroidAppHelper.currentPackageName());


            offset_height = (int) AndroidAppHelper.currentApplication().getResources().getDimension(ii);

            if (isRom("samsung")) {

                FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) bg.getLayoutParams();

                offset_height = params.topMargin;
            }


            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) shuSlit.getLayoutParams();
            layoutParams.setMargins(margin, offset_height, margin, 0);

            shuSlit.setLayoutParams(layoutParams);

            shuSlit.setElevation(bg.getElevation());

            hideBackView();

        }


        if (hengSlit != null && hengSlit.getVisibility() == View.VISIBLE) {

            //设置居中

            int full_width = fullView.getWidth();

            int bg_width = bg.getWidth();

            int margin = (full_width - bg_width) / 2;//计算距离两边的距离

            if (margin<0){
                margin=0;
            }

            int ii = AndroidAppHelper
                    .currentApplication()
                    .getResources()
                    .getIdentifier("android:dimen/quick_qs_offset_height", "dimen", AndroidAppHelper.currentPackageName());

            offset_height = (int) AndroidAppHelper.currentApplication().getResources().getDimension(ii);

            if (isRom("samsung")) {

                FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) bg.getLayoutParams();

                offset_height = params.topMargin;
            }

            FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) hengSlit.getLayoutParams();
            layoutParams.setMargins(margin, offset_height, margin, 0);

            hengSlit.setLayoutParams(layoutParams);

            hengSlit.setElevation(bg.getElevation());

            hideBackView();

        }


    }

    /**
     * 隐藏一些无关紧要的view
     */
    private void hideBackView() {

        if (mStatusBarBackground != null) {
            mStatusBarBackground.setVisibility(View.GONE);
        }

        if (mBackgroundGradient != null) {
            mBackgroundGradient.setVisibility(View.GONE);
        }

    }

    private void showBackView() {

        if (mStatusBarBackground != null) {
            mStatusBarBackground.setVisibility(View.VISIBLE);
        }

        if (mBackgroundGradient != null) {
            mBackgroundGradient.setVisibility(View.VISIBLE);
        }

    }

    private void setAlpha(View view) {

        if (view != null) {

            float f = alphaValue / 100.0f;

            if (f > 1f) {
                f = 1.0f;
            }

            if (f < 0f) {
                f = 0f;
            }

            view.setAlpha(f);

        }

    }


    private void initPs(){

        if (sharedPreferences==null){
            sharedPreferences=AndroidAppHelper.currentApplication().getSharedPreferences(Conf.SHARE, Context.MODE_PRIVATE);
        }

    }

    private boolean isFiledExist(String name,Class s){

        Field[] fields=s.getDeclaredFields();

        boolean b=false;

        for (Field field:fields){

            if (field.getName().equals(name)){
                b=true;
                break;
            }

        }

        return b;

    }

    private boolean isRom(String rom){

        return Build.MANUFACTURER.toLowerCase().contains(rom);

    }

    private void sendInfoToActivity(){

        initPs();

        int vertical_width = sharedPreferences.getInt(Conf.FULL_SHU_WIDTH, -1);
        int horizontal_width = sharedPreferences.getInt(Conf.FULL_HENG_WIDTH, -1);

        int horizontal_height = sharedPreferences.getInt(Conf.FULL_HENG_HEIGHT, -1);
        int vertical_height = sharedPreferences.getInt(Conf.FULL_SHU_HEIGHT, -1);

        Intent intent=new Intent(ReceiverAction.GET_INFO_TO_ACTIVITY);

        intent.putExtra("vertical_width",vertical_width);
        intent.putExtra("horizontal_width",horizontal_width);
        intent.putExtra("horizontal_height",horizontal_height);
        intent.putExtra("vertical_height",vertical_height);

        AndroidAppHelper.currentApplication().sendBroadcast(intent);


    }


}
