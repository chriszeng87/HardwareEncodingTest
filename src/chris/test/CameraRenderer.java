package chris.test;
//
//import java.nio.ByteBuffer;
//import java.nio.ByteOrder;
//import java.nio.FloatBuffer;
//import java.util.LinkedList;
//import java.util.Queue;
//
//import javax.microedition.khronos.egl.EGLConfig;
//import javax.microedition.khronos.opengles.GL10;
//
//import android.graphics.Bitmap;
//import android.graphics.Canvas;
//import android.graphics.SurfaceTexture;
//import android.hardware.Camera;
//import android.opengl.GLES20;
//import android.util.Log;
//
//import com.micro.filter.BaseFilter;
//import com.micro.filter.Frame;
//import com.micro.filter.SurfaceTextrueFilter;

//public class CameraRenderer implements MyGLSurfaceView.Renderer,
//		SurfaceTexture.OnFrameAvailableListener {
//
//	public static final int NO_IMAGE = -1;
//	static final float CUBE[] = { -1.0f, -1.0f, -1.0f, 1.0f, 1.0f, 1.0f, 1.0f,
//			-1.0f, };
//
//	public final Object mSurfaceChangedWaiter = new Object();
//
//	private MyGLSurfaceView mGlsv;
//
//	private BaseFilter mFilter;
//	private BaseFilter mPreviewFilter;
//	private Frame mPriewFrame;
//	private float[] mTexCoordArray = new float[8];
//	private final float[] mTransformMatrix = new float[16];
//
//	private int mGLTextureId = NO_IMAGE;
//	private SurfaceTexture mSurfaceTexture = null;
//	private final FloatBuffer mGLCubeBuffer;
//
//	private int mOutputWidth;
//	private int mOutputHeight;
//	private int mImageWidth;
//	private int mImageHeight;
//	private int mAddedPadding;
//
//	private final Queue<Runnable> mRunOnDraw;
//	private Rotation mRotation;
//	private boolean mFlipHorizontal;
//	private boolean mFlipVertical;
//	private ScaleType mScaleType = ScaleType.TOP_CROP;
//
//	public CameraRenderer(MyGLSurfaceView glsv) {
//		mGlsv = glsv;
//		mPreviewFilter = new SurfaceTextrueFilter(true);
//		mRunOnDraw = new LinkedList<Runnable>();
//
//		mGLCubeBuffer = ByteBuffer.allocateDirect(CUBE.length * 4)
//				.order(ByteOrder.nativeOrder()).asFloatBuffer();
//		mGLCubeBuffer.put(CUBE).position(0);
//
//		setRotation(Rotation.NORMAL, false, false);
//	}
//
//	@Override
//	public void onFrameAvailable(SurfaceTexture surfaceTexture) {
//		mGlsv.requestRender();
//	}
//
//	@Override
//	public void onSurfaceCreated(final GL10 unused, final EGLConfig config) {
//		GLES20.glClearColor(0.07f, 0.07f, 0.07f, 1.0f);
//		GLES20.glDisable(GLES20.GL_DEPTH_TEST);
//		// if (mFilter == null) {
//		// mFilter = FilterUtil.LENS_FILTER.newFilter();
//		// }
//		if (mPreviewFilter != null) {
//			if (mFilter != null) {
//				mPreviewFilter.setNextFilter(mFilter, null);
//			}
//			mPreviewFilter.ApplyGLSLFilter(true);
//		}
//		if (mPriewFrame == null) {
//			mPriewFrame = new Frame();
//		}
//	}
//
//	@Override
//	public void onSurfaceChanged(final GL10 gl, final int width,
//			final int height) {
//		mOutputWidth = width;
//		mOutputHeight = height;
//		GLES20.glViewport(0, 0, width, height);
//		synchronized (mSurfaceChangedWaiter) {
//			mSurfaceChangedWaiter.notifyAll();
//		}
//		adjustImageScaling();
//	}
//
//	@Override
//	public void onDrawFrame(final GL10 gl) {
//		GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
//		GLES20.glClearColor(0.07f, 0.07f, 0.07f, 1.0f);
//		synchronized (mRunOnDraw) {
//			while (!mRunOnDraw.isEmpty()) {
//				mRunOnDraw.poll().run();
//			}
//		}
//		if (mSurfaceTexture != null) {
//			mSurfaceTexture.updateTexImage();
//			mSurfaceTexture.getTransformMatrix(mTransformMatrix);
//			if (mPreviewFilter != null) {
//				mPreviewFilter.nativeUpdateMatrix(mTransformMatrix);
//			}
//			
//		}
//		if (mPreviewFilter != null && null != mTexCoordArray) {
//			mPreviewFilter.nativeUpdateTexCoord(mTexCoordArray);
//			mPreviewFilter.RenderProcess(mGLTextureId, mOutputWidth,//
//					mOutputHeight, 0, 0.0, mPriewFrame);
//		}
//	}
//
//	public float[] getTransformMatrix() {
//		return mTransformMatrix;
//	}
//
//	public void setUpSurfaceTexture(final Camera camera) {
//		runOnDraw(new Runnable() {
//			@Override
//			public void run() {
//				int[] textures = new int[1];
//				GLES20.glGenTextures(1, textures, 0);
//				mGLTextureId = textures[0];
//				mSurfaceTexture = new SurfaceTexture(mGLTextureId);
//				try {
//					camera.setPreviewTexture(mSurfaceTexture);
//				} catch (Exception e) {
//					e.printStackTrace();
//				}
//			}
//		});
//	}
//
//	public void setFilter(final BaseFilter filter) {
//		runOnDraw(new Runnable() {
//			@Override
//			public void run() {
//				mFilter = filter;
//				if (mPreviewFilter != null) {
//					mPreviewFilter.setNextFilter(filter, null);
//				}
//			}
//		});
//	}
//
//	public void deleteImage() {
//		runOnDraw(new Runnable() {
//
//			@Override
//			public void run() {
//				GLES20.glDeleteTextures(1, new int[] { mGLTextureId }, 0);
//				mGLTextureId = NO_IMAGE;
//				mSurfaceTexture = null;
//			}
//		});
//	}
//
//	public void setImageBitmap(final Bitmap bitmap) {
//		setImageBitmap(bitmap, true);
//	}
//
//	public void setImageBitmap(final Bitmap bitmap, final boolean recycle) {
//		if (bitmap == null) {
//			return;
//		}
//
//		runOnDraw(new Runnable() {
//
//			@Override
//			public void run() {
//				Bitmap resizedBitmap = null;
//				if (bitmap.getWidth() % 2 == 1) {
//					resizedBitmap = Bitmap.createBitmap(bitmap.getWidth() + 1,
//							bitmap.getHeight(), Bitmap.Config.ARGB_8888);
//					Canvas can = new Canvas(resizedBitmap);
//					can.drawARGB(0x00, 0x00, 0x00, 0x00);
//					can.drawBitmap(bitmap, 0, 0, null);
//					mAddedPadding = 1;
//				} else {
//					mAddedPadding = 0;
//				}
//
//				mGLTextureId = OpenGlUtils.loadTexture(
//						resizedBitmap != null ? resizedBitmap : bitmap,
//						mGLTextureId, recycle);
//				if (resizedBitmap != null) {
//					resizedBitmap.recycle();
//				}
//				mImageWidth = bitmap.getWidth();
//				mImageHeight = bitmap.getHeight();
//				adjustImageScaling();
//			}
//		});
//	}
//
//	public void setImageSize(final int width, final int height, final int degree) {
//		runOnDraw(new Runnable() {
//
//			@Override
//			public void run() {
//				mImageWidth = width;
//				mImageHeight = height;
//				if (degree == 90 || degree == 270) {
//					mImageWidth = height;
//					mImageHeight = width;
//				}
//				adjustImageScaling();
//			}
//		});
//	}
//
//	public void setScaleType(ScaleType scaleType) {
//		mScaleType = scaleType;
//	}
//
//	protected int getFrameWidth() {
//		return mOutputWidth;
//	}
//
//	protected int getFrameHeight() {
//		return mOutputHeight;
//	}
//	
//	public float[] getTexCoords() {
//		return mTexCoordArray;
//	}
//
//	private void adjustImageScaling() {
//		float outputWidth = mOutputWidth;
//		float outputHeight = mOutputHeight;
//		if (mRotation == Rotation.ROTATION_270
//				|| mRotation == Rotation.ROTATION_90) {
//			outputWidth = mOutputHeight;
//			outputHeight = mOutputWidth;
//		}
//
//		float ratio1 = outputWidth / mImageWidth;
//		float ratio2 = outputHeight / mImageHeight;
//		float ratioMin = Math.min(ratio1, ratio2);
//		mImageWidth = (int) ((float) mImageWidth * ratioMin);
//		mImageHeight = (int) ((float) mImageHeight * ratioMin);
//
//		float ratioWidth = 1.0f;
//		float ratioHeight = 1.0f;
//		if (mImageWidth != outputWidth) {
//			ratioWidth = mImageWidth / outputWidth;
//		} else if (mImageHeight != outputHeight) {
//			ratioHeight = mImageHeight / outputHeight;
//		}
//
//		float[] cube = CUBE;
//		float[] textureCords = TextureRotationUtil.getRotation(mRotation,
//				mFlipHorizontal, mFlipVertical);
//		if (mScaleType == ScaleType.CENTER_CROP) {
//			float distHorizontal = (1 - ratioWidth) / 2;
//			float distVertical = (1 - ratioHeight) / 2;
//			textureCords = new float[] {
//					addDistance(textureCords[0], distVertical),
//					addDistance(textureCords[1], distHorizontal),
//					addDistance(textureCords[2], distVertical),
//					addDistance(textureCords[3], distHorizontal),
//					addDistance(textureCords[4], distVertical),
//					addDistance(textureCords[5], distHorizontal),
//					addDistance(textureCords[6], distVertical),
//					addDistance(textureCords[7], distHorizontal), };
//		} else if (mScaleType == ScaleType.TOP_CROP) {
//			float distHorizontal = 1 - ratioWidth;
//			float distVertical = 1 - ratioHeight;
//			textureCords = new float[] {
//					addDistance2(textureCords[0], distVertical),
//					addDistance2(textureCords[1], distHorizontal),
//					addDistance2(textureCords[2], distVertical),
//					addDistance2(textureCords[3], distHorizontal),
//					addDistance2(textureCords[4], distVertical),
//					addDistance2(textureCords[5], distHorizontal),
//					addDistance2(textureCords[6], distVertical),
//					addDistance2(textureCords[7], distHorizontal), };
//		} else {
//			cube = new float[] { CUBE[0] * ratioWidth, CUBE[1] * ratioHeight,
//					CUBE[2] * ratioWidth, CUBE[3] * ratioHeight,
//					CUBE[4] * ratioWidth, CUBE[5] * ratioHeight,
//					CUBE[6] * ratioWidth, CUBE[7] * ratioHeight, };
//		}
//
//		mGLCubeBuffer.clear();
//		mGLCubeBuffer.put(cube).position(0);
//		mTexCoordArray = textureCords;
//	}
//
//	private float addDistance(float coordinate, float distance) {
//		return coordinate == 0.0f ? distance : 1 - distance;
//	}
//	
//	private float addDistance2(float coordinate, float distance) {
//		return coordinate == 1.0f ? 1.0f : distance;
//	}
//
//	public void setRotationCamera(final Rotation rotation,
//			final boolean flipHorizontal, final boolean flipVertical) {
//		setRotation(rotation, flipVertical, flipHorizontal);
//	}
//
//	public void setRotation(final Rotation rotation,
//			final boolean flipHorizontal, final boolean flipVertical) {
//		mRotation = rotation;
//		mFlipHorizontal = flipHorizontal;
//		mFlipVertical = flipVertical;
//		adjustImageScaling();
//	}
//
//	public Rotation getRotation() {
//		return mRotation;
//	}
//
//	public boolean isFlippedHorizontally() {
//		return mFlipHorizontal;
//	}
//
//	public boolean isFlippedVertically() {
//		return mFlipVertical;
//	}
//
//	protected void runOnDraw(final Runnable runnable) {
//		synchronized (mRunOnDraw) {
//			mRunOnDraw.add(runnable);
//		}
//	}
//
//	public enum ScaleType {
//		CENTER_INSIDE, CENTER_CROP, TOP_CROP
//	}
//
//}
