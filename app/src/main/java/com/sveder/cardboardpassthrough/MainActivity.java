/*
 * Copyright 2014 Google Inc. All Rights Reserved.

 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Original camera display code by Sveder's CardboardPassthrough project found
 * https://github.com/Sveder/CardboardPassthrough
 */

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
import android.os.Bundle;
import android.os.Environment;
import android.os.Vibrator;
import android.provider.MediaStore;
import android.util.Base64;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;

import com.google.vrtoolkit.cardboard.CardboardActivity;
import com.google.vrtoolkit.cardboard.CardboardView;
import com.google.vrtoolkit.cardboard.EyeTransform;
import com.google.vrtoolkit.cardboard.HeadTransform;
import com.google.vrtoolkit.cardboard.Viewport;
import com.memetix.mst.language.Language;
import com.memetix.mst.translate.Translate;

import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

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
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Scanner;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;


/**
 * A Cardboard sample application.
 */

public class MainActivity extends CardboardActivity implements CardboardView.StereoRenderer, OnFrameAvailableListener,
        CameraBridgeViewBase.CvCameraViewListener2, View.OnTouchListener {

    /* Required for basic app */
    public static final int MEDIA_TYPE_IMAGE = 1;
    public static final int MEDIA_TYPE_VIDEO = 2;
    private static final String TAG = "MainActivity";
    private static final int GL_TEXTURE_EXTERNAL_OES = 0x8D65;
    File temp_picture;
    private Camera camera;


    /* Required for gestures with opencv */
   // private static final String  TAG = "OCVSample::Activity";

    private boolean              mIsColorSelected1 = false;
    private boolean              mIsColorSelected2 = false;
    private Mat mRgba;
    private Scalar               mBlobColorRgba1;
    private Scalar               mBlobColorRgba2;
    private Scalar               mBlobColorHsv1;
    private ColorBlobDetector    mDetector1;
    private Scalar mBlobColorHsv2;
    private ColorBlobDetector    mDetector2;
    private Mat                  mSpectrum;
    private Size SPECTRUM_SIZE;
    private Scalar               CONTOUR_COLOR;
    private boolean               trigger=false;
    private int              threshold = 10;
    private CameraBridgeViewBase mOpenCvCameraView;

    private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
        @Override
        public void onManagerConnected(int status) {
            switch (status) {
                case LoaderCallbackInterface.SUCCESS:
                {
                    Log.e(TAG, "OpenCV loaded successfully");
                    mOpenCvCameraView.enableView();
                    mOpenCvCameraView.setOnTouchListener(MainActivity.this);
                } break;
                default:
                {
                    super.onManagerConnected(status);
                } break;
            }
        }
    };
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
        OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_3, this, mLoaderCallback);
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
     * Checks if we've had an error inside of OpenGL ES, and if so what that error is.
     */
    //private static void checkGLError(String func) {
    //    int error;
    //    while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
    //        Log.e(TAG, func + ": glError " + error);
    //        throw new RuntimeException(func + ": glError " + error);
    //    }
    //}

    /**
     * Sets the view to our CardboardView and initializes the transformation matrices we will use
     * to render our scene.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        setContentView(R.layout.color_blob_detection_activity_surface_view);


        mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.color_blob_detection_activity_surface_view);
        mOpenCvCameraView.setCvCameraViewListener(this);

    }

    public void createARView() {
        setContentView(R.layout.common_ui);
        cardboardView = (CardboardView) findViewById(R.id.cardboard_view);
        cardboardView.setRenderer(this);
        setCardboardView(cardboardView);

        mCamera = new float[16];
        mView = new float[16];
        mVibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        mOverlayView = (CardboardOverlayView) findViewById(R.id.overlay);
        mOverlayView.show3DToast("Pull magnet to identify object in Spanish! ");
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

    @Override
    public void onCameraViewStarted(int width, int height) {
        mRgba = new Mat(height, width, CvType.CV_8UC4);
        mDetector1 = new ColorBlobDetector();
        mDetector2 = new ColorBlobDetector();
        mSpectrum = new Mat();
        mBlobColorRgba1 = new Scalar(255);
        mBlobColorRgba2 = new Scalar(255);
        mBlobColorHsv1 = new Scalar(255);
        mBlobColorHsv2 = new Scalar(255);
        SPECTRUM_SIZE = new Size(200, 64);
        CONTOUR_COLOR = new Scalar(255,0,0,255);
    }

    @Override
    public void onCameraViewStopped() {
        mRgba.release();
    }

    @Override
    public Mat onCameraFrame(CameraBridgeViewBase.CvCameraViewFrame inputFrame) {
        mRgba = inputFrame.rgba();

        mDetector1.process(mRgba);
        List<MatOfPoint> contours1 = mDetector1.getContours();

        List<Integer> points1 = new ArrayList<Integer>();
        for (int i = 0; i < contours1.size(); i++){
            int x1 = 0;
            int y1=0;
            int count = 0;
            List<Point> points = contours1.get(i).toList();
            for (Point p: points) {
                x1+=p.x;
                y1+=p.y;
                count ++;
            }
            List<Integer> list = new ArrayList<Integer>();
            list.add(x1/count);
            list.add(y1/count);
            list.add(count);
            if (points1.isEmpty()||list.get(2)>points1.get(2)){
                points1 = list;
            }
        }

        if(points1.size()!= 0) {
            Log.e("Contour 1", points1.toString());
        }

        mDetector2.process(mRgba);
        List<MatOfPoint> contours2 = mDetector2.getContours();

        List<Integer> points2 = new ArrayList<Integer>();
        for (int i = 0; i < contours2.size(); i++){
            int x1 = 0;
            int y1=0;
            int count = 0;
            List<Point> points = contours2.get(i).toList();
            for (Point p: points) {
                x1+=p.x;
                y1+=p.y;
                count ++;
            }
            List<Integer> list = new ArrayList<Integer>();
            list.add(x1/count);
            list.add(y1/count);
            list.add(count);

            if (points2.isEmpty()||list.get(2)>points2.get(2)){
                points2 = list;
            }

        }

        if(points2.size()!= 0) {
            Log.e("Contour 2", points2.toString());
        }
        boolean trigger = false;

        if (!points1.isEmpty()&& !points2.isEmpty() && points2.get(2)> threshold && points1.get(2)>threshold){
            trigger = true;
            mOverlayView.show3DToast("Found Gesture!");

            Log.e("FOUND 2 POINTS!!, ","count: 2");
        }else{
            trigger = false;
            Log.e("UNABLE TO DETECT POINTS", "NOTHING");
        }

        Log.e(TAG, "Contours 1 count: " + contours1.size());
        Log.e(TAG, "Contours 2 count: " + contours2.size());

        Imgproc.drawContours(mRgba, contours1, -1, CONTOUR_COLOR);
        Imgproc.drawContours(mRgba, contours2, -1, CONTOUR_COLOR);

        //Mat colorLabel = mRgba.submat(4, 68, 4, 68);
        //colorLabel.setTo(mBlobColorRgba);

        // Mat spectrumLabel = mRgba.submat(4, 4 + mSpectrum.rows(), 70, 70 + mSpectrum.cols());
        // mSpectrum.copyTo(spectrumLabel);

        return mRgba;    }

    private Scalar converScalarHsv2Rgba(Scalar hsvColor) {
        Mat pointMatRgba = new Mat();
        Mat pointMatHsv = new Mat(1, 1, CvType.CV_8UC3, hsvColor);
        Imgproc.cvtColor(pointMatHsv, pointMatRgba, Imgproc.COLOR_HSV2RGB_FULL, 4);

        return new Scalar(pointMatRgba.get(0, 0));
    }

    @Override
    public boolean onTouch(View view, MotionEvent event) {
        if (mIsColorSelected1 == false) {
            int cols = mRgba.cols();
            int rows = mRgba.rows();

            int xOffset = (mOpenCvCameraView.getWidth() - cols) / 2;
            int yOffset = (mOpenCvCameraView.getHeight() - rows) / 2;

            int x = (int) event.getX() - xOffset;
            int y = (int) event.getY() - yOffset;

            Log.e(TAG, "Touch image coordinates: (" + x + ", " + y + ")");

            if ((x < 0) || (y < 0) || (x > cols) || (y > rows)) return false;

            Rect touchedRect = new Rect();

            touchedRect.x = (x > 4) ? x - 4 : 0;
            touchedRect.y = (y > 4) ? y - 4 : 0;

            touchedRect.width = (x + 4 < cols) ? x + 4 - touchedRect.x : cols - touchedRect.x;
            touchedRect.height = (y + 4 < rows) ? y + 4 - touchedRect.y : rows - touchedRect.y;

            Mat touchedRegionRgba = mRgba.submat(touchedRect);

            Mat touchedRegionHsv = new Mat();
            Imgproc.cvtColor(touchedRegionRgba, touchedRegionHsv, Imgproc.COLOR_RGB2HSV_FULL);

            // Calculate average color of touched region
            mBlobColorHsv1 = Core.sumElems(touchedRegionHsv);
            int pointCount = touchedRect.width * touchedRect.height;
            for (int i = 0; i < mBlobColorHsv1.val.length; i++)
                mBlobColorHsv1.val[i] /= pointCount;

            mBlobColorRgba1 = converScalarHsv2Rgba(mBlobColorHsv1);

            Log.e(TAG, "Touched rgba color: (" + mBlobColorRgba1.val[0] + ", " + mBlobColorRgba1.val[1] +
                    ", " + mBlobColorRgba1.val[2] + ", " + mBlobColorRgba1.val[3] + ")");

            mDetector1.setHsvColor(mBlobColorHsv1);

            Imgproc.resize(mDetector1.getSpectrum(), mSpectrum, SPECTRUM_SIZE);

            mIsColorSelected1 = true;

            touchedRegionRgba.release();
            touchedRegionHsv.release();
        } else if (mIsColorSelected2 == false){
            int cols = mRgba.cols();
            int rows = mRgba.rows();

            int xOffset = (mOpenCvCameraView.getWidth() - cols) / 2;
            int yOffset = (mOpenCvCameraView.getHeight() - rows) / 2;

            int x = (int) event.getX() - xOffset;
            int y = (int) event.getY() - yOffset;

            Log.e(TAG, "Touch image coordinates: (" + x + ", " + y + ")");

            if ((x < 0) || (y < 0) || (x > cols) || (y > rows)) return false;

            Rect touchedRect = new Rect();

            touchedRect.x = (x > 4) ? x - 4 : 0;
            touchedRect.y = (y > 4) ? y - 4 : 0;

            touchedRect.width = (x + 4 < cols) ? x + 4 - touchedRect.x : cols - touchedRect.x;
            touchedRect.height = (y + 4 < rows) ? y + 4 - touchedRect.y : rows - touchedRect.y;

            Mat touchedRegionRgba = mRgba.submat(touchedRect);

            Mat touchedRegionHsv = new Mat();
            Imgproc.cvtColor(touchedRegionRgba, touchedRegionHsv, Imgproc.COLOR_RGB2HSV_FULL);

            // Calculate average color of touched region
            mBlobColorHsv2 = Core.sumElems(touchedRegionHsv);
            int pointCount = touchedRect.width * touchedRect.height;
            for (int i = 0; i < mBlobColorHsv2.val.length; i++)
                mBlobColorHsv2.val[i] /= pointCount;

            mBlobColorRgba2 = converScalarHsv2Rgba(mBlobColorHsv2);

            Log.e(TAG, "Touched rgba color: (" + mBlobColorRgba2.val[0] + ", " + mBlobColorRgba2.val[1] +
                    ", " + mBlobColorRgba2.val[2] + ", " + mBlobColorRgba2.val[3] + ")");

            mDetector2.setHsvColor(mBlobColorHsv2);

            Imgproc.resize(mDetector2.getSpectrum(), mSpectrum, SPECTRUM_SIZE);

            mIsColorSelected2 = true;

            touchedRegionRgba.release();
            touchedRegionHsv.release();
        }
        createARView();
        return false; // don't need subsequent touch events
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
            byte[] b = baos.toByteArray();
            String ba = Base64.encodeToString(b, Base64.DEFAULT);
            String img = "data:image/jpg;base64," + ba;

            // Create a new HttpClient and Post Header
            HttpClient httpclient = new DefaultHttpClient();
            HttpPost httppost = new HttpPost("https://www.metamind.io/vision/classify");
            httppost.setHeader("Authorization", "Basic dEGa15gLOpEfua3MckyEXCz9MgzfzT48QEmte7wDCjeaPPtJBZ");

            try {
                // Add your data
                JSONObject object = new JSONObject();
                try {
                    object.put("classifier_id", "imagenet-1k-net");
                    object.put("image_url", img);//"https://www.petfinder.com/wp-content/uploads/2012/11/dog-how-to-select-your-new-best-friend-thinkstock99062463.jpg");
                } catch (Exception ex) {
                    Log.e("MetaMind", "JSON Object Exception");
                }
                String message = object.toString();
                httppost.setEntity(new StringEntity(message, "UTF8"));

                // Execute HTTP Post Request
                HttpResponse response = httpclient.execute(httppost);
                // Get data out of response
                Scanner sc = new Scanner(response.getEntity().getContent(), "UTF-8");

                String jsonString = "";
                while (sc.hasNextLine()) {
                    jsonString += sc.nextLine();
                }
                JSONObject jsonObject;
                Log.e("Metamind Results", jsonString);
                try {
                    jsonObject = new JSONObject(jsonString);
                    JSONArray temp = jsonObject.getJSONArray("predictions");
                    String answer = temp.getJSONObject(0).getString("class_name");

                    //Replace client_id and client_secret with your own.
                    Translate.setClientId("AugmentedLanguageImmersion");
                    Translate.setClientSecret("sKCdl6p7g8Cxv3X+QsEg58xKkxU8ZD3lGUdHiFDEM5c=");

                    // Translate an english string to another language, currently Spanish
                    String translatedAnswer = Translate.execute(answer, Language.SPANISH);
                    if (translatedAnswer.equals("")) {
                        translatedAnswer = answer;
                    }
                    Log.e("Metamind CLASS NAME", answer);
                    Log.e("Translated Metamind", translatedAnswer);

                    return translatedAnswer;
                } catch (JSONException e) {
                    e.printStackTrace();
                } catch (Exception e) {
                    e.printStackTrace();
                }

            } catch (ClientProtocolException e) {
                // TODO Auto-generated catch block
            } catch (IOException e) {
                // TODO Auto-generated catch block
            }
            return "Object not found!";
        }
    }
}
