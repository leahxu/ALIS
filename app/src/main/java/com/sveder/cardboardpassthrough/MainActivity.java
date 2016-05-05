
package com.sveder.cardboardpassthrough;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.SurfaceTexture;
import android.graphics.SurfaceTexture.OnFrameAvailableListener;
import android.hardware.Camera;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;


import com.google.vrtoolkit.cardboard.CardboardActivity;
import com.google.vrtoolkit.cardboard.CardboardView;
import com.google.vrtoolkit.cardboard.EyeTransform;
import com.google.vrtoolkit.cardboard.HeadTransform;
import com.google.vrtoolkit.cardboard.Viewport;
import com.memetix.mst.language.Language;
import com.memetix.mst.translate.Translate;


import com.clarifai.api.ClarifaiClient;
import com.clarifai.api.RecognitionRequest;
import com.clarifai.api.Tag;
import com.clarifai.api.exception.ClarifaiException;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;


/**
 * A Cardboard sample application.
 */

public class MainActivity extends CardboardActivity implements CardboardView.StereoRenderer, OnFrameAvailableListener {

    public static final int MEDIA_TYPE_IMAGE = 1;
    public static final int MEDIA_TYPE_VIDEO = 2;
    private static final String TAG = "MainActivity";
    private static final int GL_TEXTURE_EXTERNAL_OES = 0x8D65;
    File temp_picture;
    private Camera camera;
    TextToSpeech t1;

    List<String> invalid_set = Arrays.asList("no person", "room", "indoors", "one", "abstract", "furniture", "blur", "portrait", "food", "business");
    private  ClarifaiClient client;// = new ClarifaiClient(getString(R.string.client_id),
            //getString(R.string.client_secret));

    String accessToken;

    private Camera.PictureCallback mPicture =
            new Camera.PictureCallback() {

                @Override
                public void onPictureTaken(byte[] data, Camera camera) {
                    temp_picture = getOutputMediaFile(MEDIA_TYPE_IMAGE);
                    if (temp_picture == null) {
                        Log.d(TAG, "Error creating media file, check storage permissions");
                        return;
                    }
                    try {
                        FileOutputStream fos = new FileOutputStream(temp_picture);
                        fos.write(data);
                        fos.close();

                    } catch (FileNotFoundException e) {
                        Log.d(TAG, "File not found: " + e.getMessage());
                    } catch (IOException e) {
                        Log.d(TAG, "Error accessing file: " + e.getMessage());
                    }
                    AsyncCallWS task = new AsyncCallWS();
                    task.execute();
                    camera.startPreview();
                }
            };

    private static File getOutputMediaFile(int type) {
        // To be safe, you should check that the SDCard is mounted
        // using Environment.getExternalStorageState() before doing this.
        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "MyCameraApp");

        // This location works best if you want the created images to be shared
        // between applications and persist after your app has been uninstalled.

        // Create the storage directory if it does not exist
        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                Log.d("MyCameraApp", "failed to create directory");
                return null;
            }
        }

        // Create a media file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File mediaFile;
        if (type == MEDIA_TYPE_IMAGE) {
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "IMG_" + timeStamp + ".jpg");
        } else if (type == MEDIA_TYPE_VIDEO) {
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "VID_" + timeStamp + ".mp4");
        } else {
            Log.e("Derp1", "Media File Created");
            return null;
        }

        return mediaFile;
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (camera == null) {
            camera = Camera.open();
            try {
                camera.setPreviewTexture(surface);
                camera.startPreview();
            } catch (IOException ioe) {
                Log.w("MainActivity", "CAM LAUNCH FAILED");
            }

        }

    }

    @Override
    protected void onPause() {
        super.onPause();
        releaseCamera();              // release the camera immediately on pause event
    }


    private void releaseCamera() {
        if (camera != null) {
            camera.release();        // release the camera for other applications
            camera = null;
        }
    }

    private final String vertexShaderCode =
            "attribute vec4 position;" +
                    "attribute vec2 inputTextureCoordinate;" +
                    "varying vec2 textureCoordinate;" +
                    "void main()" +
                    "{" +
                    "gl_Position = position;" +
                    "textureCoordinate = inputTextureCoordinate;" +
                    "}";

    private final String fragmentShaderCode =
            "#extension GL_OES_EGL_image_external : require\n" +
                    "precision mediump float;" +
                    "varying vec2 textureCoordinate;                            \n" +
                    "uniform samplerExternalOES s_texture;               \n" +
                    "void main(void) {" +
                    "  gl_FragColor = texture2D( s_texture, textureCoordinate );\n" +
                    //"  gl_FragColor = vec4(1.0, 0.0, 0.0, 1.0);\n" +
                    "}";

    private FloatBuffer vertexBuffer, textureVerticesBuffer;
    private ShortBuffer drawListBuffer;
    private int mProgram;
    private int mColorHandle;
    private int mPositionHandle;
    private int mTextureCoordHandle;


    // number of coordinates per vertex in this array
    static final int COORDS_PER_VERTEX = 2;
    static float squareVertices[] = { // in counterclockwise order:
            -1.0f, -1.0f,   // 0.left - mid
            1.0f, -1.0f,   // 1. right - mid
            -1.0f, 1.0f,   // 2. left - top
            1.0f, 1.0f,   // 3. right - top

            //    	 -1.0f, -1.0f, //4. left - bottom
            //    	 1.0f , -1.0f, //5. right - bottom


            //       -1.0f, -1.0f,  // 0. left-bottom
            //        0.0f, -1.0f,   // 1. mid-bottom
            //       -1.0f,  1.0f,   // 2. left-top
            //        0.0f,  1.0f,   // 3. mid-top

            //        1.0f, -1.0f,  // 4. right-bottom
            //        1.0f, 1.0f,   // 5. right-top

    };


    //, 1, 4, 3, 4, 5, 3
    private short drawOrder[] = {0, 2, 1, 1, 2, 3}; // order to draw vertices

    static float textureVertices[] = {
            0.0f, 1.0f,  // A. left-bottom
            1.0f, 1.0f,  // B. right-bottom
            0.0f, 0.0f,  // C. left-top
            1.0f, 0.0f   // D. right-top

            //        1.0f,  1.0f,
            //        1.0f,  0.0f,
            //        0.0f,  1.0f,
            //        0.0f,  0.0f
    };

    private final int vertexStride = COORDS_PER_VERTEX * 4; // 4 bytes per vertex

    private int texture;

    private CardboardOverlayView mOverlayView;
    private CardboardView cardboardView;
    private SurfaceTexture surface;
    private float[] mView;
    private float[] mCamera;
    private Vibrator mVibrator;

    public void startCamera(int texture) {
        surface = new SurfaceTexture(texture);
        surface.setOnFrameAvailableListener(this);


        if (camera == null) {
            camera = Camera.open();
        }

        try {
            camera.setPreviewTexture(surface);
            camera.startPreview();
        } catch (IOException ioe) {
            Log.w("MainActivity", "CAM LAUNCH FAILED");
        }
    }

    static private int createTexture() {
        int[] texture = new int[1];

        GLES20.glGenTextures(1, texture, 0);
        GLES20.glBindTexture(GL_TEXTURE_EXTERNAL_OES, texture[0]);
        GLES20.glTexParameterf(GL_TEXTURE_EXTERNAL_OES,
                GL10.GL_TEXTURE_MIN_FILTER, GL10.GL_LINEAR);
        GLES20.glTexParameterf(GL_TEXTURE_EXTERNAL_OES,
                GL10.GL_TEXTURE_MAG_FILTER, GL10.GL_LINEAR);
        GLES20.glTexParameteri(GL_TEXTURE_EXTERNAL_OES,
                GL10.GL_TEXTURE_WRAP_S, GL10.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GL_TEXTURE_EXTERNAL_OES,
                GL10.GL_TEXTURE_WRAP_T, GL10.GL_CLAMP_TO_EDGE);

        return texture[0];
    }

    /**
     * Converts a raw text file, saved as a resource, into an OpenGL ES shader
     *
     * @param type The type of shader we will be creating.
     */
    private int loadGLShader(int type, String code) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, code);
        GLES20.glCompileShader(shader);

        // Get the compilation status.
        final int[] compileStatus = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0);

        // If the compilation failed, delete the shader.
        if (compileStatus[0] == 0) {
            Log.e(TAG, "Error compiling shader: " + GLES20.glGetShaderInfoLog(shader));
            GLES20.glDeleteShader(shader);
            shader = 0;
        }

        if (shader == 0) {
            throw new RuntimeException("Error creating shader.");
        }

        return shader;
    }


    /**
     * Sets the view to our CardboardView and initializes the transformation matrices we will use
     * to render our scene.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.common_ui);
        cardboardView = (CardboardView) findViewById(R.id.cardboard_view);
        cardboardView.setRenderer(this);
        setCardboardView(cardboardView);

        mCamera = new float[16];
        mView = new float[16];
        mVibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        mOverlayView = (CardboardOverlayView) findViewById(R.id.overlay);

        mOverlayView.setOnTouchListener(new View.OnTouchListener()
        {

            @Override
            public boolean onTouch(View v, MotionEvent event)
            {
                onCardboardTrigger();
                return false;
            }
        });

        t1=new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if(status != TextToSpeech.ERROR) {
                    t1.setLanguage(mOverlayView.getLanguage());
                }
            }
        });
        client = new ClarifaiClient(getString(R.string.client_id),
                getString(R.string.client_secret));

    }

    @Override
    public void onRendererShutdown() {
        Log.i(TAG, "onRendererShutdown");
    }

    @Override
    public void onSurfaceChanged(int width, int height) {
        Log.i(TAG, "onSurfaceChanged");
    }

    /**
     * Creates the buffers we use to store information about the 3D world. OpenGL doesn't use Java
     * arrays, but rather needs data in a format it can understand. Hence we use ByteBuffers.
     *
     * @param config The EGL configuration used when creating the surface.
     */
    @Override
    public void onSurfaceCreated(EGLConfig config) {
        Log.i(TAG, "onSurfaceCreated");
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 0.5f); // Dark background so text shows up well

        ByteBuffer bb = ByteBuffer.allocateDirect(squareVertices.length * 4);
        bb.order(ByteOrder.nativeOrder());
        vertexBuffer = bb.asFloatBuffer();
        vertexBuffer.put(squareVertices);
        vertexBuffer.position(0);

        ByteBuffer dlb = ByteBuffer.allocateDirect(drawOrder.length * 2);
        dlb.order(ByteOrder.nativeOrder());
        drawListBuffer = dlb.asShortBuffer();
        drawListBuffer.put(drawOrder);
        drawListBuffer.position(0);

        ByteBuffer bb2 = ByteBuffer.allocateDirect(textureVertices.length * 4);
        bb2.order(ByteOrder.nativeOrder());
        textureVerticesBuffer = bb2.asFloatBuffer();
        textureVerticesBuffer.put(textureVertices);
        textureVerticesBuffer.position(0);

        int vertexShader = loadGLShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode);
        int fragmentShader = loadGLShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode);

        mProgram = GLES20.glCreateProgram();             // create empty OpenGL ES Program
        GLES20.glAttachShader(mProgram, vertexShader);   // add the vertex shader to program
        GLES20.glAttachShader(mProgram, fragmentShader); // add the fragment shader to program
        GLES20.glLinkProgram(mProgram);

        texture = createTexture();
        startCamera(texture);
    }


    /**
     * Prepares OpenGL ES before we draw a frame.
     *
     * @param headTransform The head transformation in the new frame.
     */
    @Override
    public void onNewFrame(HeadTransform headTransform) {
        float[] mtx = new float[16];
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        surface.updateTexImage();
        surface.getTransformMatrix(mtx);
    }

    @Override
    public void onFrameAvailable(SurfaceTexture arg0) {
        this.cardboardView.requestRender();


    }

    /**
     * Draws a frame for an eye. The transformation for that eye (from the camera) is passed in as
     * a parameter.
     *
     * @param transform The transformations to apply to render this eye.
     */
    @Override
    public void onDrawEye(EyeTransform transform) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
        GLES20.glUseProgram(mProgram);
        GLES20.glActiveTexture(GL_TEXTURE_EXTERNAL_OES);
        GLES20.glBindTexture(GL_TEXTURE_EXTERNAL_OES, texture);

        mPositionHandle = GLES20.glGetAttribLocation(mProgram, "position");
        GLES20.glEnableVertexAttribArray(mPositionHandle);
        GLES20.glVertexAttribPointer(mPositionHandle, COORDS_PER_VERTEX, GLES20.GL_FLOAT,
                false, vertexStride, vertexBuffer);

        mTextureCoordHandle = GLES20.glGetAttribLocation(mProgram, "inputTextureCoordinate");
        GLES20.glEnableVertexAttribArray(mTextureCoordHandle);
        GLES20.glVertexAttribPointer(mTextureCoordHandle, COORDS_PER_VERTEX, GLES20.GL_FLOAT,
                false, vertexStride, textureVerticesBuffer);

        mColorHandle = GLES20.glGetAttribLocation(mProgram, "s_texture");

        GLES20.glDrawElements(GLES20.GL_TRIANGLES, drawOrder.length,
                GLES20.GL_UNSIGNED_SHORT, drawListBuffer);

        // Disable vertex array
        GLES20.glDisableVertexAttribArray(mPositionHandle);
        GLES20.glDisableVertexAttribArray(mTextureCoordHandle);

        Matrix.multiplyMM(mView, 0, transform.getEyeView(), 0, mCamera, 0);
    }

    @Override
    public void onFinishFrame(Viewport viewport) {
    }

    /**
     * Take picture and use MetaMind API to perform image classification, and then display the
     * classification of the image to the user.
     */
    @Override
    public void onCardboardTrigger() {
        Log.e(TAG, "onCardboardTrigger");

        mOverlayView.show3DToast("Identifying...");
        try {
            camera.takePicture(null, null, mPicture);
        } catch (Exception e) {
            Log.e("Take Picture Failed.:", e.getMessage());
        }

        // Always give user feedback
        mVibrator.vibrate(50);
    }


    private class AsyncCallWS extends AsyncTask<Void, Void, String> {
        @Override
        protected String doInBackground(Void... values) {
            Log.e(TAG, "doInBackground");
            return postData();
        }

        @Override
        protected void onPostExecute(String result) {
            mOverlayView.show3DToast(result);
            String[] split = result.split("\n");
            result = split[0];

            //http://stackoverflow.com/questions/27968146/texttospeech-with-api-21/28000527#28000527
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                String utteranceId = this.hashCode() + "";
                t1.speak(result, TextToSpeech.QUEUE_FLUSH, null, utteranceId);
            } else {
                HashMap<String, String> map = new HashMap<>();
                map.put(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "MessageId");
                t1.speak(result, TextToSpeech.QUEUE_FLUSH, map);
            }

            // Delete pictures now that web request has been executed
            temp_picture.delete();
        }

        @Override
        protected void onPreExecute() {
            Log.e(TAG, "onPreExecute");
        }

        public String postData() {


            BitmapFactory.Options opt = new BitmapFactory.Options();
            opt.inTempStorage = new byte[16 * 1024];
            opt.inSampleSize = 4;
            opt.outWidth = 640;
            opt.outHeight = 480;

            // Compress the Image
            Bitmap bm = BitmapFactory.decodeFile(temp_picture.getPath(), opt);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            bm.compress(Bitmap.CompressFormat.JPEG, 90, baos); //bm is the bitmap object

            byte[] jpeg = baos.toByteArray();
            String answer = "";
            try {
                RecognitionRequest r = new RecognitionRequest(jpeg);
                // r.setLocale(mOverlayView.getClarifaiLanguage());

                List<Tag> tags = client.recognize(r).get(0).getTags();//.get(0);
                Tag t = null;
                for (int i = 0; i < 3; i++) {
                    if (!invalid_set.contains(tags.get(i).getName()))
                        t = tags.get(i);

                }
                if (t == null) {
                    answer = "Try again";
                } else {
                    answer = t.getName();
                }
                //Replace client_id and client_secret with your own.
                Translate.setClientId("AugmentedLanguageImmersion");
                Translate.setClientSecret("sKCdl6p7g8Cxv3X+QsEg58xKkxU8ZD3lGUdHiFDEM5c=");

                // Translate an english string to another language, currently Spanish
                String trans_string = Translate.execute(answer, mOverlayView.getLanguageMemetix());
                answer = trans_string+ "\n" + "\n" + answer ;

                Log.e(TAG, "API result: " + answer);

            } catch (ClarifaiException e) {
                Log.e(TAG, "Clarifai error", e);
                return "Object not found!";
            } catch (Exception e) {
                e.printStackTrace();
            }
            t1.setLanguage(mOverlayView.getLanguage());
            return answer;

        }
    }
}
