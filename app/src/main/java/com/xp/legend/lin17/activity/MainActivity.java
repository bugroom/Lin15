package com.xp.legend.lin17.activity;

import android.Manifest;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.content.res.XmlResourceParser;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import androidx.annotation.NonNull;
import com.google.android.material.tabs.TabLayout;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;
import androidx.core.content.ContextCompat;
import androidx.viewpager.widget.ViewPager;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import com.xp.legend.lin17.R;
import com.xp.legend.lin17.adapter.MainAdapter;
import com.xp.legend.lin17.fragment.FullFragment;
import com.xp.legend.lin17.fragment.HeaderFragment;
import com.xp.legend.lin17.utils.Conf;
import com.xp.legend.lin17.utils.ReceiverAction;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

import static com.xp.legend.lin17.utils.ReceiverAction.GET_INFO_TO_ACTIVITY;
import static com.xp.legend.lin17.utils.ReceiverAction.SEND_INFO_TO_ACTIVITY;

public class MainActivity extends BaseActivity {

    private Toolbar toolbar;

    private ViewPager viewPager;

    private TabLayout tabLayout;

    private MainAdapter adapter;
    private TextView shang;

    private BroadcastReceiver receiver;

    private static final String[] permissionStrings =
            new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE};



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        getComponent();

        initToolbar();

        initViewPager();

        initTab();
        event();

        initReceiver();

        check(-1);//检测权限

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (receiver!=null) {
            unregisterReceiver(receiver);
        }

    }
    private void getComponent(){

        toolbar=findViewById(R.id.main_toolbar);

        viewPager=findViewById(R.id.main_pager);

        tabLayout=findViewById(R.id.main_tab);

        shang=findViewById(R.id.shang);

    }

    private void initToolbar(){

        toolbar.setTitle("");

        setSupportActionBar(toolbar);

        int color=getResources().getColor(R.color.colorCyan,getTheme());

        toolbar.setBackgroundColor(color);

    }

    private void initViewPager(){

        adapter=new MainAdapter(getSupportFragmentManager());

        HeaderFragment headerFragment=new HeaderFragment();

        FullFragment fullFragment=new FullFragment();

        List<Fragment> fragments=new ArrayList<>();

        fragments.add(headerFragment);

        fragments.add(fullFragment);

        adapter.setFragmentList(fragments);

        viewPager.setAdapter(adapter);

        viewPager.addOnPageChangeListener(new TabLayout.TabLayoutOnPageChangeListener(tabLayout));

//        viewPager.setOffscreenPageLimit(2);

        viewPager.setCurrentItem(1,false);//保证全部的图片质量不会失效

    }

    private void initTab(){

        if (tabLayout.getTabCount()>=2){
            return;
        }

        tabLayout.addTab(tabLayout.newTab().setText(getString(R.string.mode_top)));
        tabLayout.addTab(tabLayout.newTab().setText(getString(R.string.mode_full)));

        tabLayout.addOnTabSelectedListener(new TabLayout.ViewPagerOnTabSelectedListener(viewPager));

        tabLayout.setSelectedTabIndicatorColor(getResources().getColor(R.color.colorAccent,getTheme()));

        tabLayout.setSelectedTabIndicatorHeight(getResources().getDimensionPixelOffset(R.dimen.tab_height));

        tabLayout.setTabTextColors(getResources().getColor(R.color.colorGrey,getTheme()),getResources().getColor(R.color.colorAccent,getTheme()));


    }


    private void event(){

        shang.setOnClickListener(v -> {

            showShangDialog();
        });

//        about.setOnClickListener(v -> {
//
//            showAbout();
//
//        });


    }

    private void showShangDialog(){

        String content=getString(R.string.shang_info);

        AlertDialog.Builder builder=new AlertDialog.Builder(this);

        View view= LayoutInflater.from(this).inflate(R.layout.about_content,null,false);

        TextView textView=view.findViewById(R.id.about_content);


        textView.setText(content);

        builder.setView(view).setTitle(getString(R.string.shang)).setPositiveButton(getString(R.string.AliPay), (DialogInterface dialog, int which) -> {

            showImage(10);

        });

        builder.setNegativeButton(getString(R.string.wechat),(dialog, which) -> {

            showImage(20);

        });

        builder.setNeutralButton(getString(R.string.QQ),(dialog, which) -> {

            showImage(30);

        }).show();


    }

    private void showImage(int type){

        AlertDialog.Builder builder=new AlertDialog.Builder(this);

        View view= LayoutInflater.from(this).inflate(R.layout.image,null,false);

        ImageView imageView=view.findViewById(R.id.image);

        switch (type){
            case 10:

                imageView.setImageResource(R.drawable.zhifubao);

                break;

            case 20:

                imageView.setImageResource(R.drawable.weixin);

                break;

            case 30:

                imageView.setImageResource(R.drawable.qq);

                break;
        }


        imageView.setOnLongClickListener(v -> {


            check(type);

            return true;
        });

        builder.setView(view).setPositiveButton(getString(R.string.cancel),(dialog, which) -> {

            builder.create().cancel();

        }).setTitle(getString(R.string.thanks)).show();

    }

    int t=-1;

    private void check(int type){


        if (ContextCompat.checkSelfPermission(this, permissionStrings[0]) != PackageManager.PERMISSION_GRANTED) {

            t=type;

            ActivityCompat.requestPermissions(this, new String[]{permissionStrings[0]}, 1000);


        }else {

            t=-1;

            saveImage(t);

        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);

        switch (requestCode) {
            case 1000:
                if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {

                    saveImage(t);


                } else {
                    Toast.makeText(this, getString(R.string.save_info), Toast.LENGTH_SHORT).show();
                }


                break;
        }

    }


    private void saveImage(int type){

        if (type<0){
            return;
        }

        String path= Environment.getExternalStorageDirectory().getAbsolutePath();

        Bitmap bitmap=null;

        switch (type){

            case 10:

                bitmap= BitmapFactory.decodeResource(getResources(),R.drawable.zhifubao);

                File file=new File(path+"/lin16/zhifubao.jpg");

                if (!file.getParentFile().exists()){

                    file.getParentFile().mkdirs();
                }

                try {
                    FileOutputStream fileOutputStream=new FileOutputStream(file);

                    bitmap.compress(Bitmap.CompressFormat.JPEG,100,fileOutputStream);

                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }



                break;

            case 20:

                bitmap= BitmapFactory.decodeResource(getResources(),R.drawable.weixin);

                File file1=new File(path+"/lin16/weixin.jpg");

                if (!file1.getParentFile().exists()){

                    file1.getParentFile().mkdirs();
                }

                try {
                    FileOutputStream fileOutputStream=new FileOutputStream(file1);

                    bitmap.compress(Bitmap.CompressFormat.JPEG,100,fileOutputStream);

                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }

                break;

            case 30:

                bitmap= BitmapFactory.decodeResource(getResources(),R.drawable.qq);

                File file2=new File(path+"/lin16/qq.jpg");

                if (!file2.getParentFile().exists()){

                    file2.getParentFile().mkdirs();
                }

                try {
                    FileOutputStream fileOutputStream=new FileOutputStream(file2);

                    bitmap.compress(Bitmap.CompressFormat.JPEG,100,fileOutputStream);

                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                }

                break;

        }

        Toast.makeText(this, getString(R.string.save), Toast.LENGTH_SHORT).show();

    }


    private void showAbout(){

        String content=getString(R.string.string_about);

        AlertDialog.Builder builder=new AlertDialog.Builder(this);

        View view= LayoutInflater.from(this).inflate(R.layout.about_content,null,false);

        TextView textView=view.findViewById(R.id.about_content);


        textView.setText(content);

        builder.setView(view).setPositiveButton(getString(R.string.determine),(dialog, which) -> {

            builder.create().cancel();

        }).setNegativeButton(getString(R.string.quest),(dialog, which) -> {

            Uri uri = null;

            Locale locale=getResources().getConfiguration().getLocales().get(0);

            if (locale.getCountry().equals("CN")||locale.getCountry().equals("TW")) {
                uri = Uri.parse("https://github.com/liuzhushaonian/Lin15/blob/master/QUEST_ZH.md");
            }else {
                uri = Uri.parse("https://github.com/liuzhushaonian/Lin15/blob/master/QUEST.md");
            }
            Intent intent = new Intent(Intent.ACTION_VIEW, uri);
            startActivity(intent);


        }).show();

    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        getMenuInflater().inflate(R.menu.main_menu,menu);
        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        switch (item.getItemId()){

            case R.id.clean:

                showAlert();

                break;


            case R.id.about_app:

                showAbout();

                break;

            case R.id.diy_height:

                showEditDialog();

                break;

            case R.id.logs_switch:

                showLogAbout();

                break;

            case R.id.panel_infos:

                getPanelInfos();

                break;


        }


        return true;
    }

    private void showAlert(){

        AlertDialog.Builder builder=new AlertDialog.Builder(this);

        builder.setTitle(getString(R.string.reset_title))
                .setMessage(getString(R.string.reset_content))
                .setPositiveButton(getString(R.string.determine),(dialog, which) -> {

                    Intent intent=new Intent(ReceiverAction.SEND_CLEAN_ACTION);

                    sendBroadcast(intent);


                }).setNegativeButton(getString(R.string.cancel),(dialog, which) -> {


                    builder.create().cancel();

        }).show();


    }

    private int getRandomColor(){

        int color=-1;

        Random random=new Random();

        int r=random.nextInt(23);

        List<Integer> colors=new ArrayList<>();

        Resources resources=getResources();

        XmlResourceParser xmlResourceParser=resources.getXml(R.xml.color_list);

        try {
            while (xmlResourceParser.getEventType()!=XmlResourceParser.END_DOCUMENT){

                if (xmlResourceParser.getEventType()== XmlResourceParser.START_TAG){

                    String name=xmlResourceParser.getName();
                    if (name.equals("color")){
                        String c=xmlResourceParser.nextText();

                        int color_int= android.graphics.Color.parseColor(c);

                        colors.add(color_int);
                    }
                }

                xmlResourceParser.next();

            }

            if (r>colors.size()){
                r=21%r;
            }

            color=colors.get(r);//随机颜色


        } catch (Exception e) {
            e.printStackTrace();
        }


        return color;

    }


    private void showEditDialog(){

        AlertDialog.Builder builder=new AlertDialog.Builder(this);

        View view=LayoutInflater.from(this).inflate(R.layout.custom_height,null,false);

        EditText editText=view.findViewById(R.id.edit);

        RadioGroup radioGroup=view.findViewById(R.id.select_type);

        Intent intent=new Intent(ReceiverAction.SEND_CUSTOM_HEIGHT);

        intent.putExtra("type",10);//默认

        radioGroup.setOnCheckedChangeListener((group, checkedId) -> {

            switch (checkedId){

                case R.id.shu:

                    intent.putExtra("type",10);

                    break;

                case R.id.heng:

                    intent.putExtra("type",20);
                    break;

            }

        });

        builder.setTitle(getString(R.string.custom_height)).setView(view).setPositiveButton(getString(R.string.determine),(dialog, which) -> {


            String s=editText.getText().toString();

            intent.putExtra("height",s);

            sendBroadcast(intent);


        }).setNegativeButton(getString(R.string.cancel),(dialog, which) -> {

            builder.create().cancel();

        }).show();

    }

    private void showLogAbout(){

        AlertDialog.Builder builder=new AlertDialog.Builder(this);

        builder.setTitle(getString(R.string.jing)).setMessage(getString(R.string.logs_about))
                .setPositiveButton(getString(R.string.open_log),(dialog, which) -> {

            Intent intent=new Intent(ReceiverAction.SEND_LOGS);
            intent.putExtra(Conf.LOG,true);

            sendBroadcast(intent);


        }).setNegativeButton(getString(R.string.close_log),(dialog, which) -> {

            Intent intent=new Intent(ReceiverAction.SEND_LOGS);
            intent.putExtra(Conf.LOG,false);

            sendBroadcast(intent);

        }).show();

    }

    /**
     * 获取宽高信息
     */
    private void getPanelInfos(){

        Intent intent=new Intent(SEND_INFO_TO_ACTIVITY);

        sendBroadcast(intent);

    }

    private void initReceiver(){

        receiver=new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {


                if (intent==null||intent.getAction()==null){
                    return;
                }

                switch (intent.getAction()){

                    case GET_INFO_TO_ACTIVITY:

                        showPanelInfos(intent);

                        break;

                }

            }
        };

        IntentFilter intentFilter=new IntentFilter();
        intentFilter.addAction(GET_INFO_TO_ACTIVITY);

        registerReceiver(receiver,intentFilter);

    }



    private void showPanelInfos(Intent intent){

        AlertDialog.Builder builder=new AlertDialog.Builder(this);

        int vertical_width=intent.getIntExtra("vertical_width",-1);
        int horizontal_width=intent.getIntExtra("horizontal_width",-1);
        int horizontal_height=intent.getIntExtra("horizontal_height",-1);
        int vertical_height=intent.getIntExtra("vertical_height",-1);

        Locale locale=getResources().getConfiguration().getLocales().get(0);

        String content=getString(R.string.panel_info_content);

        if (locale.getCountry().equals("CN")||locale.getCountry().equals("TW")) {

            content=content+"竖屏宽度："+vertical_width+"\n竖屏高度："+vertical_height+"\n横屏宽度："
                    +horizontal_width+"\n横屏高度："+horizontal_height;

        }else {

            content=content+"vertical_width："+vertical_width+"\nvertical_height："+vertical_height+"\nhorizontal_width："
                    +horizontal_width+"\nhorizontal_height："+horizontal_height;

        }

        builder.setTitle(getString(R.string.about_this)).setMessage(content).show();

    }

}
