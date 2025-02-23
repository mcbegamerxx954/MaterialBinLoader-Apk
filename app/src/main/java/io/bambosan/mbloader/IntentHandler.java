package io.bambosan.mbloader;


import android.content.Intent;
import android.app.Activity;
import android.os.Bundle;

public class IntentHandler extends Activity
{
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
				patiencepls(getIntent());
        //setContentView(R.layout.main);
		
    }
	void patiencepls(Intent intention) {
		if (isMcLoaded()) {
			intention.setClassName(getApplicationContext(), "com.mojang.minecraftpe.Launcher");
		} else {
			intention.setClassName(getApplicationContext(), "io.bambosan.mbloader.MainActivity");
		}
		startActivity(intention);
	}
	Boolean isMcLoaded() {
		try {
			Class.forName("com.mojang.minecraftpe.Launcher", false, getClassLoader());
			return true;
		} catch (Exception e) {
			return false;
		}
	}
}
