package io.bambosan.mbloader;

import android.content.DialogInterface;
import org.jetbrains.annotations.NotNull;
import android.annotation.SuppressLint;
import 	android.app.AlertDialog;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.TextView;
import android.widget.Button;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Build;
import androidx.appcompat.app.AppCompatActivity;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import android.os.Looper;
public class MainActivity extends AppCompatActivity {

    private static final String MC_PACKAGE_NAME = "com.mojang.minecraftpe";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
//        Looper.prepare();
        TextView listener = findViewById(R.id.listener);
        Button  mbl2_button = findViewById(R.id.mbl2_load);
        Button draco_button = findViewById(R.id.draco_load);
        Handler handler = new Handler(Looper.getMainLooper());
        mbl2_button.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                mbl2_button.setEnabled(false);
                draco_button.setEnabled(false);                
                startLauncher(handler, listener, "launcher_mbl2.dex", MC_PACKAGE_NAME);
                
            }
        });
        
        draco_button.setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v)
            {
                mbl2_button.setEnabled(false);
                draco_button.setEnabled(false);                                
                startLauncher(handler, listener, "launcher_draco.dex", MC_PACKAGE_NAME);    
            }
        });
    // Looper.loop();
}
    private void startLauncher(Handler handler, TextView listener, String launcherDexName, String mcPackageName) {    
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
//                Looper.prepare();
                File cacheDexDir = new File(getCodeCacheDir(), "dex");
                handleCacheCleaning(cacheDexDir, handler, listener);
                ApplicationInfo mcInfo = null;
                try {
                    mcInfo = getPackageManager().getApplicationInfo(mcPackageName, PackageManager.GET_META_DATA);
                } catch(Exception e) {
                    handler.post(() -> alertAndExit("Minecraft cant be found", "Perhaps you dont have it installed?"));
                    return;
                };
                Object pathList = getPathList(getClassLoader());
                processDexFiles(mcInfo, cacheDexDir, pathList, handler, listener, launcherDexName);
                if (!processNativeLibraries(mcInfo, pathList, handler, listener)) {
                    return;
                };
                launchMinecraft(mcInfo);
            } catch (Exception e) {
                //Intent fallbackActivity = new Intent(this, Fallback.class);
                //handleException(e, fallbackActivity);
            String logMessage = e.getCause() != null ? e.getCause().toString() : e.toString();                
            handler.post(() -> listener.setText("Launching failed: " + logMessage));                
            }
        });    
    }
    @SuppressLint("SetTextI18n")
    private void handleCacheCleaning(@NotNull File cacheDexDir, Handler handler, TextView listener) {
        if (cacheDexDir.exists() && cacheDexDir.isDirectory()) {
            handler.post(() -> listener.setText("-> " + cacheDexDir.getAbsolutePath() + " not empty, do cleaning"));
            for (File file : Objects.requireNonNull(cacheDexDir.listFiles())) {
                if (file.delete()) {
                    handler.post(() -> listener.append("\n-> " + file.getName() + " deleted"));
                }
            }
        } else {
            handler.post(() -> listener.setText("-> " + cacheDexDir.getAbsolutePath() + " is empty, skip cleaning"));
        }
    }

    private Object getPathList(@NotNull ClassLoader classLoader) throws Exception {
        Field pathListField = Objects.requireNonNull(classLoader.getClass().getSuperclass()).getDeclaredField("pathList");
        pathListField.setAccessible(true);
        return pathListField.get(classLoader);
    }

    private void processDexFiles(ApplicationInfo mcInfo, File cacheDexDir, @NotNull Object pathList, @NotNull Handler handler, TextView listener, String launcherDexName) throws Exception {
        Method addDexPath = pathList.getClass().getDeclaredMethod("addDexPath", String.class, File.class);
        File launcherDex = new File(cacheDexDir, launcherDexName);

        copyFile(getAssets().open(launcherDexName), launcherDex);
        handler.post(() -> listener.append("\n-> " + launcherDexName + " copied to " + launcherDex.getAbsolutePath()));

        if (launcherDex.setReadOnly()) {
            addDexPath.invoke(pathList, launcherDex.getAbsolutePath(), null);
            handler.post(() -> listener.append("\n-> " + launcherDexName + " added to dex path list"));
        }
        ArrayList<String> copiedDexes = new ArrayList<String>();
        try (ZipFile zipFile = new ZipFile(mcInfo.sourceDir)) {
            for (int i = 10; i >= 0; i--) {
                String dexName = "classes" + (i == 0 ? "" : i) + ".dex";
                ZipEntry dexFile = zipFile.getEntry(dexName);
                if (dexFile != null) {
                    File mcDex = new File(cacheDexDir, dexName);
                    copyFile(zipFile.getInputStream(dexFile), mcDex);
//                    handler.post(() -> listener.append("\n-> " + mcInfo.sourceDir + "/" + dexName + " copied to " + mcDex.getAbsolutePath()));
                    if (mcDex.setReadOnly()) {
                        addDexPath.invoke(pathList, mcDex.getAbsolutePath(), null);
                        copiedDexes.add(dexName);
//                        handler.post(() -> listener.append("\n-> " + dexName + " added to dex path list"));
                    }
                }
            }
        } catch (Throwable th) {}    
        handler.post(() -> listener.append("\n-> Dex files " + copiedDexes.toString() + " copied and added to dex path list"));        
    }

    private boolean processNativeLibraries(ApplicationInfo mcInfo, @NotNull Object pathList, @NotNull Handler handler, TextView listener) throws Exception {
        FileInputStream inStream = new FileInputStream(getApkWithLibs(mcInfo));
 		    BufferedInputStream bufInStream = new BufferedInputStream(inStream);
 		    ZipInputStream inZipStream = new ZipInputStream(bufInStream);
 		    if (!checkLibCompatibility(inZipStream)) {
            handler.post(() -> alertAndExit("Wrong minecraft architecture", "The minecraft you have installed does not support the same main architecture (" + Build.SUPPORTED_ABIS[0] + ") your device uses, mbloader cant work with it"));
            return false;
 		       // 		        throw new Exception("Installled minecraft does not support main arch of device: " + Build.SUPPORTED_ABIS[0]);
 		    } 		    
        Method addNativePath = pathList.getClass().getDeclaredMethod("addNativePath", Collection.class);
        ArrayList<String> libDirList = new ArrayList<>();
        File libdir = new File(mcInfo.nativeLibraryDir);
			  if (libdir.list() == null || libdir.list().length == 0 
			   || (mcInfo.flags & ApplicationInfo.FLAG_EXTRACT_NATIVE_LIBS) != ApplicationInfo.FLAG_EXTRACT_NATIVE_LIBS) {
				    loadUnextractedLibs(mcInfo);
				    libDirList.add(getCodeCacheDir().getAbsolutePath() + "/");
			  } else {
         libDirList.add(mcInfo.nativeLibraryDir);
         }
        addNativePath.invoke(pathList, libDirList);
        handler.post(() -> listener.append("\n-> " + mcInfo.nativeLibraryDir + " added to native library directory path"));
        return true;
    }
    private static Boolean checkLibCompatibility(ZipInputStream zip) throws Exception{
         ZipEntry ze = null;
         String requiredLibDir = "lib/" + Build.SUPPORTED_ABIS[0] + "/";
         while ((ze = zip.getNextEntry()) != null) {
             if (ze.getName().startsWith(requiredLibDir)) {
                 return true;
             }
         }
         zip.close();
         return false;
     }
     private void alertAndExit(String issue, String description) {
                    AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
                    alertDialog.setTitle(issue);
                    alertDialog.setMessage(description);
                    alertDialog.setCancelable(false);
                    alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "Exit",
                    new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            finish();
                        }
                    });
                    alertDialog.show();         
     }
    private void loadUnextractedLibs(ApplicationInfo appInfo) throws Exception {
		    FileInputStream inStream = new FileInputStream(getApkWithLibs(appInfo));
		    BufferedInputStream bufInStream = new BufferedInputStream(inStream);
		    ZipInputStream inZipStream = new ZipInputStream(bufInStream);
		    String zipPath = "lib/" + Build.SUPPORTED_ABIS[0] + "/";
		    String outPath = getCodeCacheDir().getAbsolutePath() + "/";
		    File dir = new File(outPath);
		    dir.mkdir();
		    extractDir(appInfo, inZipStream, zipPath, outPath);
	  }
	  public String getApkWithLibs(ApplicationInfo pkg) throws PackageManager.NameNotFoundException
	{
		// get installed split's Names
		String[] sn=pkg.splitSourceDirs;

		// check whether if it's really split or not
		if (sn != null && sn.length > 0)
		{
			String cur_abi = Build.SUPPORTED_ABIS[0].replace('-','_');
			// search installed splits
			for(String n:sn){
				
				 //check whether is the one required
				if(n.contains(cur_abi)){
				// yes, it's installed!
					return n;
				}
			}
		}
		// couldn't find!
		return pkg.sourceDir;
	}
	private static void extractDir(ApplicationInfo mcInfo, ZipInputStream zip, String zip_folder, String out_folder ) throws Exception{
        ZipEntry ze = null;
        while ((ze = zip.getNextEntry()) != null) {
            if (ze.getName().startsWith(zip_folder) && !ze.getName().contains("c++_shared")) {
				String strippedName = ze.getName().substring(zip_folder.length());
				String path = out_folder + "/" + strippedName;
				OutputStream out = new FileOutputStream(path);
				BufferedOutputStream outBuf = new BufferedOutputStream(out);
                byte[] buffer = new byte[9000];
                int len;
                while ((len = zip.read(buffer)) != -1) {
                    outBuf.write(buffer, 0, len);
                }
                outBuf.close();
            }
        }
        zip.close();
    }
    private void launchMinecraft(ApplicationInfo mcInfo) throws ClassNotFoundException {
        Class<?> launcherClass = getClassLoader().loadClass("com.mojang.minecraftpe.Launcher");
        // We do this to preserve data that apps like file managers pass 
        Intent mcActivity = getIntent().setClass(this, launcherClass);
        mcActivity.putExtra("MC_SRC", mcInfo.sourceDir);

        if (mcInfo.splitSourceDirs != null) {
            ArrayList<String> listSrcSplit = new ArrayList<>();
            Collections.addAll(listSrcSplit, mcInfo.splitSourceDirs);
            mcActivity.putExtra("MC_SPLIT_SRC", listSrcSplit);
        }
        startActivity(mcActivity);
        finish();
    }

    private void handleException(@NotNull Exception e, @NotNull Intent fallbackActivity) {
        String logMessage = e.getCause() != null ? e.getCause().toString() : e.toString();
        fallbackActivity.putExtra("LOG_STR", logMessage);
        startActivity(fallbackActivity);
        finish();
    }

    private static void copyFile(InputStream from, @NotNull File to) throws IOException {
        File parentDir = to.getParentFile();
        if (parentDir != null && !parentDir.exists() && !parentDir.mkdirs()) {
            throw new IOException("Failed to create directories");
        }
        if (!to.exists() && !to.createNewFile()) {
            throw new IOException("Failed to create new file");
        }
        try (BufferedInputStream input = new BufferedInputStream(from);
             BufferedOutputStream output = new BufferedOutputStream(Files.newOutputStream(to.toPath()))) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = input.read(buffer)) != -1) {
                output.write(buffer, 0, bytesRead);
            }
        }
    }
    
}
