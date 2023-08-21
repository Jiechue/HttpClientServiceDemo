import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;

@Component(service = FormsHttpClientService.class, immediate = true)
@Designate(ocd = FormsHttpClientServiceImpl.Config.class)
public class HttpClientService implements FormsHttpClientService {

    @ObjectClassDefinition(name = "AEM HTTP Client Service Configuration")
    public @interface Config {

        @AttributeDefinition(
                name = "Max Connect",
                description = "Maximum number of concurrent connections",
                type = AttributeType.INTEGER )
        int max_total() default 640;

        @AttributeDefinition(
                name = "Max Per Route",
                description = "Maximum number of connections per ip address",
                type = AttributeType.INTEGER )
        int max_route() default 16;

        @AttributeDefinition(
                name = "Connect Timeout",
                description = "The timeout in milliseconds until a connection is established",
                type = AttributeType.INTEGER )
        int connect_timeout() default 5000;

        @AttributeDefinition(
                name = "Connection Request Timeout",
                description = "The timeout in milliseconds used when requesting a connection from the connection manager",
                type = AttributeType.INTEGER )
        int request_timeout() default 5000;

        @AttributeDefinition(
                name = "Socket Timeout",
                description = "The socket timeout (SO_TIMEOUT) in milliseconds, which is the timeout for waiting for data or, put differently, a maximum period inactivity between two consecutive data packets)",
                type = AttributeType.INTEGER )
        int socket_timeout() default 5000;

        @AttributeDefinition(
                name = "Retry Count",
                description = "Connect retry count when connect timeout",
                type = AttributeType.INTEGER )
        int retry_count() default 2;

        @AttributeDefinition(
                name = "Request Sent Retry Enabled",
                description = "Request sent retry enabled",
                type = AttributeType.BOOLEAN )
        boolean retry_enabled() default false;

    }

    private static final Logger LOGGER = LoggerFactory.getLogger(FormsHttpClientServiceImpl.class);

    // 创建私有静态变量池化管理poolConnManager
    private static PoolingHttpClientConnectionManager poolConnManager = null;

    private CloseableHttpClient httpClient;

    @Activate
    @Modified
    protected void activate(FormsHttpClientServiceImpl.Config config) {
        if (httpClient != null) {
            destroy();
        }
        try {
            poolConnManager = getPoolConnManager(getSocketFactoryRegistry(),config.max_total(),config.max_route());
            RequestConfig requestConfig = RequestConfig.custom().setConnectTimeout(config.connect_timeout()).setConnectionRequestTimeout(config.request_timeout()).setSocketTimeout(config.socket_timeout()).build();
            httpClient = HttpClients.custom()
                    // 设置连接池管理
                    .setConnectionManager(poolConnManager)
                    .setDefaultRequestConfig(requestConfig)
                    // 设置重试次数
                    .setRetryHandler(new DefaultHttpRequestRetryHandler(config.retry_count(), config.retry_enabled())).build();
        } catch (KeyManagementException | NoSuchAlgorithmException | KeyStoreException e) {
            LOGGER.error("", e);
        }
    }

    @Override
    public CloseableHttpClient getHttpClient() {
        return httpClient;
    }

    @Deactivate
    private void deactivate() {
        destroy();
    }

    private Registry<ConnectionSocketFactory> getSocketFactoryRegistry() throws NoSuchAlgorithmException, KeyStoreException, KeyManagementException {
        //配置SSL连接协议
        SSLContext sslContext = new SSLContextBuilder().loadTrustMaterial(null, (chain, authType) -> true).build();
        HostnameVerifier hostnameVerifier = NoopHostnameVerifier.INSTANCE;
        //创建socket连接池工厂
        SSLConnectionSocketFactory sslsf = new SSLConnectionSocketFactory(sslContext,  hostnameVerifier);
        return RegistryBuilder.<ConnectionSocketFactory>create()
                .register("http", PlainConnectionSocketFactory.getSocketFactory()).register("https", sslsf).build();
    }

    private PoolingHttpClientConnectionManager getPoolConnManager(Registry<ConnectionSocketFactory> socketFactoryRegistry,int maxTotal,int maxRoute){
        PoolingHttpClientConnectionManager poolConnManager = new PoolingHttpClientConnectionManager(socketFactoryRegistry);
        // 同时最多连接数
        poolConnManager.setMaxTotal(maxTotal);
        // 设置最大路由
        poolConnManager.setDefaultMaxPerRoute(maxRoute);

        return poolConnManager;
    }

    private void destroy() {
        if (httpClient != null) {
            try {
                httpClient.close();
            } catch (IOException e) {
                LOGGER.error("", e);
            }
        }
    }

}
