package org.aerogear.mobile.security;

import android.content.Context;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import org.aerogear.mobile.core.MobileCore;
import org.aerogear.mobile.core.executor.AppExecutors;
import org.aerogear.mobile.core.logging.Logger;
import org.aerogear.mobile.core.metrics.MetricsService;
import org.aerogear.mobile.security.metrics.SecurityCheckResultMetric;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Executor used to asynchronously execute checks.
 * Checks are executed by using {@link AppExecutors#singleThreadService()} if no custom executor is configured.
 */
public class AsyncSecurityCheckExecutor extends AbstractSecurityCheckExecutor<AsyncSecurityCheckExecutor> {

    private final static String TAG = "AsyncSecurityCheckExecutor";
    private final static Logger LOG = MobileCore.getLogger();

    private final ExecutorService executorService;


    /**
     * Builder class for constructing an AsyncSecurityCheckExecutor object.
     */
    public static class Builder extends SecurityCheckExecutor.Builder.AbstractBuilder<Builder, AsyncSecurityCheckExecutor> {

        private ExecutorService executorService;

        /**
         * Creates a Builder object.
         *
         * @param ctx {@link Context} to be used by security checks
         */
        Builder(final Context ctx) {
            super(ctx);
        }

        /**
         * A custom {@link ExecutorService} for this SecurityCheckExecutor.
         *
         * @param executorService the {@link ExecutorService} to be used. Defaults to {@link AppExecutors#singleThreadService()} if null
         * @return this
         */
        public Builder withExecutorService(@Nullable final ExecutorService executorService) {
            this.executorService = executorService;
            return this;
        }

        /**
         * Creates a new AsyncSecurityCheckExecutor object.
         *
         * If no {@link ExecutorService} has been defined, defaults to {@link AppExecutors#singleThreadService()}.
         *
         * @return {@link AsyncSecurityCheckExecutor}
         */
        @Override
        public AsyncSecurityCheckExecutor build() {
            if (executorService == null) {
                executorService = new AppExecutors().singleThreadService();
            }
            return new AsyncSecurityCheckExecutor(getCtx(), executorService, getChecks(), getMetricsService());
        }
    }

    /**
     * Constructor for AsyncSecurityCheckExecutor.
     *
     * @param context the {@link Context} to be used by security checks
     * @param executorService the custom {@link ExecutorService}
     * @param checks the {@link Collection<SecurityCheck>} of security checks to be tested
     * @param metricsService {@link MetricsService}. Can be null
     */
    AsyncSecurityCheckExecutor(@NonNull final Context context,
                               @NonNull final ExecutorService executorService,
                               @NonNull final Collection<SecurityCheck> checks,
                               @Nullable final MetricsService metricsService) {
        super(context, checks, metricsService);
        this.executorService = executorService;
    }

    /**
     * Executes the checks asynchronously.
     *
     * Returns a {@link Map} containing the results of each executed test (a {@link Future}).
     * The key of the map will be the output of {@link SecurityCheck#getName()}, while the value will be
     * a {@link Map} of {@link Future} with the {@link SecurityCheckResult} of the check.
     *
     * @param securityCheckExecutorListeners list of listeners that will receive events about checks execution
     * @return {@link Map}
     */
    public Map<String, Future<SecurityCheckResult>> execute(SecurityCheckExecutorListener... securityCheckExecutorListeners) {

        final List<SecurityCheckExecutorListener> listeners;
        final Collection<SecurityCheck> checks = getChecks();

        // Initializes listener list: the metric publisher will be added to passed in listeners
        if (securityCheckExecutorListeners == null) {
            listeners = new ArrayList<>(1);
        } else {
            listeners = new ArrayList(Arrays.asList(securityCheckExecutorListeners));
        }

        listeners.add(getMetricServicePublisher());

        final Map<String, Future<SecurityCheckResult>> res = new HashMap<>();
        final AtomicInteger count = new AtomicInteger(checks.size());

        for (final SecurityCheck check : getChecks()) {
            res.put(check.getName(), (executorService.submit(() -> {
                final SecurityCheckResult result =  check.test(getContext());

                final int remaining = count.decrementAndGet();

                for (SecurityCheckExecutorListener listener : listeners) {
                    listener.onExecuted(result);

                    if (remaining <= 0) {
                        listener.onFinished();
                    }
                }

                return result;
            })));
        }

        return res;
    }
}
