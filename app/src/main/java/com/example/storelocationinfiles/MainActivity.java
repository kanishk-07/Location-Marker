package com.example.storelocationinfiles;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.google.android.gms.common.api.ApiException;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.LocationSettingsResponse;
import com.google.android.gms.location.LocationSettingsStatusCodes;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.opencsv.CSVWriter;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.util.function.Consumer;

public class MainActivity extends AppCompatActivity {

    final int REQUEST_CODE_FOR_GPS_LOCATION = 123;
    final int REQUEST_CODE_HARD_DRIVE = 321;
    final int REQUEST_CHECK_SETTING = 1001;

    private Button tasker;

    ActivityResultLauncher<Intent> activityResultLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tasker = (Button) findViewById(R.id.angry_btn);
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d("Clima", "onResume");
        tasker.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                getNStoreLocation();
            }
        });
        activityResultLauncher = registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), new ActivityResultCallback<ActivityResult>() {
            @Override
            public void onActivityResult(ActivityResult result) {
                if(result.getResultCode()==Activity.RESULT_OK) {
                    if(android.os.Build.VERSION.SDK_INT >= 30) {
                        if(Environment.isExternalStorageManager()) {
                            Log.d("Clima", "A11 Storage permission granted");
                        } else {
                            Log.d("Clima", "A11 Storage permission denied");
                        }
                    }
                }
            }
        });
    }

    private void getNStoreLocation() {

        if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            if(android.os.Build.VERSION.SDK_INT <30 &&
                    ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_CODE_HARD_DRIVE);
            }
            if(android.os.Build.VERSION.SDK_INT >=30 && !Environment.isExternalStorageManager()) {
                try {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    intent.addCategory("android.intent.category.default");
                    intent.setData(Uri.parse(String.format("package:%s", new Object[]{getApplicationContext().getPackageName()})));
                    activityResultLauncher.launch(intent);
                } catch (Exception e) {
                    Intent intent = new Intent();
                    intent.setAction(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    activityResultLauncher.launch(intent);
                }
            }

            LocationManager locationManager;
            if (isGPSEnabled()) {
                locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
                Log.d("Clima", "Get Location");
                if (android.os.Build.VERSION.SDK_INT <30) {
                    Log.d("Clima", "Get Location SDK <30");
                    LocationListener locationListener = new LocationListener() {
                        @Override
                        public void onLocationChanged(@NonNull Location location) {
                            confirmNSave(location);
                        }
                    };
                    locationManager.requestSingleUpdate(LocationManager.GPS_PROVIDER, locationListener, Looper.getMainLooper());
                }
                else {
                    Consumer<Location> locationCallback = this::confirmNSave;
                    locationManager.getCurrentLocation(LocationManager.GPS_PROVIDER, null, this.getMainExecutor(), locationCallback);
                }
            } else {
                turnOnGPS();
            }
        } else {
            requestPermissions(new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, REQUEST_CODE_FOR_GPS_LOCATION);
        }
    }

    private void confirmNSave(Location location) {
        String latitude = String.valueOf(location.getLatitude());
        String longitude = String.valueOf(location.getLongitude());
        Toast.makeText(MainActivity.this, latitude +" "+ longitude, Toast.LENGTH_LONG).show();
        Log.d("Clima", latitude +" 105 "+ longitude);
        AlertDialog adl = new AlertDialog.Builder(MainActivity.this)
                .setIcon(R.drawable.ic_baseline_save_24)
                .setTitle("Confirmation")
                .setMessage("Save Location:\nLatitude: "+latitude+"\nLongitude: "+longitude)
                .setPositiveButton("Yes", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        saveLocationInHardDrive(latitude, longitude);
                    }
                })
                .setNegativeButton("No", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                }).create();
        adl.show();
        saveLocationInHardDrive(latitude, longitude);

    }

    private void saveLocationInHardDrive(String latitude, String longitude) {
        Log.d("Clima", "134");
        FileOutputStream fos = null;
        try {
            String path = Environment.getExternalStorageDirectory().getPath() + "/LocationMarker/location.csv";
            File file = new File(path);
            file.createNewFile();
            fos = new FileOutputStream(file);
            boolean bool = file.mkdirs();
            CSVWriter csvWriter = new CSVWriter(new FileWriter(path, true));
            String[] row = new String[]{latitude, longitude};
            csvWriter.writeNext(row);
            csvWriter.close();
            Toast.makeText(MainActivity.this, "Location Stored", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
            Log.d("Clima", "Exception while writing");
        }

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if(requestCode==REQUEST_CODE_FOR_GPS_LOCATION) {
            if(grantResults.length>0 && grantResults[0]== PackageManager.PERMISSION_GRANTED) {
                if (isGPSEnabled()) {
                    getNStoreLocation();
                }else {
                    turnOnGPS();
                    getNStoreLocation();
                }
            }
            else {
                Log.d("Clima", "GPS Location Permission Denied");
            }
        }
        if(requestCode==REQUEST_CODE_HARD_DRIVE) {
            if(grantResults.length>0 && grantResults[0]== PackageManager.PERMISSION_GRANTED) {
                Log.d("Clima", "Storage Permission Granted");
                getNStoreLocation();
            }
            else {
                Log.d("Clima", "Storage Permission Denied");
            }
        }
    }

    private boolean isGPSEnabled() {
        LocationManager locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);
    }

    private void turnOnGPS() {
        LocationRequest locationRequest;
        locationRequest = LocationRequest.create();
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                .addLocationRequest(locationRequest);
        builder.setAlwaysShow(true);

        Task<LocationSettingsResponse> result = LocationServices.getSettingsClient(getApplicationContext())
                .checkLocationSettings(builder.build());

        result.addOnCompleteListener(new OnCompleteListener<LocationSettingsResponse>() {
            @Override
            public void onComplete(@NonNull Task<LocationSettingsResponse> task) {
                try {
                    LocationSettingsResponse response = task.getResult(ApiException.class);
                    Toast.makeText(MainActivity.this, "GPS is already tured on", Toast.LENGTH_SHORT).show();
                } catch (ApiException e) {
                    switch (e.getStatusCode()) {
                        case LocationSettingsStatusCodes.RESOLUTION_REQUIRED:
                            try {
                                ResolvableApiException resolvableApiException = (ResolvableApiException) e;
                                resolvableApiException.startResolutionForResult(MainActivity.this, REQUEST_CHECK_SETTING);
                            } catch (IntentSender.SendIntentException ex) {
                                ex.printStackTrace();
                            }
                            break;
                        case LocationSettingsStatusCodes.SETTINGS_CHANGE_UNAVAILABLE:
                            //Device does not have location
                            break;
                    }
                }
            }
        });

    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == REQUEST_CHECK_SETTING) {
            if (resultCode == Activity.RESULT_OK) {
                getNStoreLocation();
            }
        }
    }

}
