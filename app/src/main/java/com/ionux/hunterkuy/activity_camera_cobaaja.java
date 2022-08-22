package com.ionux.hunterkuy;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.ionux.hunterkuy.proses_Kamera.camera_fragment_cobaaja;

import androidx.appcompat.app.AlertDialog;

public class activity_camera_cobaaja extends Activity {
    private TextView txtPilihMode;
    private LinearLayout TopGame, BotGame;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
       // getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_camera_cobaaja);

        if (null == savedInstanceState) {
            getFragmentManager()
                    .beginTransaction()
                    .replace(R.id.container_cobaaja, camera_fragment_cobaaja.newInstance())
                    .commit();
        }


    }

    @Override
    public void onBackPressed() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setCancelable(false);
        builder.setMessage("Bali menyang omah ?");
        builder.setPositiveButton("Leres", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {

                finish();
            }
        });
        builder.setNegativeButton("Sanes", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
            }
        });
        AlertDialog alert = builder.create();
        alert.show();
    }

    @Override
    public boolean onKeyDown(int key_code, KeyEvent key_event){
        if (key_code== KeyEvent.KEYCODE_BACK)
        {
            super.onKeyDown(key_code,key_event);
            return true;
        }
        return false;
    }
}
