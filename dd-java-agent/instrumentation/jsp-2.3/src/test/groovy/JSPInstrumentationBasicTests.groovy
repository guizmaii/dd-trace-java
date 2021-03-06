import com.google.common.io.Files
import datadog.trace.agent.test.AgentTestRunner
import datadog.trace.agent.test.OkHttpUtils
import datadog.trace.agent.test.TestUtils
import datadog.trace.api.DDSpanTypes
import io.netty.handler.codec.http.HttpResponseStatus
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.Response
import org.apache.catalina.Context
import org.apache.catalina.startup.Tomcat
import org.apache.jasper.JasperException
import spock.lang.Shared
import spock.lang.Unroll

import static datadog.trace.agent.test.ListWriterAssert.assertTraces

class JSPInstrumentationBasicTests extends AgentTestRunner {

  static {
    System.setProperty("dd.integration.jsp.enabled", "true")
    // skip jar scanning using environment variables:
    // http://tomcat.apache.org/tomcat-7.0-doc/config/systemprops.html#JAR_Scanning
    // having this set allows us to test with old versions of the tomcat api since
    // JarScanFilter did not exist in the tomcat 7 api
    System.setProperty("org.apache.catalina.startup.ContextConfig.jarsToSkip", "*")
    System.setProperty("org.apache.catalina.startup.TldConfig.jarsToSkip", "*")
  }

  @Shared
  int port
  @Shared
  Tomcat tomcatServer
  @Shared
  Context appContext
  @Shared
  String jspWebappContext = "jsptest-context"

  @Shared
  File baseDir
  @Shared
  String baseUrl
  @Shared
  String expectedJspClassFilesDir = "/work/Tomcat/localhost/$jspWebappContext/org/apache/jsp/"

  OkHttpClient client = OkHttpUtils.client()

  def setupSpec() {
    port = TestUtils.randomOpenPort()
    tomcatServer = new Tomcat()
    tomcatServer.setPort(port)
    // comment to debug
    tomcatServer.setSilent(true)

    baseDir = Files.createTempDir()
    baseDir.deleteOnExit()
    expectedJspClassFilesDir = baseDir.getCanonicalFile().getAbsolutePath() + expectedJspClassFilesDir
    baseUrl = "http://localhost:$port/$jspWebappContext"
    tomcatServer.setBaseDir(baseDir.getAbsolutePath())

    appContext = tomcatServer.addWebapp("/$jspWebappContext",
      JSPInstrumentationBasicTests.getResource("/webapps/jsptest").getPath())

    tomcatServer.start()
    System.out.println(
      "Tomcat server: http://" + tomcatServer.getHost().getName() + ":" + port + "/")
  }

  def cleanupSpec() {
    tomcatServer.stop()
    tomcatServer.destroy()
  }

  @Unroll
  def "non-erroneous GET #test test"() {
    setup:
    String reqUrl = baseUrl + "/$jspFileName"
    def req = new Request.Builder().url(new URL(reqUrl)).get().build()

    when:
    Response res = client.newCall(req).execute()

    then:
    assertTraces(TEST_WRITER, 1) {
      trace(0, 3) {
        span(0) {
          parent()
          serviceName jspWebappContext
          operationName "servlet.request"
          resourceName "GET /$jspWebappContext/$jspFileName"
          spanType DDSpanTypes.WEB_SERVLET
          errored false
          tags {
            "http.url" "http://localhost:$port/$jspWebappContext/$jspFileName"
            "http.method" "GET"
            "span.kind" "server"
            "component" "java-web-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
            "servlet.context" "/$jspWebappContext"
            "http.status_code" 200
            defaultTags()
          }
        }
        span(1) {
          childOf span(0)
          serviceName jspWebappContext
          operationName "jsp.render"
          resourceName "/$jspFileName"
          spanType DDSpanTypes.WEB_SERVLET
          errored false
          tags {
            "span.kind" "server"
            "component" "jsp-http-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
            "servlet.context" "/$jspWebappContext"
            "jsp.requestURL" reqUrl
            defaultTags()
          }
        }
        span(2) {
          childOf span(0)
          serviceName jspWebappContext
          operationName "jsp.compile"
          resourceName "/$jspFileName"
          spanType DDSpanTypes.WEB_SERVLET
          errored false
          tags {
            "span.kind" "server"
            "component" "jsp-http-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
            "servlet.context" "/$jspWebappContext"
            "jsp.classFQCN" "org.apache.jsp.$jspClassNamePrefix$jspClassName"
            "jsp.compiler" "org.apache.jasper.compiler.JDTCompiler"
            defaultTags()
          }
        }
      }
    }
    res.code() == HttpResponseStatus.OK.code()

    cleanup:
    res.close()

    where:
    test                  | jspFileName         | jspClassName        | jspClassNamePrefix
    "no java jsp"         | "nojava.jsp"        | "nojava_jsp"        | ""
    "basic loop jsp"      | "common/loop.jsp"   | "loop_jsp"          | "common."
    "invalid HTML markup" | "invalidMarkup.jsp" | "invalidMarkup_jsp" | ""
  }

  def "non-erroneous GET with query string"() {
    setup:
    String queryString = "HELLO"
    String reqUrl = baseUrl + "/getQuery.jsp"
    Request req = new Request.Builder().url(new URL(reqUrl + "?" + queryString)).get().build()

    when:
    Response res = client.newCall(req).execute()

    then:
    assertTraces(TEST_WRITER, 1) {
      trace(0, 3) {
        span(0) {
          parent()
          serviceName jspWebappContext
          operationName "servlet.request"
          resourceName "GET /$jspWebappContext/getQuery.jsp"
          spanType DDSpanTypes.WEB_SERVLET
          errored false
          tags {
            "http.url" "http://localhost:$port/$jspWebappContext/getQuery.jsp"
            "http.method" "GET"
            "span.kind" "server"
            "component" "java-web-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
            "servlet.context" "/$jspWebappContext"
            "http.status_code" 200
            defaultTags()
          }
        }
        span(1) {
          childOf span(0)
          serviceName jspWebappContext
          operationName "jsp.render"
          resourceName "/getQuery.jsp"
          spanType DDSpanTypes.WEB_SERVLET
          errored false
          tags {
            "span.kind" "server"
            "component" "jsp-http-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
            "servlet.context" "/$jspWebappContext"
            "jsp.requestURL" reqUrl
            defaultTags()
          }
        }
        span(2) {
          childOf span(0)
          serviceName jspWebappContext
          operationName "jsp.compile"
          resourceName "/getQuery.jsp"
          spanType DDSpanTypes.WEB_SERVLET
          errored false
          tags {
            "span.kind" "server"
            "component" "jsp-http-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
            "servlet.context" "/$jspWebappContext"
            "jsp.classFQCN" "org.apache.jsp.getQuery_jsp"
            "jsp.compiler" "org.apache.jasper.compiler.JDTCompiler"
            defaultTags()
          }
        }
      }
    }
    res.code() == HttpResponseStatus.OK.code()

    cleanup:
    res.close()
  }

  def "non-erroneous POST"() {
    setup:
    String reqUrl = baseUrl + "/post.jsp"
    RequestBody requestBody = new MultipartBody.Builder()
      .setType(MultipartBody.FORM)
      .addFormDataPart("name", "world")
      .build()
    Request req = new Request.Builder().url(new URL(reqUrl)).post(requestBody).build()

    when:
    Response res = client.newCall(req).execute()

    then:
    assertTraces(TEST_WRITER, 1) {
      trace(0, 3) {
        span(0) {
          parent()
          serviceName jspWebappContext
          operationName "servlet.request"
          resourceName "POST /$jspWebappContext/post.jsp"
          spanType DDSpanTypes.WEB_SERVLET
          errored false
          tags {
            "http.url" "http://localhost:$port/$jspWebappContext/post.jsp"
            "http.method" "POST"
            "span.kind" "server"
            "component" "java-web-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
            "servlet.context" "/$jspWebappContext"
            "http.status_code" 200
            defaultTags()
          }
        }
        span(1) {
          childOf span(0)
          serviceName jspWebappContext
          operationName "jsp.render"
          resourceName "/post.jsp"
          spanType DDSpanTypes.WEB_SERVLET
          errored false
          tags {
            "span.kind" "server"
            "component" "jsp-http-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
            "servlet.context" "/$jspWebappContext"
            "jsp.requestURL" reqUrl
            defaultTags()
          }
        }
        span(2) {
          childOf span(0)
          serviceName jspWebappContext
          operationName "jsp.compile"
          resourceName "/post.jsp"
          spanType DDSpanTypes.WEB_SERVLET
          errored false
          tags {
            "span.kind" "server"
            "component" "jsp-http-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
            "servlet.context" "/$jspWebappContext"
            "jsp.classFQCN" "org.apache.jsp.post_jsp"
            "jsp.compiler" "org.apache.jasper.compiler.JDTCompiler"
            defaultTags()
          }
        }
      }
    }
    res.code() == HttpResponseStatus.OK.code()

    cleanup:
    res.close()
  }

  @Unroll
  def "erroneous runtime errors GET jsp with #test test"() {
    setup:
    String reqUrl = baseUrl + "/$jspFileName"
    def req = new Request.Builder().url(new URL(reqUrl)).get().build()

    when:
    Response res = client.newCall(req).execute()

    then:
    assertTraces(TEST_WRITER, 1) {
      trace(0, 3) {
        span(0) {
          parent()
          serviceName jspWebappContext
          operationName "servlet.request"
          resourceName "GET /$jspWebappContext/$jspFileName"
          spanType DDSpanTypes.WEB_SERVLET
          errored true
          tags {
            "http.url" "http://localhost:$port/$jspWebappContext/$jspFileName"
            "http.method" "GET"
            "span.kind" "server"
            "component" "java-web-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
            "servlet.context" "/$jspWebappContext"
            "http.status_code" 500
            errorTags(JasperException, String)
            defaultTags()
          }
        }
        span(1) {
          childOf span(0)
          serviceName jspWebappContext
          operationName "jsp.render"
          resourceName "/$jspFileName"
          spanType DDSpanTypes.WEB_SERVLET
          errored true
          tags {
            "span.kind" "server"
            "component" "jsp-http-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
            "servlet.context" "/$jspWebappContext"
            "jsp.requestURL" reqUrl
            errorTags(exceptionClass, errorMessage)
            defaultTags()
          }
        }
        span(2) {
          childOf span(0)
          serviceName jspWebappContext
          operationName "jsp.compile"
          resourceName "/$jspFileName"
          spanType DDSpanTypes.WEB_SERVLET
          errored false
          tags {
            "span.kind" "server"
            "component" "jsp-http-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
            "servlet.context" "/$jspWebappContext"
            "jsp.classFQCN" "org.apache.jsp.$jspClassName"
            "jsp.compiler" "org.apache.jasper.compiler.JDTCompiler"
            defaultTags()
          }
        }
      }
    }
    res.code() == HttpResponseStatus.INTERNAL_SERVER_ERROR.code()

    cleanup:
    res.close()

    where:
    test                       | jspFileName        | jspClassName       | exceptionClass                  | errorMessage
    "java runtime error"       | "runtimeError.jsp" | "runtimeError_jsp" | ArithmeticException             | String
    "invalid write"            | "invalidWrite.jsp" | "invalidWrite_jsp" | StringIndexOutOfBoundsException | String
    "missing query gives null" | "getQuery.jsp"     | "getQuery_jsp"     | NullPointerException            | null
  }

  def "non-erroneous include plain HTML GET"() {
    setup:
    String reqUrl = baseUrl + "/includes/includeHtml.jsp"
    Request req = new Request.Builder().url(new URL(reqUrl)).get().build()

    when:
    Response res = client.newCall(req).execute()

    then:
    assertTraces(TEST_WRITER, 1) {
      trace(0, 3) {
        span(0) {
          parent()
          serviceName jspWebappContext
          operationName "servlet.request"
          resourceName "GET /$jspWebappContext/includes/includeHtml.jsp"
          spanType DDSpanTypes.WEB_SERVLET
          errored false
          tags {
            "http.url" "http://localhost:$port/$jspWebappContext/includes/includeHtml.jsp"
            "http.method" "GET"
            "span.kind" "server"
            "component" "java-web-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
            "servlet.context" "/$jspWebappContext"
            "http.status_code" 200
            defaultTags()
          }
        }
        span(1) {
          childOf span(0)
          serviceName jspWebappContext
          operationName "jsp.render"
          resourceName "/includes/includeHtml.jsp"
          spanType DDSpanTypes.WEB_SERVLET
          errored false
          tags {
            "span.kind" "server"
            "component" "jsp-http-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
            "servlet.context" "/$jspWebappContext"
            "jsp.requestURL" reqUrl
            defaultTags()
          }
        }
        span(2) {
          childOf span(0)
          serviceName jspWebappContext
          operationName "jsp.compile"
          resourceName "/includes/includeHtml.jsp"
          spanType DDSpanTypes.WEB_SERVLET
          errored false
          tags {
            "span.kind" "server"
            "component" "jsp-http-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
            "servlet.context" "/$jspWebappContext"
            "jsp.classFQCN" "org.apache.jsp.includes.includeHtml_jsp"
            "jsp.compiler" "org.apache.jasper.compiler.JDTCompiler"
            defaultTags()
          }
        }
      }
    }
    res.code() == HttpResponseStatus.OK.code()

    cleanup:
    res.close()
  }

  def "non-erroneous multi GET"() {
    setup:
    String reqUrl = baseUrl + "/includes/includeMulti.jsp"
    Request req = new Request.Builder().url(new URL(reqUrl)).get().build()

    when:
    Response res = client.newCall(req).execute()

    then:
    assertTraces(TEST_WRITER, 1) {
      trace(0, 7) {
        span(0) {
          parent()
          serviceName jspWebappContext
          operationName "servlet.request"
          resourceName "GET /$jspWebappContext/includes/includeMulti.jsp"
          spanType DDSpanTypes.WEB_SERVLET
          errored false
          tags {
            "http.url" "http://localhost:$port/$jspWebappContext/includes/includeMulti.jsp"
            "http.method" "GET"
            "span.kind" "server"
            "component" "java-web-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
            "servlet.context" "/$jspWebappContext"
            "http.status_code" 200
            defaultTags()
          }
        }
        span(1) {
          childOf span(0)
          serviceName jspWebappContext
          operationName "jsp.render"
          resourceName "/includes/includeMulti.jsp"
          spanType DDSpanTypes.WEB_SERVLET
          errored false
          tags {
            "span.kind" "server"
            "component" "jsp-http-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
            "servlet.context" "/$jspWebappContext"
            "jsp.requestURL" reqUrl
            defaultTags()
          }
        }
        span(2) {
          childOf span(1)
          serviceName jspWebappContext
          operationName "jsp.render"
          resourceName "/common/javaLoopH2.jsp"
          spanType DDSpanTypes.WEB_SERVLET
          errored false
          tags {
            "span.kind" "server"
            "component" "jsp-http-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
            "servlet.context" "/$jspWebappContext"
            "jsp.requestURL" reqUrl
            defaultTags()
          }
        }
        span(3) {
          childOf span(1)
          serviceName jspWebappContext
          operationName "jsp.compile"
          resourceName "/common/javaLoopH2.jsp"
          spanType DDSpanTypes.WEB_SERVLET
          errored false
          tags {
            "span.kind" "server"
            "component" "jsp-http-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
            "servlet.context" "/$jspWebappContext"
            "jsp.classFQCN" "org.apache.jsp.common.javaLoopH2_jsp"
            "jsp.compiler" "org.apache.jasper.compiler.JDTCompiler"
            defaultTags()
          }
        }
        span(4) {
          childOf span(1)
          serviceName jspWebappContext
          operationName "jsp.render"
          resourceName "/common/javaLoopH2.jsp"
          spanType DDSpanTypes.WEB_SERVLET
          errored false
          tags {
            "span.kind" "server"
            "component" "jsp-http-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
            "servlet.context" "/$jspWebappContext"
            "jsp.requestURL" reqUrl
            defaultTags()
          }
        }
        span(5) {
          childOf span(1)
          serviceName jspWebappContext
          operationName "jsp.compile"
          resourceName "/common/javaLoopH2.jsp"
          spanType DDSpanTypes.WEB_SERVLET
          errored false
          tags {
            "span.kind" "server"
            "component" "jsp-http-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
            "servlet.context" "/$jspWebappContext"
            "jsp.classFQCN" "org.apache.jsp.common.javaLoopH2_jsp"
            "jsp.compiler" "org.apache.jasper.compiler.JDTCompiler"
            defaultTags()
          }
        }
        span(6) {
          childOf span(0)
          serviceName jspWebappContext
          operationName "jsp.compile"
          resourceName "/includes/includeMulti.jsp"
          spanType DDSpanTypes.WEB_SERVLET
          errored false
          tags {
            "span.kind" "server"
            "component" "jsp-http-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
            "servlet.context" "/$jspWebappContext"
            "jsp.classFQCN" "org.apache.jsp.includes.includeMulti_jsp"
            "jsp.compiler" "org.apache.jasper.compiler.JDTCompiler"
            defaultTags()
          }
        }
      }
    }
    res.code() == HttpResponseStatus.OK.code()

    cleanup:
    res.close()
  }

  def "#test compile error should not produce render traces and spans"() {
    setup:
    String reqUrl = baseUrl + "/$jspFileName"
    Request req = new Request.Builder().url(new URL(reqUrl)).get().build()

    when:
    Response res = client.newCall(req).execute()

    then:
    assertTraces(TEST_WRITER, 1) {
      trace(0, 2) {
        span(0) {
          parent()
          serviceName jspWebappContext
          operationName "servlet.request"
          resourceName "GET /$jspWebappContext/$jspFileName"
          spanType DDSpanTypes.WEB_SERVLET
          errored true
          tags {
            "http.url" "http://localhost:$port/$jspWebappContext/$jspFileName"
            "http.method" "GET"
            "span.kind" "server"
            "component" "java-web-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
            "servlet.context" "/$jspWebappContext"
            "http.status_code" 500
            errorTags(JasperException, String)
            defaultTags()
          }
        }
        span(1) {
          childOf span(0)
          serviceName jspWebappContext
          operationName "jsp.compile"
          resourceName "/$jspFileName"
          spanType DDSpanTypes.WEB_SERVLET
          errored true
          tags {
            "span.kind" "server"
            "component" "jsp-http-servlet"
            "span.type" DDSpanTypes.WEB_SERVLET
            "servlet.context" "/$jspWebappContext"
            "jsp.classFQCN" "org.apache.jsp.$jspClassNamePrefix$jspClassName"
            "jsp.compiler" "org.apache.jasper.compiler.JDTCompiler"
            "jsp.javaFile" expectedJspClassFilesDir + jspClassNamePrefix.replace('.', '/') + jspClassName + ".java"
            "jsp.classpath" String
            errorTags(JasperException, String)
            defaultTags()
          }
        }
      }
    }
    res.code() == HttpResponseStatus.INTERNAL_SERVER_ERROR.code()

    cleanup:
    res.close()

    where:
    test      | jspFileName                            | jspClassName                  | jspClassNamePrefix
    "normal"  | "compileError.jsp"                     | "compileError_jsp"            | ""
    "forward" | "forwards/forwardWithCompileError.jsp" | "forwardWithCompileError_jsp" | "forwards."
  }
}
