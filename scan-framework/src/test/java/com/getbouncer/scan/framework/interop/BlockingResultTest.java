package com.getbouncer.scan.framework.interop;

import androidx.test.filters.MediumTest;

import com.getbouncer.scan.framework.AggregateResultListener;
import com.getbouncer.scan.framework.ResultAggregator;
import com.getbouncer.scan.framework.ResultAggregatorConfig;
import com.getbouncer.scan.framework.ResultHandler;
import com.getbouncer.scan.framework.SavedFrame;
import com.getbouncer.scan.framework.StatefulResultHandler;
import com.getbouncer.scan.framework.TerminatingResultHandler;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import kotlin.Pair;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import kotlin.jvm.functions.Function0;
import kotlin.jvm.functions.Function2;
import kotlinx.coroutines.BuildersKt;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.CoroutineStart;
import kotlinx.coroutines.Deferred;
import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.GlobalScope;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class BlockingResultTest {

    private static class TerminatingTestResult {
        public boolean handledResult = false;
        public boolean handledAllResults = false;
        public boolean terminatedEarly = false;
    }

    private static class AggregateTestResult {
        public boolean handledInterim = false;
        public boolean handledFinal = false;
        public boolean handledReset = false;
    }

    @Test(timeout = 1000)
    @MediumTest
    public void blockingResultHandler_works() throws InterruptedException {
        final ResultHandler<Integer, Integer, Boolean> resultHandler =
            new BlockingResultHandler<Integer, Integer, Boolean>() {
                @Override
                public Boolean onResultBlocking(Integer result, Integer data) {
                    return result != null && result.equals(data);
                }
            };

        final Deferred<Boolean> deferred = BuildersKt.async(
            GlobalScope.INSTANCE,
            Dispatchers.getDefault(),
            CoroutineStart.DEFAULT,
            new Function2<CoroutineScope, Continuation<? super Boolean>, Object>() {
                @Override
                public Object invoke(CoroutineScope coroutineScope, Continuation<? super Boolean> continuation) {
                    return resultHandler.onResult(2, 2, continuation);
                }
            }
        );

        while (!deferred.isCompleted()) {
            Thread.sleep(100);
        }

        assertTrue(deferred.getCompleted());
    }

    @Test(timeout = 1000)
    @MediumTest
    public void blockingStatefulResultHandler_works() throws InterruptedException {
        final StatefulResultHandler<Integer, Boolean, Integer, Boolean> resultHandler =
            new BlockingStatefulResultHandler<Integer, Boolean, Integer, Boolean>(true) {
                @Override
                public Boolean onResultBlocking(Integer result, Integer data) {
                    return result != null && result.equals(data) && getState();
                }
            };

        final Deferred<Boolean> deferred = BuildersKt.async(
            GlobalScope.INSTANCE,
            Dispatchers.getDefault(),
            CoroutineStart.DEFAULT,
            new Function2<CoroutineScope, Continuation<? super Boolean>, Object>() {
                @Override
                public Object invoke(CoroutineScope coroutineScope, Continuation<? super Boolean> continuation) {
                    return resultHandler.onResult(2, 2, continuation);
                }
            }
        );

        while (!deferred.isCompleted()) {
            Thread.sleep(100);
        }

        assertTrue(deferred.getCompleted());
    }

    @Test(timeout = 1000)
    @MediumTest
    public void blockingTerminatingResultHandler_works() throws InterruptedException {
        final TerminatingTestResult testResult = new TerminatingTestResult();

        final TerminatingResultHandler<Integer, Boolean, Integer> resultHandler =
            new BlockingTerminatingResultHandler<Integer, Boolean, Integer>(true) {
                @Override
                public void onAllDataProcessedBlocking() {
                    testResult.handledAllResults = true;
                }

                @Override
                public void onTerminatedEarlyBlocking() {
                    testResult.terminatedEarly = true;
                }

                @Override
                public void onResultBlocking(Integer result, Integer data) {
                    testResult.handledResult = true;
                }
            };

        final Deferred<Unit> ranAllDataProcessed = BuildersKt.async(
            GlobalScope.INSTANCE,
            Dispatchers.getDefault(),
            CoroutineStart.DEFAULT,
            new Function2<CoroutineScope, Continuation<? super Unit>, Object>() {
                @Override
                public Object invoke(CoroutineScope coroutineScope, Continuation<? super Unit> continuation) {
                    return resultHandler.onAllDataProcessed(continuation);
                }
            }
        );

        final Deferred<Unit> ranTerminatedEarly = BuildersKt.async(
            GlobalScope.INSTANCE,
            Dispatchers.getDefault(),
            CoroutineStart.DEFAULT,
            new Function2<CoroutineScope, Continuation<? super Unit>, Object>() {
                @Override
                public Object invoke(CoroutineScope coroutineScope, Continuation<? super Unit> continuation) {
                    return resultHandler.onTerminatedEarly(continuation);
                }
            }
        );

        final Deferred<Unit> ranResult = BuildersKt.async(
            GlobalScope.INSTANCE,
            Dispatchers.getDefault(),
            CoroutineStart.DEFAULT,
            new Function2<CoroutineScope, Continuation<? super Unit>, Object>() {
                @Override
                public Object invoke(CoroutineScope coroutineScope, Continuation<? super Unit> continuation) {
                    return resultHandler.onResult(2, 2, continuation);
                }
            }
        );

        while (!testResult.handledResult ||
            !testResult.handledAllResults ||
            !testResult.terminatedEarly ||
            !ranAllDataProcessed.isCompleted() ||
            !ranTerminatedEarly.isCompleted() ||
            !ranResult.isCompleted()
        ) {
            Thread.sleep(100);
        }
    }

    @Test(timeout = 1000L)
    @MediumTest
    public void blockingAggregateResultListener_works() throws InterruptedException {
        final AggregateTestResult testResult = new AggregateTestResult();

        final AggregateResultListener<Integer, Boolean, Integer, Boolean> resultListener =
            new BlockingAggregateResultListener<Integer, Boolean, Integer, Boolean>() {
                @Override
                public void onInterimResultBlocking(Integer result, Boolean state, Integer frame) {
                    testResult.handledInterim = true;
                }

                @Override
                public void onResultBlocking(
                    Boolean result,
                    @NotNull Map<String, ? extends List<SavedFrame<Integer, Boolean, Integer>>> frames
                ) {
                    testResult.handledFinal = true;
                }

                @Override
                public void onResetBlocking() {
                    testResult.handledReset = true;
                }
            };

        final Deferred<Unit> deferredInterim = BuildersKt.async(
            GlobalScope.INSTANCE,
            Dispatchers.getDefault(),
            CoroutineStart.DEFAULT,
            new Function2<CoroutineScope, Continuation<? super Unit>, Object>() {
                @Override
                public Object invoke(CoroutineScope coroutineScope, Continuation<? super Unit> continuation) {
                    return resultListener.onInterimResult(1, true, 2, continuation);
                }
            }
        );

        final Deferred<Unit> deferredResult = BuildersKt.async(
            GlobalScope.INSTANCE,
            Dispatchers.getDefault(),
            CoroutineStart.DEFAULT,
            new Function2<CoroutineScope, Continuation<? super Unit>, Object>() {
                @Override
                public Object invoke(CoroutineScope coroutineScope, Continuation<? super Unit> continuation) {
                    return resultListener.onResult(
                        true,
                        Collections.<String, List<SavedFrame<Integer, Boolean, Integer>>>emptyMap(),
                        continuation
                    );
                }
            }
        );

        final Deferred<Unit> deferredReset = BuildersKt.async(
            GlobalScope.INSTANCE,
            Dispatchers.getDefault(),
            CoroutineStart.DEFAULT,
            new Function2<CoroutineScope, Continuation<? super Unit>, Object>() {
                @Override
                public Object invoke(CoroutineScope coroutineScope, Continuation<? super Unit> continuation) {
                    return resultListener.onReset(continuation);
                }
            }
        );

        while (!testResult.handledInterim ||
            !testResult.handledFinal ||
            !testResult.handledReset ||
            !deferredInterim.isCompleted() ||
            !deferredResult.isCompleted() ||
            !deferredReset.isCompleted()
        ) {
            Thread.sleep(100);
        }
    }

    @Test(timeout = 1000L)
    @MediumTest
    public void blockingResultAggregator_works() throws InterruptedException {
        final ResultAggregatorConfig config = new ResultAggregatorConfig.Builder().build();

        final AggregateResultListener<Integer, Boolean, Integer, Boolean> listener =
            new BlockingAggregateResultListener<Integer, Boolean, Integer, Boolean>() {
                @Override
                public void onInterimResultBlocking(Integer result, Boolean state, Integer frame) { }

                @Override
                public void onResultBlocking(
                    Boolean result,
                    @NotNull Map<String, ? extends List<SavedFrame<Integer, Boolean, Integer>>> frames
                ) { }

                @Override
                public void onResetBlocking() { }
            };

        final ResultAggregator<Integer, Boolean, Integer, Integer, Boolean> resultAggregator =
            new BlockingResultAggregator<Integer, Boolean, Integer, Integer, Boolean>(config, listener, true) {
                @Override
                public int getFrameSizeBytes(Integer frame) {
                    return 0;
                }

                @Nullable
                @Override
                public String getSaveFrameIdentifier(Integer result, Integer frame) {
                    return null;
                }

                @NotNull
                @Override
                public Pair<Integer, Boolean> aggregateResultBlocking(
                    Integer result,
                    @NotNull Function0<Unit> startAggregationTimer,
                    boolean mustReturnFinal
                ) {
                    return new Pair<>(1, true);
                }

                @NotNull
                @Override
                protected String getName() {
                    return "test_blocking_result_aggregator";
                }
            };

        final Deferred<Pair<? extends Integer, ? extends Boolean>> deferred = BuildersKt.async(
            GlobalScope.INSTANCE,
            Dispatchers.getDefault(),
            CoroutineStart.DEFAULT,
            new Function2<CoroutineScope, Continuation<? super Pair<? extends Integer, ? extends Boolean>>, Object>() {
                @Override
                public Object invoke(CoroutineScope coroutineScope, Continuation<? super Pair<? extends Integer, ? extends Boolean>> continuation) {
                    return resultAggregator.aggregateResult(
                        1,
                        new Function0<Unit>() {
                            @Override
                            public Unit invoke() {
                                return Unit.INSTANCE;
                            }
                        },
                        true,
                        continuation
                    );
                }
            }
        );

        while (!deferred.isCompleted()) {
            Thread.sleep(100);
        }

        assertEquals(1, (int) deferred.getCompleted().getFirst());
        assertTrue(deferred.getCompleted().getSecond());
    }
}
