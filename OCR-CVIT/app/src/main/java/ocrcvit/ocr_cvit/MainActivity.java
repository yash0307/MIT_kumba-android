package ocrcvit.ocr_cvit;

import android.app.Activity;
import android.app.ProgressDialog;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.speech.tts.TextToSpeech;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Locale;

import com.loopj.android.http.*;
import org.apache.http.*;

import eu.janmuller.android.simplecropimage.CropImage;

public class MainActivity extends Activity {

    public static final String TAG = "MainActivity";

    public static final String TEMP_PHOTO_FILE_NAME = "temp_photo.JPEG";

    public static final int REQUEST_CODE_GALLERY      = 0x1;
    public static final int REQUEST_CODE_TAKE_PICTURE = 0x2;
    public static final int REQUEST_CODE_CROP_IMAGE   = 0x3;

    private ImageView mImageView;
    private File      mFileTemp;

    private int serverResponseCode = 0;
    private ProgressDialog dialog = null;

    private String upLoadServerUri = "http://192.168.43.29:8080/imgtotxt/";
    private String imagepath=null;

    private Button uploadButton;
    private ContentResolver mContentResolver;

    private String globResponseString;

    final Context context = this;

    private TextToSpeech t1;

    private String choosenLanguage;

    public static
    String unescape_perl_string(String oldstr) {

        StringBuffer newstr = new StringBuffer(oldstr.length());

        boolean saw_backslash = false;

        for (int i = 0; i < oldstr.length(); i++) {
            int cp = oldstr.codePointAt(i);
            if (oldstr.codePointAt(i) > Character.MAX_VALUE) {
                i++;
            }
            if (!saw_backslash) {
                if (cp == '\\') {
                    saw_backslash = true;
                } else {
                    newstr.append(Character.toChars(cp));
                }
                continue; /* switch */
            }
            if (cp == '\\') {
                saw_backslash = false;
                newstr.append('\\');
                newstr.append('\\');
                continue; /* switch */
            }

            switch (cp) {

                case 'r':  newstr.append('\r');
                    break; /* switch */

                case 'n':  newstr.append('\n');
                    break; /* switch */

                case 'f':  newstr.append('\f');
                    break; /* switch */

                case 'b':  newstr.append("\\b");
                    break; /* switch */

                case 't':  newstr.append('\t');
                    break; /* switch */

                case 'a':  newstr.append('\007');
                    break; /* switch */

                case 'e':  newstr.append('\033');
                    break; /* switch */

                case 'c':   {
                    if (++i == oldstr.length()) { die("trailing \\c"); }
                    cp = oldstr.codePointAt(i);
                    if (cp > 0x7f) { die("expected ASCII after \\c"); }
                    newstr.append(Character.toChars(cp ^ 64));
                    break; /* switch */
                }

                case '8':
                case '9': die("illegal octal digit");
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7': --i;
                case '0': {
                    if (i+1 == oldstr.length()) {
                        newstr.append(Character.toChars(0));
                        break; /* switch */
                    }
                    i++;
                    int digits = 0;
                    int j;
                    for (j = 0; j <= 2; j++) {
                        if (i+j == oldstr.length()) {
                            break; /* for */
                        }
                    /* safe because will unread surrogate */
                        int ch = oldstr.charAt(i+j);
                        if (ch < '0' || ch > '7') {
                            break; /* for */
                        }
                        digits++;
                    }
                    if (digits == 0) {
                        --i;
                        newstr.append('\0');
                        break; /* switch */
                    }
                    int value = 0;
                    try {
                        value = Integer.parseInt(
                                oldstr.substring(i, i+digits), 8);
                    } catch (NumberFormatException nfe) {
                        die("invalid octal value for \\0 escape");
                    }
                    newstr.append(Character.toChars(value));
                    i += digits-1;
                    break; /* switch */
                } /* end case '0' */

                case 'x':  {
                    if (i+2 > oldstr.length()) {
                        die("string too short for \\x escape");
                    }
                    i++;
                    boolean saw_brace = false;
                    if (oldstr.charAt(i) == '{') {
                        /* ^^^^^^ ok to ignore surrogates here */
                        i++;
                        saw_brace = true;
                    }
                    int j;
                    for (j = 0; j < 8; j++) {

                        if (!saw_brace && j == 2) {
                            break;  /* for */
                        }
                        int ch = oldstr.charAt(i+j);
                        if (ch > 127) {
                            die("illegal non-ASCII hex digit in \\x escape");
                        }

                        if (saw_brace && ch == '}') { break; /* for */ }

                        if (! ( (ch >= '0' && ch <= '9')
                                ||
                                (ch >= 'a' && ch <= 'f')
                                ||
                                (ch >= 'A' && ch <= 'F')
                        )
                                )
                        {
                            die(String.format(
                                    "illegal hex digit #%d '%c' in \\x", ch, ch));
                        }

                    }
                    if (j == 0) { die("empty braces in \\x{} escape"); }
                    int value = 0;
                    try {
                        value = Integer.parseInt(oldstr.substring(i, i+j), 16);
                    } catch (NumberFormatException nfe) {
                        die("invalid hex value for \\x escape");
                    }
                    newstr.append(Character.toChars(value));
                    if (saw_brace) { j++; }
                    i += j-1;
                    break; /* switch */
                }

                case 'u': {
                    if (i+4 > oldstr.length()) {
                        die("string too short for \\u escape");
                    }
                    i++;
                    int j;
                    for (j = 0; j < 4; j++) {
                    /* this also handles the surrogate issue */
                        if (oldstr.charAt(i+j) > 127) {
                            die("illegal non-ASCII hex digit in \\u escape");
                        }
                    }
                    int value = 0;
                    try {
                        value = Integer.parseInt( oldstr.substring(i, i+j), 16);
                    } catch (NumberFormatException nfe) {
                        die("invalid hex value for \\u escape");
                    }
                    newstr.append(Character.toChars(value));
                    i += j-1;
                    break; /* switch */
                }

                case 'U': {
                    if (i+8 > oldstr.length()) {
                        die("string too short for \\U escape");
                    }
                    i++;
                    int j;
                    for (j = 0; j < 8; j++) {
                    /* this also handles the surrogate issue */
                        if (oldstr.charAt(i+j) > 127) {
                            die("illegal non-ASCII hex digit in \\U escape");
                        }
                    }
                    int value = 0;
                    try {
                        value = Integer.parseInt(oldstr.substring(i, i+j), 16);
                    } catch (NumberFormatException nfe) {
                        die("invalid hex value for \\U escape");
                    }
                    newstr.append(Character.toChars(value));
                    i += j-1;
                    break; /* switch */
                }

                default:   newstr.append('\\');
                    newstr.append(Character.toChars(cp));
           /*
            * say(String.format(
            *       "DEFAULT unrecognized escape %c passed through",
            *       cp));
            */
                    break; /* switch */

            }
            saw_backslash = false;
        }

    /* weird to leave one at the end */
        if (saw_backslash) {
            newstr.append('\\');
        }

        return newstr.toString();
    }
    private static final
    void die(String foa) {
        throw new IllegalArgumentException(foa);
    }



    String[] languages = new String[] {
            "English",
            "Bangla",
            "Gujarati",
            "Hindi",
            "Kannada",
            "Tamil",
            "Telugu",
            "Urdu"
    };



    ArrayAdapter<String> languageAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);    //To change body of overridden methods use File | Settings | File Templates.
        setContentView(R.layout.activity_main);



        final Spinner languageSpinner=(Spinner)findViewById(R.id.language_spinner);

        uploadButton = (Button)findViewById(R.id.ocr_button);

        //set adapter to spinner
        languageAdapter=new ArrayAdapter<String>(this,android.R.layout.simple_spinner_item,languages);
        languageAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        languageSpinner.setAdapter(languageAdapter);


        findViewById(R.id.gallery).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                openGallery();
            }
        });

        findViewById(R.id.take_picture).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {

                takePicture();
                /*dialog = ProgressDialog.show(MainActivity.this, "", "Uploading file...", true);
                new Thread(new Runnable() {
                    public void run() {
                        uploadFile(mFileTemp.getPath(), languageSpinner.getSelectedItem().toString());
                    }
                }).start();*/

            }
        });


       uploadButton.setOnClickListener(new View.OnClickListener() {
           @Override
           public void onClick(View view) {

               dialog = ProgressDialog.show(MainActivity.this, "", "Uploading file...", true);
               new Thread(new Runnable() {
                   public void run() {
                       uploadFile(mFileTemp.getPath(), languageSpinner.getSelectedItem().toString());
                   }



               }).start();


           }
        });


        mImageView = (ImageView) findViewById(R.id.image);

        String state = Environment.getExternalStorageState();
        if (Environment.MEDIA_MOUNTED.equals(state)) {
            mFileTemp = new File(Environment.getExternalStorageDirectory(), TEMP_PHOTO_FILE_NAME);
        }
        else {
            mFileTemp = new File(getFilesDir(), TEMP_PHOTO_FILE_NAME);
        }

    }

    private void takePicture() {

        Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);

        try {
            Uri mImageCaptureUri = null;
            String state = Environment.getExternalStorageState();
            if (Environment.MEDIA_MOUNTED.equals(state)) {
                mImageCaptureUri = Uri.fromFile(mFileTemp);
            }
            else {
	        	/*
	        	 * The solution is taken from here: http://stackoverflow.com/questions/10042695/how-to-get-camera-result-as-a-uri-in-data-folder
	        	 */
                mImageCaptureUri = InternalStorageContentProvider.CONTENT_URI;
            }
            intent.putExtra(MediaStore.EXTRA_OUTPUT, mImageCaptureUri);
            intent.putExtra("return-data", true);
            startActivityForResult(intent, REQUEST_CODE_TAKE_PICTURE);
        } catch (ActivityNotFoundException e) {

            Log.d(TAG, "cannot take picture", e);
        }
    }

    private void openGallery() {

        Intent photoPickerIntent = new Intent(Intent.ACTION_PICK);
        photoPickerIntent.setType("image/*");
        startActivityForResult(photoPickerIntent, REQUEST_CODE_GALLERY);
    }

    private void startCropImage() {

        Intent intent = new Intent(this, CropImage.class);
        intent.putExtra(CropImage.IMAGE_PATH, mFileTemp.getPath());
        intent.putExtra(CropImage.SCALE, true);

        intent.putExtra(CropImage.ASPECT_X, 2);
        intent.putExtra(CropImage.ASPECT_Y, 3);

        startActivityForResult(intent, REQUEST_CODE_CROP_IMAGE);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {

        if (resultCode != RESULT_OK) {

            return;
        }

        Bitmap bitmap;

        switch (requestCode) {

            case REQUEST_CODE_GALLERY:

                try {

                    InputStream inputStream = getContentResolver().openInputStream(data.getData());
                    FileOutputStream fileOutputStream = new FileOutputStream(mFileTemp);
                    copyStream(inputStream, fileOutputStream);
                    fileOutputStream.close();
                    inputStream.close();

                    /// binarize the image before passing to the crop functionality

                       binarizeAndSaveIntheSameLocation();

                   startCropImage();

                } catch (Exception e) {

                    Log.e(TAG, "Error while creating temp file", e);
                }

                break;
            case REQUEST_CODE_TAKE_PICTURE:

                //binrize image before passing to crop functionality
               binarizeAndSaveIntheSameLocation();

               startCropImage();
                break;
            case REQUEST_CODE_CROP_IMAGE:

                String path = data.getStringExtra(CropImage.IMAGE_PATH);
                if (path == null) {

                    return;
                }

                bitmap = BitmapFactory.decodeFile(mFileTemp.getPath());
                Log.w("myApp","mFileTemp is ="+mFileTemp.getPath());

                mImageView.setImageBitmap(bitmap);
                break;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }


    public static void copyStream(InputStream input, OutputStream output)
            throws IOException {

        byte[] buffer = new byte[1024];
        int bytesRead;
        while ((bytesRead = input.read(buffer)) != -1) {
            output.write(buffer, 0, bytesRead);
        }
    }



    public int uploadFile(String sourceFileUri, String lang) {
        SyncHttpClient client = new SyncHttpClient();
        PersistentCookieStore myCookieStore = new PersistentCookieStore(this);
        client.setCookieStore(myCookieStore);
        RequestParams params = new RequestParams();
        File sourceFile = new File(sourceFileUri);
        choosenLanguage = lang;
        params.put("lang", lang);
        try {
            params.put("image", sourceFile);
        }
        catch (Exception e) {
            dialog.dismiss();
            runOnUiThread(new Runnable() {
                public void run() {

                }
            });
            return 0;
        }

        client.post(upLoadServerUri, params, new TextHttpResponseHandler() {
            @Override
            public void onFailure(int statusCode, Header[] headers, final String responseString, Throwable throwable) {
                // error handling
                serverResponseCode = statusCode;
                dialog.dismiss();
                Log.i("uploadFile","Failure!" + responseString);
                globResponseString =responseString;
                runOnUiThread(new Runnable() {
                    public void run() {
                        Toast.makeText(MainActivity.this, globResponseString, Toast.LENGTH_SHORT).show();
                    }
                });
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        String str = globResponseString;
                        /*AlertDialog.Builder alert = new AlertDialog.Builder(context);
                        alert.setTitle("Failure");
                        alert.setMessage(str);
                        alert.show();*/
                        AlertDialog dialog = new AlertDialog.Builder(context).setMessage("").show();
                        //TextView textView = (TextView) dialog.findViewById(android.R.id.message);
                        //Typeface face=Typeface.createFromAsset(getAssets(),"fonts/Devlys_010.ttf");
                        //textView.setTypeface(face);
                        str = unescape_perl_string(str);
                        dialog.setMessage(str);
                    }
                });
            }

            @Override
            public void onSuccess(int statusCode, Header[] headers, String responseString) {
                // success
                serverResponseCode = statusCode;
                dialog.dismiss();
                Log.i("uploadFile", "Success!" + responseString);
                globResponseString = responseString;
                /*runOnUiThread(new Runnable() {
                    public void run() {
                        Toast.makeText(MainActivity.this, globResponseString, Toast.LENGTH_SHORT).show();
                    }
                });*/

                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        String str = globResponseString;
                        //Typeface face=Typeface.createFromAsset(getAssets(),"fonts/Ananda.ttf");
                        AlertDialog dialog = new AlertDialog.Builder(context).setMessage("").show();
                        //TextView textView = (TextView) dialog.findViewById(android.R.id.message);
                        //textView.setTypeface(face);
                        str = unescape_perl_string(str);
                        dialog.setMessage(str);
                        t1= new TextToSpeech(context, new TextToSpeech.OnInitListener() {
                            @Override
                            public void onInit(int status) {
                                if(status != TextToSpeech.ERROR) {
                                    String str = unescape_perl_string(globResponseString);
                                    if(choosenLanguage=="English") {
                                        int result = t1.setLanguage(Locale.US);
                                        if (result == TextToSpeech.LANG_MISSING_DATA
                                                || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                                            Log.e("TTS", "This Language is not supported");
                                        }
                                        else {
                                            t1.speak(str, TextToSpeech.QUEUE_FLUSH, null);
                                        }
                                    }
                                    else if (choosenLanguage=="Hindi") {
                                        int result = t1.setLanguage(new Locale("hin-IND"));
                                        if (result == TextToSpeech.LANG_MISSING_DATA
                                                || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                                            Log.e("TTS", "This Language is not supported");
                                        }
                                        else {
                                            t1.speak(str, TextToSpeech.QUEUE_FLUSH, null);
                                        }
                                    }
                                }
                            }
                        });

                        //dialog.setTitle("Success");
                        //dialog.show();

                        /*Typeface face=Typeface.createFromAsset(getAssets(),"fonts/Devlys_010.ttf");
                        AlertDialog dialog = new AlertDialog.Builder(context).setMessage("").show();
                        TextView textView = (TextView) dialog.findViewById(android.R.id.message);
                        textView.setTypeface(face);
                        dialog.setMessage(str);*/
                    }
                });
            }
        });

        return serverResponseCode;
    }

public void binarizeAndSaveIntheSameLocation()
{
    BitmapFactory.Options options = new BitmapFactory.Options();
    options.inPreferredConfig = Bitmap.Config.ARGB_8888;
    Bitmap bitmapColor = BitmapFactory.decodeFile(mFileTemp.getPath(), options);
    final int height = bitmapColor.getHeight();
    final int width = bitmapColor.getWidth();

    final Bitmap bmpGrayscale = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
    final Canvas c = new Canvas(bmpGrayscale);
    final Paint paint = new Paint();
    final ColorMatrix cm = new ColorMatrix();
    cm.setSaturation(0);
    //filter converts the image to greyscale.
    //final ColorMatrixColorFilter f = new ColorMatrixColorFilter(cm);
    //paint.setColorFilter(f);
    c.drawBitmap(bitmapColor, 0, 0, paint); //now the bmpGrayscalewillbe in gray  mode

    bitmapColor.recycle();

    /*

    when the bitmap is written to mfiltemp location using fileoutputstream; the image was loaded correctly
    on the crop view and was shown crrectly in the main layout

    but on upload the iamge was not appearing in the uploads folder in the server.
    at the same time when the image was cropped ( after crop a save happens there) there was no issue

    so now saving is done after geting contentresolver etc using outputstream ( as it is done in the save fuction in crop library)
    even this didnt work

    update - anytime even if gray is passed, if crop is done image was successfully uploaded
     */

    //now save the graybitmap to the mFileTemp location

    //http://stackoverflow.com/questions/7769806/convert-bitmap-to-file

    //saving as suggested in the above stackoverflow thread fixed the issue
    ByteArrayOutputStream bos = new ByteArrayOutputStream();
   // Bitmap onlyAlpha=bmpGrayscale.extractAlpha();

    bmpGrayscale.compress(Bitmap.CompressFormat.JPEG,100,bos);

    Boolean hasAlpha=bmpGrayscale.hasAlpha();
    Log.w("myApp", "has alpha?" + Boolean.toString(hasAlpha));
    bmpGrayscale.recycle();

    byte[] bitmapdata = bos.toByteArray();
    try {
        FileOutputStream fos = new FileOutputStream(mFileTemp);
        fos.write(bitmapdata);
        fos.flush();
        fos.close();
    } catch (FileNotFoundException e) {
        e.printStackTrace();
    } catch (IOException e) {
        e.printStackTrace();
    }



}

}
