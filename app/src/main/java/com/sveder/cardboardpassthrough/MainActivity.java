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
 */

package com.sveder.cardboardpassthrough;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.graphics.SurfaceTexture.OnFrameAvailableListener;
import android.hardware.Camera;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.Bundle;
import android.os.Environment;
import android.os.Vibrator;
import android.util.Log;

import com.google.vrtoolkit.cardboard.*;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.File;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.Date;
import java.text.SimpleDateFormat;
import java.util.List;


import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpVersion;
import org.apache.http.NameValuePair;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.ContentBody;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.params.CoreProtocolPNames;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;

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

    private Camera.PictureCallback mPicture =
            new Camera.PictureCallback() {

                @Override
                public void onPictureTaken(byte[] data, Camera camera) {

                    temp_picture = getOutputMediaFile(MEDIA_TYPE_IMAGE);
                    if (temp_picture == null){
                        Log.d(TAG, "Error creating media file, check storage permissions");
                        return;
                    }
/*
            try {
                FileOutputStream fos = new FileOutputStream(temp_picture);
                fos.write(data);
                fos.close();
            } catch (FileNotFoundException e) {
                Log.d(TAG, "File not found: " + e.getMessage());
            } catch (IOException e) {
                Log.d(TAG, "Error accessing file: " + e.getMessage());
            }
*/
                    mOverlayView.show3DToast("Recognizing object");
                }
            };
    private static File getOutputMediaFile(int type){
        // To be safe, you should check that the SDCard is mounted
        // using Environment.getExternalStorageState() before doing this.

        File mediaStorageDir = new File(Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_PICTURES), "MyCameraApp");
        // This location works best if you want the created images to be shared
        // between applications and persist after your app has been uninstalled.

        // Create the storage directory if it does not exist
        if (! mediaStorageDir.exists()){
            if (! mediaStorageDir.mkdirs()){
                Log.d("MyCameraApp", "failed to create directory");
                return null;
            }
        }

        // Create a media file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        File mediaFile;
        if (type == MEDIA_TYPE_IMAGE){
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "IMG_"+ timeStamp + ".jpg");
        } else if(type == MEDIA_TYPE_VIDEO) {
            mediaFile = new File(mediaStorageDir.getPath() + File.separator +
                    "VID_"+ timeStamp + ".mp4");
        } else {
            return null;
        }

        return mediaFile;
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

    private FloatBuffer vertexBuffer, textureVerticesBuffer, vertexBuffer2;
    private ShortBuffer drawListBuffer, buf2;
    private int mProgram;
    private int mPositionHandle, mPositionHandle2;
    private int mColorHandle;
    private int mTextureCoordHandle;


    // number of coordinates per vertex in this array
    static final int COORDS_PER_VERTEX = 2;
    static float squareVertices[] = { // in counterclockwise order:
            -1.0f, -1.0f,   // 0.left - mid
            1.0f, -1.0f,   // 1. right - mid
            -1.0f, 1.0f,   // 2. left - top
            1.0f, 1.0f,   // 3. right - top
//    	 
//    	 -1.0f, -1.0f, //4. left - bottom
//    	 1.0f , -1.0f, //5. right - bottom


//       -1.0f, -1.0f,  // 0. left-bottom
//        0.0f, -1.0f,   // 1. mid-bottom
//       -1.0f,  1.0f,   // 2. left-top
//        0.0f,  1.0f,   // 3. mid-top

            //1.0f, -1.0f,  // 4. right-bottom
            //1.0f, 1.0f,   // 5. right-top

    };


    //, 1, 4, 3, 4, 5, 3
//    private short drawOrder[] =  {0, 1, 2, 1, 3, 2 };//, 4, 5, 0, 5, 0, 1 }; // order to draw vertices
    private short drawOrder[] = {0, 2, 1, 1, 2, 3}; // order to draw vertices
    private short drawOrder2[] = {2, 0, 3, 3, 0, 1}; // order to draw vertices

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

    private ByteBuffer indexBuffer;    // Buffer for index-array

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

        camera = Camera.open();

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
     * @param type  The type of shader we will be creating.
     * @return
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
     *
     * @param func
     */
    private static void checkGLError(String func) {
        int error;
        while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
            Log.e(TAG, func + ": glError " + error);
            throw new RuntimeException(func + ": glError " + error);
        }
    }

    /**
     * Sets the view to our CardboardView and initializes the transformation matrices we will use
     * to render our scene.
     *
     * @param savedInstanceState
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.common_ui);
        cardboardView = (CardboardView) findViewById(R.id.cardboard_view);
        cardboardView.setRenderer(this);
        setCardboardView(cardboardView);

//        mModelCube = new float[16];
        mCamera = new float[16];
        mView = new float[16];
//        mModelViewProjection = new float[16];
//        mModelView = new float[16];
//        mModelFloor = new float[16];
//        mHeadView = new float[16];
        mVibrator = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
//
//
        mOverlayView = (CardboardOverlayView) findViewById(R.id.overlay);
        mOverlayView.show3DToast("Pull the magnet when you find an object.");
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


//        ByteBuffer bbVertices = ByteBuffer.allocateDirect(DATA.CUBE_COORDS.length * 4);
//        bbVertices.order(ByteOrder.nativeOrder());
//        mCubeVertices = bbVertices.asFloatBuffer();
//        mCubeVertices.put(DATA.CUBE_COORDS);
//        mCubeVertices.position(0);
//
//        ByteBuffer bbColors = ByteBuffer.allocateDirect(DATA.CUBE_COLORS.length * 4);
//        bbColors.order(ByteOrder.nativeOrder());
//        mCubeColors = bbColors.asFloatBuffer();
//        mCubeColors.put(DATA.CUBE_COLORS);
//        mCubeColors.position(0);
//
//        ByteBuffer bbFoundColors = ByteBuffer.allocateDirect(DATA.CUBE_FOUND_COLORS.length * 4);
//        bbFoundColors.order(ByteOrder.nativeOrder());
//        mCubeFoundColors = bbFoundColors.asFloatBuffer();
//        mCubeFoundColors.put(DATA.CUBE_FOUND_COLORS);
//        mCubeFoundColors.position(0);
//
//        ByteBuffer bbNormals = ByteBuffer.allocateDirect(DATA.CUBE_NORMALS.length * 4);
//        bbNormals.order(ByteOrder.nativeOrder());
//        mCubeNormals = bbNormals.asFloatBuffer();
//        mCubeNormals.put(DATA.CUBE_NORMALS);
//        mCubeNormals.position(0);
//
//        // make a floor
//        ByteBuffer bbFloorVertices = ByteBuffer.allocateDirect(DATA.FLOOR_COORDS.length * 4);
//        bbFloorVertices.order(ByteOrder.nativeOrder());
//        mFloorVertices = bbFloorVertices.asFloatBuffer();
//        mFloorVertices.put(DATA.FLOOR_COORDS);
//        mFloorVertices.position(0);
//
//        ByteBuffer bbFloorNormals = ByteBuffer.allocateDirect(DATA.FLOOR_NORMALS.length * 4);
//        bbFloorNormals.order(ByteOrder.nativeOrder());
//        mFloorNormals = bbFloorNormals.asFloatBuffer();
//        mFloorNormals.put(DATA.FLOOR_NORMALS);
//        mFloorNormals.position(0);
//
//        ByteBuffer bbFloorColors = ByteBuffer.allocateDirect(DATA.FLOOR_COLORS.length * 4);
//        bbFloorColors.order(ByteOrder.nativeOrder());
//        mFloorColors = bbFloorColors.asFloatBuffer();
//        mFloorColors.put(DATA.FLOOR_COLORS);
//        mFloorColors.position(0);
//
//        int vertexShader = loadGLShader(GLES20.GL_VERTEX_SHADER, R.raw.light_vertex);
//        int gridShader = loadGLShader(GLES20.GL_FRAGMENT_SHADER, R.raw.grid_fragment);
//
//        mGlProgram = GLES20.glCreateProgram();
//        GLES20.glAttachShader(mGlProgram, vertexShader);
//        GLES20.glAttachShader(mGlProgram, gridShader);
//        GLES20.glLinkProgram(mGlProgram);
//
//        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
//
//        // Object first appears directly in front of user
//        Matrix.setIdentityM(mModelCube, 0);
//        Matrix.translateM(mModelCube, 0, 0, 0, -mObjectDistance);
//
//        Matrix.setIdentityM(mModelFloor, 0);
//        Matrix.translateM(mModelFloor, 0, 0, -mFloorDepth, 0); // Floor appears below user
//
//        checkGLError("onSurfaceCreated");
    }

//    /**
//     * Converts a raw text file into a string.
//     * @param resId The resource ID of the raw text file about to be turned into a shader.
//     * @return
//     */
//    private String readRawTextFile(int resId) {
//        InputStream inputStream = getResources().openRawResource(resId);
//        try {
//            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
//            StringBuilder sb = new StringBuilder();
//            String line;
//            while ((line = reader.readLine()) != null) {
//                sb.append(line).append("\n");
//            }
//            reader.close();
//            return sb.toString();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//        return "";
//    }
//

    /**
     * Prepares OpenGL ES before we draw a frame.
     *
     * @param headTransform The head transformation in the new frame.
     */
    @Override
    public void onNewFrame(HeadTransform headTransform) {
//        GLES20.glUseProgram(mGlProgram);
//
//        mModelViewProjectionParam = GLES20.glGetUniformLocation(mGlProgram, "u_MVP");
//        mLightPosParam = GLES20.glGetUniformLocation(mGlProgram, "u_LightPos");
//        mModelViewParam = GLES20.glGetUniformLocation(mGlProgram, "u_MVMatrix");
//        mModelParam = GLES20.glGetUniformLocation(mGlProgram, "u_Model");
//        mIsFloorParam = GLES20.glGetUniformLocation(mGlProgram, "u_IsFloor");
//
//        // Build the Model part of the ModelView matrix.
//        Matrix.rotateM(mModelCube, 0, TIME_DELTA, 0.5f, 0.5f, 1.0f);
//
//        // Build the camera matrix and apply it to the ModelView.
//        Matrix.setLookAtM(mCamera, 0, 0.0f, 0.0f, CAMERA_Z, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f);
//
//        headTransform.getHeadView(mHeadView, 0);
//
//        checkGLError("onReadyToDraw");

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


//        mPositionParam = GLES20.glGetAttribLocation(mGlProgram, "a_Position");
//        mNormalParam = GLES20.glGetAttribLocation(mGlProgram, "a_Normal");
//        mColorParam = GLES20.glGetAttribLocation(mGlProgram, "a_Color");
//
//        GLES20.glEnableVertexAttribArray(mPositionParam);
//        GLES20.glEnableVertexAttribArray(mNormalParam);
//        GLES20.glEnableVertexAttribArray(mColorParam);
//        checkGLError("mColorParam");
//
//        // Apply the eye transformation to the camera.
//        Matrix.multiplyMM(mView, 0, transform.getEyeView(), 0, mCamera, 0);
//
//        // Set the position of the light
//        Matrix.multiplyMV(mLightPosInEyeSpace, 0, mView, 0, mLightPosInWorldSpace, 0);
//        GLES20.glUniform3f(mLightPosParam, mLightPosInEyeSpace[0], mLightPosInEyeSpace[1],
//                mLightPosInEyeSpace[2]);
//
//        // Build the ModelView and ModelViewProjection matrices
//        // for calculating cube position and light.
//        Matrix.multiplyMM(mModelView, 0, mView, 0, mModelCube, 0);
//        Matrix.multiplyMM(mModelViewProjection, 0, transform.getPerspective(), 0, mModelView, 0);
//        drawCube();
//
//        // Set mModelView for the floor, so we draw floor in the correct location
//        Matrix.multiplyMM(mModelView, 0, mView, 0, mModelFloor, 0);
//        Matrix.multiplyMM(mModelViewProjection, 0, transform.getPerspective(), 0,
//            mModelView, 0);
//        drawFloor(transform.getPerspective());
    }

    @Override
    public void onFinishFrame(Viewport viewport) {
    }

    /**
     * Increment the score, hide the object, and give feedback if the user pulls the magnet while
     * looking at the object. Otherwise, remind the user what to do.
     */
    @Override
    public void onCardboardTrigger() {
        Log.i(TAG, "onCardboardTrigger");

        mOverlayView.show3DToast("Capturing Object");

        try {
            camera.takePicture(null, null, mPicture);
        } catch (Exception e) {
            Log.d("Take Picture Failed.:", e.getMessage());
        }
        try {
            camera.setPreviewTexture(surface);
            camera.startPreview();
        } catch (IOException ioe) {
            Log.w("MainActivity", "CAM LAUNCH FAILED");
        }

        String apiKey =  "Basic dEGa15gLOpEfua3MckyEXCz9MgzfzT48QEmte7wDCjeaPPtJBZ";
        String uri = "https://www.metamind.io/vision/classify";
        String result = "";
        String contentType = "image/jpeg";
        String imgSource = "<img src=" + temp_picture + ">";

        HttpClient httpclient = new DefaultHttpClient();
        httpclient.getParams().setParameter(CoreProtocolPNames.PROTOCOL_VERSION, HttpVersion.HTTP_1_1);

        HttpPost httppost = new HttpPost(uri);
        httppost.setHeader("Authorization", apiKey);

        List<NameValuePair> nameValuePairs = new ArrayList<NameValuePair>();
        nameValuePairs.add(new BasicNameValuePair("classifier_id", "general"));
        nameValuePairs.add(new BasicNameValuePair("image_url", imgSource));

        //ContentBody cbFile = new FileBody(temp_picture, contentType);

        try {
            httppost.setEntity(new UrlEncodedFormEntity(nameValuePairs));
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        System.out.println("executing request " + httppost.getRequestLine());

        try {
            HttpResponse response = httpclient.execute(httppost);
            HttpEntity resEntity = response.getEntity();
            System.out.println(response.getStatusLine());
            if (resEntity != null) {
                System.out.println(EntityUtils.toString(resEntity));
            }
            if (resEntity != null) {
                resEntity.consumeContent();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }

        httpclient.getConnectionManager().shutdown();

       // Always give user feedback
        mVibrator.vibrate(50);
    }


    /**
     * Check if user is looking at object by calculating where the object is in eye-space.
     * @return
     */
//    private boolean isLookingAtObject() {
//        float[] initVec = {0, 0, 0, 1.0f};
//        float[] objPositionVec = new float[4];
//
//        // Convert object space to camera space. Use the headView from onNewFrame.
//        Matrix.multiplyMM(mModelView, 0, mHeadView, 0, mModelCube, 0);
//        Matrix.multiplyMV(objPositionVec, 0, mModelView, 0, initVec, 0);
//
//        float pitch = (float)Math.atan2(objPositionVec[1], -objPositionVec[2]);
//        float yaw = (float)Math.atan2(objPositionVec[0], -objPositionVec[2]);
//
//        Log.i(TAG, "Object position: X: " + objPositionVec[0]
//                + "  Y: " + objPositionVec[1] + " Z: " + objPositionVec[2]);
//        Log.i(TAG, "Object Pitch: " + pitch +"  Yaw: " + yaw);
//
//        return (Math.abs(pitch) < PITCH_LIMIT) && (Math.abs(yaw) < YAW_LIMIT);
//    }
}
