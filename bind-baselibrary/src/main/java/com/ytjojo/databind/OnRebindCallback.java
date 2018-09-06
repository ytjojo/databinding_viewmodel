package com.ytjojo.databind;

/**
 * Listener set on {@link BaseViewDataBinding#addOnRebindCallback(OnRebindCallback)} that
 * is called when bound values must be reevaluated in {@link
 * BaseViewDataBinding#executePendingBindings()}.
 */
public abstract class OnRebindCallback<T extends BaseViewDataBinding> {

    /**
     * Called when values in a ViewDataBinding should be reevaluated. This does not
     * mean that values will actually change, but only that something in the data
     * model that affects the bindings has been perturbed.
     * <p>
     * Return true to allow the reevaluation to happen or false if the reevaluation
     * should be stopped. If false is returned, it is the responsibility of the
     * OnRebindListener implementer to explicitly call {@link
     * BaseViewDataBinding#executePendingBindings()}.
     * <p>
     * The default implementation only returns <code>true</code>.
     *
     * @param binding The ViewDataBinding that is reevaluating its bound values.
     * @return true to indicate that the reevaluation should continue or false to
     * halt evaluation.
     */
    public boolean onPreBind(T binding) {
        return true;
    }

    /**
     * Called after all callbacks have completed {@link #onPreBind(BaseViewDataBinding)} when
     * one or more of the calls has returned <code>false</code>.
     * <p>
     * The default implementation does nothing.
     *
     * @param binding The ViewDataBinding that is reevaluating its bound values.
     */
    public void onCanceled(T binding) {
    }

    /**
     * Called after values have been reevaluated in {@link
     * BaseViewDataBinding#executePendingBindings()}. This is only called if all listeners have
     * returned true from {@link #onPreBind(BaseViewDataBinding)}.
     * <p>
     * The default implementation does nothing.
     *
     * @param binding The ViewDataBinding that is reevaluating its bound values.
     */
    public void onBound(T binding) {
    }
}