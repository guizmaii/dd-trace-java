import datadog.opentracing.DDSpan
import datadog.opentracing.scopemanager.ContinuableScope
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.api.Trace
import io.opentracing.util.GlobalTracer
import spock.lang.Shared

import java.lang.reflect.Method
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.Callable
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.Future
import java.util.concurrent.RejectedExecutionException
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit

class ExecutorInstrumentationTest extends AgentTestRunner {
  @Shared
  Method submitMethod
  @Shared
  Method executeMethod

  static {
    System.setProperty("dd.integration.java_concurrent.enabled", "true")
  }

  def setupSpec() {
    executeMethod = Executor.getMethod("execute", Runnable)
    submitMethod = ExecutorService.getMethod("submit", Callable)
  }

  // more useful name breaks java9 javac
  // def "#poolImpl.getClass().getSimpleName() #method.getName() propagates"()
  def "#poolImpl #method propagates"() {
    setup:
    def pool = poolImpl
    def m = method

    new Runnable() {
      @Override
      @Trace(operationName = "parent")
      void run() {
        ((ContinuableScope) GlobalTracer.get().scopeManager().active()).setAsyncPropagation(true)
        // this child will have a span
        m.invoke(pool, new AsyncChild())
        // this child won't
        m.invoke(pool, new AsyncChild(false, false))
      }
    }.run()

    TEST_WRITER.waitForTraces(1)
    List<DDSpan> trace = TEST_WRITER.get(0)

    expect:
    TEST_WRITER.size() == 1
    trace.size() == 2
    trace.get(0).operationName == "parent"
    trace.get(1).operationName == "asyncChild"
    trace.get(1).parentId == trace.get(0).spanId

    cleanup:
    pool?.shutdown()

    // Unfortunately, there's no simple way to test the cross product of methods/pools.
    where:
    poolImpl                                                                                      | method
    new ForkJoinPool()                                                                            | submitMethod
    new ForkJoinPool()                                                                            | executeMethod
    new ThreadPoolExecutor(1, 1, 1000, TimeUnit.NANOSECONDS, new ArrayBlockingQueue<Runnable>(1)) | submitMethod
    new ThreadPoolExecutor(1, 1, 1000, TimeUnit.NANOSECONDS, new ArrayBlockingQueue<Runnable>(1)) | executeMethod
    new ScheduledThreadPoolExecutor(1)                                                            | submitMethod
    new ScheduledThreadPoolExecutor(1)                                                            | executeMethod
  }

  // more useful name breaks java9 javac
  // def "#poolImpl.getClass().getSimpleName() #method.getName() propagates"()
  def "#poolImpl reports after canceled jobs"() {
    setup:
    def pool = poolImpl
    final AsyncChild child = new AsyncChild(true, true)
    List<Future> jobFutures = new ArrayList<Future>()

    new Runnable() {
      @Override
      @Trace(operationName = "parent")
      void run() {
        ((ContinuableScope) GlobalTracer.get().scopeManager().active()).setAsyncPropagation(true)
        try {
          for (int i = 0; i < 20; ++i) {
            Future f = pool.submit((Callable) child)
            jobFutures.add(f)
          }
        } catch (RejectedExecutionException e) {
        }

        for (Future f : jobFutures) {
          f.cancel(false)
        }
        child.unblock()
      }
    }.run()

    TEST_WRITER.waitForTraces(1)

    expect:
    TEST_WRITER.size() == 1

    where:
    poolImpl                                                                                      | _
    new ForkJoinPool()                                                                            | _
    new ThreadPoolExecutor(1, 1, 1000, TimeUnit.NANOSECONDS, new ArrayBlockingQueue<Runnable>(1)) | _
    new ScheduledThreadPoolExecutor(1)                                                            | _
  }
}
