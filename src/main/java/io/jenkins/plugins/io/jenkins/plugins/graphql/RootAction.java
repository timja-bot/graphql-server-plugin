package io.jenkins.plugins.io.jenkins.plugins.graphql;

import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import hudson.Extension;
import hudson.init.InitMilestone;
import hudson.init.Initializer;
import hudson.model.Actionable;
import hudson.model.User;
import jenkins.model.Jenkins;
import net.sf.json.JSONArray;
import net.sf.json.JSONNull;
import net.sf.json.JSONObject;
import org.apache.commons.io.IOUtils;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.StaplerResponse;

import javax.annotation.CheckForNull;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

@Extension
@SuppressWarnings("unused")
public class RootAction extends Actionable implements hudson.model.RootAction {
    private final static Logger LOGGER = Logger.getLogger(RootAction.class.getName());
    private static GraphQL builtSchema;
    private static GraphQLSchemaGenerator graphQLSchemaGenerator;

    @CheckForNull
    @Override
    public String getIconFileName() {
        return null;
    }

    @CheckForNull
    @Override
    public String getDisplayName() {
        return null;
    }

    @CheckForNull
    @Override
    public String getUrlName() {
        return "graphql";
    }

    @Initializer(after = InitMilestone.JOB_LOADED)
    public static void init() {
        graphQLSchemaGenerator = new GraphQLSchemaGenerator();
        builtSchema = graphQLSchemaGenerator.generateSchema();
    }

    public static String optString(JSONObject json, String key, String defaultValue)
    {
        // http://code.google.com/p/android/issues/detail?id=13830
        if (json.opt(key) instanceof JSONNull)
            return defaultValue;
        else
            return json.optString(key, defaultValue);
    }

    @SuppressWarnings("unused")
    // @RequirePOST
    public void doIndex(StaplerRequest req, StaplerResponse rsp) throws IOException {
        HashMap<String, Object> variables = new HashMap<>();
        HashMap<String, Object> context = new HashMap<>();
        String query = "";
        String operationName = "";
        Map<String, String[]> parameterMap = req.getParameterMap();

        String body = IOUtils.toString(req.getInputStream(), "UTF-8");
        LOGGER.info("Body: " + body);

        String id = req.getHeader("Authorization");
        if (id != null || !id.isEmpty()) {
            // FIXME - security is currently Bearer <username>
            context.put("user", User.get(id.replace("Bearer ", ""), false, Collections.emptyMap()));
        } else {
            context.put("user", User.current());
        }

        if ("application/graphql".equals(req.getContentType())) {
            query = body;
        } else if (parameterMap.size() > 0) {
            query = parameterMap.get("query")[0];
            operationName = parameterMap.get("operationName")[0];
            // FIXME - variables
        } else if (!body.isEmpty()) {
            JSONObject jsonRequest = JSONObject.fromObject(body);
            query = optString(jsonRequest, "query", "");
            operationName = optString(jsonRequest, "operationName", "");
            JSONObject jsonVariables = jsonRequest.optJSONObject("variables");
            if (jsonVariables != null) {
                variables = (HashMap<String, Object>) jsonVariables.toBean(HashMap.class);
            }
        }

        if (query.isEmpty()) {
            rsp.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        ServletOutputStream outputStream = rsp.getOutputStream();
        OutputStreamWriter osw = new OutputStreamWriter(outputStream, "UTF-8");
        rsp.setContentType("application/json");
        // rsp.setHeader("Access-Control-Allow-Origin", "*");
        try {
            ExecutionInput executionInput = ExecutionInput
                .newExecutionInput()
                .query(query)
                .operationName(operationName)
                .context(context)
                .variables(variables)
                .build();

            ExecutionResult executionResult = builtSchema.execute(executionInput);
            osw.write(JSONObject.fromObject(executionResult.toSpecification()).toString());
        } catch (graphql.execution.UnknownOperationException e) {
            JSONObject errResp = new JSONObject();
            errResp.put("data", new JSONObject());
            JSONObject errJsonObject = new JSONObject();
            errJsonObject.put("message", e.getMessage());
            errJsonObject.put("locations", null);
            errJsonObject.put("errorType", e.getClass().getSimpleName());

            JSONArray errorsList = new JSONArray();
            errorsList.add(errJsonObject);

            errResp.put("errors", errorsList);
            osw.write(errResp.toString(2));
            LOGGER.log(Level.SEVERE, "Error processing query", e);
        }
        osw.flush();
        osw.close();
    }

    @SuppressWarnings("unused")
    public void doSchema(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        Jenkins.getInstanceOrNull().checkPermission(Jenkins.ADMINISTER);
        rsp.setStatus(HttpServletResponse.SC_OK);
        rsp.setContentType("text/html");
        req.getView(this, "schema.jelly").forward(req, rsp);
    }

    @SuppressWarnings("unused")
    public void doClient(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        Jenkins.getInstanceOrNull().checkPermission(Jenkins.ADMINISTER);
        rsp.setStatus(HttpServletResponse.SC_OK);
        rsp.setContentType("text/html");
        req.getView(this, "client.jelly").forward(req, rsp);
    }

    @SuppressWarnings("unused")
    public void doPlayground(StaplerRequest req, StaplerResponse rsp) throws IOException, ServletException {
        Jenkins.getInstanceOrNull().checkPermission(Jenkins.ADMINISTER);
        rsp.setStatus(HttpServletResponse.SC_OK);
        rsp.setContentType("text/html");
        req.getView(this, "playground.jelly").forward(req, rsp);
    }

    @Override
    public String getSearchUrl() {
        return null;
    }
}
