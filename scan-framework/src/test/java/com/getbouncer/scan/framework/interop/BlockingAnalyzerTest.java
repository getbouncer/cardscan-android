package com.getbouncer.scan.framework.interop;

import androidx.test.filters.MediumTest;

import com.getbouncer.scan.framework.Analyzer;
import com.getbouncer.scan.framework.AnalyzerFactory;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;
import org.junit.Test;

import kotlin.coroutines.Continuation;
import kotlin.jvm.functions.Function2;
import kotlinx.coroutines.BuildersKt;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.CoroutineStart;
import kotlinx.coroutines.Deferred;
import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.GlobalScope;

public class BlockingAnalyzerTest {

    @Test(timeout = 1000)
    @MediumTest
    public void blockingAnalyzer_works() throws InterruptedException {
        final Analyzer<Integer, Boolean, Boolean> analyzer = new BlockingAnalyzer<Integer, Boolean, Boolean>() {
            @NotNull
            @Override
            public String getName() {
                return "test_blocking_analyzer";
            }

            @Override
            public Boolean analyzeBlocking(Integer data, Boolean state) {
                return data > 0 && state;
            }
        };

        final Deferred<Boolean> deferred = BuildersKt.async(
            GlobalScope.INSTANCE,
            Dispatchers.getDefault(),
            CoroutineStart.DEFAULT,
            new Function2<CoroutineScope, Continuation<? super Boolean>, Object>() {
                @Override
                public Object invoke(CoroutineScope coroutineScope, Continuation<? super Boolean> continuation) {
                    return analyzer.analyze(1, true, continuation);
                }
            }
        );

        while (!deferred.isCompleted()) {
            Thread.sleep(100);
        }

        Assert.assertTrue(deferred.getCompleted());
    }

    @Test(timeout = 1000)
    @MediumTest
    public void blockingAnalyzerFactory_works() throws InterruptedException {
        final AnalyzerFactory<Analyzer<Integer, Boolean, Boolean>> factory =
            new BlockingAnalyzerFactory<Analyzer<Integer, Boolean, Boolean>>() {
                @Override
                public Analyzer<Integer, Boolean, Boolean> newInstanceBlocking() {
                    return new Analyzer<Integer, Boolean, Boolean>() {
                        @NotNull
                        @Override
                        public String getName() {
                            return "test_analyzer";
                        }

                        @Nullable
                        @Override
                        public Object analyze(
                            Integer data,
                            Boolean state,
                            @NotNull Continuation<? super Boolean> $completion
                        ) {
                            return null;
                        }
                    };
                }
            };

        final Deferred<Analyzer<Integer, Boolean, Boolean>> deferred = BuildersKt.async(
            GlobalScope.INSTANCE,
            Dispatchers.getDefault(),
            CoroutineStart.DEFAULT,
            new Function2<CoroutineScope, Continuation<? super Analyzer<Integer, Boolean, Boolean>>, Object>() {
                @Override
                public Object invoke(
                    CoroutineScope coroutineScope,
                    Continuation<? super Analyzer<Integer, Boolean, Boolean>> continuation
                ) {
                    return factory.newInstance(continuation);
                }
            }
        );

        while (!deferred.isCompleted()) {
            Thread.sleep(100);
        }

        Assert.assertNotNull(deferred.getCompleted());
    }

    @Test
    @MediumTest
    public void blockingAnalyzerPoolFactory_works() {
        final AnalyzerFactory<Analyzer<Integer, Boolean, Boolean>> factory =
            new BlockingAnalyzerFactory<Analyzer<Integer, Boolean, Boolean>>() {
                @Override
                public Analyzer<Integer, Boolean, Boolean> newInstanceBlocking() {
                    return new Analyzer<Integer, Boolean, Boolean>() {
                        @NotNull
                        @Override
                        public String getName() {
                            return "test_analyzer";
                        }

                        @Nullable
                        @Override
                        public Object analyze(
                            Integer data,
                            Boolean state,
                            @NotNull Continuation<? super Boolean> $completion
                        ) {
                            return null;
                        }
                    };
                }
            };

        final BlockingAnalyzerPoolFactory<Integer, Boolean, Boolean> poolFactory =
                new BlockingAnalyzerPoolFactory<>(factory);

        Assert.assertNotNull(poolFactory.buildAnalyzerPool());
    }
}
