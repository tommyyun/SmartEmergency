package com.example.q.smartemergency;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.location.Location;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.StringRequest;
import com.android.volley.toolbox.Volley;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import org.json.JSONObject;
import org.w3c.dom.Text;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import static com.google.android.gms.location.LocationServices.getFusedLocationProviderClient;

public class MainActivity extends AppCompatActivity {
    public String tel = "01068594888";
    public String caseId = null;
    private final int INITIAL_PERMISSION = 0;
    private final int CALL_PHONE_REQUEST_FOR_EMERGENCY_CALL = 1;
    private static final int CAMERA_PERMISSION_REQUEST = 2;
    private GoogleMap map;

    @SuppressLint("MissingPermission")
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case INITIAL_PERMISSION: {
                for (int i = 0; i < permissions.length; i++)
                    if (permissions[i].equals(Manifest.permission.ACCESS_FINE_LOCATION))
                        if (grantResults[i] == PackageManager.PERMISSION_GRANTED)
                            setupMap();
                break;
            }

            case CALL_PHONE_REQUEST_FOR_EMERGENCY_CALL: {
                if (permissions.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED)
                    startActivity(new Intent(Intent.ACTION_CALL, Uri.parse("tel:119")));
                else
                    Toast.makeText(this, "전화 권한이 없습니다.권한 요청을 승인해주세요.", Toast.LENGTH_SHORT).show();
                break;
            }

            case CAMERA_PERMISSION_REQUEST: {
                String[] PERMISSIONS = {Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.CAMERA};
                if (!hasCameraPermissions(this, PERMISSIONS))
                    Toast.makeText(this, "카메라, 저장공간 권한은 필수입니다. 설정에서 변경해주세요.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (resultCode == RESULT_OK) {
            if (requestCode == CAMERA_CASE_IMAGE_CODE) {
                galleryAddPicture();
                sendCaseImage();
            }
            if (requestCode == CAMERA_ID_IMAGE_CODE) {
                galleryAddPicture();
                sendIdImage();
            }
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // TODO : get tel from database

        if(tel==null)
            loggedOutInitialization();

        else {
            loggedInInitialization();
        }

    }

    void loggedOutInitialization() {
//        setContentView(R.layout.login);

    }

    void loggedInInitialization() {
        setContentView(R.layout.network_error_layout);

        Volley.newRequestQueue(this).add(new StringRequest("http://52.231.68.157:8080/public/" + tel, new Response.Listener<String>() {
            @Override
            public void onResponse(String response) {
                if (response.equals("not found")) {
                    setContentView(R.layout.standby_layout);
                    standby_initialization();
                } else if (response != null) {
                    System.out.println("Response : " + response);
                    caseId = response;
                    setContentView(R.layout.case_layout);
                    case_initialization();
                } else
                    finish();
            }
        }, null));
    }


    ///// ----- STANDBY LAYOUT INITIALIZATION START ----- /////

    void standby_initialization() {

        // Layout initialization
        ((TextView)findViewById(R.id.tel_field)).setText("인증정보 : "+tel);
        Point point = new Point();
        getWindowManager().getDefaultDisplay().getSize(point);
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map);
        mapFragment.getView().getLayoutParams().width = point.x - 40 * (int) (((float) getResources().getDisplayMetrics().densityDpi) / DisplayMetrics.DENSITY_DEFAULT);
        mapFragment.getView().getLayoutParams().height = point.x * 3 / 4;


        // Map and permission initialization - START
        mapFragment.getMapAsync(new OnMapReadyCallback() {
            @Override
            public void onMapReady(final GoogleMap googleMap) {
                map = googleMap;
                googleMap.moveCamera(CameraUpdateFactory.zoomTo(10));
                googleMap.getUiSettings().setZoomControlsEnabled(true);
                googleMap.moveCamera(CameraUpdateFactory.newLatLng(new LatLng(37.549573, 126.989079)));

                // Setup emergency GPS button
                findViewById(R.id.emergency_button).setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        final LatLng latlng = map.getCameraPosition().target;
                        final JSONObject body = new JSONObject();
                        try {
                            body.put("tel", tel);
                            body.put("lat", Double.toString(latlng.latitude));
                            body.put("lng", Double.toString(latlng.longitude));
                        } catch (Exception e) {
                            System.out.println(e.getMessage());
                            e.printStackTrace();
                        }
                        Volley.newRequestQueue(MainActivity.this).add(new JsonObjectRequest(Request.Method.POST, "http://52.231.68.157:8080/public/report", body, new Response.Listener<JSONObject>() {
                            @Override
                            public void onResponse(JSONObject response) {
                                try {
                                    if (response.getInt("distance") != -1) {
                                        new ProxWarnDialog(MainActivity.this, body, response).show();
                                    }
                                    else {
                                        caseId = response.getString("caseId");
                                        setContentView(R.layout.case_layout);
                                        case_initialization();
                                    }
                                } catch (Exception e) {
                                    System.out.println(e.getMessage());
                                    e.printStackTrace();
                                }
                            }
                        }, new Response.ErrorListener() {
                            @Override
                            public void onErrorResponse(VolleyError error) {
                                System.out.println("Server error : " + error.toString());
                                Toast.makeText(MainActivity.this, "구조요청 실패, 전화로 연결합니다.", Toast.LENGTH_SHORT).show();
                                if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.CALL_PHONE}, CALL_PHONE_REQUEST_FOR_EMERGENCY_CALL);
                                    return;
                                }
                                startActivity(new Intent(Intent.ACTION_CALL, Uri.parse("tel:119")));
                            }
                        }));
                    }
                });

                // Permission check
                ArrayList<String> permissionList = new ArrayList<>();
                if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
                    permissionList.add(Manifest.permission.ACCESS_FINE_LOCATION);
                else
                    setupMap();
                if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED)
                    permissionList.add(Manifest.permission.CALL_PHONE);

                String[] permissionRequests = new String[permissionList.size()];
                for (int i = 0; i < permissionRequests.length; i++) {
                    permissionRequests[i] = permissionList.get(i);
                }
                if (permissionRequests.length > 0)
                    ActivityCompat.requestPermissions(MainActivity.this, permissionRequests, 0);
            }
        });
        // Map and permission initialization - END


        // Setup emergency call button
        findViewById(R.id.emergency_call).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.CALL_PHONE}, CALL_PHONE_REQUEST_FOR_EMERGENCY_CALL);
                    return;
                }
                startActivity(new Intent(Intent.ACTION_CALL, Uri.parse("tel:119")));
            }
        });
    }

    void setupMap() {
        if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED)
            Toast.makeText(MainActivity.this, "위치 권한이 없습니다. 설정에서 권한 요청을 승인해주세요.", Toast.LENGTH_SHORT).show();
        else {
            map.setMyLocationEnabled(true);
            map.getUiSettings().setMyLocationButtonEnabled(true);
            getFusedLocationProviderClient(MainActivity.this).getLastLocation().addOnCompleteListener(MainActivity.this, new OnCompleteListener<Location>() {
                @Override
                public void onComplete(@NonNull Task<Location> task) {
                    Location location = task.getResult();
                    map.moveCamera(CameraUpdateFactory.newLatLng(new LatLng(location.getLatitude(), location.getLongitude())));
                }
            });
            map.moveCamera(CameraUpdateFactory.zoomTo(17));
        }
    }

    ///// ----- STANDBY LAYOUT INITIALIZATION FINISH ----- /////

    //---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------//

    ///// ----- CASE LAYOUT INITIALIZATION START ----- /////

    private String FILE_PATH_TO_SAVE = "/DCIM/SmartEmergency/";
    private Uri imageUri_camera;
    private String imageFilePath_camera, imageFileName_camera;
    private int serverResponseCode = 0;
    private static final int CAMERA_CASE_IMAGE_CODE = 0;
    private static final int CAMERA_ID_IMAGE_CODE = 1;

    void case_initialization() {
        ((TextView)findViewById(R.id.tel_field)).setText("인증정보 : "+tel);
        getCameraPermission();
        ((TextView) findViewById(R.id.case_field)).setText(caseId);
        findViewById(R.id.id_image).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String[] PERMISSIONS = {Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.CAMERA};
                if (hasCameraPermissions(MainActivity.this, PERMISSIONS))
                    takePictureAndSendAsIdImage();
                else
                    Toast.makeText(MainActivity.this, "카메라 및 저장공간 권한이 필요합니다.", Toast.LENGTH_SHORT).show();
            }
        });
        findViewById(R.id.case_image).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String[] PERMISSIONS = {Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.CAMERA};
                if (hasCameraPermissions(MainActivity.this, PERMISSIONS))
                    takePictureAndSendAsCaseImage();
                else
                    Toast.makeText(MainActivity.this, "카메라 및 저장공간 권한이 필요합니다.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void takePictureAndSendAsCaseImage() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(getPackageManager()) != null) {
            File imageFile = null;
            try {
                imageFile = createImageFile(FILE_PATH_TO_SAVE);
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (imageFile != null) {
                imageUri_camera = FileProvider.getUriForFile(this, getPackageName(), imageFile);
                intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri_camera);
                startActivityForResult(intent, CAMERA_CASE_IMAGE_CODE);
            }
        }
    }

    private void takePictureAndSendAsIdImage() {
        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (intent.resolveActivity(getPackageManager()) != null) {
            File imageFile = null;
            try {
                imageFile = createImageFile(FILE_PATH_TO_SAVE);
            } catch (IOException e) {
                e.printStackTrace();
            }
            if (imageFile != null) {
                imageUri_camera = FileProvider.getUriForFile(this, getPackageName(), imageFile);
                intent.putExtra(MediaStore.EXTRA_OUTPUT, imageUri_camera);
                startActivityForResult(intent, CAMERA_ID_IMAGE_CODE);
            }
        }
    }

    private File createImageFile(String path) throws IOException {
        File saveDir = new File(Environment.getExternalStorageDirectory() + path);
        if (!saveDir.exists()) saveDir.mkdirs();
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        imageFileName_camera = timeStamp + ".jpg";
        File imageFile = new File(Environment.getExternalStorageDirectory().getAbsoluteFile() + path + imageFileName_camera);
        imageFilePath_camera = imageFile.getAbsolutePath();
        return imageFile;
    }

    private void galleryAddPicture() {
        Intent intent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        File f = new File(imageFilePath_camera);
        Uri contentUri = Uri.fromFile(f);
        intent.setData(contentUri);
        this.sendBroadcast(intent);
    }

    private void sendCaseImage() {
        new UploadTask("http://52.231.68.157:8080/public/report/sendImage/" + tel + "/" + caseId).execute();
    }

    private void sendIdImage() {
        new UploadTask("http://52.231.68.157:8080/public/report/sendPatientId/" + tel + "/" + caseId).execute();
    }

    public void getCameraPermission() {
        String[] PERMISSIONS = {Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.CAMERA};
        if (!hasCameraPermissions(this, PERMISSIONS)) {
            ActivityCompat.requestPermissions(this, PERMISSIONS, CAMERA_PERMISSION_REQUEST);
        }
    }

    public boolean hasCameraPermissions(Context context, String... permissions) {
        if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && context != null && permissions != null) {
            for (String permission : permissions) {
                if (ActivityCompat.checkSelfPermission(context, permission) != PackageManager.PERMISSION_GRANTED) {
                    return false;
                }
            }
        }
        return true;
    }

    public class UploadTask extends AsyncTask<Void, Void, Void> {
        String url = null;

        public UploadTask(String _url) {
            url = _url;
        }

        @Override
        protected Void doInBackground(Void... voids) {
            uploadFile(imageFileName_camera, imageFilePath_camera, url);
            return null;
        }
    }

    public int uploadFile(String fileName, String filepath, String serverURL) {

        if (fileName == null || filepath == null) {
            serverResponseCode = -1;
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, " File upload Fail (null)", Toast.LENGTH_LONG).show();
                }
            });
            return 0;
        }

        HttpURLConnection conn = null;
        DataOutputStream dos = null;
        String lineEnd = "\r\n";
        String twoHyphens = "--";
        String boundary = "***";
        int bytesRead, bytesAvailable, bufferSize;
        byte[] buffer;
        int maxBufferSize = 1 * 1024 * 1024;
        File sourceFile = new File(filepath);
        if (!sourceFile.isFile()) {
            serverResponseCode = -1;
            Log.e("uploadFile", "Source File not exist : " + filepath);
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, " File not exist ", Toast.LENGTH_LONG).show();
                }
            });
            return 0;
        } else {
            try {
                // open a URL connection to the Servlet
                FileInputStream fileInputStream = new FileInputStream(sourceFile);
                URL url = new URL(serverURL);

                // Open a HTTP  connection to  the URL
                conn = (HttpURLConnection) url.openConnection();
                conn.setDoInput(true); // Allow Inputs
                conn.setDoOutput(true); // Allow Outputs
                conn.setUseCaches(false); // Don't use a Cached Copy
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Connection", "Keep-Alive");
                conn.setRequestProperty("ENCTYPE", "multipart/form-data");
                conn.setRequestProperty("Content-Type", "multipart/form-data;boundary=" + boundary);
                conn.setRequestProperty("uploaded_file", fileName);

                dos = new DataOutputStream(conn.getOutputStream());

                dos.writeBytes(twoHyphens + boundary + lineEnd);
                dos.writeBytes("Content-Disposition: form-data; name=\"uploaded_file\";filename=\"" + fileName + "\"" + lineEnd);

                dos.writeBytes(lineEnd);

                // create a buffer of  maximum size
                bytesAvailable = fileInputStream.available();

                bufferSize = Math.min(bytesAvailable, maxBufferSize);
                buffer = new byte[bufferSize];

                // read file and write it into form...
                bytesRead = fileInputStream.read(buffer, 0, bufferSize);

                while (bytesRead > 0) {

                    dos.write(buffer, 0, bufferSize);
                    bytesAvailable = fileInputStream.available();
                    bufferSize = Math.min(bytesAvailable, maxBufferSize);
                    bytesRead = fileInputStream.read(buffer, 0, bufferSize);

                }

                // send multipart form data necesssary after file data...
                dos.writeBytes(lineEnd);
                dos.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);

                // Responses from the server (code and message)
                serverResponseCode = conn.getResponseCode();
                String serverResponseMessage = conn.getResponseMessage();

                Log.i("uploadFile", "HTTP Response is : " + serverResponseMessage + ": " + serverResponseCode);

                if (serverResponseCode == 200) {
                    BufferedReader input = new BufferedReader(new InputStreamReader(conn.getInputStream()), 8192);
                    final StringBuilder response = new StringBuilder();
                    String strLine = null;
                    while ((strLine = input.readLine()) != null) {
                        response.append(strLine);
                    }
                    input.close();
                }

                fileInputStream.close();
                dos.flush();
                dos.close();

            } catch (Exception e) {
                serverResponseCode = -1;
                e.printStackTrace();
            }
        }
        if (serverResponseCode == 200) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, "전송 성공", Toast.LENGTH_SHORT).show();
                }
            });

        }
        return serverResponseCode;
    }

    ///// ----- CASE LAYOUT INITIALIZATION FINISH ----- /////

    class ProxWarnDialog extends Dialog {

        JSONObject body;
        JSONObject response;

        public ProxWarnDialog(@NonNull Context context, JSONObject _body, JSONObject _response) {
            super(context);
            body = _body;
            response = _response;
        }

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.prox_warn_dialog);
            try {
                ((TextView) findViewById(R.id.warning)).setText(response.getInt("distance") + "m 거리에 접수된 신고가 있습니다.");
            }catch(Exception e) {
                System.out.println(e.getMessage());
                e.printStackTrace();
            }
            findViewById(R.id.force_report_button).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    Volley.newRequestQueue(getContext()).add(new JsonObjectRequest(Request.Method.POST, "http://52.231.68.157:8080/public/report/re", body, new Response.Listener<JSONObject>() {
                        @Override
                        public void onResponse(JSONObject response) {
                            try {
                                caseId = response.getString("caseId");
                                MainActivity.this.setContentView(R.layout.case_layout);
                                case_initialization();
                            } catch (Exception e) {
                                System.out.println(e.getMessage());
                                e.printStackTrace();
                            }
                            cancel();
                        }
                    }, new Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(VolleyError error) {
                            System.out.println("Server error : " + error.toString());
                            Toast.makeText(MainActivity.this, "구조요청 실패, 전화로 연결합니다.", Toast.LENGTH_SHORT).show();
                            if (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                                ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.CALL_PHONE}, CALL_PHONE_REQUEST_FOR_EMERGENCY_CALL);
                                return;
                            }
                            startActivity(new Intent(Intent.ACTION_CALL, Uri.parse("tel:119")));
                        }
                    }));
                }
            });
            findViewById(R.id.duplicate_button).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    try {
                        System.out.println(body.toString());
                        caseId = response.getString("caseId");
                    } catch(Exception e) {
                        System.out.println(e.getMessage());
                        e.printStackTrace();
                    }
                    Volley.newRequestQueue(getContext()).add(new StringRequest("http://52.231.68.157:8080/public/report/duplicate/" + tel + "/" + caseId, null, new Response.ErrorListener() {
                        @Override
                        public void onErrorResponse(VolleyError error) {
                            Toast.makeText(getContext(), "네트워크 오류 다시 시도해주세요.", Toast.LENGTH_SHORT).show();
                        }
                    }));
                    MainActivity.this.setContentView(R.layout.case_layout);
                    case_initialization();
                    cancel();
                }
            });
            findViewById(R.id.cancel_button).setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    cancel();
                }
            });
        }
    }

}