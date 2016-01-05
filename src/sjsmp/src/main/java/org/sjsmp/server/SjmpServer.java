package org.sjsmp.server;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.io.UnsupportedEncodingException;
import java.net.BindException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.xml.bind.DatatypeConverter;

import org.json.JSONException;
import org.json.JSONObject;
import org.sjsmp.HttpStatusCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

public final class SjmpServer implements AutoCloseable
{
    private static final int MAX_REQUEST_LENGTH = 1 * 1024 * 1024;
    private static final int MAX_RESPONSE_LENGTH = 1 * 1024 * 1024;

    private HttpServer m_server;

    private final Logger m_logger = LoggerFactory.getLogger(SjmpServer.class);
    private int m_port;
    private final String m_name;
    private final String m_description;
    private final String m_group;
    private URL m_schemaPushUrl;
    private final IServerAuthorization m_auth;

    private String m_schema;
    private long m_schemaVersionNumber = 0;
    private final ReentrantReadWriteLock m_schemaLock = new ReentrantReadWriteLock();
    private final Map<Object, ObjectDescription> m_objects = new HashMap<>();
    private final Map<String, Object> m_objectNames = new HashMap<>();
    private ScheduledExecutorService m_schemaPushExecutor;

    private final int SCHEMA_PUSH_INTERVAL_SECONDS = 1 * 60;

    public static final int PORT_MIN = 40234;
    public static final int PORT_MAX = PORT_MIN + 1000;

    public SjmpServer(
            final String name,
            final String description,
            final String group,
            int startPort,
    		int endPort,
    		final IServerAuthorization auth,
    		final String schemaPushUrl
		) throws IOException
    {
    	m_logger.info("Starting SjmpServer");

        m_name = name;
        m_description = description;
        m_group = group;
        m_auth = auth;

        if (startPort == 0)
        {
            startPort = PORT_MIN;
        };
        if (endPort == 0)
        {
            endPort = PORT_MAX;
        };

        m_port = startPort;

        for (;;)
        {
            RefreshSchema();

            try
            {
                final InetSocketAddress sockAddr = new InetSocketAddress(m_port);
                m_server = HttpServer.create(sockAddr, 0);
                m_server.createContext("/", new HttpQueryHandler());
                m_server.setExecutor(null); // creates a default executor
                m_server.start();
                break;
            }
            catch (BindException e)
            {
                m_server = null;
                m_logger.info("Can not bind to port " + m_port + " (" + e.getMessage() + "), trying next");
            }

            if (++m_port > endPort)
            {
                throw new RuntimeException("Can not find free TCP port in range " + startPort + " - " + endPort + " to bind to");
            }
        }
        m_logger.info("Connected to port {}", m_port);

        if (schemaPushUrl != null)
        {
        	try
        	{
        		m_schemaPushUrl = new URL(schemaPushUrl);
            	m_logger.info("Starting schema push job for url {}", m_schemaPushUrl);
            	m_schemaPushExecutor = Executors.newScheduledThreadPool(1, new NamedThreadFactory("SjmpSchemaPush-"));
            	m_schemaPushExecutor.scheduleAtFixedRate(new SchemaPushRunnable(), SCHEMA_PUSH_INTERVAL_SECONDS / 10, SCHEMA_PUSH_INTERVAL_SECONDS, TimeUnit.SECONDS);
        	}
        	catch (MalformedURLException ex)
        	{
        		m_logger.error("Bad schema push URL '" + ex.getMessage() + "'. Schema push is disabled!", ex);
        	}
        }
    }

    private void schemaPushJob()
    {
        try
        {
            //Create connection
        	HttpURLConnection connection = (HttpURLConnection)m_schemaPushUrl.openConnection();
            connection.setRequestMethod("POST");
            connection.setRequestProperty("Content-Type", "text/json; charset=UTF-8");
            connection.setDoOutput(true);

            //Send request
            try (DataOutputStream wr = new DataOutputStream (connection.getOutputStream()))
            {
                wr.write(m_schema.getBytes("UTF-8"));
            }

            //see http://stackoverflow.com/a/613484/376066
            InputStream inputStream;
            if (connection.getResponseCode() == HttpStatusCode.OK.code)
            {
            	inputStream = connection.getInputStream();
            }
            else
            {
            	inputStream = connection.getErrorStream();
            }

            JSONObject jResp;
            try
            {
            	String content = ReadResponse(inputStream);
            	jResp = new JSONObject(content);
            }
            catch (IOException | JSONException ex)
            {
            	throw new SjmpServerException("Failed to read schema push result", ex);
            }

            if (!jResp.getString("result").equals("ok"))
            {
                throw new SjmpServerException("Schema push result is not ok: '" + jResp.getString("result") + "'; message is '" + jResp.getString("message") + "'");
            }

            if (connection.getResponseCode() != HttpStatusCode.OK.code)
            {
            	throw new SjmpServerException("Schema push http status code is not OK but " + connection.getResponseCode());
            }
        }
        catch (Exception e)
        {
            m_logger.error("Failed to push schema: " + e.getMessage(), e);
        }
    }

    private static String ReadResponse(InputStream responseInputStream) throws IOException
    {
        try (InputStreamReader reader = new InputStreamReader(responseInputStream))
        {
            StringBuilder builder = new StringBuilder();
            char[] buf = new char[4096];
            while (true)
            {
                int readCount = reader.read(buf, 0, buf.length);
                if (readCount < 0)
                {
                	break;
                }
                builder.append(buf, 0, readCount);

                if (builder.length() >= MAX_RESPONSE_LENGTH)
                {
                    throw new IOException("Length of the response it too large");
                }
                if (readCount < buf.length)
                {
                	break;
                }
            }
            return builder.toString();
        }
    }

    private void RequestReceived(final HttpExchange t) throws IOException
    {
        final String requestHostName = t.getRemoteAddress().getHostName();

        if (m_auth != null)
        {
            if (!TryAuth(t, m_auth))
            {
                m_logger.trace("[" + requestHostName + "][error] Auth false, returning Unauthorized");
                t.getResponseHeaders().add("WWW-Authenticate", "Basic realm=\"" + this.m_name.replace("\"", "") + "\"");
                t.sendResponseHeaders(HttpStatusCode.Unauthorized.code, 0);
                t.getResponseBody().close();
                return;
            }
        }

        // Method allowed
        if (!t.getRequestMethod().equals("POST"))
        {
            m_logger.trace("[" + requestHostName + "][error] Wrong request method");
            MakeReponseError(t, "Wrong request method", HttpStatusCode.Forbidden);
            return;
        }

        try
        {
            final String body = ReadStream(t.getRequestBody(), MAX_REQUEST_LENGTH);
            if (body == null || body.trim().equals(""))
            {
            	ProcessSchemaRequest(t);
                return;
            }

            RequestCommandWrapper command = new RequestCommandWrapper(body);
            ProcessServiceRequest(t, command);

        }
        catch (SjmlArgumentException | SjmpServerException ex)
        {
            m_logger.error("[" + requestHostName + "][error] Exception: \n" + ex.getMessage());
            MakeReponseError(t, "exception: " + ex.getMessage(), HttpStatusCode.InternalServerError);
            return;
        }
    }

    private void ProcessSchemaRequest(final HttpExchange t) throws IOException
    {
        MakeResponse(t, HttpStatusCode.OK, m_schema);
    }

    private void ProcessServiceRequest(final HttpExchange t, final RequestCommandWrapper command) throws SjmlArgumentException, IOException, SjmpServerException
    {
        JSONObject responseObject;
        switch (command.action)
        {
        case "get_properties":
            responseObject = ProcessGetProperties(command);
            break;
        case "set_property":
            responseObject = ProcessSetProperty(command);
            break;
        case "execute":
            responseObject = ProcessExecute(command);
            break;
        default:
            throw new RuntimeException("unsupported action '" + command.action + "'");
        }
        MakeResponse(t, HttpStatusCode.OK, responseObject);
    }

    private JSONObject ProcessGetProperties(final RequestCommandWrapper command) throws SjmlArgumentException, SjmpServerException
    {
        assert(command.action.equals("get_properties"));

        final JSONObject ret = new JSONObject();
        ret.put("request_id", command.requestId);
        ret.put("result", "ok");

        final String objectName = command.jObject.has("object_name") ? command.jObject.getString("object_name") : null;
        final String propertyName = command.jObject.has("property_name") ? command.jObject.getString("property_name") : null;

        final JSONObject objects = new JSONObject();

        m_schemaLock.readLock().lock();
        try
        {
            if (objectName == null)
            {
                for (Map.Entry<Object, ObjectDescription> objPair : m_objects.entrySet())
                {
                    final JSONObject obj = new JSONObject();
                    for (Map.Entry<String, PropertyDescription> propPair : objPair.getValue().properties.entrySet())
                    {
                        obj.put(propPair.getKey(), propPair.getValue().GetValue(objPair.getKey()));
                    }
                    objects.put(objPair.getValue().name, obj);
                }
            }
            else
            {
                Object obj;
                if ((obj = m_objectNames.get(objectName)) != null)
                {
                    final ObjectDescription descr = m_objects.get(obj);
                    final JSONObject jObj = new JSONObject();
                    if (propertyName == null)
                    {
                        for (Map.Entry<String, PropertyDescription> pair : descr.properties.entrySet())
                        {
                            jObj.put(pair.getKey(), pair.getValue().GetValue(obj));
                        }
                    }
                    else
                    {
                        PropertyDescription propDescr;
                        if ((propDescr = descr.properties.get(propertyName)) != null)
                        {
                            jObj.put(propDescr.name, propDescr.GetValue(obj));
                        }
                        else
                        {
                            throw new SjmlArgumentException("Unknown property '" + propertyName + "'");
                        }
                    }
                    objects.put(descr.name, jObj);
                }
                else
                {
                    throw new SjmlArgumentException("Unknown object '" + objectName + "'");
                }
            }
        }
        finally
        {
            m_schemaLock.readLock().unlock();
        }

        ret.put("objects", objects);
        return ret;
    }

    private JSONObject ProcessSetProperty(final RequestCommandWrapper command) throws SjmlArgumentException, SjmpServerException
    {
        assert(command.action.equals("set_property"));

        final String objectName = command.jObject.getString("object_name");
        final String propertyName = command.jObject.getString("property_name");
        final Object value = command.jObject.get("value");

        if (objectName == null
            || propertyName == null)
        {
            throw new SjmlArgumentException("You must set object_name and property_name fields");
        }

        m_schemaLock.readLock().lock();
        try
        {
            final Object obj = m_objectNames.get(objectName);
            if (obj == null)
            {
                throw new SjmlArgumentException("Unknown object '" + objectName + "'");
            }

            final ObjectDescription descr = m_objects.get(obj);
            final PropertyDescription propDescr = descr.properties.get(propertyName);
            if (propDescr == null)
            {
                throw new SjmlArgumentException("Unknown property '" + propertyName + "'");
            }

            propDescr.SetValue(obj, value);
        }
        finally
        {
            m_schemaLock.readLock().unlock();
        }

        final JSONObject ret = new JSONObject();
        ret.put("request_id", command.requestId);
        ret.put("result", "ok");
        return ret;
    }

    private JSONObject ProcessExecute(final RequestCommandWrapper command) throws SjmlArgumentException, SjmpServerException
    {
        assert(command.action.equals("execute"));

        final String objectName = command.jObject.getString("object_name");
        final String actionName = command.jObject.getString("action_name");
        final JSONObject parameters = command.jObject.getJSONObject("parameters");

        if (objectName == null
            || actionName == null
            || parameters == null)
        {
            throw new SjmlArgumentException("You must set object_name, action_name and parameters fields");
        }

        final JSONObject ret = new JSONObject();

        m_schemaLock.readLock().lock();
        try
        {
            final Object obj = m_objectNames.get(objectName);
            if (obj == null)
            {
                throw new SjmlArgumentException("Unknown object '" + objectName + "'");
            }

            final ObjectDescription descr = m_objects.get(obj);
			final ActionDescription actionDescr = descr.actions.get(actionName);
            if (actionDescr == null)
            {
                throw new SjmlArgumentException("Unknown action '" + actionName + "'");
            }

            final Object value = actionDescr.Call(obj, parameters);
            ret.put("value", value);
        }
        finally
        {
            m_schemaLock.readLock().unlock();
        }

        ret.put("request_id", command.requestId);
        ret.put("result", "ok");
        return ret;
    }

    private void MakeReponseError(final HttpExchange t, final String message, final HttpStatusCode code) throws IOException
    {
        MakeReponseError(t, "", message, code);
    }

	private void MakeReponseError(final HttpExchange t, final String requestId, final String message, final HttpStatusCode code) throws IOException
	{
        final JSONObject body = new JSONObject();
        body.put("request_id", requestId);
	    body.put("result", "error");
	    body.put("message", message);
	    MakeResponse(t, code, body);
	}

	private void MakeResponse(final HttpExchange t, final HttpStatusCode code, final JSONObject body) throws IOException
	{
	    final String bodyText = body.toString();
	    MakeResponse(t, code, bodyText);
	}

	private void MakeResponse(final HttpExchange t, final HttpStatusCode code, final String bodyText) throws IOException
	{
        t.getResponseHeaders().add("Content-Type", "text/json; charset=UTF-8");
		t.sendResponseHeaders(code.code, 0);

        try (final OutputStream os = t.getResponseBody())
        {
            os.write(bodyText.getBytes("UTF-8"));
        }
	}

	public void RegisterObject(final Object obj) throws SjmpServerException
	{
		RegisterObject(obj, obj.getClass().getSimpleName());
	}

	public void RegisterObject(final Object obj, final String description) throws SjmpServerException
	{
		RegisterObject(obj, obj.getClass().getSimpleName(), description, "", true);
	}

	public void RegisterObject(final Object obj, final String name, final String description) throws SjmpServerException
	{
		RegisterObject(obj, name, description, "", true);
	}

	public void RegisterObject(final Object obj, final String name, final String description, final String group, final boolean immediatePushSchema) throws SjmpServerException
    {
        final ObjectDescription descr = new ObjectDescription(obj, name, description, group);
        m_schemaLock.writeLock().lock();
        try
        {
            if (m_objects.containsKey(obj) || m_objectNames.containsKey(name))
            {
                throw new SjmpServerException("object already registered: " + obj + ", name '" + name + "'");
            }
            m_objects.put(obj, descr);
            m_objectNames.put(name, obj);

            RefreshSchema();
        }
        finally
        {
            m_schemaLock.writeLock().unlock();
        }

        if (this.m_schemaPushUrl != null && immediatePushSchema)
        {
        	m_schemaPushExecutor.schedule(new SchemaPushRunnable(), 0, TimeUnit.SECONDS);
        }
    }

	public boolean UnRegisterObject(final Object obj) throws SjmpServerException
	{
		return UnRegisterObject(obj, true);
	}

    public boolean UnRegisterObject(final Object obj, final boolean immediatePushSchema) throws SjmpServerException
    {
    	boolean removed;
        m_schemaLock.writeLock().lock();
        try
        {
            ObjectDescription descr;
            if ((descr = m_objects.get(obj)) == null)
            {
                throw new SjmpServerException("Object '" + obj + "' not found");
            }

            removed = m_objects.remove(obj) != null;
            boolean removedName = m_objectNames.remove(descr.name) != null;
            if (removedName != removed)
            {
                throw new SjmpServerException("Wrong internal state: object names '" + descr.name + "' not found in name index");
            }

            RefreshSchema();
        }
        finally
        {
            m_schemaLock.writeLock().unlock();
        }

        if (this.m_schemaPushUrl != null && immediatePushSchema)
        {
        	m_schemaPushExecutor.schedule(new SchemaPushRunnable(), 0, TimeUnit.SECONDS);
        }
        return removed;
    }

    private void RefreshSchema()
    {
        ++m_schemaVersionNumber;
        final long unixTimestamp = LocalDateTime.now().toEpochSecond(ZoneOffset.UTC);

        JSONObject jsonObject = new JSONObject();
        jsonObject.put("result", "ok");
        jsonObject.put("type", "SimpleJMP/schema");
        jsonObject.put("version", "1.0");
        jsonObject.put("name", m_name);
        jsonObject.put("description", m_description);
        jsonObject.put("group", m_group);
        jsonObject.put("port", m_port);
        jsonObject.put("schema_version", String.format("%d.%d", unixTimestamp, m_schemaVersionNumber));

        final JSONObject objects = new JSONObject();
        for (ObjectDescription descr : m_objects.values())
        {
            objects.put(descr.name, descr.ToJSONObject());
        }
        jsonObject.put("objects", objects);

        m_schema = jsonObject.toString();
    }

    public static String ReadStream(final InputStream is, final int maxSize)
    {
        final char[] buffer = new char[4096];
        final StringBuilder out = new StringBuilder();
        try (Reader in = new InputStreamReader(is, "UTF-8"))
        {
        	int copied = 0;
            for (;;)
            {
                int rsz = in.read(buffer, 0, buffer.length);
                copied += rsz;
                if (copied > maxSize)
                {
                	return "";
                }

                if (rsz < 0)
                {
                    break;
                }
                out.append(buffer, 0, rsz);
            }
        }
        catch (UnsupportedEncodingException ex)
        {
        }
        catch (IOException ex)
        {
        }
        return out.toString();
    }

    private static boolean TryAuth(final HttpExchange t, final IServerAuthorization authorization)
    {
        final Headers headers = t.getRequestHeaders();

        final List<String> headersAuth = headers.get("Authorization");
        final String headerAuth;
        if (headersAuth == null || headersAuth.size() != 1 || (headerAuth = headersAuth.get(0)) == null)
        {
            //m_logger.Trace("[" + request.UserHostAddress + "][false] No 'Authorization' header");
            return false;
        }

        final byte[] tempConverted = DatatypeConverter.parseBase64Binary(headerAuth.replace("Basic ", "").trim());
        final String userInfo;
        try
        {
            userInfo = new String(tempConverted, "UTF-8");
        }
        catch (UnsupportedEncodingException e)
        {
            throw new RuntimeException("UnsupportedEncodingException - should not happen", e);
        }

        final String[] usernamePassword = userInfo.split(":");
        if (usernamePassword.length != 2)
        {
            //m_logger.Trace("[" + request.UserHostAddress + "][false] Can not split usernamePassword");
            return false;
        }

        final String username = usernamePassword[0].trim();
        final String password = usernamePassword[1].trim();

        if (username.equals("") || password.equals(""))
        {
            //m_logger.Trace("[" + request.UserHostAddress + "][false] Username or password is empty");
            return false;
        }

        boolean result = authorization.CheckAccess(username, password);
        //m_logger.trace("[" + request.UserHostAddress + "][" + result + "] User '" + username + "' auth");
        return result;
    }

    @Override
    public void close()
    {
    	if (m_schemaPushExecutor != null)
    	{
    		m_schemaPushExecutor.shutdown();
    		m_schemaPushExecutor = null;
    	}
        if (m_server != null)
        {
            m_server.stop(0);
            m_server = null;
        }
        m_logger.info("SjmpServer stopped");
    }

    private final class HttpQueryHandler implements HttpHandler
    {
        @Override
        public void handle(HttpExchange t) throws IOException
        {
        	SjmpServer.this.RequestReceived(t);
        }
    }

    private final class SchemaPushRunnable implements Runnable
    {
		@Override
		public void run()
		{
			schemaPushJob();
		}
    }
}
