package me.lake.librestreaming.client;

import android.graphics.SurfaceTexture;
import android.hardware.Camera;

import java.io.IOException;
import java.util.List;

import me.lake.librestreaming.core.CameraHelper;
import me.lake.librestreaming.core.RESHardVideoCore;
import me.lake.librestreaming.core.RESSoftVideoCore;
import me.lake.librestreaming.core.RESVideoCore;
import me.lake.librestreaming.core.listener.RESScreenShotListener;
import me.lake.librestreaming.filter.hardvideofilter.BaseHardVideoFilter;
import me.lake.librestreaming.filter.softvideofilter.BaseSoftVideoFilter;
import me.lake.librestreaming.model.RESConfig;
import me.lake.librestreaming.model.RESCoreParameters;
import me.lake.librestreaming.rtmp.RESFlvDataCollecter;
import me.lake.librestreaming.tools.LogTools;

/**
 * Created by lake on 16-5-24.
 */
public class RESVideoClient {
    RESCoreParameters resCoreParameters;
    private final Object syncOp = new Object();
    private Camera camera;
    private SurfaceTexture camTexture;
    private int cameraNum;
    private int currentCameraIndex;
    private RESVideoCore videoCore;

    public RESVideoClient(RESCoreParameters parameters) {
        resCoreParameters = parameters;
        cameraNum = Camera.getNumberOfCameras();
        currentCameraIndex = Camera.CameraInfo.CAMERA_FACING_BACK;
    }

    public boolean prepare(RESConfig resConfig) {
        synchronized (syncOp) {
            if ((cameraNum - 1) >= resConfig.getDefaultCamera()) {
                currentCameraIndex = resConfig.getDefaultCamera();
            }
            if (null == (camera = createCamera(currentCameraIndex))) {
                LogTools.e("can not open camera");
                return false;
            }
            Camera.Parameters parameters = camera.getParameters();
            CameraHelper.selectCameraPreviewWH(parameters, resCoreParameters, resConfig.getTargetVideoSize());
            CameraHelper.selectCameraFpsRange(parameters, resCoreParameters);
            resoveResolution(resCoreParameters, resConfig);
            if (!CameraHelper.selectCameraColorFormat(parameters, resCoreParameters)) {
                LogTools.e("CameraHelper.selectCameraColorFormat,Failed");
                resCoreParameters.dump();
                return false;
            }
            if (!CameraHelper.configCamera(camera, resCoreParameters)) {
                LogTools.e("CameraHelper.configCamera,Failed");
                resCoreParameters.dump();
                return false;
            }
            switch (resCoreParameters.filterMode) {
                case RESCoreParameters.FILTER_MODE_SOFT:
                    videoCore = new RESSoftVideoCore(resCoreParameters);
                    break;
                case RESCoreParameters.FILTER_MODE_HARD:
                    videoCore = new RESHardVideoCore(resCoreParameters);
                    break;
            }
            videoCore.setCurrentCamera(currentCameraIndex);
            if (!videoCore.prepare(resConfig)) {
                return false;
            }
            prepareVideo();
            return true;
        }
    }

    private Camera createCamera(int cameraId) {
        try {
            camera = Camera.open(cameraId);
        } catch (SecurityException e) {
            LogTools.trace("no permission", e);
            return null;
        } catch (Exception e) {
            LogTools.trace("camera.open()failed", e);
            return null;
        }
        return camera;
    }

    private boolean prepareVideo() {
        if (resCoreParameters.filterMode == RESCoreParameters.FILTER_MODE_SOFT) {
            camera.addCallbackBuffer(new byte[resCoreParameters.previewBufferSize]);
            camera.addCallbackBuffer(new byte[resCoreParameters.previewBufferSize]);
        }
        return true;
    }

    private boolean startVideo() {
        camTexture = new SurfaceTexture(RESVideoCore.OVERWATCH_TEXTURE_ID);
        if (resCoreParameters.filterMode == RESCoreParameters.FILTER_MODE_SOFT) {
            camera.setPreviewCallbackWithBuffer(new Camera.PreviewCallback() {
                @Override
                public void onPreviewFrame(byte[] data, Camera camera) {
                    synchronized (syncOp) {
                        if (videoCore != null && data != null) {
                            ((RESSoftVideoCore) videoCore).queueVideo(data);
                        }
                        camera.addCallbackBuffer(data);
                    }
                }
            });
        } else {
            camTexture.setOnFrameAvailableListener(new SurfaceTexture.OnFrameAvailableListener() {
                @Override
                public void onFrameAvailable(SurfaceTexture surfaceTexture) {
                    synchronized (syncOp) {
                        if (videoCore != null) {
                            ((RESHardVideoCore) videoCore).onFrameAvailable();
                        }
                    }
                }
            });
        }
        try {
            camera.setPreviewTexture(camTexture);
        } catch (IOException e) {
            LogTools.trace(e);
            camera.release();
            return false;
        }
        camera.startPreview();
        return true;
    }

    public boolean start(RESFlvDataCollecter flvDataCollecter) {
        synchronized (syncOp) {
            if (!startVideo()) {
                resCoreParameters.dump();
                LogTools.e("RESVideoClient,start(),failed");
                return false;
            }
            videoCore.start(flvDataCollecter, camTexture);
            return true;
        }
    }

    public boolean stop() {
        synchronized (syncOp) {
            camera.stopPreview();
            videoCore.stop();
            camTexture.release();
            return true;
        }
    }

    public boolean destroy() {
        synchronized (syncOp) {
            camera.release();
            videoCore.destroy();
            videoCore = null;
            camera = null;
            return true;
        }
    }

    public void createPreview(SurfaceTexture surfaceTexture, int visualWidth, int visualHeight) {
        videoCore.createPreview(surfaceTexture, visualWidth, visualHeight);
    }

    public void updatePreview(int visualWidth, int visualHeight) {
        videoCore.updatePreview(visualWidth, visualHeight);
    }

    public void destroyPreview() {
        videoCore.destroyPreview();
    }

    public boolean swapCamera() {
        synchronized (syncOp) {
            LogTools.d("RESClient,swapCamera()");
            camera.stopPreview();
            camera.release();
            camera = null;
            if (null == (camera = createCamera(currentCameraIndex = (++currentCameraIndex) % cameraNum))) {
                LogTools.e("can not swap camera");
                return false;
            }
            videoCore.setCurrentCamera(currentCameraIndex);
            CameraHelper.selectCameraFpsRange(camera.getParameters(), resCoreParameters);
            if (!CameraHelper.configCamera(camera, resCoreParameters)) {
                camera.release();
                return false;
            }
            prepareVideo();
            camTexture.release();
            videoCore.updateCamTexture(null);
            startVideo();
            videoCore.updateCamTexture(camTexture);
            return true;
        }
    }

    public boolean toggleFlashLight() {
        synchronized (syncOp) {
            try {
                Camera.Parameters parameters = camera.getParameters();
                List<String> flashModes = parameters.getSupportedFlashModes();
                String flashMode = parameters.getFlashMode();
                if (!Camera.Parameters.FLASH_MODE_TORCH.equals(flashMode)) {
                    if (flashModes.contains(Camera.Parameters.FLASH_MODE_TORCH)) {
                        parameters.setFlashMode(Camera.Parameters.FLASH_MODE_TORCH);
                        camera.setParameters(parameters);
                        return true;
                    }
                } else if (!Camera.Parameters.FLASH_MODE_OFF.equals(flashMode)) {
                    if (flashModes.contains(Camera.Parameters.FLASH_MODE_OFF)) {
                        parameters.setFlashMode(Camera.Parameters.FLASH_MODE_OFF);
                        camera.setParameters(parameters);
                        return true;
                    }
                }
            } catch (Exception e) {
                LogTools.d("toggleFlashLight,failed" + e.getMessage());
                return false;
            }
            return false;
        }
    }

    public boolean setZoomByPercent(float targetPercent) {
        targetPercent = Math.min(Math.max(0f, targetPercent), 1f);
        Camera.Parameters p = camera.getParameters();
        p.setZoom((int) (p.getMaxZoom() * targetPercent));
        camera.setParameters(p);
        return true;
    }

    public BaseSoftVideoFilter acquireSoftVideoFilter() {
        if (resCoreParameters.filterMode == RESCoreParameters.FILTER_MODE_SOFT) {
            return ((RESSoftVideoCore) videoCore).acquireVideoFilter();
        }
        return null;
    }

    public void releaseSoftVideoFilter() {
        if (resCoreParameters.filterMode == RESCoreParameters.FILTER_MODE_SOFT) {
            ((RESSoftVideoCore) videoCore).releaseVideoFilter();
        }
    }

    public void setSoftVideoFilter(BaseSoftVideoFilter baseSoftVideoFilter) {
        if (resCoreParameters.filterMode == RESCoreParameters.FILTER_MODE_SOFT) {
            ((RESSoftVideoCore) videoCore).setVideoFilter(baseSoftVideoFilter);
        }
    }

    public BaseHardVideoFilter acquireHardVideoFilter() {
        if (resCoreParameters.filterMode == RESCoreParameters.FILTER_MODE_HARD) {
            return ((RESHardVideoCore) videoCore).acquireVideoFilter();
        }
        return null;
    }

    public void releaseHardVideoFilter() {
        if (resCoreParameters.filterMode == RESCoreParameters.FILTER_MODE_HARD) {
            ((RESHardVideoCore) videoCore).releaseVideoFilter();
        }
    }

    public void setHardVideoFilter(BaseHardVideoFilter baseHardVideoFilter) {
        if (resCoreParameters.filterMode == RESCoreParameters.FILTER_MODE_HARD) {
            ((RESHardVideoCore) videoCore).setVideoFilter(baseHardVideoFilter);
        }
    }

    public void takeScreenShot(RESScreenShotListener listener) {
        synchronized (syncOp) {
            if (videoCore != null) {
                videoCore.takeScreenShot(listener);
            }
        }
    }

    public float getDrawFrameRate() {
        synchronized (syncOp) {
            return videoCore == null ? 0 : videoCore.getDrawFrameRate();
        }
    }

    private void resoveResolution(RESCoreParameters resCoreParameters, RESConfig resConfig) {
        if (resCoreParameters.filterMode == RESCoreParameters.FILTER_MODE_SOFT) {
            if (resCoreParameters.isPortrait) {
                resCoreParameters.videoHeight = resCoreParameters.previewVideoWidth;
                resCoreParameters.videoWidth = resCoreParameters.previewVideoHeight;
            } else {
                resCoreParameters.videoWidth = resCoreParameters.previewVideoWidth;
                resCoreParameters.videoHeight = resCoreParameters.previewVideoHeight;
            }
        } else {
            float pw, ph, vw, vh;
            if (resCoreParameters.isPortrait) {
                resCoreParameters.videoHeight = resConfig.getTargetVideoSize().getWidth();
                resCoreParameters.videoWidth = resConfig.getTargetVideoSize().getHeight();
                pw = resCoreParameters.previewVideoHeight;
                ph = resCoreParameters.previewVideoWidth;
            } else {
                resCoreParameters.videoWidth = resConfig.getTargetVideoSize().getWidth();
                resCoreParameters.videoHeight = resConfig.getTargetVideoSize().getHeight();
                pw = resCoreParameters.previewVideoWidth;
                ph = resCoreParameters.previewVideoHeight;
            }
            vw = resCoreParameters.videoWidth;
            vh = resCoreParameters.videoHeight;
            float pr = ph / pw, vr = vh / vw;
            if (pr == vr) {
                resCoreParameters.cropRatio = 0.0f;
            } else if (pr > vr) {
                resCoreParameters.cropRatio = (1.0f - vr / pr) / 2.0f;
            } else {
                resCoreParameters.cropRatio = -(1.0f - pr / vr) / 2.0f;
            }
        }
    }
}
