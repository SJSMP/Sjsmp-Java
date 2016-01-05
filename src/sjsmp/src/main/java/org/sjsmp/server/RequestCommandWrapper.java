package org.sjsmp.server;

import org.json.JSONObject;

final class RequestCommandWrapper
{
	public final String requestId;
	public final String action;
    
    public final JSONObject jObject;
	
	public RequestCommandWrapper(final String body)
	{
        jObject = new JSONObject(body);
        requestId = jObject.getString("request_id");
        action = jObject.getString("action");
	}
}

