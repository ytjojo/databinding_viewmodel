package com.ytjojo.databind;

import android.annotation.TargetApi;
import android.arch.lifecycle.Lifecycle;
import android.arch.lifecycle.LifecycleObserver;
import android.arch.lifecycle.LifecycleOwner;
import android.arch.lifecycle.LiveData;
import android.arch.lifecycle.Observer;
import android.arch.lifecycle.OnLifecycleEvent;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.support.annotation.IdRes;
import android.support.annotation.LayoutRes;
import android.support.annotation.MainThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.Choreographer;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.github.ytjojo.databind.R;

import java.lang.ref.Reference;
import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;

/**
 * Created by jiulongteng on 2018/8/7.
 */

public abstract class BaseViewDataBinding {

    static int SDK_INT = Build.VERSION.SDK_INT;
    private static final int REBIND = 1;
    private static final int HALTED = 2;
    private static final int REBOUND = 3;

    private static final boolean USE_CHOREOGRAPHER = SDK_INT >= 16;
    private View mBindingRootView;


    private static final CreateWeakListener CREATE_LIVE_DATA_LISTENER = new CreateWeakListener() {
        @Override
        public WeakListener create(BaseViewDataBinding viewDataBinding, int localFieldId) {
            return new LiveDataListener(viewDataBinding, localFieldId).getListener();
        }
    };


    public void inflate(@LayoutRes int layout, LayoutInflater layoutInflater, ViewGroup parent, boolean attachToRoot) {
        mBindingRootView = layoutInflater.inflate(layout, parent, attachToRoot);
    }

    public void inflate(@LayoutRes int layout, LayoutInflater layoutInflater, ViewGroup parent) {
        mBindingRootView = layoutInflater.inflate(layout, parent);
    }

    public BaseViewDataBinding() {
        mLocalFieldObservers = new WeakListener[getLocalFieldCount()];
        if (USE_CHOREOGRAPHER) {
            mChoreographer = Choreographer.getInstance();
            mFrameCallback = new Choreographer.FrameCallback() {
                @Override
                public void doFrame(long frameTimeNanos) {
                    mRebindRunnable.run();
                }
            };
        } else {
            mFrameCallback = null;
            mUIThreadHandler = new Handler(Looper.myLooper());
        }
    }

    public void setBindingRootView(View root) {
        this.mBindingRootView = root;
        initAllViews();
        invalidateAll();
    }
    protected abstract void initAllViews();

    public <E extends View> E findViewById(@IdRes int id) {
        View target = mBindingRootView.findViewById(id);
        if (target == null) {
            return null;
        }
        return (E) target;
    }

    private LifecycleOwner mLifecycleOwner;


    /**
     * The collection of OnRebindCallbacks.
     */
    private CallbackRegistry<OnRebindCallback, BaseViewDataBinding, Void> mRebindCallbacks;

    /**
     * Flag to prevent reentrant executePendingBinding calls.
     */
    private boolean mIsExecutingPendingBindings;

    // null api < 16
    private Choreographer mChoreographer;

    private final Choreographer.FrameCallback mFrameCallback;

    // null api >= 16
    private Handler mUIThreadHandler;

    /**
     * If this ViewDataBinding is an include in another ViewDataBinding, this is the one
     * that contains this. mContainingBinding is used to order executeBindings -- containing
     * bindings should execute prior to included bindings.
     */
    private BaseViewDataBinding mContainingBinding;

    /**
     * The observed expressions.
     */
    private WeakListener[] mLocalFieldObservers;

    private static final CallbackRegistry.NotifierCallback<OnRebindCallback, BaseViewDataBinding, Void>
            REBIND_NOTIFIER = new CallbackRegistry.NotifierCallback<OnRebindCallback, BaseViewDataBinding, Void>() {
        @Override
        public void onNotifyCallback(OnRebindCallback callback, BaseViewDataBinding sender, int mode,
                                     Void arg2) {
            switch (mode) {
                case REBIND:
                    if (!callback.onPreBind(sender)) {
                        sender.mRebindHalted = true;
                    }
                    break;
                case HALTED:
                    callback.onCanceled(sender);
                    break;
                case REBOUND:
                    callback.onBound(sender);
                    break;
            }
        }
    };

    private static final ReferenceQueue<BaseViewDataBinding> sReferenceQueue = new ReferenceQueue<>();


    private static final View.OnAttachStateChangeListener ROOT_REATTACHED_LISTENER;

    static {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.KITKAT) {
            ROOT_REATTACHED_LISTENER = null;
        } else {
            ROOT_REATTACHED_LISTENER = new View.OnAttachStateChangeListener() {
                @TargetApi(Build.VERSION_CODES.KITKAT)
                @Override
                public void onViewAttachedToWindow(View v) {
                    // execute the pending bindings.
                    final BaseViewDataBinding binding = getBinding(v);
                    binding.mRebindRunnable.run();
                    v.removeOnAttachStateChangeListener(this);
                }

                @Override
                public void onViewDetachedFromWindow(View v) {
                }
            };
        }
    }

    /**
     * Runnable executed on animation heartbeat to rebind the dirty Views.
     */
    private final Runnable mRebindRunnable = new Runnable() {
        @Override
        public void run() {
            synchronized (this) {
                mPendingRebind = false;
            }
            processReferenceQueue();

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
                // Nested so that we don't get a lint warning in IntelliJ
                if (!mBindingRootView.isAttachedToWindow()) {
                    // Don't execute the pending bindings until the View
                    // is attached again.
                    mBindingRootView.removeOnAttachStateChangeListener(ROOT_REATTACHED_LISTENER);
                    mBindingRootView.addOnAttachStateChangeListener(ROOT_REATTACHED_LISTENER);
                    return;
                }
            }
            executePendingBindings();
        }
    };

    /**
     * Flag indicates that there are pending bindings that need to be reevaluated.
     */
    private boolean mPendingRebind = false;

    /**
     * Indicates that a onPreBind has stopped the executePendingBindings call.
     */
    private boolean mRebindHalted = false;

    /**
     * @hide
     */
    protected void setRootTag(View view) {
        view.setTag(R.id.baseviewbinding, this);
    }


    /**
     * Sets the {@link LifecycleOwner} that should be used for observing changes of
     * LiveData in this binding. If a {@link LiveData} is in one of the binding expressions
     * and no LifecycleOwner is set, the LiveData will not be observed and updates to it
     * will not be propagated to the UI.
     *
     * @param lifecycleOwner The LifecycleOwner that should be used for observing changes of
     *                       LiveData in this binding.
     */
    @MainThread
    public void setLifecycleOwner(@Nullable LifecycleOwner lifecycleOwner) {
        if (mLifecycleOwner == lifecycleOwner) {
            return;
        }
        if (mLifecycleOwner != null) {
            mLifecycleOwner.getLifecycle().removeObserver(mOnStartListener);
        }
        mLifecycleOwner = lifecycleOwner;
        if (lifecycleOwner != null) {
            if (mOnStartListener == null) {
                mOnStartListener = new OnStartListener();
            }
            lifecycleOwner.getLifecycle().addObserver(mOnStartListener);
        }
        for (WeakListener<?> weakListener : mLocalFieldObservers) {
            if (weakListener != null) {
                weakListener.setLifecycleOwner(lifecycleOwner);
            }
        }
    }

    /**
     * Add a listener to be called when reevaluating dirty fields. This also allows automatic
     * updates to be halted, but does not stop explicit calls to {@link #executePendingBindings()}.
     *
     * @param listener The listener to add.
     */
    public void addOnRebindCallback(@NonNull OnRebindCallback listener) {
        if (mRebindCallbacks == null) {
            mRebindCallbacks = new CallbackRegistry<OnRebindCallback, BaseViewDataBinding, Void>(REBIND_NOTIFIER);
        }
        mRebindCallbacks.add(listener);
    }

    /**
     * Removes a listener that was added in {@link #addOnRebindCallback(OnRebindCallback)}.
     *
     * @param listener The listener to remove.
     */
    public void removeOnRebindCallback(@NonNull OnRebindCallback listener) {
        if (mRebindCallbacks != null) {
            mRebindCallbacks.remove(listener);
        }
    }

    /**
     * Listener when mLifecycleOwner is non-null to allow delaying rebind calls while the
     * status is less than started.
     */
    private OnStartListener mOnStartListener;


    /**
     * @hide
     */
    protected Object getObservedField(int localFieldId) {
        WeakListener listener = mLocalFieldObservers[localFieldId];
        if (listener == null) {
            return null;
        }
        return listener.getTarget();
    }


    private void handleFieldChange(int mLocalFieldId, Object object) {

        boolean result = onFieldChange(mLocalFieldId, object);
        if (result) {
            requestRebind();
        }
    }


    protected boolean unregisterFrom(int localFieldId) {
        WeakListener listener = mLocalFieldObservers[localFieldId];
        if (listener != null) {
            return listener.unregister();
        }
        return false;
    }

    private boolean updateRegistration(int localFieldId, Object observable,
                                       CreateWeakListener listenerCreator) {
        if (observable == null) {
            return unregisterFrom(localFieldId);
        }
        WeakListener listener = mLocalFieldObservers[localFieldId];
        if (listener == null) {
            registerTo(localFieldId, observable, listenerCreator);
            return true;
        }
        if (listener.getTarget() == observable) {
            return false;//nothing to do, same object
        }
        unregisterFrom(localFieldId);
        registerTo(localFieldId, observable, listenerCreator);
        return true;
    }


    /**
     * @hide
     */
    protected boolean updateLiveDataRegistration(int localFieldId, LiveData<?> observable) {
        try {
            return updateRegistration(localFieldId, observable, CREATE_LIVE_DATA_LISTENER);
        } finally {
        }
    }
    /**
     * @hide
     */
    protected void registerTo(int localFieldId, Object observable,
                              CreateWeakListener listenerCreator) {
        if (observable == null) {
            return;
        }
        WeakListener listener = mLocalFieldObservers[localFieldId];
        if (listener == null) {
            listener = listenerCreator.create(this, localFieldId);
            mLocalFieldObservers[localFieldId] = listener;
            if (mLifecycleOwner != null) {
                listener.setLifecycleOwner(mLifecycleOwner);
            }
        }
        listener.setTarget(observable);
    }

    private void executePendingBindings() {
        executeBindingsInternal();
    }

    /**
     * @hide
     */
    protected void requestRebind() {
        if (mContainingBinding != null) {
            mContainingBinding.requestRebind();
        } else {
            synchronized (this) {
                if (mPendingRebind) {
                    return;
                }
                mPendingRebind = true;
            }
            if (mLifecycleOwner != null) {
                Lifecycle.State state = mLifecycleOwner.getLifecycle().getCurrentState();
                if (!state.isAtLeast(Lifecycle.State.STARTED)) {
                    return; // wait until lifecycle owner is started
                }
            }
            if (USE_CHOREOGRAPHER) {
                mChoreographer.postFrameCallback(mFrameCallback);
            } else {
                mUIThreadHandler.post(mRebindRunnable);
            }
        }
    }

    /**
     * Evaluates the pending bindings without executing the parent bindings.
     */
    private void executeBindingsInternal() {
        if (mIsExecutingPendingBindings) {
            requestRebind();
            return;
        }
        if (!hasPendingBindings()) {
            return;
        }
        mIsExecutingPendingBindings = true;
        mRebindHalted = false;
        if (mRebindCallbacks != null) {
            mRebindCallbacks.notifyCallbacks(this, REBIND, null);

            // The onRebindListeners will change mPendingHalted
            if (mRebindHalted) {
                mRebindCallbacks.notifyCallbacks(this, HALTED, null);
            }
        }
        if (!mRebindHalted) {
            executeBindings();
            if (mRebindCallbacks != null) {
                mRebindCallbacks.notifyCallbacks(this, REBOUND, null);
            }
        }
        mIsExecutingPendingBindings = false;
    }

    /**
     * Calls executeBindingsInternal on the other ViewDataBinding
     *
     * @hide
     */
    protected static void executeBindingsOn(BaseViewDataBinding other) {
        other.executeBindingsInternal();
    }


    void forceExecuteBindings() {
        executeBindings();
    }


    static BaseViewDataBinding getBinding(View v) {
        if (v != null) {
            return (BaseViewDataBinding) v.getTag(R.id.baseviewbinding);
        }
        return null;
    }

    /**
     * Called when an observed object changes. Sets the appropriate dirty flag if applicable.
     * @param localFieldId The index into mLocalFieldObservers that this Object resides in.
     * @param object The object that has changed.
     * @return true if this change should cause a change to the UI.
     * @hide
     */
    protected abstract boolean onFieldChange(int localFieldId, Object object);

    /**
     * @hide
     */
    protected abstract void executeBindings();

    protected abstract int getLocalFieldCount();

    /**
     * Invalidates all binding expressions and requests a new rebind to refresh UI.
     */
    public abstract void invalidateAll();

    /**
     * Returns whether the UI needs to be refresh to represent the current data.
     *
     * @return true if any field has changed and the binding should be evaluated.
     */
    public abstract boolean hasPendingBindings();


    /**
     * Polls sReferenceQueue to remove listeners on ViewDataBindings that have been collected.
     */
    private static void processReferenceQueue() {
        Reference<? extends BaseViewDataBinding> ref;
        while ((ref = sReferenceQueue.poll()) != null) {
            if (ref instanceof WeakListener) {
                WeakListener listener = (WeakListener) ref;
                listener.unregister();
            }
        }
    }

    private interface ObservableReference<T> {
        WeakListener<T> getListener();

        void addListener(T target);

        void removeListener(T target);

        void setLifecycleOwner(LifecycleOwner lifecycleOwner);
    }

    private static class WeakListener<T> extends WeakReference<BaseViewDataBinding> {
        private final ObservableReference<T> mObservable;
        protected final int mLocalFieldId;
        private T mTarget;

        public WeakListener(BaseViewDataBinding binder, int localFieldId,
                            ObservableReference<T> observable) {
            super(binder, sReferenceQueue);
            mLocalFieldId = localFieldId;
            mObservable = observable;
        }

        public void setLifecycleOwner(LifecycleOwner lifecycleOwner) {
            mObservable.setLifecycleOwner(lifecycleOwner);
        }

        public void setTarget(T object) {
            unregister();
            mTarget = object;
            if (mTarget != null) {
                mObservable.addListener(mTarget);
            }
        }

        public boolean unregister() {
            boolean unregistered = false;
            if (mTarget != null) {
                mObservable.removeListener(mTarget);
                unregistered = true;
            }
            mTarget = null;
            return unregistered;
        }

        public T getTarget() {
            return mTarget;
        }

        protected BaseViewDataBinding getBinder() {
            BaseViewDataBinding binder = get();
            if (binder == null) {
                unregister(); // The binder is dead
            }
            return binder;
        }
    }


    private static class LiveDataListener implements Observer,
            ObservableReference<LiveData<?>> {
        final WeakListener<LiveData<?>> mListener;
        LifecycleOwner mLifecycleOwner;

        public LiveDataListener(BaseViewDataBinding binder, int localFieldId) {
            mListener = new WeakListener(binder, localFieldId, this);
        }

        @Override
        public void setLifecycleOwner(LifecycleOwner lifecycleOwner) {
            LifecycleOwner owner = (LifecycleOwner) lifecycleOwner;
            LiveData<?> liveData = mListener.getTarget();
            if (liveData != null) {
                if (mLifecycleOwner != null) {
                    liveData.removeObserver(this);
                }
                if (lifecycleOwner != null) {
                    liveData.observe(owner, this);
                }
            }
            mLifecycleOwner = owner;
        }

        @Override
        public WeakListener<LiveData<?>> getListener() {
            return mListener;
        }

        @Override
        public void addListener(LiveData<?> target) {
            if (mLifecycleOwner != null) {
                target.observe(mLifecycleOwner, this);
            }
        }

        @Override
        public void removeListener(LiveData<?> target) {
            target.removeObserver(this);
        }

        @Override
        public void onChanged(@Nullable Object o) {
            BaseViewDataBinding binder = mListener.getBinder();
            binder.handleFieldChange(mListener.mLocalFieldId, mListener.getTarget());
        }
    }
    private interface CreateWeakListener {
        WeakListener create(BaseViewDataBinding viewDataBinding, int localFieldId);
    }

    /**
     * This class is used internally to handle Lifecycle events in ViewDataBinding. A
     * LifecycleObserver class MUST be public.
     *
     * @hide
     */
    public class OnStartListener implements LifecycleObserver {
        private OnStartListener() {
        }

        @OnLifecycleEvent(Lifecycle.Event.ON_START)
        public void onStart() {
            executePendingBindings();
        }
    }



    /** @hide */
    protected static int safeUnbox(java.lang.Integer boxed) {
        return boxed == null ? 0 : (int)boxed;
    }

    /** @hide */
    protected static long safeUnbox(java.lang.Long boxed) {
        return boxed == null ? 0L : (long)boxed;
    }

    /** @hide */
    protected static short safeUnbox(java.lang.Short boxed) {
        return boxed == null ? 0 : (short)boxed;
    }

    /** @hide */
    protected static byte safeUnbox(java.lang.Byte boxed) {
        return boxed == null ? 0 : (byte)boxed;
    }

    /** @hide */
    protected static char safeUnbox(java.lang.Character boxed) {
        return boxed == null ? '\u0000' : (char)boxed;
    }

    /** @hide */
    protected static double safeUnbox(java.lang.Double boxed) {
        return boxed == null ? 0.0 : (double)boxed;
    }

    /** @hide */
    protected static float safeUnbox(java.lang.Float boxed) {
        return boxed == null ? 0f : (float)boxed;
    }

    /** @hide */
    protected static boolean safeUnbox(java.lang.Boolean boxed) {
        return boxed == null ? false : (boolean)boxed;
    }

}
